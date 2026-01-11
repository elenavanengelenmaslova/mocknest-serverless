package io.mocknest.infra.aws.generation

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mocknest.application.generation.interfaces.GenerationStorageInterface
import io.mocknest.application.generation.interfaces.SpecificationMetadata
import io.mocknest.domain.generation.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runCatching
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * S3-based storage adapter for generated mocks and specifications.
 * Implements namespace-based organization and job persistence.
 */
@Component
class S3GenerationStorageAdapter(
    private val s3Client: S3Client,
    @Value("\${storage.bucket.name}") private val bucketName: String
) : GenerationStorageInterface {
    
    private val logger = KotlinLogging.logger {}
    private val objectMapper = ObjectMapper().registerKotlinModule()
    
    companion object {
        private const val GENERATED_MOCKS_PREFIX = "generated-mocks"
        private const val API_SPECS_PREFIX = "api-specs"
        private const val JOBS_PREFIX = "jobs"
        private const val CURRENT_SPEC_FILE = "current.json"
        private const val VERSIONS_PREFIX = "versions"
    }
    
    override suspend fun storeGeneratedMocks(mocks: List<GeneratedMock>, jobId: String): String {
        logger.info { "Storing ${mocks.size} generated mocks for job: $jobId" }
        
        return runCatching {
            val namespace = mocks.firstOrNull()?.namespace 
                ?: throw IllegalArgumentException("No mocks provided or missing namespace")
            
            val storageKey = "${namespace.toPrefix()}/$GENERATED_MOCKS_PREFIX/$JOBS_PREFIX/$jobId"
            
            // Store individual mocks
            mocks.forEach { mock ->
                val mockKey = "$storageKey/mocks/${mock.id}.json"
                val mockJson = objectMapper.writeValueAsString(mock)
                
                s3Client.putObject {
                    bucket = bucketName
                    key = mockKey
                    body = mockJson.toByteArray()
                    contentType = "application/json"
                    metadata = mapOf(
                        "job-id" to jobId,
                        "mock-id" to mock.id,
                        "namespace" to namespace.displayName(),
                        "generated-at" to mock.generatedAt.toString()
                    )
                }
            }
            
            // Store job results summary
            val resultsKey = "$storageKey/results.json"
            val results = mapOf(
                "jobId" to jobId,
                "totalMocks" to mocks.size,
                "mockIds" to mocks.map { it.id },
                "namespace" to namespace.displayName(),
                "storedAt" to Instant.now().toString()
            )
            
            s3Client.putObject {
                bucket = bucketName
                key = resultsKey
                body = objectMapper.writeValueAsBytes(results)
                contentType = "application/json"
            }
            
            storageKey
        }.onFailure { exception ->
            logger.error(exception) { "Failed to store generated mocks for job: $jobId" }
        }.getOrThrow()
    }
    
    override suspend fun getGeneratedMocks(jobId: String): List<GeneratedMock> {
        logger.debug { "Retrieving generated mocks for job: $jobId" }
        
        return runCatching {
            // Find the job by searching across namespaces
            val jobKey = findJobKey(jobId) ?: throw IllegalArgumentException("Job not found: $jobId")
            val mocksPrefix = "$jobKey/mocks/"
            
            val listRequest = ListObjectsV2Request {
                bucket = bucketName
                prefix = mocksPrefix
            }
            
            val objects = s3Client.listObjectsV2(listRequest).contents ?: emptyList()
            
            objects.mapNotNull { obj ->
                try {
                    val getRequest = GetObjectRequest {
                        bucket = bucketName
                        key = obj.key!!
                    }
                    
                    val response = s3Client.getObject(getRequest)
                    val content = response.body?.let { String(it) } ?: return@mapNotNull null
                    
                    objectMapper.readValue(content, GeneratedMock::class.java)
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to deserialize mock from key: ${obj.key}" }
                    null
                }
            }
        }.onFailure { exception ->
            logger.error(exception) { "Failed to retrieve generated mocks for job: $jobId" }
        }.getOrElse { emptyList() }
    }
    
    override suspend fun storeSpecification(
        namespace: MockNamespace,
        specification: APISpecification,
        version: String?
    ): String {
        logger.info { "Storing API specification for namespace: ${namespace.displayName()}" }
        
        return runCatching {
            val specJson = objectMapper.writeValueAsString(specification)
            val timestamp = Instant.now().toString()
            val actualVersion = version ?: specification.version
            
            // Store as current specification
            val currentKey = "${namespace.toPrefix()}/$API_SPECS_PREFIX/$CURRENT_SPEC_FILE"
            s3Client.putObject {
                bucket = bucketName
                key = currentKey
                body = specJson.toByteArray()
                contentType = "application/json"
                metadata = mapOf(
                    "namespace" to namespace.displayName(),
                    "version" to actualVersion,
                    "title" to specification.title,
                    "format" to specification.format.name,
                    "stored-at" to timestamp
                )
            }
            
            // Store versioned copy
            val versionedKey = "${namespace.toPrefix()}/$API_SPECS_PREFIX/$VERSIONS_PREFIX/$actualVersion.json"
            s3Client.putObject {
                bucket = bucketName
                key = versionedKey
                body = specJson.toByteArray()
                contentType = "application/json"
                metadata = mapOf(
                    "namespace" to namespace.displayName(),
                    "version" to actualVersion,
                    "title" to specification.title,
                    "format" to specification.format.name,
                    "stored-at" to timestamp
                )
            }
            
            currentKey
        }.onFailure { exception ->
            logger.error(exception) { "Failed to store specification for namespace: ${namespace.displayName()}" }
        }.getOrThrow()
    }
    
    override suspend fun getSpecification(namespace: MockNamespace, version: String?): APISpecification? {
        logger.debug { "Retrieving specification for namespace: ${namespace.displayName()}, version: $version" }
        
        return runCatching {
            val key = if (version != null) {
                "${namespace.toPrefix()}/$API_SPECS_PREFIX/$VERSIONS_PREFIX/$version.json"
            } else {
                "${namespace.toPrefix()}/$API_SPECS_PREFIX/$CURRENT_SPEC_FILE"
            }
            
            val getRequest = GetObjectRequest {
                bucket = bucketName
                key = key
            }
            
            val response = s3Client.getObject(getRequest)
            val content = response.body?.let { String(it) } ?: return@runCatching null
            
            objectMapper.readValue(content, APISpecification::class.java)
        }.onFailure { exception ->
            if (exception is NoSuchKey) {
                logger.debug { "Specification not found for namespace: ${namespace.displayName()}, version: $version" }
            } else {
                logger.error(exception) { "Failed to retrieve specification for namespace: ${namespace.displayName()}" }
            }
        }.getOrNull()
    }
    
    override suspend fun storeJob(job: GenerationJob): String {
        logger.debug { "Storing generation job: ${job.id}" }
        
        return runCatching {
            val jobKey = "${job.request.namespace.toPrefix()}/$JOBS_PREFIX/${job.id}/metadata.json"
            val jobJson = objectMapper.writeValueAsString(job)
            
            s3Client.putObject {
                bucket = bucketName
                key = jobKey
                body = jobJson.toByteArray()
                contentType = "application/json"
                metadata = mapOf(
                    "job-id" to job.id,
                    "namespace" to job.request.namespace.displayName(),
                    "status" to job.status.name,
                    "type" to job.request.type.name,
                    "created-at" to job.createdAt.toString()
                )
            }
            
            jobKey
        }.onFailure { exception ->
            logger.error(exception) { "Failed to store job: ${job.id}" }
        }.getOrThrow()
    }
    
    override suspend fun getJob(jobId: String): GenerationJob? {
        logger.debug { "Retrieving job: $jobId" }
        
        return runCatching {
            val jobKey = findJobKey(jobId) ?: return@runCatching null
            val metadataKey = "$jobKey/metadata.json"
            
            val getRequest = GetObjectRequest {
                bucket = bucketName
                key = metadataKey
            }
            
            val response = s3Client.getObject(getRequest)
            val content = response.body?.let { String(it) } ?: return@runCatching null
            
            objectMapper.readValue(content, GenerationJob::class.java)
        }.onFailure { exception ->
            if (exception !is NoSuchKey) {
                logger.error(exception) { "Failed to retrieve job: $jobId" }
            }
        }.getOrNull()
    }
    
    override suspend fun updateJobStatus(jobId: String, status: JobStatus, error: String?) {
        logger.debug { "Updating job status: $jobId -> $status" }
        
        runCatching {
            val job = getJob(jobId) ?: throw IllegalArgumentException("Job not found: $jobId")
            
            val updatedJob = job.copy(
                status = status,
                error = error,
                completedAt = if (status in listOf(JobStatus.COMPLETED, JobStatus.FAILED, JobStatus.CANCELLED)) {
                    Instant.now()
                } else job.completedAt
            )
            
            storeJob(updatedJob)
        }.onFailure { exception ->
            logger.error(exception) { "Failed to update job status: $jobId" }
        }
    }
    
    override suspend fun storeJobResults(jobId: String, results: GenerationResults) {
        logger.debug { "Storing job results: $jobId" }
        
        runCatching {
            val jobKey = findJobKey(jobId) ?: throw IllegalArgumentException("Job not found: $jobId")
            val resultsKey = "$jobKey/results.json"
            
            val resultsJson = objectMapper.writeValueAsString(results)
            
            s3Client.putObject {
                bucket = bucketName
                key = resultsKey
                body = resultsJson.toByteArray()
                contentType = "application/json"
                metadata = mapOf(
                    "job-id" to jobId,
                    "total-generated" to results.totalGenerated.toString(),
                    "successful" to results.successful.toString(),
                    "failed" to results.failed.toString()
                )
            }
        }.onFailure { exception ->
            logger.error(exception) { "Failed to store job results: $jobId" }
        }
    }
    
    override suspend fun listSpecifications(namespace: MockNamespace): List<SpecificationMetadata> {
        logger.debug { "Listing specifications for namespace: ${namespace.displayName()}" }
        
        return runCatching {
            val prefix = "${namespace.toPrefix()}/$API_SPECS_PREFIX/$VERSIONS_PREFIX/"
            
            val listRequest = ListObjectsV2Request {
                bucket = bucketName
                prefix = prefix
            }
            
            val objects = s3Client.listObjectsV2(listRequest).contents ?: emptyList()
            
            objects.mapNotNull { obj ->
                try {
                    val metadata = obj.metadata
                    SpecificationMetadata(
                        title = metadata?.get("title") ?: "Unknown",
                        version = metadata?.get("version") ?: "Unknown",
                        format = metadata?.get("format")?.let { SpecificationFormat.valueOf(it) } ?: SpecificationFormat.OPENAPI_3,
                        endpointCount = 0, // Would need to parse spec to get this
                        schemaCount = 0    // Would need to parse spec to get this
                    )
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to extract metadata from specification: ${obj.key}" }
                    null
                }
            }
        }.onFailure { exception ->
            logger.error(exception) { "Failed to list specifications for namespace: ${namespace.displayName()}" }
        }.getOrElse { emptyList() }
    }
    
    override suspend fun listJobs(namespace: MockNamespace, status: JobStatus?, limit: Int): List<GenerationJob> {
        logger.debug { "Listing jobs for namespace: ${namespace.displayName()}" }
        
        return runCatching {
            val prefix = "${namespace.toPrefix()}/$JOBS_PREFIX/"
            
            val listRequest = ListObjectsV2Request {
                bucket = bucketName
                prefix = prefix
                maxKeys = limit
            }
            
            val objects = s3Client.listObjectsV2(listRequest).contents ?: emptyList()
            
            objects.mapNotNull { obj ->
                if (obj.key?.endsWith("/metadata.json") == true) {
                    try {
                        val getRequest = GetObjectRequest {
                            bucket = bucketName
                            key = obj.key!!
                        }
                        
                        val response = s3Client.getObject(getRequest)
                        val content = response.body?.let { String(it) } ?: return@mapNotNull null
                        
                        val job = objectMapper.readValue(content, GenerationJob::class.java)
                        
                        // Filter by status if specified
                        if (status == null || job.status == status) job else null
                    } catch (e: Exception) {
                        logger.warn(e) { "Failed to deserialize job from key: ${obj.key}" }
                        null
                    }
                } else null
            }.take(limit)
        }.onFailure { exception ->
            logger.error(exception) { "Failed to list jobs for namespace: ${namespace.displayName()}" }
        }.getOrElse { emptyList() }
    }
    
    override suspend fun deleteGeneratedMocks(jobId: String): Boolean {
        logger.info { "Deleting generated mocks for job: $jobId" }
        
        return runCatching {
            val jobKey = findJobKey(jobId) ?: return@runCatching false
            val mocksPrefix = "$jobKey/mocks/"
            
            // List all mock objects
            val listRequest = ListObjectsV2Request {
                bucket = bucketName
                prefix = mocksPrefix
            }
            
            val objects = s3Client.listObjectsV2(listRequest).contents ?: emptyList()
            
            // Delete all mock objects
            objects.forEach { obj ->
                s3Client.deleteObject {
                    bucket = bucketName
                    key = obj.key!!
                }
            }
            
            // Delete results file
            s3Client.deleteObject {
                bucket = bucketName
                key = "$jobKey/results.json"
            }
            
            true
        }.onFailure { exception ->
            logger.error(exception) { "Failed to delete generated mocks for job: $jobId" }
        }.getOrElse { false }
    }
    
    override suspend fun deleteSpecification(namespace: MockNamespace, version: String?): Boolean {
        logger.info { "Deleting specification for namespace: ${namespace.displayName()}, version: $version" }
        
        return runCatching {
            if (version != null) {
                // Delete specific version
                val versionedKey = "${namespace.toPrefix()}/$API_SPECS_PREFIX/$VERSIONS_PREFIX/$version.json"
                s3Client.deleteObject {
                    bucket = bucketName
                    key = versionedKey
                }
            } else {
                // Delete current specification
                val currentKey = "${namespace.toPrefix()}/$API_SPECS_PREFIX/$CURRENT_SPEC_FILE"
                s3Client.deleteObject {
                    bucket = bucketName
                    key = currentKey
                }
            }
            
            true
        }.onFailure { exception ->
            logger.error(exception) { "Failed to delete specification for namespace: ${namespace.displayName()}" }
        }.getOrElse { false }
    }
    
    override suspend fun isHealthy(): Boolean {
        return runCatching {
            // Try to list objects in the bucket to verify connectivity
            val listRequest = ListObjectsV2Request {
                bucket = bucketName
                maxKeys = 1
            }
            
            s3Client.listObjectsV2(listRequest)
            true
        }.onFailure { exception ->
            logger.warn(exception) { "S3 health check failed" }
        }.getOrElse { false }
    }
    
    private suspend fun findJobKey(jobId: String): String? {
        // Search for job across all namespaces
        val listRequest = ListObjectsV2Request {
            bucket = bucketName
            prefix = "mocknest/"
        }
        
        return runCatching {
            val objects = s3Client.listObjectsV2(listRequest).contents ?: emptyList()
            
            objects.find { obj ->
                obj.key?.contains("/$JOBS_PREFIX/$jobId/") == true
            }?.key?.let { key ->
                // Extract job key prefix (everything up to /metadata.json or /results.json)
                key.substringBeforeLast("/")
            }
        }.getOrNull()
    }
}