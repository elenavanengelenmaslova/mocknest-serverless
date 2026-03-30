package nl.vintik.mocknest.application.generation.validators

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nl.vintik.mocknest.application.generation.interfaces.MockValidationResult
import nl.vintik.mocknest.application.generation.interfaces.MockValidatorInterface
import nl.vintik.mocknest.domain.generation.APISpecification
import nl.vintik.mocknest.domain.generation.EndpointDefinition
import nl.vintik.mocknest.domain.generation.GeneratedMock
import nl.vintik.mocknest.domain.generation.SoapVersion
import nl.vintik.mocknest.domain.generation.SpecificationFormat
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

private val logger = KotlinLogging.logger {}

/**
 * Validates generated WireMock SOAP mock mappings against the CompactWsdl embedded
 * in the APISpecification metadata.
 *
 * Applies all 7 SOAP validation rules in order, collecting all errors before returning.
 */
class SoapMockValidator : MockValidatorInterface {

    override suspend fun validate(mock: GeneratedMock, specification: APISpecification): MockValidationResult {
        if (specification.format != SpecificationFormat.WSDL) {
            return MockValidationResult.valid()
        }

        logger.debug { "Validating SOAP mock: ${mock.id}" }

        val errors = mutableListOf<String>()

        return runCatching {
            val wireMockJson = Json.parseToJsonElement(mock.wireMockMapping).jsonObject

            val requestNode = wireMockJson["request"]?.jsonObject
                ?: return MockValidationResult.invalid(listOf("Missing request section in WireMock mapping"))
            val responseNode = wireMockJson["response"]?.jsonObject
                ?: return MockValidationResult.invalid(listOf("Missing response section in WireMock mapping"))

            val soapVersion = resolveSoapVersion(specification)
            val targetNamespace = specification.metadata["targetNamespace"] ?: ""

            // Rule 1: Request method must be POST
            val method = requestNode["method"]?.jsonPrimitive?.content
            if (method == null || !method.equals("POST", ignoreCase = true)) {
                errors.add("Request method must be POST, found: ${method ?: "null"}")
            }

            // Rule 2: SOAPAction / action in Content-Type references a valid operation
            errors.addAll(validateSoapAction(requestNode, soapVersion, specification.endpoints))

            // Rules 3–6: Response body XML validation
            val responseBody = extractResponseBody(responseNode)
            if (responseBody != null) {
                val xmlResult = parseXml(responseBody)
                if (xmlResult == null) {
                    errors.add("Response body is not well-formed XML: unable to parse response body")
                } else {
                    errors.addAll(validateSoapEnvelope(xmlResult, soapVersion, targetNamespace))
                }
            } else {
                errors.add("Response body is not well-formed XML: response body is missing")
            }

            // Rule 7: Response Content-Type header matches SOAP version
            errors.addAll(validateContentTypeHeader(responseNode, soapVersion))

            if (errors.isEmpty()) {
                logger.debug { "SOAP mock validation passed: ${mock.id}" }
                MockValidationResult.valid()
            } else {
                logger.warn { "SOAP mock validation failed: ${mock.id}, errors: ${errors.size}" }
                MockValidationResult.invalid(errors)
            }
        }.fold(
            onSuccess = { it },
            onFailure = { exception ->
                logger.error(exception) { "SOAP mock validation error: ${mock.id}" }
                MockValidationResult.invalid(
                    listOf("Validation error: ${exception.message ?: "Unknown error"}")
                )
            }
        )
    }

    private fun resolveSoapVersion(specification: APISpecification): SoapVersion {
        val versionName = specification.metadata["soapVersion"]
        return runCatching { SoapVersion.valueOf(versionName ?: "") }.getOrDefault(SoapVersion.SOAP_1_1)
    }

    /**
     * Rule 2: SOAPAction header (SOAP 1.1) or action in Content-Type (SOAP 1.2) references a valid operation.
     */
    private fun validateSoapAction(
        requestNode: JsonObject,
        soapVersion: SoapVersion,
        endpoints: List<EndpointDefinition>
    ): List<String> {
        val headers = requestNode["headers"]?.jsonObject

        val action: String? = when (soapVersion) {
            SoapVersion.SOAP_1_1 -> {
                // SOAPAction header — check headers map for SOAPAction key (case-insensitive)
                headers?.entries
                    ?.firstOrNull { it.key.equals("SOAPAction", ignoreCase = true) }
                    ?.value
                    ?.let { extractMatcherValue(it) }
            }
            SoapVersion.SOAP_1_2 -> {
                // action parameter in Content-Type header
                headers?.entries
                    ?.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
                    ?.value
                    ?.let { extractMatcherValue(it) }
                    ?.let { extractActionFromContentType(it) }
            }
        }

        if (action == null) {
            return listOf("Missing SOAPAction header")
        }

        // Strip surrounding quotes if present
        val cleanAction = action.trim().removeSurrounding("\"")

        // Check if the action matches any endpoint: exact soapAction metadata match when available, fallback otherwise
        val matchesOperation = endpoints.any { endpoint ->
            val metaSoapAction = endpoint.metadata["soapAction"]
            if (metaSoapAction != null) {
                cleanAction == metaSoapAction
            } else {
                val opName = endpoint.operationId ?: return@any false
                cleanAction.equals(opName, ignoreCase = true) ||
                    cleanAction.endsWith("/$opName") ||
                    cleanAction.endsWith("#$opName")
            }
        }

        return if (!matchesOperation) {
            listOf("SOAPAction '$cleanAction' does not match any operation in the WSDL")
        } else {
            emptyList()
        }
    }

