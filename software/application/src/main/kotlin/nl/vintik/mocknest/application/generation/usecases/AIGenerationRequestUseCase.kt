package nl.vintik.mocknest.application.generation.usecases

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.generation.interfaces.GenerationStorageInterface
import nl.vintik.mocknest.application.usecase.HandleAIGenerationRequest
import nl.vintik.mocknest.domain.generation.*
import nl.vintik.mocknest.domain.core.HttpRequest
import nl.vintik.mocknest.domain.core.HttpResponse
import kotlinx.coroutines.runBlocking
import org.springframework.http.HttpStatusCode
import org.springframework.util.LinkedMultiValueMap
import java.time.Instant

private val logger = KotlinLogging.logger {}
private val mapper = jacksonObjectMapper()

class AIGenerationRequestUseCase(
    private val generateFromSpecUseCase: GenerateMocksFromSpecUseCase,
    private val generateFromSpecWithDescriptionUseCase: GenerateMocksFromSpecWithDescriptionUseCase,
    private val generateFromDescriptionUseCase: GenerateMocksFromDescriptionUseCase,
    private val generationStorageInterface: GenerationStorageInterface
) : HandleAIGenerationRequest {

    override fun invoke(path: String, httpRequest: HttpRequest): HttpResponse = runBlocking {
        logger.info { "Handling AI generation request: ${httpRequest.method} $path" }

        try {
            when {
                path == "/from-spec" && httpRequest.method.name() == "POST" -> generateFromSpec(httpRequest)
                path == "/from-description" && httpRequest.method.name() == "POST" -> generateFromDescription(httpRequest)
                path == "/from-spec-with-description" && httpRequest.method.name() == "POST" -> generateFromSpecWithDescription(httpRequest)
                path.startsWith("/jobs/") && path.endsWith("/mocks") && httpRequest.method.name() == "GET" -> {
                    val jobId = path.removePrefix("/jobs/").removeSuffix("/mocks")
                    getGeneratedMocks(jobId)
                }
                path.startsWith("/jobs/") && path.endsWith("/status") && httpRequest.method.name() == "GET" -> {
                    val jobId = path.removePrefix("/jobs/").removeSuffix("/status")
                    getJobStatus(jobId)
                }
                path == "/health" && httpRequest.method.name() == "GET" -> health()
                else -> HttpResponse(HttpStatusCode.valueOf(404), body = "Path $path not found for AI generation")
            }
        } catch (e: Exception) {
            logger.error(e) { "Error processing AI generation request: $path" }
            HttpResponse(
                HttpStatusCode.valueOf(500),
                jsonHeaders(),
                mapper.writeValueAsString(mapOf("error" to (e.message ?: "Internal Server Error")))
            )
        }
    }

    private suspend fun generateFromSpec(request: HttpRequest): HttpResponse {
        val body = request.body ?: throw IllegalArgumentException("Body must be a string")
        val dto = mapper.readValue(body, GenerateFromSpecRequest::class.java)
        
        val mockRequest = MockGenerationRequest(
            namespace = dto.namespace,
            specificationContent = dto.specification,
            format = dto.format,
            options = dto.options
        )
        
        val result = generateFromSpecUseCase.execute(mockRequest)
        
        return if (result.success) {
            ok(GenerationResponse(
                jobId = result.jobId,
                namespace = dto.namespace.toPrefix(),
                status = "COMPLETED",
                mocksGenerated = result.mocksGenerated
            ))
        } else {
            HttpResponse(
                HttpStatusCode.valueOf(500),
                jsonHeaders(),
                mapper.writeValueAsString(GenerationResponse(
                    jobId = result.jobId,
                    namespace = dto.namespace.toPrefix(),
                    status = "FAILED",
                    error = result.error
                ))
            )
        }
    }

    private suspend fun generateFromDescription(request: HttpRequest): HttpResponse {
        val body = request.body ?: throw IllegalArgumentException("Body must be a string")
        val dto = mapper.readValue(body, GenerateFromDescriptionRequest::class.java)
        
        val nlRequest = NaturalLanguageRequest(
            namespace = dto.namespace,
            description = dto.description,
            useExistingSpec = dto.useExistingSpec,
            context = dto.context,
            options = dto.options
        )
        
        val result = generateFromDescriptionUseCase.execute(nlRequest)
        
        return if (result.success) {
            ok(GenerationResponse(
                jobId = result.jobId,
                namespace = dto.namespace.toPrefix(),
                status = "COMPLETED",
                mocksGenerated = result.mocksGenerated
            ))
        } else {
            HttpResponse(
                HttpStatusCode.valueOf(500),
                jsonHeaders(),
                mapper.writeValueAsString(GenerationResponse(
                    jobId = result.jobId,
                    namespace = dto.namespace.toPrefix(),
                    status = "FAILED",
                    error = result.error
                ))
            )
        }
    }

    private suspend fun generateFromSpecWithDescription(request: HttpRequest): HttpResponse {
        val body = request.body ?: throw IllegalArgumentException("Body must be a string")
        val dto = mapper.readValue(body, GenerateFromSpecWithDescriptionRequest::class.java)
        
        val specWithDescRequest = SpecWithDescriptionRequest(
            namespace = dto.namespace,
            specificationContent = dto.specification,
            format = dto.format,
            description = dto.description,
            options = dto.options
        )
        
        val result = generateFromSpecWithDescriptionUseCase.execute(specWithDescRequest)
        
        return if (result.success) {
            ok(GenerationResponse(
                jobId = result.jobId,
                namespace = dto.namespace.toPrefix(),
                status = "COMPLETED",
                mocksGenerated = result.mocksGenerated
            ))
        } else {
            HttpResponse(
                HttpStatusCode.valueOf(500),
                jsonHeaders(),
                mapper.writeValueAsString(GenerationResponse(
                    jobId = result.jobId,
                    namespace = dto.namespace.toPrefix(),
                    status = "FAILED",
                    error = result.error
                ))
            )
        }
    }

    private suspend fun getGeneratedMocks(jobId: String): HttpResponse {
        val job = generationStorageInterface.getJob(jobId) ?: return HttpResponse(HttpStatusCode.valueOf(404))
        val mocks = generationStorageInterface.getGeneratedMocks(jobId)
        
        return ok(MocksResponse(
            jobId = jobId,
            status = job.status.name,
            mocks = mocks.map { mock ->
                MockResponse(
                    id = mock.id,
                    name = mock.name,
                    wireMockMapping = mock.wireMockMapping,
                    metadata = MockMetadataResponse(
                        sourceType = mock.metadata.sourceType.name,
                        endpoint = EndpointResponse(
                            method = mock.metadata.endpoint.method.name(),
                            path = mock.metadata.endpoint.path,
                            statusCode = mock.metadata.endpoint.statusCode,
                            contentType = mock.metadata.endpoint.contentType
                        ),
                        tags = mock.metadata.tags.toList()
                    ),
                    generatedAt = mock.generatedAt.toString()
                )
            },
            totalMocks = mocks.size
        ))
    }

    private suspend fun getJobStatus(jobId: String): HttpResponse {
        val job = generationStorageInterface.getJob(jobId) ?: return HttpResponse(HttpStatusCode.valueOf(404))
        
        return ok(JobStatusResponse(
            jobId = jobId,
            status = job.status.name,
            createdAt = job.createdAt.toString(),
            completedAt = job.completedAt?.toString(),
            error = job.error,
            mocksGenerated = job.results?.totalGenerated ?: 0
        ))
    }

    private suspend fun health(): HttpResponse {
        val storageHealthy = generationStorageInterface.isHealthy()
        return ok(HealthResponse(
            status = if (storageHealthy) "healthy" else "degraded",
            services = mapOf(
                "storage" to if (storageHealthy) "healthy" else "unhealthy",
                "ai-generation" to "healthy"
            ),
            timestamp = Instant.now().toString()
        ))
    }

    private fun ok(body: Any): HttpResponse = HttpResponse(
        HttpStatusCode.valueOf(200),
        jsonHeaders(),
        mapper.writeValueAsString(body)
    )

    private fun jsonHeaders() = LinkedMultiValueMap<String, String>().apply {
        add("Content-Type", "application/json")
    }
}
