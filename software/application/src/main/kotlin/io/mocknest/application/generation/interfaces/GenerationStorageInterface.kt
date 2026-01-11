package io.mocknest.application.generation.interfaces

import io.mocknest.domain.generation.*

/**
 * Abstraction for storing generated mocks and specifications.
 * Handles namespace-based organization and job persistence.
 */
interface GenerationStorageInterface {
    
    /**
     * Store generated mocks for a job.
     */
    suspend fun storeGeneratedMocks(
        mocks: List<GeneratedMock>,
        jobId: String
    ): String // Returns storage key/path
    
    /**
     * Retrieve generated mocks for a job.
     */
    suspend fun getGeneratedMocks(jobId: String): List<GeneratedMock>
    
    /**
     * Store API specification for future evolution.
     */
    suspend fun storeSpecification(
        namespace: MockNamespace,
        specification: APISpecification,
        version: String? = null
    ): String // Returns storage key/path
    
    /**
     * Retrieve stored API specification.
     */
    suspend fun getSpecification(
        namespace: MockNamespace,
        version: String? = null // null = latest
    ): APISpecification?
    
    /**
     * Store generation job metadata.
     */
    suspend fun storeJob(job: GenerationJob): String
    
    /**
     * Retrieve generation job.
     */
    suspend fun getJob(jobId: String): GenerationJob?
    
    /**
     * Update job status.
     */
    suspend fun updateJobStatus(jobId: String, status: JobStatus, error: String? = null)
    
    /**
     * Store job results.
     */
    suspend fun storeJobResults(jobId: String, results: GenerationResults)
    
    /**
     * List all specifications for a namespace.
     */
    suspend fun listSpecifications(namespace: MockNamespace): List<SpecificationMetadata>
    
    /**
     * List all jobs for a namespace.
     */
    suspend fun listJobs(
        namespace: MockNamespace,
        status: JobStatus? = null,
        limit: Int = 50
    ): List<GenerationJob>
    
    /**
     * Delete generated mocks for a job.
     */
    suspend fun deleteGeneratedMocks(jobId: String): Boolean
    
    /**
     * Delete stored specification.
     */
    suspend fun deleteSpecification(namespace: MockNamespace, version: String? = null): Boolean
    
    /**
     * Check if storage is healthy and accessible.
     */
    suspend fun isHealthy(): Boolean
}