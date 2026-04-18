package nl.vintik.mocknest.infra.aws.runtime.storage

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.*
import aws.smithy.kotlin.runtime.content.ByteStream
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class S3ObjectStorageAdapterTest {

    private val mockS3Client: S3Client = mockk(relaxed = true)
    private val bucketName = "test-bucket"
    private val adapter = S3ObjectStorageAdapter(bucketName, mockS3Client)

    @AfterEach
    fun tearDown() {
        clearMocks(mockS3Client)
    }

    @Nested
    inner class Save {

        @Test
        fun `Given JSON file When saving Then should set application json content type`() = runTest {
            coEvery { mockS3Client.putObject(any<PutObjectRequest>()) } returns PutObjectResponse {}

            val result = adapter.save("mappings/test.json", """{"key":"value"}""")

            assertEquals("s3://test-bucket/mappings/test.json", result)
            coVerify {
                mockS3Client.putObject(match<PutObjectRequest> { req ->
                    req.bucket == bucketName &&
                    req.key == "mappings/test.json" &&
                    req.contentType == "application/json; charset=UTF-8"
                })
            }
        }

        @Test
        fun `Given text file When saving Then should set text plain content type`() = runTest {
            coEvery { mockS3Client.putObject(any<PutObjectRequest>()) } returns PutObjectResponse {}

            val result = adapter.save("files/readme.txt", "Hello world")

            assertEquals("s3://test-bucket/files/readme.txt", result)
            coVerify {
                mockS3Client.putObject(match<PutObjectRequest> { req ->
                    req.contentType == "text/plain; charset=UTF-8"
                })
            }
        }

        @Test
        fun `Given binary file When saving Then should set octet stream content type`() = runTest {
            coEvery { mockS3Client.putObject(any<PutObjectRequest>()) } returns PutObjectResponse {}

            val result = adapter.save("files/data.bin", "binary content")

            assertEquals("s3://test-bucket/files/data.bin", result)
            coVerify {
                mockS3Client.putObject(match<PutObjectRequest> { req ->
                    req.contentType == "application/octet-stream"
                })
            }
        }

        @Test
        fun `Given uppercase JSON extension When saving Then should detect content type case insensitively`() = runTest {
            coEvery { mockS3Client.putObject(any<PutObjectRequest>()) } returns PutObjectResponse {}

            adapter.save("mappings/test.JSON", """{"key":"value"}""")

            coVerify {
                mockS3Client.putObject(match<PutObjectRequest> { req ->
                    req.contentType == "application/json; charset=UTF-8"
                })
            }
        }
    }

    @Nested
    inner class Get {

        @Test
        fun `Given existing object When getting Then should return content`() = runTest {
            val expectedContent = """{"mapping":"data"}"""
            coEvery {
                mockS3Client.getObject(any<GetObjectRequest>(), any<suspend (GetObjectResponse) -> String?>())
            } coAnswers {
                val block = secondArg<suspend (GetObjectResponse) -> String?>()
                val response = GetObjectResponse {
                    body = ByteStream.fromBytes(expectedContent.toByteArray())
                }
                block(response)
            }

            val result = adapter.get("mappings/test.json")

            assertNotNull(result)
            assertEquals(expectedContent, result)
        }

        @Test
        fun `Given non-existing object When getting Then should return null`() = runTest {
            coEvery {
                mockS3Client.getObject(any<GetObjectRequest>(), any<suspend (GetObjectResponse) -> String?>())
            } throws NoSuchKey {}

            val result = adapter.get("mappings/missing.json")

            assertNull(result)
        }
    }

    @Nested
    inner class Delete {

        @Test
        fun `Given existing object When deleting Then should call deleteObject`() = runTest {
            coEvery { mockS3Client.deleteObject(any<DeleteObjectRequest>()) } returns DeleteObjectResponse {}

            adapter.delete("mappings/test.json")

            coVerify {
                mockS3Client.deleteObject(match<DeleteObjectRequest> { req ->
                    req.bucket == bucketName && req.key == "mappings/test.json"
                })
            }
        }
    }

    @Nested
    inner class ListObjects {

        @Test
        fun `Given objects in bucket When listing Then should emit all keys`() = runTest {
            coEvery { mockS3Client.listObjectsV2(any<ListObjectsV2Request>()) } returns ListObjectsV2Response {
                contents = listOf(
                    Object { key = "mappings/a.json" },
                    Object { key = "mappings/b.json" },
                )
                nextContinuationToken = null
            }

            val keys = adapter.list().toList()

            assertEquals(listOf("mappings/a.json", "mappings/b.json"), keys)
        }

        @Test
        fun `Given paginated results When listing Then should follow continuation tokens`() = runTest {
            coEvery {
                mockS3Client.listObjectsV2(match<ListObjectsV2Request> { it.continuationToken == null })
            } returns ListObjectsV2Response {
                contents = listOf(Object { key = "a.json" })
                nextContinuationToken = "token-1"
            }
            coEvery {
                mockS3Client.listObjectsV2(match<ListObjectsV2Request> { it.continuationToken == "token-1" })
            } returns ListObjectsV2Response {
                contents = listOf(Object { key = "b.json" })
                nextContinuationToken = null
            }

            val keys = adapter.list().toList()

            assertEquals(listOf("a.json", "b.json"), keys)
        }

        @Test
        fun `Given empty bucket When listing Then should return empty flow`() = runTest {
            coEvery { mockS3Client.listObjectsV2(any<ListObjectsV2Request>()) } returns ListObjectsV2Response {
                contents = emptyList()
                nextContinuationToken = null
            }

            val keys = adapter.list().toList()

            assertTrue(keys.isEmpty())
        }
    }

    @Nested
    inner class ListPrefix {

        @Test
        fun `Given prefix When listing Then should pass prefix to S3`() = runTest {
            coEvery { mockS3Client.listObjectsV2(any<ListObjectsV2Request>()) } returns ListObjectsV2Response {
                contents = listOf(
                    Object { key = "mappings/a.json" },
                    Object { key = "mappings/b.json" },
                )
                nextContinuationToken = null
            }

            val keys = adapter.listPrefix("mappings/").toList()

            assertEquals(listOf("mappings/a.json", "mappings/b.json"), keys)
            coVerify {
                mockS3Client.listObjectsV2(match<ListObjectsV2Request> { req ->
                    req.prefix == "mappings/" && req.bucket == bucketName
                })
            }
        }
    }

    @Nested
    inner class GetMany {

        @Test
        fun `Given multiple ids When getting many Then should return pairs of id and content`() = runTest {
            val ids = flowOf("a.json", "b.json")

            coEvery {
                mockS3Client.getObject(match<GetObjectRequest> { it.key == "a.json" }, any<suspend (GetObjectResponse) -> String?>())
            } coAnswers {
                val block = secondArg<suspend (GetObjectResponse) -> String?>()
                block(GetObjectResponse { body = ByteStream.fromBytes("content-a".toByteArray()) })
            }
            coEvery {
                mockS3Client.getObject(match<GetObjectRequest> { it.key == "b.json" }, any<suspend (GetObjectResponse) -> String?>())
            } coAnswers {
                val block = secondArg<suspend (GetObjectResponse) -> String?>()
                block(GetObjectResponse { body = ByteStream.fromBytes("content-b".toByteArray()) })
            }

            val results = adapter.getMany(ids).toList().sortedBy { it.first }

            assertEquals(2, results.size)
            assertEquals("a.json" to "content-a", results[0])
            assertEquals("b.json" to "content-b", results[1])
        }

        @Test
        fun `Given failing object When getting many Then should return null for failed item`() = runTest {
            val ids = flowOf("good.json", "bad.json")

            coEvery {
                mockS3Client.getObject(match<GetObjectRequest> { it.key == "good.json" }, any<suspend (GetObjectResponse) -> String?>())
            } coAnswers {
                val block = secondArg<suspend (GetObjectResponse) -> String?>()
                block(GetObjectResponse { body = ByteStream.fromBytes("content".toByteArray()) })
            }
            coEvery {
                mockS3Client.getObject(match<GetObjectRequest> { it.key == "bad.json" }, any<suspend (GetObjectResponse) -> String?>())
            } throws NoSuchKey {}

            val results = adapter.getMany(ids).toList().sortedBy { it.first }

            assertEquals(2, results.size)
            assertEquals("bad.json", results[0].first)
            assertNull(results[0].second)
            assertEquals("good.json", results[1].first)
            assertEquals("content", results[1].second)
        }
    }

    @Nested
    inner class DeleteMany {

        @Test
        fun `Given multiple ids When deleting many Then should batch delete`() = runTest {
            val ids = flowOf("a.json", "b.json", "c.json")

            coEvery { mockS3Client.deleteObjects(any<DeleteObjectsRequest>()) } returns DeleteObjectsResponse {}

            adapter.deleteMany(ids)

            coVerify {
                mockS3Client.deleteObjects(match<DeleteObjectsRequest> { req ->
                    req.bucket == bucketName &&
                    req.delete?.objects?.size == 3 &&
                    req.delete?.quiet == true
                })
            }
        }
    }
}
