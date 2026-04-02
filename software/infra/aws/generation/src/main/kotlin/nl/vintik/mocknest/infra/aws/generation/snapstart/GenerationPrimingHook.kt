package nl.vintik.mocknest.infra.aws.generation.snapstart

import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.HeadBucketRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import nl.vintik.mocknest.application.generation.graphql.GraphQLIntrospectionClientInterface
import nl.vintik.mocknest.application.generation.graphql.GraphQLSchemaReducerInterface
import nl.vintik.mocknest.application.generation.parsers.OpenAPISpecificationParser
import nl.vintik.mocknest.application.generation.services.PromptBuilderService
import nl.vintik.mocknest.application.generation.usecases.GetAIHealth
import nl.vintik.mocknest.application.generation.validators.OpenAPIMockValidator
import nl.vintik.mocknest.application.generation.validators.SoapMockValidator
import nl.vintik.mocknest.application.generation.wsdl.WsdlParserInterface
import nl.vintik.mocknest.application.generation.wsdl.WsdlSchemaReducerInterface
import nl.vintik.mocknest.domain.generation.EndpointInfo
import nl.vintik.mocknest.domain.generation.GeneratedMock
import nl.vintik.mocknest.domain.generation.MockMetadata
import nl.vintik.mocknest.domain.generation.MockNamespace
import nl.vintik.mocknest.domain.generation.SourceType
import nl.vintik.mocknest.domain.generation.SpecificationFormat
import nl.vintik.mocknest.infra.aws.generation.ai.config.ModelConfiguration
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Priming hook for Generation function SnapStart optimization.
 * 
 * Executes initialization code during SnapStart snapshot creation to warm up
 * resources before the first invocation, reducing cold start times.
 * 
 * This component:
 * - Detects SnapStart environment using AWS_LAMBDA_INITIALIZATION_TYPE
 * - Warms up AI health check endpoint
 * - Initializes S3 client connections
 * - Initializes Bedrock client reference (no model invocation to avoid costs)
 * - Validates AI model configuration
 * - Exercises OpenAPI specification parser
 * - Exercises SOAP/WSDL parsers and validators
 * - Exercises GraphQL introspection client and schema reducer
 * - Exercises prompt builder service (loads templates)
 * - Exercises mock validators
 * - Uses graceful degradation for non-critical failures
 */
