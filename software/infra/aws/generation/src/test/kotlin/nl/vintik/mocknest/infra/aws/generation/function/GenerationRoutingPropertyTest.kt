package nl.vintik.mocknest.infra.aws.generation.function

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import nl.vintik.mocknest.application.generation.usecases.GetAIHealth
import nl.vintik.mocknest.application.runtime.usecases.HandleAIGenerationRequest
import nl.vintik.mocknest.domain.core.HttpResponse
import nl.vintik.mocknest.domain.core.HttpStatusCode
import nl.vintik.mocknest.infra.aws.core.di.KoinBootstrap
import nl.vintik.mocknest.infra.aws.generation.snapstart.GenerationPrimingHook
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Isolated
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import java.util.stream.Stream

/**
 * Property-based tests for generation routing equivalence (Property 4).
 *
 * Verifies that [GenerationLambdaHandler.handleRequest] routes to the correct use case
 * for all routing branches:
 * - /ai/generation/health -> GetAIHealth
 * - /ai/generation/... -> HandleAIGenerationRequest
 * - All other paths -> 404 response
 *
 * **Validates: Requirements 3.4, 7.2, 10.2**
 */
@Isolated
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GenerationRoutingPropertyTest : KoinTest {

    private val mockHandleAIGenerationRequest: HandleAIGenerationRequest = mockk(relaxed = true)
    private val mockGetAIHealth: GetAIHealth = mockk(relaxed = true)
    private val mockPrimingHook: GenerationPrimingHook = mockk(relaxed = true)
    private val mockContext: Context = mockk(relaxed = true)
    private lateinit var handler: GenerationLambdaHandler

    @BeforeAll
    fun setUp() {
        KoinBootstrap.init(listOf(module {
            single<HandleAIGenerationRequest> { mockHandleAIGenerationRequest }
            single<GetAIHealth> { mockGetAIHealth }
            single { mockPrimingHook }
        }))
        handler = GenerationLambdaHandler()
    }

    @AfterAll
    fun tearDownAll() {
        stopKoin()
        KoinBootstrap.reset()
    }

    @AfterEach
    fun tearDown() {
        clearMocks(mockHandleAIGenerationRequest, mockGetAIHealth)
    }

    @ParameterizedTest(name = "Health path {0} delegates to GetAIHealth")
    @MethodSource("healthCheckPaths")
    fun `Given health check path When routing Then delegates to GetAIHealth`(
        path: String,
        httpMethod: String
    ) {
        val event = createEvent(path, httpMethod)
        val healthResponse = HttpResponse(
            HttpStatusCode.OK,
            mapOf("Content-Type" to listOf("application/json")),
            """{"status":"healthy"}"""
        )
        every { mockGetAIHealth.invoke() } returns healthResponse

        val response = handler.handleRequest(event, mockContext)

        verify(exactly = 1) { mockGetAIHealth.invoke() }
        verify(exactly = 0) { mockHandleAIGenerationRequest.invoke(any(), any()) }
        assertEquals(200, response.statusCode)
    }

    @ParameterizedTest(name = "AI path {0} delegates to HandleAIGenerationRequest")
    @MethodSource("aiGenerationPaths")
    fun `Given AI generation path When routing Then delegates to HandleAIGenerationRequest`(
        path: String,
        httpMethod: String,
        expectedAiPath: String
    ) {
        val event = createEvent(path, httpMethod)
        val aiResponse = HttpResponse(
            HttpStatusCode.OK,
            mapOf("Content-Type" to listOf("application/json")),
            """{"status":"ok"}"""
        )
        every { mockHandleAIGenerationRequest.invoke(any(), any()) } returns aiResponse

        val response = handler.handleRequest(event, mockContext)

        verify(exactly = 1) { mockHandleAIGenerationRequest.invoke(eq(expectedAiPath), any()) }
        verify(exactly = 0) { mockGetAIHealth.invoke() }
        assertEquals(200, response.statusCode)
    }

    @ParameterizedTest(name = "Unknown path {0} returns 404")
    @MethodSource("unknownPaths")
    fun `Given unknown path When routing Then returns 404 without delegating`(
        path: String,
        httpMethod: String
    ) {
        val event = createEvent(path, httpMethod)

        val response = handler.handleRequest(event, mockContext)

        verify(exactly = 0) { mockGetAIHealth.invoke() }
        verify(exactly = 0) { mockHandleAIGenerationRequest.invoke(any(), any()) }
        assertEquals(404, response.statusCode)
        assert(response.body?.contains("not found") == true) { "Expected 404 body to contain 'not found'" }
    }

    private fun createEvent(path: String, httpMethod: String): APIGatewayProxyRequestEvent =
        APIGatewayProxyRequestEvent()
            .withPath(path)
            .withHttpMethod(httpMethod)
            .withHeaders(mapOf("Accept" to "application/json"))
            .withQueryStringParameters(emptyMap())

    companion object {
        @JvmStatic
        fun healthCheckPaths(): Stream<Arguments> = Stream.of(
            Arguments.of("/ai/generation/health", "GET"),
            Arguments.of("/ai/generation/health", "HEAD"),
            Arguments.of("/ai/generation/health", "POST")
        )

        @JvmStatic
        fun aiGenerationPaths(): Stream<Arguments> = Stream.of(
            Arguments.of("/ai/generation/generate", "POST", "/generate"),
            Arguments.of("/ai/generation/from-spec", "POST", "/from-spec"),
            Arguments.of("/ai/generation/status", "GET", "/status"),
            Arguments.of("/ai/generation/bulk-generate", "POST", "/bulk-generate"),
            Arguments.of("/ai/generation/analyze-traffic", "POST", "/analyze-traffic"),
            Arguments.of("/ai/generation/update-from-spec", "POST", "/update-from-spec"),
            Arguments.of("/ai/generation/jobs/123", "GET", "/jobs/123"),
            Arguments.of("/ai/generation/jobs/123/results", "GET", "/jobs/123/results"),
            Arguments.of("/ai/generation/delete", "DELETE", "/delete"),
            Arguments.of("/ai/generation/deeply/nested/path", "GET", "/deeply/nested/path")
        )

        @JvmStatic
        fun unknownPaths(): Stream<Arguments> = Stream.of(
            Arguments.of("/", "GET"),
            Arguments.of("/unknown/path", "GET"),
            Arguments.of("/__admin/mappings", "GET"),
            Arguments.of("/__admin/health", "GET"),
            Arguments.of("/mocknest/api/users", "GET"),
            Arguments.of("/api/users", "GET"),
            Arguments.of("/admin/mappings", "GET"),
            Arguments.of("/ai/other/endpoint", "GET"),
            Arguments.of("/random", "POST"),
            Arguments.of("/favicon.ico", "GET")
        )
    }
}
