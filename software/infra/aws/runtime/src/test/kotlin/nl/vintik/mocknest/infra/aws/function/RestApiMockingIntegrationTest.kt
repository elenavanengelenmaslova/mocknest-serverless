package nl.vintik.mocknest.infra.aws.function

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import kotlinx.coroutines.flow.toList
import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import nl.vintik.mocknest.infra.aws.config.AwsLocalStackTestConfiguration
import nl.vintik.mocknest.infra.aws.config.TestContextDebugListener
import nl.vintik.mocknest.infra.aws.runtime.RuntimeApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestExecutionListeners
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener
import java.util.function.Function
import kotlin.test.assertContains
import kotlin.test.assertEquals

@Disabled("LocalStack integration tests disabled - Docker/Colima connectivity issue")
@SpringBootTest(classes = [RuntimeApplication::class])
@TestPropertySource(locations = ["classpath:application-test.properties"])
@ContextConfiguration(classes = [AwsLocalStackTestConfiguration::class])
@TestExecutionListeners(
    listeners = [
        TestContextDebugListener::class,
        DependencyInjectionTestExecutionListener::class
    ],
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
)
class RestApiMockingIntegrationTest {

    // Spring Boot will inject the lambda handler router
    @Autowired
    @Qualifier("runtimeRouter")
    private lateinit var lambdaHandler: Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>

    // Spring Boot will inject the test storage
    @Autowired
    private lateinit var storage: ObjectStorageInterface

    @BeforeEach
    fun setup() {
    }

    @AfterEach
    suspend fun tearDown() {
        // Clean up after each test method completes
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
    fun `Given REST API mock When setting up via admin API and calling as client Then should return response from S3`() {
        // Given - Create REST API mock via admin API (using proper UUID format)
        val restMockMapping = """
            {
                "id": "550e8400-e29b-41d4-a716-446655440001",
                "priority": 1,
                "request": {
                    "method": "GET",
                    "urlPath": "/api/users/123"
                },
                "response": {
                    "status": 200,
                    "headers": {
                        "Content-Type": "application/json"
                    },
                    "body": "{\"id\":123,\"name\":\"John Doe\",\"email\":\"john@example.com\"}"
                },
                "persistent": true
            }
        """.trimIndent()

        val adminRequest = createApiGatewayEvent(
            httpMethod = "POST",
            path = "/__admin/mappings",
            body = restMockMapping,
            headers = mapOf("Content-Type" to "application/json")
        )

        // When - Set up mock via admin API
        val adminResponse = lambdaHandler.apply(adminRequest)

        // Then - Admin API should succeed
        assertEquals(201, adminResponse.statusCode)

        // When - Call the mock endpoint as client
        val clientRequest = createApiGatewayEvent(
            httpMethod = "GET",
            path = "/mocknest/api/users/123"
        )

        val clientResponse = lambdaHandler.apply(clientRequest)

        // Then - Should get mocked response
        assertEquals(200, clientResponse.statusCode)
        assertEquals("application/json", clientResponse.headers?.get("Content-Type"))
        assertContains(clientResponse.body, "John Doe")
        assertContains(clientResponse.body, "john@example.com")
    }

    @Test
    fun `Given REST API POST mock When setting up via admin API and calling as client Then should return created response`() {
        // Given - Create REST API POST mock (using proper UUID format)
        val restMockMapping = """
            {
                "id": "550e8400-e29b-41d4-a716-446655440002",
                "priority": 1,
                "request": {
                    "method": "POST",
                    "urlPath": "/api/users"
                },
                "response": {
                    "status": 201,
                    "headers": {
                        "Content-Type": "application/json"
                    },
                    "body": "{\"id\":456,\"name\":\"Jane Smith\",\"email\":\"jane@example.com\"}"
                },
                "persistent": true
            }
        """.trimIndent()

        val adminRequest = createApiGatewayEvent(
            httpMethod = "POST",
            path = "/__admin/mappings",
            body = restMockMapping,
            headers = mapOf("Content-Type" to "application/json")
        )

        // When - Set up mock via admin API
        val adminResponse = lambdaHandler.apply(adminRequest)

        // Then - Admin API should succeed
        assertEquals(201, adminResponse.statusCode)

        // When - Call the POST endpoint as client
        val postRequestBody = """{"name": "Jane Smith", "email": "jane@example.com"}"""
        val clientRequest = createApiGatewayEvent(
            httpMethod = "POST",
            path = "/mocknest/api/users",
            body = postRequestBody,
            headers = mapOf("Content-Type" to "application/json")
        )

        val clientResponse = lambdaHandler.apply(clientRequest)

        // Then - Should get created response
        assertEquals(201, clientResponse.statusCode)
        assertContains(clientResponse.body, "Jane Smith")
        assertContains(clientResponse.body, "jane@example.com")
    }
}