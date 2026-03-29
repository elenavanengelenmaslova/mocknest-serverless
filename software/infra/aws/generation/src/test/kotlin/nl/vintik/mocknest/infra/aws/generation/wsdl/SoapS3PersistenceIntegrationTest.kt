package nl.vintik.mocknest.infra.aws.generation.wsdl

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.CreateBucketRequest
import aws.sdk.kotlin.services.s3.model.DeleteObjectRequest
import aws.sdk.kotlin.services.s3.model.ListObjectsV2Request
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.net.url.Url
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import nl.vintik.mocknest.application.generation.interfaces.GenerationStorageInterface
import nl.vintik.mocknest.application.generation.parsers.WsdlSpecificationParser
import nl.vintik.mocknest.application.generation.validators.SoapMockValidator
import nl.vintik.mocknest.application.generation.wsdl.WsdlParser
import nl.vintik.mocknest.application.generation.wsdl.WsdlSchemaReducer
import nl.vintik.mocknest.domain.generation.*
import nl.vintik.mocknest.infra.aws.generation.storage.S3GenerationStorageAdapter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * LocalStack integration test: inline WSDL XML → S3 persistence.
 *
 * Tests the complete flow:
 * inline WSDL XML → WsdlSpecificationParser → WsdlSchemaReducer → APISpecification
 *   → AI generation (mocked) → SoapMockValidator → GenerationStorageInterface → S3 persistence
 *
 * Verifies generated mocks are retrievable from S3 and match expected WireMock mapping structure.
 *
 * Validates: Requirements 10.1, 10.2, 12.9
 */
@Tag("soap-wsdl-ai-generation")
@Tag("integration")
@Testcontainers
class SoapS3PersistenceIntegrationTest {

    companion object {
        private const val TEST_BUCKET = "mocknest-soap-test-bucket"
        private const val TEST_REGION = "us-east-1"

        private lateinit var localStack: LocalStackContainer
        private lateinit var s3Client: S3Client
        private lateinit var storageAdapter: GenerationStorageInterface

        @BeforeAll
        @JvmStatic
        fun setupClass() {
            localStack = LocalStackContainer(
                DockerImageName.parse("localstack/localstack:4.12.0")
            ).withServices(
                LocalStackContainer.Service.S3
            ).waitingFor(
                Wait.forHttp("/_localstack/health").forStatusCode(200)
            )
            localStack.start()

            val s3Endpoint = localStack.getEndpointOverride(LocalStackContainer.Service.S3).toString()

            s3Client = S3Client {
                region = TEST_REGION
                endpointUrl = Url.parse(s3Endpoint)
                forcePathStyle = true  // Required for LocalStack
                credentialsProvider = StaticCredentialsProvider(
                    Credentials(
                        accessKeyId = localStack.accessKey,
                        secretAccessKey = localStack.secretKey
                    )
                )
            }

            runBlocking {
                s3Client.createBucket(CreateBucketRequest { bucket = TEST_BUCKET })
            }

            storageAdapter = S3GenerationStorageAdapter(s3Client, TEST_BUCKET)
        }
    }

    private val wsdlParser = WsdlParser()
    private val schemaReducer = WsdlSchemaReducer()
    private val soapValidator = SoapMockValidator()
    private val specParser = WsdlSpecificationParser(
        contentFetcher = mockk(relaxed = true),
        wsdlParser = wsdlParser,
        schemaReducer = schemaReducer
    )

    private fun loadWsdl(filename: String): String =
        this::class.java.getResource("/wsdl/$filename")?.readText()
            ?: error("WSDL test resource not found: $filename")

    @BeforeEach
    fun setUp() {
        // Nothing needed — each test uses unique job IDs
    }

    @AfterEach
    fun tearDown() {
        // Clean up all objects after each test
        runBlocking {
            runCatching {
                val listResponse = s3Client.listObjectsV2(ListObjectsV2Request { bucket = TEST_BUCKET })
                listResponse.contents?.forEach { obj ->
                    s3Client.deleteObject(DeleteObjectRequest {
                        bucket = TEST_BUCKET
                        key = obj.key!!
                    })
                }
            }
        }
    }