    /**
     * Extracts the string value from a WireMock header matcher element.
     * Handles equalTo, contains, matches patterns.
     */
    private fun extractMatcherValue(element: JsonElement): String? {
        return when (element) {
            is JsonPrimitive -> element.content
            is JsonObject -> {
                element["equalTo"]?.jsonPrimitive?.content
                    ?: element["contains"]?.jsonPrimitive?.content
                    ?: element["matches"]?.jsonPrimitive?.content
            }
            is JsonArray -> null
            is JsonNull -> null
        }
    }

    /**
     * Extracts the action parameter value from a SOAP 1.2 Content-Type header value.
     * e.g. "application/soap+xml; action=\"MyOperation\"" → "MyOperation"
     */
    private fun extractActionFromContentType(contentType: String): String? {
        val actionRegex = """action\s*=\s*"?([^";,\s]+)"?""".toRegex(RegexOption.IGNORE_CASE)
        return actionRegex.find(contentType)?.groupValues?.get(1)
    }

    /**
     * Extracts the response body string from the WireMock response node.
     */
    private fun extractResponseBody(responseNode: JsonObject): String? {
        return responseNode["body"]?.jsonPrimitive?.content
    }

    /**
     * Parses XML string using DocumentBuilder. Returns the root Element or null on failure.
     */
    private fun parseXml(xml: String): ParsedXmlResult? {
        return runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                isXIncludeAware = false
                isExpandEntityReferences = false
            }
            val builder = factory.newDocumentBuilder()
            builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
            val doc = builder.parse(InputSource(StringReader(xml)))
            doc.documentElement?.let { ParsedXmlResult(it, null) }
        }.fold(
            onSuccess = { it },
            onFailure = { exception ->
                ParsedXmlResult(null, exception.message ?: "XML parse error")
            }
        )
    }

    /**
     * Rules 3–6: Validates the SOAP envelope structure.
     */
    private fun validateSoapEnvelope(
        result: ParsedXmlResult,
        soapVersion: SoapVersion,
        targetNamespace: String
    ): List<String> {
        val errors = mutableListOf<String>()

        // Rule 3: Well-formed XML
        if (result.parseError != null) {
            errors.add("Response body is not well-formed XML: ${result.parseError}")
            return errors
        }

        val root = result.root ?: run {
            errors.add("Response body is not well-formed XML: empty document")
            return errors
        }

        // Rule 4: Root element must be Envelope with correct namespace
        val expectedNs = soapVersion.envelopeNamespace
        val foundNs = root.namespaceURI ?: ""
        val localName = root.localName ?: root.nodeName

        if (localName != "Envelope" || foundNs != expectedNs) {
            errors.add(
                "SOAP Envelope element missing or has wrong namespace. Expected: $expectedNs, found: $foundNs"
            )
            return errors // Can't validate further without a valid Envelope
        }

        // Rule 5: Envelope must contain Body child in same namespace
        val bodyElement = findChildElement(root, "Body", expectedNs)
        if (bodyElement == null) {
            errors.add("SOAP Body element missing inside Envelope")
            return errors
        }

        // Rule 6: Element inside Body must use correct target namespace
        if (targetNamespace.isNotBlank()) {
            val bodyChild = firstChildElement(bodyElement)
            if (bodyChild != null) {
                val bodyChildNs = bodyChild.namespaceURI ?: ""
                if (bodyChildNs != targetNamespace) {
                    errors.add(
                        "Response body element namespace does not match WSDL targetNamespace. Expected: $targetNamespace"
                    )
                }
            }
        }

        return errors
    }

    /**
     * Rule 7: Response Content-Type header matches SOAP version.
     */
    private fun validateContentTypeHeader(
        responseNode: JsonObject,
        soapVersion: SoapVersion
    ): List<String> {
        val headers = responseNode["headers"]?.jsonObject
            ?: return listOf("Response is missing 'headers'; expected a Content-Type header")

        val contentTypeEntry = headers.entries
            .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
            ?: return listOf("Response headers are missing the required 'Content-Type' header")

        val contentType = contentTypeEntry.value.jsonPrimitive.content
        val expectedContentType = soapVersion.contentType

        return if (!contentType.contains(expectedContentType, ignoreCase = true)) {
            listOf("Content-Type header '$contentType' does not match SOAP version. Expected: $expectedContentType")
        } else {
            emptyList()
        }
    }

    private fun findChildElement(parent: Element, localName: String, namespace: String): Element? {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.localName == localName && node.namespaceURI == namespace) {
                return node
            }
        }
        return null
    }

    private fun firstChildElement(parent: Element): Element? {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element) return node
        }
        return null
    }
}

private data class ParsedXmlResult(
    val root: Element?,
    val parseError: String?
)
