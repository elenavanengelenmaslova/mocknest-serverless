package nl.vintik.mocknest.infra.aws.runtime.function

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import nl.vintik.mocknest.application.runtime.usecases.GetRuntimeHealth
import nl.vintik.mocknest.application.runtime.usecases.HandleAdminRequest
import nl.vintik.mocknest.application.runtime.usecases.HandleClientRequest
import nl.vintik.mocknest.domain.core.HttpResponse
import nl.vintik.mocknest.domain.core.HttpStatusCode
import nl.vintik.mocknest.infra.aws.core.di.KoinBootstrap
import nl.vintik.mocknest.infra.aws.runtime.snapstart.RuntimeMappingReloadHook
import nl.vintik.mocknest.infra.aws.runtime.snapstart.RuntimePrimingHook
import org.crac.Core
import org.crac.Resource
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
 * Property-based tests for runtime routing equivalence (Property 3).
 *
 * Verifies that [RuntimeLambdaHandler.handleRequest] routes to the correct use case
 * for all routing branches:
 * - /__admin/health -> GetRuntimeHealth
 * - /__admin/... -> HandleAdminRequest
 * - /mocknest/... -> HandleClientRequest
 * - All other paths -> 404 response
 *
 * Validates: Requirements 3.4, 7.1, 10.1
 */
