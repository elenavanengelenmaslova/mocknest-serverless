package nl.vintik.mocknest.infra.aws.function

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import nl.vintik.mocknest.infra.aws.config.AwsLocalStackTestConfiguration
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.ContextConfiguration
import kotlin.test.assertEquals
import kotlin.test.assertContains
import org.springframework.beans.factory.annotation.*
import nl.vintik.mocknest.infra.aws.Application

@SpringBootTest(classes = [Application::class])
@TestPropertySource(locations = ["classpath:application-test.properties"])
@ContextConfiguration(classes = [AwsLocalStackTestConfiguration::class])
class SoapMockingIntegrationTest {

    // Spring Boot will inject the lambda handler
    @Autowired
    private lateinit var lambdaHandler: MockNestLambdaHandler

    // Spring Boot will inject the test storage
    @Autowired
    private lateinit var storage: ObjectStorageInterface

    @BeforeEach
    suspend fun setup() {
    }

    @AfterEach
    suspend fun tearDown() {
        val keys = storage.list().toList()
        keys.forEach { key -> storage.delete(key) }
    }

    private fun createApiGatewayEvent(
        httpMethod: String,
        path: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
        queryStringParameters: Map<String, String> = emptyMap()
    ): APIGatewayProxyRequestEvent {
        return APIGatewayProxyRequestEvent().apply {
            this.httpMethod = httpMethod
            this.path = path
            this.body = body
            this.headers = headers
            this.queryStringParameters = queryStringParameters.takeIf { it.isNotEmpty() }
        }
    }

    @Test
    fun `Given SOAP API mock When setting up via admin API and calling as client Then should return XML response from S3`() {
        // Given - Create SOAP API mock via admin API (simplified matching)
        val soapMockMapping = """
            {
  "id": "76ada7b0-55ae-4229-91c4-396a36f18123",
  "priority": 1,
  "request": {
    "method": "POST",
    "url": "/dneonline/calculator.asmx",
    "bodyPatterns": [
      {
        "matchesXPath": "//*[local-name()='intA' and text()='5']"
      },
      {
        "matchesXPath": "//*[local-name()='intB' and text()='3']"
      }
    ]
  },
  "response": {
    "status": 200,
    "headers": {
      "Content-Type": "text/xml; charset=utf-8"
    },
    "body": "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n  <soap:Body>\n    <AddResponse xmlns=\"http://tempuri.org/\">\n      <AddResult>42</AddResult>\n    </AddResponse>\n  </soap:Body>\n</soap:Envelope>"
  },
  "persistent": true
}""".trimIndent()

        val adminRequest = createApiGatewayEvent(
            httpMethod = "POST",
            path = "/__admin/mappings",
            body = soapMockMapping,
            headers = mapOf("Content-Type" to "application/json")
        )

        // When - Set up mock via admin API
        val adminResponse = lambdaHandler.router().apply(adminRequest)

        // Then - Admin API should succeed
        assertEquals(201, adminResponse.statusCode)

        // When - Call the SOAP endpoint as client
        val soapRequestBody = """
<?xml version="1.0" encoding="UTF-8"?>
<soap:Envelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xmlns:xsd="http://www.w3.org/2001/XMLSchema"
               xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Body>
    <Add xmlns="http://tempuri.org/">
      <intA>5</intA>
      <intB>3</intB>
    </Add>
  </soap:Body>
</soap:Envelope>
        """.trimIndent()

        val clientRequest = createApiGatewayEvent(
            httpMethod = "POST",
            path = "/mocknest/dneonline/calculator.asmx",
            body = soapRequestBody,
            headers = mapOf(
                "Content-Type" to "text/xml; charset=utf-8",
            )
        )

        val clientResponse = lambdaHandler.router().apply(clientRequest)

        // Then - Should get SOAP response
        assertEquals(200, clientResponse.statusCode)
        assertEquals("text/xml; charset=utf-8", clientResponse.headers?.get("Content-Type"))
        assertContains(clientResponse.body, "<AddResponse")
        assertContains(clientResponse.body, "<AddResult>42</AddResult>")
    }

    @Test
    fun `Given SOAP fault mock When setting up via admin API and calling as client Then should return SOAP fault response`() {
        // Given - Create SOAP fault mock (simplified matching)
        val soapFaultMapping = """
            {
                "id": "550e8400-e29b-41d4-a716-446655440006",
                "priority": 2,
                "request": {
                    "method": "POST",
                    "urlPath": "/soap/UserService",
                    "bodyPatterns": [
                        {
                            "contains": "999"
                        }
                    ]
                },
                "response": {
                    "status": 500,
                    "headers": {
                        "Content-Type": "text/xml; charset=utf-8"
                    },
                    "body": "<?xml version=\"1.0\" encoding=\"utf-8\"?><soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body><soap:Fault><faultcode>soap:Client</faultcode><faultstring>User not found</faultstring></soap:Fault></soap:Body></soap:Envelope>"
                },
                "persistent": true
            }
        """.trimIndent()

        val adminRequest = createApiGatewayEvent(
            httpMethod = "POST",
            path = "/__admin/mappings",
            body = soapFaultMapping,
            headers = mapOf("Content-Type" to "application/json")
        )

        // When - Set up mock via admin API
        val adminResponse = lambdaHandler.router().apply(adminRequest)

        // Then - Admin API should succeed
        assertEquals(201, adminResponse.statusCode)

        // When - Call the SOAP endpoint with invalid user
        val soapRequestBody = """
            <?xml version="1.0" encoding="utf-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                <soap:Body>
                    <GetUser>
                        <UserId>999</UserId>
                    </GetUser>
                </soap:Body>
            </soap:Envelope>
        """.trimIndent()

        val clientRequest = createApiGatewayEvent(
            httpMethod = "POST",
            path = "/mocknest/soap/UserService",
            body = soapRequestBody,
            headers = mapOf(
                "Content-Type" to "text/xml; charset=utf-8",
                "SOAPAction" to "\"GetUser\""
            )
        )

        val clientResponse = lambdaHandler.router().apply(clientRequest)

        // Then - Should get SOAP fault response
        assertEquals(500, clientResponse.statusCode)
        assertEquals("text/xml; charset=utf-8", clientResponse.headers?.get("Content-Type"))
        assertContains(clientResponse.body, "<soap:Fault>")
        assertContains(clientResponse.body, "<faultstring>User not found</faultstring>")
    }
}