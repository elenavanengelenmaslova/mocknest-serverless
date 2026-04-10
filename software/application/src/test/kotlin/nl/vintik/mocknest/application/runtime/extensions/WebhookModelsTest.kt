package nl.vintik.mocknest.application.runtime.extensions

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class WebhookModelsTest {

    @AfterEach
    fun tearDown() {
        // No mocks to clear — pure data model tests
    }

    @Test
    fun `Given valid fields When constructing WebhookRequest Then all properties are accessible`() {
        val headers = mapOf("Content-Type" to "application/json", "x-correlation-id" to "abc-123")
        val request = WebhookRequest(
            url = "https://example.com/callback",
            method = "POST",
            headers = headers,
            body = """{"event":"order.created"}""",
        )

        assertEquals("https://example.com/callback", request.url)
        assertEquals("POST", request.method)
        assertEquals(headers, request.headers)
        assertEquals("""{"event":"order.created"}""", request.body)
    }

    @Test
    fun `Given WebhookRequest with null body When constructing Then body is null`() {
        val request = WebhookRequest(
            url = "https://example.com/callback",
            method = "GET",
            headers = emptyMap(),
            body = null,
        )

        assertNull(request.body)
    }

    @Test
    fun `Given Success result When checking type Then is Success subtype`() {
        val result: WebhookResult = WebhookResult.Success(statusCode = 200)

        assertIs<WebhookResult.Success>(result)
        assertEquals(200, result.statusCode)
    }

    @Test
    fun `Given Failure result with non-null statusCode When checking fields Then statusCode and message are preserved`() {
        val result: WebhookResult = WebhookResult.Failure(statusCode = 503, message = "Service Unavailable")

        assertIs<WebhookResult.Failure>(result)
        assertEquals(503, result.statusCode)
        assertEquals("Service Unavailable", result.message)
    }

    @Test
    fun `Given Failure result with null statusCode When checking message Then message is preserved`() {
        val result: WebhookResult = WebhookResult.Failure(statusCode = null, message = "Connection refused")

        assertIs<WebhookResult.Failure>(result)
        assertNull(result.statusCode)
        assertEquals("Connection refused", result.message)
    }

    @Test
    fun `Given None auth config When checking type Then is None subtype`() {
        val config: WebhookAuthConfig = WebhookAuthConfig.None

        assertIs<WebhookAuthConfig.None>(config)
    }

    @Test
    fun `Given AwsIam auth config with region and service When accessing fields Then both are accessible`() {
        val config: WebhookAuthConfig = WebhookAuthConfig.AwsIam(
            region = "eu-west-1",
            service = "execute-api",
        )

        assertIs<WebhookAuthConfig.AwsIam>(config)
        assertEquals("eu-west-1", config.region)
        assertEquals("execute-api", config.service)
    }

    @Test
    fun `Given AwsIam auth config with no region or service When accessing fields Then both are null`() {
        val config: WebhookAuthConfig = WebhookAuthConfig.AwsIam()

        assertIs<WebhookAuthConfig.AwsIam>(config)
        assertNull(config.region)
        assertNull(config.service)
    }

    @Test
    fun `Given AsyncEvent When serialized and deserialized Then round-trips correctly`() {
        val event = AsyncEvent(
            actionType = "webhook",
            url = "https://example.com/callback",
            method = "POST",
            headers = mapOf("Content-Type" to "application/json"),
            body = """{"event":"order.created"}""",
            auth = AsyncEventAuth(type = "aws_iam", region = "eu-west-1", service = "execute-api"),
        )

        val json = Json.encodeToString(AsyncEvent.serializer(), event)
        val decoded = Json.decodeFromString(AsyncEvent.serializer(), json)

        assertEquals(event, decoded)
    }

    @Test
    fun `Given AsyncEvent with null body When serialized and deserialized Then body remains null`() {
        val event = AsyncEvent(
            actionType = "webhook",
            url = "https://example.com/callback",
            method = "GET",
            headers = emptyMap(),
            body = null,
            auth = AsyncEventAuth(type = "none"),
        )

        val json = Json.encodeToString(AsyncEvent.serializer(), event)
        val decoded = Json.decodeFromString(AsyncEvent.serializer(), json)

        assertNull(decoded.body)
        assertEquals(event, decoded)
    }

    @Test
    fun `Given AsyncEventAuth with type none When serialized and deserialized Then round-trips correctly`() {
        val auth = AsyncEventAuth(type = "none")

        val json = Json.encodeToString(AsyncEventAuth.serializer(), auth)
        val decoded = Json.decodeFromString(AsyncEventAuth.serializer(), json)

        assertEquals("none", decoded.type)
        assertNull(decoded.region)
        assertNull(decoded.service)
    }

    @Test
    fun `Given AsyncEventAuth with aws_iam type When serialized and deserialized Then region and service are preserved`() {
        val auth = AsyncEventAuth(type = "aws_iam", region = "us-east-1", service = "execute-api")

        val json = Json.encodeToString(AsyncEventAuth.serializer(), auth)
        val decoded = Json.decodeFromString(AsyncEventAuth.serializer(), json)

        assertEquals("aws_iam", decoded.type)
        assertEquals("us-east-1", decoded.region)
        assertEquals("execute-api", decoded.service)
    }
}