@Isolated
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RuntimeRoutingPropertyTest : KoinTest {

    private val mockHandleClientRequest: HandleClientRequest = mockk(relaxed = true)
    private val mockHandleAdminRequest: HandleAdminRequest = mockk(relaxed = true)
    private val mockGetRuntimeHealth: GetRuntimeHealth = mockk(relaxed = true)
    private val mockPrimingHook: RuntimePrimingHook = mockk(relaxed = true)
    private val mockReloadHook: RuntimeMappingReloadHook = mockk(relaxed = true)
    private val mockContext: Context = mockk(relaxed = true)
    private lateinit var handler: RuntimeLambdaHandler

    @BeforeAll
    fun setUp() {
        mockkStatic(Core::class)
        val mockCracContext: org.crac.Context<Resource> = mockk(relaxed = true)
        every { Core.getGlobalContext() } returns mockCracContext

        KoinBootstrap.init(listOf(module {
            single<HandleClientRequest> { mockHandleClientRequest }
            single<HandleAdminRequest> { mockHandleAdminRequest }
            single<GetRuntimeHealth> { mockGetRuntimeHealth }
            single { mockPrimingHook }
            single { mockReloadHook }
        }))
        handler = RuntimeLambdaHandler()
    }

    @AfterAll
    fun tearDownAll() {
        stopKoin()
        KoinBootstrap.reset()
        unmockkAll()
    }

    @AfterEach
    fun tearDown() {
        clearMocks(mockHandleClientRequest, mockHandleAdminRequest, mockGetRuntimeHealth)
    }

    @ParameterizedTest(name = "Health path {0} delegates to GetRuntimeHealth")
    @MethodSource("healthCheckPaths")
    fun `Given health check path When routing Then delegates to GetRuntimeHealth`(
        path: String,
        httpMethod: String
    ) {
        val event = createEvent(path, httpMethod)
        val healthResponse = HttpResponse(
            HttpStatusCode.OK,
            mapOf("Content-Type" to listOf("application/json")),
            """{"status":"healthy"}"""
        )
        every { mockGetRuntimeHealth.invoke() } returns healthResponse

        val response = handler.handleRequest(event, mockContext)

        verify(exactly = 1) { mockGetRuntimeHealth.invoke() }
        verify(exactly = 0) { mockHandleAdminRequest.invoke(any(), any()) }
        verify(exactly = 0) { mockHandleClientRequest.invoke(any()) }
        assertEquals(200, response.statusCode)
    }

    @ParameterizedTest(name = "Admin path {0} delegates to HandleAdminRequest")
    @MethodSource("adminPaths")
    fun `Given admin path When routing Then delegates to HandleAdminRequest`(
        path: String,
        httpMethod: String,
        expectedAdminPath: String
    ) {
        val event = createEvent(path, httpMethod)
        val adminResponse = HttpResponse(
            HttpStatusCode.OK,
            mapOf("Content-Type" to listOf("application/json")),
            """{"mappings":[]}"""
        )
        every { mockHandleAdminRequest.invoke(any(), any()) } returns adminResponse

        val response = handler.handleRequest(event, mockContext)

        verify(exactly = 1) { mockHandleAdminRequest.invoke(eq(expectedAdminPath), any()) }
        verify(exactly = 0) { mockGetRuntimeHealth.invoke() }
        verify(exactly = 0) { mockHandleClientRequest.invoke(any()) }
        assertEquals(200, response.statusCode)
    }

    @ParameterizedTest(name = "Client path {0} delegates to HandleClientRequest")
    @MethodSource("clientPaths")
    fun `Given client path When routing Then delegates to HandleClientRequest`(
        path: String,
        httpMethod: String
    ) {
        val event = createEvent(path, httpMethod)
        val clientResponse = HttpResponse(
            HttpStatusCode.OK,
            mapOf("Content-Type" to listOf("application/json")),
            """{"result":"ok"}"""
        )
        every { mockHandleClientRequest.invoke(any()) } returns clientResponse

        val response = handler.handleRequest(event, mockContext)

        verify(exactly = 1) { mockHandleClientRequest.invoke(any()) }
        verify(exactly = 0) { mockGetRuntimeHealth.invoke() }
        verify(exactly = 0) { mockHandleAdminRequest.invoke(any(), any()) }
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

        verify(exactly = 0) { mockGetRuntimeHealth.invoke() }
        verify(exactly = 0) { mockHandleAdminRequest.invoke(any(), any()) }
        verify(exactly = 0) { mockHandleClientRequest.invoke(any()) }
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
            Arguments.of("/__admin/health", "GET"),
            Arguments.of("/__admin/health", "HEAD"),
            Arguments.of("/__admin/health", "POST")
        )

        @JvmStatic
        fun adminPaths(): Stream<Arguments> = Stream.of(
            Arguments.of("/__admin/mappings", "GET", "mappings"),
            Arguments.of("/__admin/mappings", "POST", "mappings"),
            Arguments.of("/__admin/mappings/test-id", "DELETE", "mappings/test-id"),
            Arguments.of("/__admin/requests", "GET", "requests"),
            Arguments.of("/__admin/requests/count", "GET", "requests/count"),
            Arguments.of("/__admin/settings", "POST", "settings"),
            Arguments.of("/__admin/scenarios", "GET", "scenarios"),
            Arguments.of("/__admin/near-misses/request", "POST", "near-misses/request"),
            Arguments.of("/__admin/recordings/start", "POST", "recordings/start"),
            Arguments.of("/__admin/recordings/stop", "POST", "recordings/stop")
        )

        @JvmStatic
        fun clientPaths(): Stream<Arguments> = Stream.of(
            Arguments.of("/mocknest/api/users", "GET"),
            Arguments.of("/mocknest/api/users", "POST"),
            Arguments.of("/mocknest/api/users/123", "PUT"),
            Arguments.of("/mocknest/api/users/123", "DELETE"),
            Arguments.of("/mocknest/api/products", "GET"),
            Arguments.of("/mocknest/api/orders/456/items", "PATCH"),
            Arguments.of("/mocknest/health", "GET"),
            Arguments.of("/mocknest/", "GET"),
            Arguments.of("/mocknest/deeply/nested/path/to/resource", "OPTIONS"),
            Arguments.of("/mocknest/soap-endpoint", "POST")
        )

        @JvmStatic
        fun unknownPaths(): Stream<Arguments> = Stream.of(
            Arguments.of("/", "GET"),
            Arguments.of("/unknown/path", "GET"),
            Arguments.of("/ai/generation/generate", "POST"),
            Arguments.of("/ai/health", "GET"),
            Arguments.of("/api/users", "GET"),
            Arguments.of("/admin/mappings", "GET"),
            Arguments.of("/__admins/health", "GET"),
            Arguments.of("/mocknests/api/users", "GET"),
            Arguments.of("/random", "POST"),
            Arguments.of("/favicon.ico", "GET")
        )
    }
}

