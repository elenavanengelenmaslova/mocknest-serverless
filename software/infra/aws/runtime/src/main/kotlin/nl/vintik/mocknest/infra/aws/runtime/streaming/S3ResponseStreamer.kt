package nl.vintik.mocknest.infra.aws.runtime.streaming

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.HeadObjectRequest
import aws.smithy.kotlin.runtime.content.toInputStream
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.io.OutputStream

private val logger = KotlinLogging.logger {}

/**
 * Streams S3 object content directly to an [OutputStream] using a bounded 1MB buffer.
 *
 * When a WireMock response references a `bodyFileName` stored in S3, this class
 * streams the file content without loading the entire object into memory.
 *
 * **Validates: Requirements 7.1, 7.2, 7.3, 7.5**
 */
class S3ResponseStreamer(
    private val s3Client: S3Client,
    private val bucketName: String,
) {

    companion object {
        /** Maximum buffer size for streaming S3 content: 1MB */
        const val BUFFER_SIZE = 1024 * 1024
    }

    /**
     * Gets the content length of an S3 object without downloading it.
     * Uses HeadObject to retrieve metadata only.
     *
     * @param s3Key The S3 object key
     * @return Content length in bytes, or null if the object doesn't exist or an error occurs
     */
    suspend fun getContentLength(s3Key: String): Long? =
        runCatching {
            s3Client.headObject(HeadObjectRequest {
                bucket = bucketName
                key = s3Key
            }).contentLength
        }.onFailure { exception ->
            logger.error(exception) {
                "Failed to get content length for S3 object: key=$s3Key, bucket=$bucketName"
            }
        }.getOrNull()

    /**
     * Streams the content of an S3 object identified by [s3Key] directly to [output]
     * using a buffer no larger than [BUFFER_SIZE] (1MB).
     *
     * @param s3Key The S3 object key (e.g., `__files/my-response.json`)
     * @param output The OutputStream to write the streamed content to
     * @return `true` if streaming completed successfully, `false` if an error occurred
     */
    fun streamToOutput(s3Key: String, output: OutputStream): Boolean =
        runCatching {
            runBlocking {
                s3Client.getObject(GetObjectRequest {
                    bucket = bucketName
                    key = s3Key
                }) { response ->
                    val body = response.body
                        ?: throw S3StreamingException("S3 object body is null for key: $s3Key")

                    val inputStream = body.toInputStream()
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        output.flush()
                    }
                }
            }
            true
        }.onFailure { exception ->
            logger.error(exception) {
                "Failed to stream S3 object: key=$s3Key, bucket=$bucketName, reason=${exception.message}"
            }
        }.getOrDefault(false)

    /**
     * Streams S3 object content through a consumer callback, keeping the getObject
     * lifecycle contained within this method. The consumer receives the InputStream
     * and content length, and must consume the stream before returning.
     *
     * This design avoids returning an InputStream that would be invalid outside
     * the Kotlin AWS SDK's getObject callback scope.
     *
     * @param s3Key The S3 object key
     * @param consumer Callback that receives the InputStream and content length.
     *                 Must consume the stream before returning.
     * @return true if streaming completed successfully, false on error
     */
    suspend fun streamWithConsumer(
        s3Key: String,
        consumer: suspend (inputStream: InputStream, contentLength: Long) -> Unit,
    ): Boolean =
        runCatching {
            s3Client.getObject(GetObjectRequest {
                bucket = bucketName
                key = s3Key
            }) { response ->
                val body = response.body
                    ?: throw S3StreamingException("S3 object body is null for key: $s3Key")
                val contentLength = response.contentLength ?: 0L
                val inputStream = body.toInputStream()
                consumer(inputStream, contentLength)
            }
            true
        }.onFailure { exception ->
            logger.error(exception) {
                "Failed to stream S3 object with consumer: key=$s3Key, bucket=$bucketName"
            }
        }.getOrDefault(false)
}

/**
 * Exception indicating a failure during S3 streaming operations.
 */
class S3StreamingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