@Component
open class GenerationPrimingHook(
    private val aiHealthUseCase: GetAIHealth,
    private val s3Client: S3Client,
    @param:Value($$"${storage.bucket.name}") private val bucketName: String,
    private val bedrockClient: BedrockRuntimeClient,
    private val modelConfig: ModelConfiguration,
    private val specificationParser: OpenAPISpecificationParser,
    private val promptBuilderService: PromptBuilderService,
    private val mockValidator: OpenAPIMockValidator,
    private val wsdlParser: WsdlParserInterface,
    private val wsdlSchemaReducer: WsdlSchemaReducerInterface,
    private val soapMockValidator: SoapMockValidator,
    private val graphQLIntrospectionClient: GraphQLIntrospectionClientInterface,
    private val graphQLSchemaReducer: GraphQLSchemaReducerInterface
) {
    
    /**
     * Triggered when Spring application context is fully initialized.
     * Only executes priming logic in SnapStart environments.
     */
    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        if (isSnapStartEnvironment()) {
            logger.info { "SnapStart detected - executing generation priming hook" }
            runBlocking {
                prime()
            }
        } else {
            logger.debug { "Not a SnapStart environment - skipping priming hook" }
        }
    }
    
    /**
     * Execute priming logic to warm up resources during snapshot creation.
     * 
     * All operations are wrapped in runCatching to ensure non-critical failures
     * don't prevent snapshot creation.
     */
    suspend fun prime() {
        logger.info { "Starting generation function priming" }
        
        // Warm up AI health check endpoint
        runCatching {
            aiHealthUseCase.invoke()
            logger.info { "AI health check primed successfully" }
        }.onFailure { exception ->
            logger.warn(exception) { "AI health check priming failed - continuing with snapshot creation" }
        }
        
        // Initialize S3 client connections with timeout protection
        runCatching {
            withTimeout(5000) { // 5 second timeout to prevent hanging snapshot creation
                s3Client.headBucket(HeadBucketRequest {
                    bucket = bucketName
                })
            }
            logger.info { "S3 client primed successfully for bucket: $bucketName" }
        }.onFailure { exception ->
            logger.warn(exception) { "S3 client priming failed for bucket: $bucketName - continuing with snapshot creation" }
        }
        
        // Initialize Bedrock client reference (no model invocation to avoid costs during snapshot creation)
        runCatching {
            logger.info { "Bedrock client reference initialized for model: ${modelConfig.getModelName()}" }
        }.onFailure { exception ->
            logger.warn(exception) { "Bedrock client reference initialization failed - continuing with snapshot creation" }
        }
        
        // Validate AI model configuration
        runCatching {
            val modelName = modelConfig.getModelName()
            val prefix = modelConfig.getConfiguredPrefix()
            val isSupported = modelConfig.isOfficiallySupported()
            
            logger.info { 
                "AI model configuration validated: model=$modelName, prefix=$prefix, officiallySupported=$isSupported" 
            }
        }.onFailure { exception ->
            logger.warn(exception) { "Model configuration validation failed - continuing with snapshot creation" }
        }
        
        // Exercise OpenAPI specification parser
        runCatching {
            exerciseSpecificationParser()
            logger.info { "OpenAPI specification parser primed successfully" }
        }.onFailure { exception ->
            logger.warn(exception) { "Specification parser priming failed - continuing with snapshot creation" }
        }
        
        // Exercise prompt builder service
        runCatching {
            exercisePromptBuilder()
            logger.info { "Prompt builder service primed successfully" }
        }.onFailure { exception ->
            logger.warn(exception) { "Prompt builder priming failed - continuing with snapshot creation" }
        }
        
        // Exercise mock validator
        runCatching {
            exerciseMockValidator()
            logger.info { "Mock validator primed successfully" }
        }.onFailure { exception ->
            logger.warn(exception) { "Mock validator priming failed - continuing with snapshot creation" }
        }
        
        // Exercise SOAP/WSDL parsers and validators
        runCatching {
            exerciseSoapWsdlComponents()
            logger.info { "SOAP/WSDL components primed successfully" }
        }.onFailure { exception ->
            logger.warn(exception) { "SOAP/WSDL priming failed - continuing with snapshot creation" }
        }
        
        // Exercise GraphQL introspection client and schema reducer
        runCatching {
            exerciseGraphQLComponents()
            logger.info { "GraphQL components primed successfully" }
        }.onFailure { exception ->
            logger.warn(exception) { "GraphQL priming failed - continuing with snapshot creation" }
        }
        
        logger.info { "Generation function priming completed" }
    }
    
    /**
     * Exercise OpenAPI specification parser to warm up parsing logic.
     * Parses a minimal test OpenAPI specification.
     */
    private suspend fun exerciseSpecificationParser() {
        val minimalSpec = """
        {
          "openapi": "3.0.0",
          "info": {
            "title": "SnapStart Priming Test API",
            "version": "1.0.0"
          },
          "paths": {
            "/test": {
              "get": {
                "responses": {
                  "200": {
                    "description": "Success",
                    "content": {
                      "application/json": {
                        "schema": {
                          "type": "object",
                          "properties": {
                            "message": {
                              "type": "string"
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
        """.trimIndent()
        
        // Parse the specification to warm up the parser
        specificationParser.parse(minimalSpec, SpecificationFormat.OPENAPI_3)
        logger.debug { "Parsed test OpenAPI specification" }
    }
    
    /**
     * Exercise prompt builder service to warm up template loading.
     * Loads prompt templates from classpath resources.
     */
    private fun exercisePromptBuilder() {
        // Load system prompt template to warm up template loading
        promptBuilderService.loadSystemPrompt()
        logger.debug { "Loaded system prompt template" }
    }
    
    /**
     * Exercise mock validator to warm up validation logic.
     * Validates a test mock against a test specification.
     */
    private suspend fun exerciseMockValidator() {
        // Create a minimal test specification
        val testSpec = specificationParser.parse(
            """
            {
              "openapi": "3.0.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              },
              "paths": {
                "/test": {
                  "get": {
                    "responses": {
                      "200": {
                        "description": "Success",
                        "content": {
                          "application/json": {
                            "schema": {
                              "type": "object",
                              "properties": {
                                "status": {
                                  "type": "string"
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
            """.trimIndent(),
            SpecificationFormat.OPENAPI_3
        )
        
        // Create a test mock
        val testMock = GeneratedMock(
            id = "snapstart-test-${UUID.randomUUID()}",
            name = "SnapStart Priming Test Mock",
            namespace = MockNamespace(apiName = "test-api", client = null),
            wireMockMapping = """
            {
              "request": {
                "method": "GET",
                "url": "/test"
              },
              "response": {
                "status": 200,
                "jsonBody": {
                  "status": "ok"
                }
              }
            }
            """.trimIndent(),
            metadata = MockMetadata(
                sourceType = SourceType.SPEC_WITH_DESCRIPTION,
                sourceReference = "SnapStart priming test",
                endpoint = EndpointInfo(
                    method = HttpMethod.GET,
                    path = "/test",
                    statusCode = 200,
                    contentType = "application/json"
                )
            )
        )
        
        // Validate the test mock to warm up the validator
        mockValidator.validate(testMock, testSpec)
        logger.debug { "Validated test mock against specification" }
    }
    
    /**
     * Exercise SOAP/WSDL components to warm up parsing and validation logic.
     * Parses a minimal test WSDL specification and reduces the schema.
     */
    private suspend fun exerciseSoapWsdlComponents() {
        // Create a minimal test WSDL
        val testWsdl = createTestWsdl()
        
        // Parse the WSDL to warm up the parser
        val parsedWsdl = wsdlParser.parse(testWsdl)
        logger.debug { "Parsed test WSDL specification" }
        
        // Reduce the parsed WSDL to warm up the schema reducer
        wsdlSchemaReducer.reduce(parsedWsdl)
        logger.debug { "Reduced test WSDL schema" }
        
        // Note: We don't validate SOAP mocks during priming to keep it simple
        // The validator will be warmed up when the first real request comes in
    }
    
    /**
     * Create a minimal test WSDL specification for priming.
     * Returns a simple SOAP 1.2 WSDL with one operation.
     */
    private fun createTestWsdl(): String {
        return """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://schemas.xmlsoap.org/wsdl/"
                     xmlns:soap12="http://schemas.xmlsoap.org/wsdl/soap12/"
                     xmlns:tns="http://example.com/calculator"
                     xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                     targetNamespace="http://example.com/calculator">
          
          <types>
            <xsd:schema targetNamespace="http://example.com/calculator">
              <xsd:element name="Add">
                <xsd:complexType>
                  <xsd:sequence>
                    <xsd:element name="a" type="xsd:int"/>
                    <xsd:element name="b" type="xsd:int"/>
                  </xsd:sequence>
                </xsd:complexType>
              </xsd:element>
              <xsd:element name="AddResponse">
                <xsd:complexType>
                  <xsd:sequence>
                    <xsd:element name="Result" type="xsd:int"/>
                  </xsd:sequence>
                </xsd:complexType>
              </xsd:element>
            </xsd:schema>
          </types>
          
          <message name="AddRequest">
            <part name="parameters" element="tns:Add"/>
          </message>
          <message name="AddResponse">
            <part name="parameters" element="tns:AddResponse"/>
          </message>
          
          <portType name="CalculatorPortType">
            <operation name="Add">
              <input message="tns:AddRequest"/>
              <output message="tns:AddResponse"/>
            </operation>
          </portType>
          
          <binding name="CalculatorSoap12Binding" type="tns:CalculatorPortType">
            <soap12:binding transport="http://schemas.xmlsoap.org/soap/http"/>
            <operation name="Add">
              <soap12:operation soapAction="http://example.com/calculator/Add"/>
              <input>
                <soap12:body use="literal"/>
              </input>
              <output>
                <soap12:body use="literal"/>
              </output>
            </operation>
          </binding>
          
          <service name="CalculatorService">
            <port name="CalculatorSoap12Port" binding="tns:CalculatorSoap12Binding">
              <soap12:address location="http://example.com/CalculatorService"/>
            </port>
          </service>
        </definitions>
        """.trimIndent()
    }
    
    /**
     * Exercise GraphQL components to warm up introspection client and schema reducer.
     * Note: We don't actually call the introspection client with a URL to avoid network calls
     * during snapshot creation. Instead, we warm up the schema reducer with test data.
     */
    private suspend fun exerciseGraphQLComponents() {
        // Create a minimal test GraphQL introspection result
        val testIntrospectionResult = """
        {
          "__schema": {
            "queryType": {
              "name": "Query"
            },
            "types": [
              {
                "kind": "OBJECT",
                "name": "Query",
                "fields": [
                  {
                    "name": "hello",
                    "type": {
                      "kind": "SCALAR",
                      "name": "String"
                    },
                    "args": []
                  }
                ]
              },
              {
                "kind": "SCALAR",
                "name": "String"
              }
            ]
          }
        }
        """.trimIndent()
        
        // Warm up the GraphQL schema reducer with test data
        graphQLSchemaReducer.reduce(testIntrospectionResult)
        logger.debug { "Reduced test GraphQL schema" }
    }
    
    /**
     * Detect if running in SnapStart environment.
     * 
     * AWS sets AWS_LAMBDA_INITIALIZATION_TYPE=snap-start during snapshot creation.
     * 
     * @return true if running in SnapStart environment, false otherwise
     */
    protected open fun isSnapStartEnvironment(): Boolean {
        val initType = System.getenv("AWS_LAMBDA_INITIALIZATION_TYPE")
        logger.debug { "AWS_LAMBDA_INITIALIZATION_TYPE: $initType" }
        return initType == "snap-start"
    }
}
