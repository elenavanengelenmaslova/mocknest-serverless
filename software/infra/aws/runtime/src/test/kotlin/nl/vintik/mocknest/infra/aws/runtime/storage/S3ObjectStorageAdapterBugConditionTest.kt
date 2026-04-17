package nl.vintik.mocknest.infra.aws.runtime.storage

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.DeleteObjectRequest
import aws.sdk.kotlin.services.s3.model.DeleteObjectResponse
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.GetObjectResponse
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.sdk.kotlin.services.s3.model.PutObjectResponse
import aws.smithy.kotlin.runtime.content.ByteStream
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.stream.Stream
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Bug Condition Exploration Test 1b — `S3ObjectStorageAdapter` per-object operations log at INFO instead of DEBUG
 *
 * These tests PROVE the bug exists on UNFIXED code.
 * They are EXPECTED TO FAIL — that is the correct outcome, confirming the bug exists.
 *
 * DO NOT fix production code to make these pass.
 * DO NOT fix these tests when they fail.
 *
 * **Validates: Requirements 1.3, 2.3**
 *
 * Bug Condition: `get()`, `delete()`, and `save()` log per-object messages at INFO level
 * instead of DEBUG level, flooding CloudWatch during bulk operations.
 */
class S3ObjectStorageAdapterBugConditionTest {

    private val s3Client: S3Client = mockk(relaxed = true)
    private val bucketName = "test-bucket"
    private val adapter = S3ObjectStorageAdapter(bucketName, s3Client)

    private val capturedEvents = CopyOnWriteArrayList<ILoggingEvent>()
    private lateinit var logAppender: AppenderBase<ILoggingEvent>
    private var originalLoggerLevel: Level? = null

    @BeforeEach
    fun attachLogAppender() {
        capturedEvents.clear()
        logAppender = object : AppenderBase<ILoggingEvent>() {
            override fun append(event: ILoggingEvent) {
                capturedEvents.add(event)
            }
        }
        logAppender.start()

        // Attach to the S3ObjectStorageAdapter logger specifically
        val adapterLogger = LoggerFactory.getLogger("nl.vintik.mocknest.infra.aws.runtime.storage.S3ObjectStorageAdapter") as? Logger
        assertNotNull(adapterLogger, "Expected Logback logger for S3ObjectStorageAdapter")
        originalLoggerLevel = adapterLogger.level
        adapterLogger.level = Level.DEBUG
        adapterLogger.addAppender(logAppender)
    }

    @AfterEach
    fun detachLogAppender() {
        val adapterLogger = LoggerFactory.getLogger("nl.vintik.mocknest.infra.aws.runtime.storage.S3ObjectStorageAdapter") as? Logger
        adapterLogger?.detachAppender(logAppender)
        adapterLogger?.level = originalLoggerLevel
        logAppender.stop()
        clearAllMocks()
    }

    companion object {
        @JvmStatic
        fun operations(): Stream<String> = Stream.of("get", "delete", "save")
    }

    /**
     * Given a per-object operation (get, delete, or save) When called on S3ObjectStorageAdapter
     * Then the per-object log message should be at DEBUG level, not INFO level.
     *
     * EXPECTED TO FAIL on unfixed code:
     * - Per-object log messages are at INFO level instead of DEBUG
     *
     * Counterexample: `get("test-key")` logs `INFO: Getting object with id: test-key` instead of DEBUG.
     */
    @ParameterizedTest(name = "Given {0} operation When called Then per-object log should be at DEBUG level not INFO")
    @MethodSource("operations")
    fun `Given per-object operation When called Then log message should be at DEBUG level not INFO`(operation: String) = runBlocking {
        // Given — stub S3 client responses
        val testKey = "test-key-$operation"

        coEvery { s3Client.putObject(any<PutObjectRequest>()) } returns PutObjectResponse {}
        coEvery { s3Client.deleteObject(any<DeleteObjectRequest>()) } returns DeleteObjectResponse {}
        coEvery { s3Client.getObject(any<GetObjectRequest>(), any<suspend (GetObjectResponse) -> Unit>()) } coAnswers {
            val handler = secondArg<suspend (GetObjectResponse) -> Unit>()
            handler(GetObjectResponse {
                body = ByteStream.fromBytes("test-content".toByteArray())
            })
        }

        // When — execute the operation
        when (operation) {
            "get" -> adapter.get(testKey)
            "delete" -> adapter.delete(testKey)
            "save" -> adapter.save(testKey, "test-content")
        }

        // Then — find the per-object log message and verify it is at DEBUG level
        val perObjectMessages = capturedEvents.filter { event ->
            event.formattedMessage.contains(testKey)
        }

        assertTrue(
            perObjectMessages.isNotEmpty(),
            "Expected at least one log message containing '$testKey' for operation '$operation'"
        )

        // EXPECTED TO FAIL on unfixed code:
        // The per-object log messages are at INFO level, not DEBUG
        val allAtDebug = perObjectMessages.all { it.level == Level.DEBUG }
        assertTrue(
            allAtDebug,
            "Bug 1.3 counterexample: '$operation(\"$testKey\")' logged at " +
                    "${perObjectMessages.map { it.level }.distinct()} instead of DEBUG. " +
                    "Messages: ${perObjectMessages.map { "${it.level}: ${it.formattedMessage}" }}"
        )
    }
}