    private fun buildValidSoap11Mock(
        namespace: MockNamespace,
        operationName: String = "Add"
    ): GeneratedMock = GeneratedMock(
        id = UUID.randomUUID().toString(),
        name = "SOAP 1.1 $operationName mock",
        namespace = namespace,
        wireMockMapping = """
            {
              "request": {
                "method": "POST",
                "urlPath": "/CalculatorService",
                "headers": {
                  "SOAPAction": { "equalTo": "http://example.com/calculator-service/$operationName" }
                }
              },
              "response": {
                "status": 200,
                "headers": { "Content-Type": "text/xml; charset=utf-8" },
                "body": "<?xml version=\"1.0\" encoding=\"utf-8\"?><soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body><${operationName}Response xmlns=\"http://example.com/calculator-service\"><result>42</result></${operationName}Response></soap:Body></soap:Envelope>"
              },
              "persistent": true
            }
        """.trimIndent(),
        metadata = MockMetadata(
            sourceType = SourceType.SPEC_WITH_DESCRIPTION,
            sourceReference = "CalculatorService: test",
            endpoint = EndpointInfo(HttpMethod.POST, "/CalculatorService", 200, "text/xml")
        ),
        generatedAt = Instant.now()
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Task 4.8: LocalStack integration test — inline XML → S3 persistence
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `Given inline WSDL XML When running complete flow Then generated mocks are persisted to S3`() {
        runBlocking {
            // Given — parse inline WSDL XML
            val wsdlXml = loadWsdl("calculator-soap11.wsdl")
            val namespace = MockNamespace(apiName = "calculator-api")
            val jobId = "job-soap-s3-${UUID.randomUUID()}"

            val spec = specParser.parse(wsdlXml, SpecificationFormat.WSDL)
            assertEquals(SpecificationFormat.WSDL, spec.format)
            assertTrue(spec.endpoints.isNotEmpty(), "Should have endpoints from WSDL operations")

            // Simulate AI generation — produce valid SOAP mocks
            val generatedMocks = spec.endpoints.map { endpoint ->
                buildValidSoap11Mock(namespace, endpoint.operationId ?: "Add")
            }
            assertTrue(generatedMocks.isNotEmpty(), "Should have generated mocks")

            // Validate each mock with SoapMockValidator
            generatedMocks.forEach { mock ->
                val validationResult = soapValidator.validate(mock, spec)
                assertTrue(
                    validationResult.isValid,
                    "Mock ${mock.name} should pass SoapMockValidator. Errors: ${validationResult.errors}"
                )
            }

            // Persist to S3 via GenerationStorageInterface
            val storageKey = storageAdapter.storeGeneratedMocks(generatedMocks, jobId)
            assertNotNull(storageKey, "Storage key should not be null")

            // Verify mocks are retrievable from S3
            val retrievedMocks = storageAdapter.getGeneratedMocks(jobId)
            assertEquals(generatedMocks.size, retrievedMocks.size, "Retrieved mock count should match stored count")

            // Verify each retrieved mock has the expected WireMock mapping structure
            retrievedMocks.forEach { mock ->
                assertTrue(mock.wireMockMapping.isNotBlank(), "WireMock mapping should not be blank")
                assertTrue(mock.wireMockMapping.contains("\"method\""), "Should contain 'method' field")
                assertTrue(mock.wireMockMapping.contains("\"response\""), "Should contain 'response' field")
                assertTrue(mock.wireMockMapping.contains("\"request\""), "Should contain 'request' field")
            }
        }
    }

    @Test
    fun `Given inline WSDL XML When persisting spec Then specification is retrievable from S3`() {
        runBlocking {
            // Given
            val wsdlXml = loadWsdl("calculator-soap11.wsdl")
            val namespace = MockNamespace(apiName = "calculator-api-spec-${UUID.randomUUID()}")

            val spec = specParser.parse(wsdlXml, SpecificationFormat.WSDL)

            // When — store the specification
            val storageKey = storageAdapter.storeSpecification(namespace, spec, "1.0")
            assertNotNull(storageKey, "Storage key should not be null")

            // Then — retrieve and verify
            val retrievedSpec = storageAdapter.getSpecification(namespace, null)
            assertNotNull(retrievedSpec, "Specification should be retrievable from S3")
            assertEquals(spec.title, retrievedSpec.title, "Title should match")
            assertEquals(SpecificationFormat.WSDL, retrievedSpec.format, "Format should be WSDL")
            assertEquals(spec.endpoints.size, retrievedSpec.endpoints.size, "Endpoint count should match")
        }
    }

    @Test
    fun `Given SOAP mocks When stored and retrieved Then WireMock mapping structure is preserved`() {
        runBlocking {
            // Given
            val wsdlXml = loadWsdl("calculator-soap11.wsdl")
            val namespace = MockNamespace(apiName = "calculator-api-mapping-${UUID.randomUUID()}")
            val jobId = "job-soap-mapping-${UUID.randomUUID()}"

            val spec = specParser.parse(wsdlXml, SpecificationFormat.WSDL)
            val mock = buildValidSoap11Mock(namespace, "Add")

            // When — store and retrieve
            storageAdapter.storeGeneratedMocks(listOf(mock), jobId)
            val retrievedMocks = storageAdapter.getGeneratedMocks(jobId)

            // Then — WireMock mapping structure is preserved
            assertEquals(1, retrievedMocks.size, "Should retrieve exactly 1 mock")
            val retrievedMock = retrievedMocks.first()

            assertEquals(mock.id, retrievedMock.id, "Mock ID should be preserved")
            assertEquals(mock.name, retrievedMock.name, "Mock name should be preserved")

            // Verify the WireMock mapping is still valid after round-trip through S3
            val validationResult = soapValidator.validate(retrievedMock, spec)
            assertTrue(
                validationResult.isValid,
                "Retrieved mock should still pass SoapMockValidator. Errors: ${validationResult.errors}"
            )
        }
    }

    @Test
    fun `Given multiple SOAP operations When all mocks stored Then all are retrievable from S3`() {
        runBlocking {
            // Given — calculator has 3 operations: Add, Subtract, Multiply
            val wsdlXml = loadWsdl("calculator-soap11.wsdl")
            val namespace = MockNamespace(apiName = "calculator-api-multi-${UUID.randomUUID()}")
            val jobId = "job-soap-multi-${UUID.randomUUID()}"

            val spec = specParser.parse(wsdlXml, SpecificationFormat.WSDL)
            val operationNames = spec.endpoints.mapNotNull { it.operationId }
            assertTrue(operationNames.isNotEmpty(), "Calculator WSDL should have operations")

            val mocks = operationNames.map { opName -> buildValidSoap11Mock(namespace, opName) }

            // When — store all mocks
            storageAdapter.storeGeneratedMocks(mocks, jobId)

            // Then — all mocks are retrievable
            val retrievedMocks = storageAdapter.getGeneratedMocks(jobId)
            assertEquals(mocks.size, retrievedMocks.size, "All mocks should be retrievable from S3")

            val retrievedIds = retrievedMocks.map { it.id }.toSet()
            mocks.forEach { mock ->
                assertTrue(retrievedIds.contains(mock.id), "Mock ${mock.id} should be retrievable")
            }
        }
    }
}
