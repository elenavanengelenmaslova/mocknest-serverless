package nl.vintik.mocknest.infra.aws.generation.storage

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.*
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.toByteArray
import nl.vintik.mocknest.application.core.mapper
import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.generation.interfaces.GenerationStorageInterface
import nl.vintik.mocknest.domain.generation.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import kotlin.runCatching

/**
 * S3-based storage adapter for generated mocks and specifications.
 * Implements namespace-based organization and job persistence.
 */
@Component
class S3GenerationStorageAdapter(
    private val s3Client: S3Client,
    @param:Value("\${storage.bucket.name:}") private val bucketName: String
) : GenerationStorageInterface {
    
    private val logger = KotlinLogging.logger {}
    private val objectMapper = mapper
    
    companion object {
        private const val GENERATED_MOCKS_PREFIX = "generated-mocks"
        private const val API_SPECS_PREFIX = "api-specs"
        private const val JOBS_PREFIX = "jobs"
        private const val CURRENT_SPEC_FILE = "current.json"
        private const val VERSIONS_PREFIX = "versions"
    }
    
    override suspend fun storeGeneratedMocks(mocks: List<GeneratedMock>, jobId: String): String {
        logger.info { "Storing ${mocks.size} generated mocks for job: $jobId" }

        return s3Client.runCatching {
            val namespace = requireNotNull(mocks.firstOrNull()?.namespace) { "No mocks provided or missing namespace" }

            val storageKey = "${namespace.toPrefix()}/$GENERATED_MOCKS_PREFIX/$JOBS_PREFIX/$jobId"
            
            // Store individual mocks
            mocks.forEach { mock ->
                val mockKey = "$storageKey/mocks/${mock.id}.json"
                val mockJson = objectMapper.writeValueAsString(mock)
                
                putObject(PutObjectRequest {
                    bucket = bucketName
                    key = mockKey
                    body = ByteStream.fromBytes(mockJson.toByteArray())
                    contentType = "application/json"
                    metadata = mapOf(
                        "job-id" to jobId,
                        "mock-id" to mock.id,
                        "namespace" to namespace.displayName(),
                        "generated-at" to mock.generatedAt.toString()
                    )
                })
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
            
            putObject(PutObjectRequest {
                bucket = bucketName
                key = resultsKey
                body = ByteStream.fromBytes(objectMapper.writeValueAsBytes(results))
                contentType = "application/json"
            })
            
            storageKey
        }.onFailure { exception ->
            logger.error(exception) { "Failed to store generated mocks for job: $jobId" }
        }.getOrThrow()
    }
    
    override suspend fun getGeneratedMocks(jobId: String): List<GeneratedMock> {
        logger.debug { "Retrieving generated mocks for job: $jobId" }

        return s3Client.runCatching {
            // Find the job by searching across namespaces
            val jobKey = requireNotNull(findJobKey(jobId)) { "Job not found: $jobId" }
            val mocksPrefix = "$jobKey/mocks/"

            val listRequest = ListObjectsV2Request {
                bucket = bucketName
                prefix = mocksPrefix
            }

            val objects = listObjectsV2(listRequest).contents ?: emptyList()

            objects.mapNotNull { obj ->
                runCatching {
                    val getRequest = GetObjectRequest {
                        bucket = bucketName
                        key = obj.key!!
                    }

                    var content: String? = null
                    getObject(getRequest) { response ->
                        content = response.body?.toByteArray()?.let { String(it) }
                    }

                    content?.let { objectMapper.readValue(it, GeneratedMock::class.java) }
                }.onFailure { e ->
                    logger.warn(e) { "Failed to deserialize mock from key: ${obj.key}" }
                }.getOrNull()
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
        
        return s3Client.runCatching {
            val specJson = objectMapper.writeValueAsString(specification)
            val timestamp = Instant.now().toString()
            val actualVersion = version ?: specification.version
            
            // Store as current specification
            val currentKey = "${namespace.toPrefix()}/$API_SPECS_PREFIX/$CURRENT_SPEC_FILE"
            putObject(PutObjectRequest {
                bucket = bucketName
                key = currentKey
                body = ByteStream.fromBytes(specJson.toByteArray())
                contentType = "application/json"
                metadata = mapOf(
                    "namespace" to namespace.displayName(),
                    "version" to actualVersion,
                    "title" to specification.title,
                    "format" to specification.format.name,
                    "stored-at" to timestamp
                )
            })
            
            // Store versioned copy
            val versionedKey = "${namespace.toPrefix()}/$API_SPECS_PREFIX/$VERSIONS_PREFIX/$actualVersion.json"
            putObject(PutObjectRequest {
                bucket = bucketName
                key = versionedKey
                body = ByteStream.fromBytes(specJson.toByteArray())
                contentType = "application/json"
                metadata = mapOf(
                    "namespace" to namespace.displayName(),
                    "version" to actualVersion,
                    "title" to specification.title,
                    "format" to specification.format.name,
                    "stored-at" to timestamp
                )
            })
            
            currentKey
        }.onFailure { exception ->
            logger.error(exception) { "Failed to store specification for namespace: ${namespace.displayName()}" }
        }.getOrThrow()
    }
    
    override suspend fun getSpecification(namespace: MockNamespace, version: String?): APISpecification? {
        logger.debug { "Retrieving specification for namespace: ${namespace.displayName()}, version: $version" }
        
        return s3Client.runCatching {
            val objectKey = if (version != null) {
                "${namespace.toPrefix()}/$API_SPECS_PREFIX/$VERSIONS_PREFIX/$version.json"
            } else {
                "${namespace.toPrefix()}/$API_SPECS_PREFIX/$CURRENT_SPEC_FILE"
            }
            
            val getRequest = GetObjectRequest {
                bucket = bucketName
                key = objectKey
            }
            
            var content: String? = null
            getObject(getRequest) { response ->
                content = response.body?.toByteArray()?.let { String(it) }
            }
            
            content?.let { objectMapper.readValue(it, APISpecification::class.java) }
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
        
        return s3Client.runCatching {
            val jobKey = "${job.request.namespace.toPrefix()}/$JOBS_PREFIX/${job.id}/metadata.json"
            val jobJson = objectMapper.writeValueAsString(job)
            
            putObject(PutObjectRequest {
                bucket = bucketName
                key = jobKey
                body = ByteStream.fromBytes(jobJson.toByteArray())
                contentType = "application/json"
                metadata = mapOf(
                    "job-id" to job.id,
                    "namespace" to job.request.namespace.displayName(),
                    "status" to job.status.name,
                    "type" to job.request.type.name,
                    "created-at" to job.createdAt.toString()
                )
            })
            
            jobKey
        }.onFailure { exception ->
            logger.error(exception) { "Failed to store job: ${job.id}" }
        }.getOrThrow()
    }
    
    override suspend fun getJob(jobId: String): GenerationJob? {
        logger.debug { "Retrieving job: $jobId" }
        
        return s3Client.runCatching {
            val jobKey = findJobKey(jobId) ?: return@runCatching null
            val metadataKey = "$jobKey/metadata.json"
            
            val getRequest = GetObjectRequest {
                bucket = bucketName
                key = metadataKey
            }
            
            var content: String? = null
            getObject(getRequest) { response ->
                content = response.body?.toByteArray()?.let { String(it) }
            }
            
            content?.let { objectMapper.readValue(it, GenerationJob::class.java) }
        }.onFailure { exception ->
            if (exception !is NoSuchKey) {
                logger.error(exception) { "Failed to retrieve job: $jobId" }
            }
        }.getOrNull()
    }
    
    override suspend fun updateJobStatus(jobId: String, status: JobStatus, error: String?) {
        logger.debug { "Updating job status: $jobId -> $status" }

        runCatching {
            val job = requireNotNull(getJob(jobId)) { "Job not found: $jobId" }

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

        s3Client.runCatching {
            val jobKey = requireNotNull(findJobKey(jobId)) { "Job not found: $jobId" }
            val resultsKey = "$jobKey/results.json"
            
            val resultsJson = objectMapper.writeValueAsString(results)
            
            putObject(PutObjectRequest {
                bucket = bucketName
                key = resultsKey
                body = ByteStream.fromBytes(resultsJson.toByteArray())
                contentType = "application/json"
                metadata = mapOf(
                    "job-id" to jobId,
                    "total-generated" to results.totalGenerated.toString(),
                    "successful" to results.successful.toString(),
                    "failed" to results.failed.toString()
                )
            })
        }.onFailure { exception ->
            logger.error(exception) { "Failed to store job results: $jobId" }
        }
    }
    
    override suspend fun listSpecifications(namespace: MockNamespace): List<SpecificationMetadata> {
        logger.debug { "Listing specifications for namespace: ${namespace.displayName()}" }

        return s3Client.runCatching {
            val searchPrefix = "${namespace.toPrefix()}/$API_SPECS_PREFIX/$VERSIONS_PREFIX/"

            val listRequest = ListObjectsV2Request {
                bucket = bucketName
                prefix = searchPrefix
            }

            val objects = listObjectsV2(listRequest).contents ?: emptyList()

            objects.mapNotNull { obj ->
                val key = obj.key ?: return@mapNotNull null
                runCatching {
                    val versionFromKey = key.substringAfter("$VERSIONS_PREFIX/").substringBefore(".json")
                    SpecificationMetadata(
                        title = "Unknown",
                        version = versionFromKey,
                        format = SpecificationFormat.OPENAPI_3,
                        endpointCount = 0,
                        schemaCount = 0
                    )
                }.onFailure { e ->
                    logger.warn(e) { "Failed to parse specification metadata from key: $key" }
                }.getOrNull()
            }
        }.onFailure { exception ->
            logger.error(exception) { "Failed to list specifications for namespace: ${namespace.displayName()}" }
        }.getOrElse { emptyList() }
    }

    override suspend fun listJobs(namespace: MockNamespace, status: JobStatus?, limit: Int): List<GenerationJob> {
        logger.debug { "Listing jobs for namespace: ${namespace.displayName()}" }

        return s3Client.runCatching {
            val searchPrefix = "${namespace.toPrefix()}/$JOBS_PREFIX/"

            val listRequest = ListObjectsV2Request {
                bucket = bucketName
                prefix = searchPrefix
                maxKeys = limit
            }

            val objects = listObjectsV2(listRequest).contents ?: emptyList()

            objects.mapNotNull { obj ->
                if (obj.key?.endsWith("/metadata.json") == true) {
                    runCatching {
                        val getRequest = GetObjectRequest {
                            bucket = bucketName
                            key = obj.key!!
                        }

                        var content: String? = null
                        getObject(getRequest) { response ->
                            content = response.body?.toByteArray()?.let { String(it) }
                        }

                        val job =
                            content?.let { objectMapper.readValue(it, GenerationJob::class.java) } ?: return@runCatching null

                        // Filter by status if specified
                        if (status == null || job.status == status) job else null
                    }.onFailure { e ->
                        logger.warn(e) { "Failed to deserialize job from key: ${obj.key}" }
                    }.getOrNull()
                } else null
            }.take(limit)
        }.onFailure { exception ->
            logger.error(exception) { "Failed to list jobs for namespace: ${namespace.displayName()}" }
        }.getOrElse { emptyList() }
    }
    
    override suspend fun deleteGeneratedMocks(jobId: String): Boolean {
        logger.info { "Deleting generated mocks for job: $jobId" }
        
        return s3Client.runCatching {
            val jobKey = findJobKey(jobId) ?: return@runCatching false
            val mocksPrefix = "$jobKey/mocks/"
            
            // List all mock objects
            val listRequest = ListObjectsV2Request {
                bucket = bucketName
                prefix = mocksPrefix
            }
            
            val objects = listObjectsV2(listRequest).contents ?: emptyList()
            
            // Collect all keys to delete: mock objects + results file
            val keysToDelete = objects.mapNotNull { it.key } + "$jobKey/results.json"
            
            if (keysToDelete.isEmpty()) return@runCatching true
            
            // Batch delete all objects in a single request
            deleteObjects(DeleteObjectsRequest {
                bucket = bucketName
                delete = Delete {
                    this.objects = keysToDelete.map { key -> ObjectIdentifier { this.key = key } }
                    quiet = true
                }
            })
            
            true
        }.onFailure { exception ->
            logger.error(exception) { "Failed to delete generated mocks for job: $jobId" }
        }.getOrElse { false }
    }
    
    override suspend fun deleteSpecification(namespace: MockNamespace, version: String?): Boolean {
        logger.info { "Deleting specification for namespace: ${namespace.displayName()}, version: $version" }
        
        return s3Client.runCatching {
            if (version != null) {
                // Delete specific version
                val versionedKey = "${namespace.toPrefix()}/$API_SPECS_PREFIX/$VERSIONS_PREFIX/$version.json"
                deleteObject(DeleteObjectRequest {
                    bucket = bucketName
                    key = versionedKey
                })
            } else {
                // Delete current specification
                val currentKey = "${namespace.toPrefix()}/$API_SPECS_PREFIX/$CURRENT_SPEC_FILE"
                deleteObject(DeleteObjectRequest {
                    bucket = bucketName
                    key = currentKey
                })
            }
            
            true
        }.onFailure { exception ->
            logger.error(exception) { "Failed to delete specification for namespace: ${namespace.displayName()}" }
        }.getOrElse { false }
    }
    
    override suspend fun isHealthy(): Boolean {
        return s3Client.runCatching {
            // Try to list objects in the bucket to verify connectivity
            val listRequest = ListObjectsV2Request {
                bucket = bucketName
                maxKeys = 1
            }
            
            listObjectsV2(listRequest)
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
        
        return s3Client.runCatching {
            val objects = listObjectsV2(listRequest).contents ?: emptyList()

            objects.find { obj ->
                        obj.key?.contains("/$JOBS_PREFIX/$jobId/") == true
                    }?.key?.substringBeforeLast("/")
        }.getOrNull()
    }
}