package nl.vintik.mocknest.infra.aws

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import kotlin.test.assertEquals

class S3BucketPropertyResolutionTest {

    @SpringBootTest(
        classes = [TestPropertyConfig::class,
            org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration::class],
        properties = ["MOCKNEST_S3_BUCKET_NAME=test-env-bucket"]
    )
    @Nested
    inner class `With Environment Variable Set` {
        @Value("\${storage.bucket.name}")
        lateinit var bucketName: String

        @Test
        fun `Given environment variable MOCKNEST_S3_BUCKET_NAME is set When loading context Then should resolve bucket name from environment`() {
            assertEquals("test-env-bucket", bucketName)
        }
    }

    @SpringBootTest(
        classes = [TestPropertyConfig::class,
            org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration::class],
    )
    @Nested
    inner class `Without Environment Variable Set` {
        @Value("\${storage.bucket.name}")
        lateinit var bucketName: String

        @Test
        fun `Given environment variable MOCKNEST_S3_BUCKET_NAME is not set When loading context Then should fallback to default in application properties`() {
            assertEquals("mocknest-serverless-storage", bucketName)
        }
    }

    @Configuration
    @PropertySource("classpath:application-test.properties")
    class TestPropertyConfig
}
