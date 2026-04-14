package nl.vintik.mocknest.infra.aws.function

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import nl.vintik.mocknest.infra.aws.MockNestApplication
import nl.vintik.mocknest.infra.aws.runtime.config.AwsLocalStackTestConfiguration
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import java.util.function.Function
import kotlin.test.assertContains
import kotlin.test.assertEquals

@SpringBootTest(classes = [MockNestApplication::class])
@TestPropertySource(locations = ["classpath:application-test.properties"])
@ContextConfiguration(classes = [AwsLocalStackTestConfiguration::class])
@ActiveProfiles("runtime")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GraphQLMockingIntegrationTest {

    // Spring Boot will inject the lambda handler router
    @Autowired
    @Qualifier("runtimeRouter")
    private lateinit var lambdaHandler: Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>

    // Spring Boot will inject the test storage
    @Autowired
    private lateinit var storage: ObjectStorageInterface

    @AfterAll
    fun tearDown() = runBlocking {
        val keys = storage.list().toList()
        if (keys.isNotEmpty()) {
            storage.deleteMany(keys.asFlow(), concurrency = 10)
        }
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
    fun `Given GraphQL query mock When setting up via admin API and calling as client Then should return JSON response from S3`() {
        // Given - Create GraphQL API mock via admin API (simplified matching)
        val graphqlMockMapping = """
            {
                "id": "550e8400-e29b-41d4-a716-446655440003",
                "priority": 1,
                "request": {
                    "method": "POST",
                    "urlPath": "/graphql",
                    "bodyPatterns": [
                        {
                            "contains": "GetUser"
                        }
                    ]
                },
                "response": {
                    "status": 200,
                    "headers": {
                        "Content-Type": "application/json"
                    },
                    "body": "{\"data\":{\"user\":{\"id\":\"123\",\"name\":\"John Doe\",\"email\":\"john@example.com\"}}}"
                },
                "persistent": true
            }
        """.trimIndent()

        val adminRequest = createApiGatewayEvent(
            httpMethod = "POST",
            path = "/__admin/mappings",
            body = graphqlMockMapping,
            headers = mapOf("Content-Type" to "application/json")
        )

        // When - Set up mock via admin API
        val adminResponse = lambdaHandler.apply(adminRequest)

        // Then - Admin API should succeed
        assertEquals(201, adminResponse.statusCode)

        // When - Call the GraphQL endpoint as client
        val graphqlRequestBody = """
            {
                "query": "query GetUser { user(id: \"123\") { id name email } }",
                "variables": {
                    "id": "123"
                }
            }
        """.trimIndent()

        val clientRequest = createApiGatewayEvent(
            httpMethod = "POST",
            path = "/mocknest/graphql",
            body = graphqlRequestBody,
            headers = mapOf("Content-Type" to "application/json")
        )

        val clientResponse = lambdaHandler.apply(clientRequest)

        // Then - Should get GraphQL response
        assertEquals(200, clientResponse.statusCode)
        assertEquals("application/json", clientResponse.headers?.get("Content-Type"))
        assertContains(clientResponse.body, "\"data\":")
        assertContains(clientResponse.body, "\"user\":")
        assertContains(clientResponse.body, "\"name\":\"John Doe\"")
        assertContains(clientResponse.body, "\"email\":\"john@example.com\"")
    }

    @Test
    fun `Given GraphQL error mock When setting up via admin API and calling as client Then should return error response from S3`() {
        // Given - Create GraphQL error mock via admin API (simplified matching)
        val graphqlErrorMapping = """
            {
                "id": "550e8400-e29b-41d4-a716-446655440004",
                "priority": 2,
                "request": {
                    "method": "POST",
                    "urlPath": "/graphql",
                    "bodyPatterns": [
                        {
                            "contains": "999"
                        }
                    ]
                },
                "response": {
                    "status": 200,
                    "headers": {
                        "Content-Type": "application/json"
                    },
                    "body": "{\"errors\":[{\"message\":\"User not found\"}],\"data\":{\"user\":null}}"
                },
                "persistent": true
            }
        """.trimIndent()

        val adminRequest = createApiGatewayEvent(
            httpMethod = "POST",
            path = "/__admin/mappings",
            body = graphqlErrorMapping,
            headers = mapOf("Content-Type" to "application/json")
        )

        // When - Set up mock via admin API
        val adminResponse = lambdaHandler.apply(adminRequest)

        // Then - Admin API should succeed
        assertEquals(201, adminResponse.statusCode)

        // When - Call the GraphQL endpoint with non-existent user
        val graphqlRequestBody = """
            {
                "query": "query GetUser { user(id: \"999\") { id name email } }",
                "variables": {
                    "id": "999"
                }
            }
        """.trimIndent()

        val clientRequest = createApiGatewayEvent(
            httpMethod = "POST",
            path = "/mocknest/graphql",
            body = graphqlRequestBody,
            headers = mapOf("Content-Type" to "application/json")
        )

        val clientResponse = lambdaHandler.apply(clientRequest)

        // Then - Should get GraphQL error response
        assertEquals(200, clientResponse.statusCode)
        assertEquals("application/json", clientResponse.headers?.get("Content-Type"))
        assertContains(clientResponse.body, "\"errors\":")
        assertContains(clientResponse.body, "\"message\":\"User not found\"")
        assertContains(clientResponse.body, "\"data\":{\"user\":null}")
    }
}
