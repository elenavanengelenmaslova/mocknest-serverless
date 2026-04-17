package nl.vintik.mocknest.infra.aws.runtime.storage

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.*
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.toByteArray
import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import java.nio.charset.StandardCharsets

private val logger = KotlinLogging.logger {}

@Repository
class S3ObjectStorageAdapter(
    @param:Value("\${storage.bucket.name}") private val bucketName: String,
    private val s3Client: S3Client,
) : ObjectStorageInterface {

    override suspend fun save(id: String, content: String): String {
        logger.debug { "Saving object with id: $id" }
        val contentBytes = content.toByteArray(StandardCharsets.UTF_8)
        val byteStream = ByteStream.fromBytes(contentBytes)
        s3Client.putObject(
            PutObjectRequest {
                bucket = bucketName
                key = id
                body = byteStream
                contentLength = byteStream.contentLength
                contentType = when {
                    id.endsWith(".json", ignoreCase = true) -> "application/json; charset=UTF-8"
                    id.endsWith(".txt", ignoreCase = true) -> "text/plain; charset=UTF-8"
                    else -> "application/octet-stream"
                }
            }
        )
        return "s3://$bucketName/$id"
    }

    override suspend fun get(id: String): String? {
        logger.debug { "Getting object with id: $id" }
        return s3Client.runCatching {
            var content: String? = null
            getObject(GetObjectRequest {
                bucket = bucketName
                key = id
            }) { response ->
                content = response.body?.toByteArray()?.toString(StandardCharsets.UTF_8)
            }
            content
        }.onFailure { exception ->
            logger.warn(exception) { "Object not found or failed to retrieve: id=$id" }
        }.getOrNull()
    }

    override suspend fun delete(id: String) {
        logger.debug { "Deleting object with id: $id" }
        s3Client.runCatching {
            deleteObject(DeleteObjectRequest {
                bucket = bucketName
                key = id
            })
        }.onFailure { exception -> 
            logger.warn(exception) { "Failed to delete object: id=$id" }
        }.getOrThrow()
    }

    override fun list(): Flow<String> = flow {
        logger.info { "Listing all object" }
        var token: String? = null
        do {
            val resp = s3Client.listObjectsV2(ListObjectsV2Request {
                bucket = bucketName; continuationToken = token; maxKeys = 1000
            })
            resp.contents?.forEach { it.key?.let { k -> emit(k) } }
            token = resp.nextContinuationToken
        } while (!token.isNullOrBlank())
    }

    override fun listPrefix(prefix: String): Flow<String> = flow {
        logger.info { "Listing objects with prefix: $prefix and bucket: '$bucketName'" }
        var token: String? = null
        do {
            val resp = s3Client.listObjectsV2(ListObjectsV2Request {
                bucket = bucketName
                this.prefix = prefix
                continuationToken = token
                maxKeys = 1000
            })
            resp.contents?.forEach { it.key?.let { k -> emit(k) } }
            token = resp.nextContinuationToken
        } while (!token.isNullOrBlank())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getMany(ids: Flow<String>, concurrency: Int): Flow<Pair<String, String?>> =
        ids.flatMapMerge(concurrency) { id ->
            flow {
                val content = s3Client.runCatching {
                    var result: String? = null
                    getObject(GetObjectRequest {
                        bucket = bucketName
                        key = id
                    }) { response ->
                        result = response.body?.toByteArray()?.toString(StandardCharsets.UTF_8)
                    }
                    result
                }.onFailure { exception -> 
                    logger.warn(exception) { "Failed to get object in batch operation: id=$id" }
                }.getOrNull()
                emit(id to content)
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun deleteMany(ids: Flow<String>, concurrency: Int) {
        // Batch at 1000 and submit batches in parallel
        fun chunkedFlow(source: Flow<String>, batchSize: Int): Flow<List<String>> = flow {
            val buf = ArrayList<String>(batchSize)
            source.collect { id ->
                buf.add(id)
                if (buf.size >= batchSize) {
                    emit(ArrayList(buf))
                    buf.clear()
                }
            }
            if (buf.isNotEmpty()) emit(ArrayList(buf))
        }

        chunkedFlow(ids, 1000)
            .flatMapMerge(concurrency) { batch ->
                flow {
                    s3Client.deleteObjects(DeleteObjectsRequest {
                        bucket = bucketName
                        delete = Delete {
                            objects = batch.map { key -> ObjectIdentifier { this.key = key } }
                            quiet = true
                        }
                    })
                    emit(Unit)
                }
            }
            .collect { } // drain to execute
    }
}