package nl.vintik.mocknest.application.generation.parsers

import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.generation.interfaces.SpecificationParserInterface
import nl.vintik.mocknest.application.generation.util.SafeUrlResolver
import nl.vintik.mocknest.application.generation.wsdl.WsdlContentFetcherInterface
import nl.vintik.mocknest.application.generation.wsdl.WsdlParserInterface
import nl.vintik.mocknest.application.generation.wsdl.WsdlSchemaReducerInterface
import nl.vintik.mocknest.domain.generation.*
import org.springframework.http.HttpMethod
import java.net.URI

private val logger = KotlinLogging.logger {}

/**
 * Parser for WSDL/SOAP specifications.
 * Supports both inline XML content and URL-based fetching.
 */
class WsdlSpecificationParser(
    private val contentFetcher: WsdlContentFetcherInterface,
    private val wsdlParser: WsdlParserInterface,
    private val schemaReducer: WsdlSchemaReducerInterface
) : SpecificationParserInterface {

    override suspend fun parse(content: String, format: SpecificationFormat): APISpecification {
        require(format == SpecificationFormat.WSDL) { "Only WSDL format supported" }

        logger.info { "Parsing WSDL specification" }

        return runCatching {
            val wsdlXml = resolveWsdlContent(content)
            val parsedWsdl = wsdlParser.parse(wsdlXml)
            val compactWsdl = schemaReducer.reduce(parsedWsdl)
            convertToAPISpecification(compactWsdl)
        }.onFailure { exception ->
            logger.error(exception) { "Failed to parse WSDL specification" }
        }.getOrThrow()
    }

    override fun supports(format: SpecificationFormat): Boolean = format == SpecificationFormat.WSDL

    override suspend fun validate(content: String, format: SpecificationFormat): ValidationResult {
        require(format == SpecificationFormat.WSDL) { "Only WSDL format supported" }

        logger.debug { "Validating WSDL specification" }

        return runCatching {
            val wsdlXml = resolveWsdlContent(content)
            val parsedWsdl = wsdlParser.parse(wsdlXml)
            schemaReducer.reduce(parsedWsdl)
            ValidationResult.valid()
        }.fold(
            onSuccess = { it },
            onFailure = { exception ->
                logger.warn(exception) { "WSDL specification validation failed" }
                ValidationResult.invalid(
                    listOf(
                        ValidationError(
                            message = exception.message ?: "Unknown validation error",
                            path = null
                        )
                    )
                )
            }
        )
    }

    override suspend fun extractMetadata(content: String, format: SpecificationFormat): SpecificationMetadata {
        require(format == SpecificationFormat.WSDL) { "Only WSDL format supported" }

        logger.debug { "Extracting metadata from WSDL specification" }

        return runCatching {
            val wsdlXml = resolveWsdlContent(content)
            val parsedWsdl = wsdlParser.parse(wsdlXml)
            val compactWsdl = schemaReducer.reduce(parsedWsdl)

            SpecificationMetadata(
                title = compactWsdl.serviceName,
                version = "1.0",
                format = SpecificationFormat.WSDL,
                endpointCount = compactWsdl.operations.size,
                schemaCount = compactWsdl.xsdTypes.size
            )
        }.onFailure { exception ->
            logger.error(exception) { "Failed to extract metadata from WSDL specification" }
        }.getOrThrow()
    }

    private suspend fun resolveWsdlContent(content: String): String {
        val trimmed = content.trim()
        return if (SafeUrlResolver.isHttpUrl(trimmed)) {
            logger.info { "Detected URL input, fetching WSDL" }
            contentFetcher.fetch(trimmed)
        } else {
            logger.debug { "Using inline WSDL XML content" }
            trimmed
        }
    }

    private fun serviceAddressPath(compactWsdl: CompactWsdl): String {
        val address = compactWsdl.serviceAddress
        if (!address.isNullOrBlank()) {
            runCatching {
                val path = URI(address).path
                if (!path.isNullOrBlank() && path != "/") return path
            }
        }
        return "/${compactWsdl.serviceName}"
    }

    private fun convertToAPISpecification(compactWsdl: CompactWsdl): APISpecification {
        logger.debug { "Converting CompactWsdl to APISpecification: service=${compactWsdl.serviceName}, operations=${compactWsdl.operations.size}" }

        val endpoints = compactWsdl.operations.map { operation ->
            // Look up binding for this operation to get its specific service address
            val bindingKey = "${operation.portTypeName}#${operation.name}"
            val binding = compactWsdl.operationBindings[bindingKey]
            
            // Use operation-specific service address if available, otherwise fall back to default
            val endpointPath = if (binding != null && binding.serviceAddress.isNotBlank()) {
                runCatching {
                    val path = URI(binding.serviceAddress).path
                    if (!path.isNullOrBlank() && path != "/") path else "/${compactWsdl.serviceName}"
                }.getOrElse { "/${compactWsdl.serviceName}" }
            } else {
                serviceAddressPath(compactWsdl)
            }

            EndpointDefinition(
                path = endpointPath,
                method = HttpMethod.POST,
                operationId = operation.name,
                summary = operation.name,
                parameters = emptyList(),
                requestBody = RequestBodyDefinition(
                    required = true,
                    content = mapOf(
                        compactWsdl.soapVersion.contentType to MediaTypeDefinition(
                            schema = JsonSchema(
                                type = JsonSchemaType.STRING,
                                description = "SOAP XML request body for ${operation.inputMessage}"
                            )
                        )
                    ),
                    description = "SOAP request for operation ${operation.name}"
                ),
                responses = mapOf(
                    200 to ResponseDefinition(
                        statusCode = 200,
                        description = "Successful SOAP response for ${operation.name}",
                        schema = JsonSchema(
                            type = JsonSchemaType.STRING,
                            description = "SOAP XML response body for ${operation.outputMessage}"
                        )
                    )
                ),
                metadata = mapOf("soapAction" to operation.soapAction)
            )
        }

        return APISpecification(
            format = SpecificationFormat.WSDL,
            version = "1.0",
            title = compactWsdl.serviceName,
            endpoints = endpoints,
            schemas = compactWsdl.xsdTypes.mapValues { (_, xsdType) ->
                JsonSchema(
                    type = JsonSchemaType.OBJECT,
                    properties = xsdType.fields.associate { field ->
                        field.name to JsonSchema(
                            type = JsonSchemaType.STRING,
                            description = field.type
                        )
                    },
                    description = xsdType.name
                )
            },
            metadata = mapOf(
                "targetNamespace" to compactWsdl.targetNamespace,
                "soapVersion" to compactWsdl.soapVersion.name,
                "operationCount" to compactWsdl.operations.size.toString(),
                "serviceAddress" to (compactWsdl.serviceAddress ?: "")
            ),
            rawContent = compactWsdl.prettyPrint()
        )
    }
}
