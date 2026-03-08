package nl.vintik.mocknest.infra.aws.core.storage.config

import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class S3ConfigurationTest {

    @Test
    fun `Should create S3Client`() {
        val config = S3Configuration()
        val s3 = config.s3Client("us-east-1")
        assertNotNull(s3)
        s3.close()
    }
}
