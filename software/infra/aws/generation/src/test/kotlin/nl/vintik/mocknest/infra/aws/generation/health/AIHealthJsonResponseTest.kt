package nl.vintik.mocknest.infra.aws.generation.health

import nl.vintik.mocknest.domain.generation.AIHealth
import nl.vintik.mocknest.domain.generation.AIModelHealth
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AIHealthJsonResponseTest {

    private companion object {
        const val TEST_VERSION = "test-version"
    }

    @Nested
    inner class AIHealthJsonResponseMapping {

        @Test
        fun `Given healthy AIHealth When converting to JSON response Then should map all fields correctly`() {
            // Given
            val timestamp = Instant.now()
            val health = AIHealth(
                status = "healthy",
                timestamp = timestamp,
                region = "eu-west-1",
                version = TEST_VERSION,
                ai = AIModelHealth(
                    modelName = "amazon.nova-pro-v1:0",
                    inferencePrefix = "eu",
                    inferenceMode = "AUTO"
                )
            )

            // When
            val jsonResponse = AIHealthJsonResponse.from(health)

            // Then
            assertEquals("healthy", jsonResponse.status)
            assertEquals(timestamp.toString(), jsonResponse.timestamp)
            assertEquals("eu-west-1", jsonResponse.region)
            assertEquals(TEST_VERSION, jsonResponse.version)
            assertNotNull(jsonResponse.ai)
            assertEquals("amazon.nova-pro-v1:0", jsonResponse.ai.modelName)
            assertEquals("eu", jsonResponse.ai.inferencePrefix)
            assertEquals("AUTO", jsonResponse.ai.inferenceMode)
        }

        @Test
        fun `Given degraded AIHealth When converting to JSON response Then should map degraded status`() {
            // Given
            val timestamp = Instant.now()
            val health = AIHealth(
                status = "degraded",
                timestamp = timestamp,
                region = "us-east-1",
                version = TEST_VERSION,
                ai = AIModelHealth(
                    modelName = "amazon.nova-lite-v1:0",
                    inferencePrefix = null,
                    inferenceMode = "GLOBAL_ONLY"
                )
            )

            // When
            val jsonResponse = AIHealthJsonResponse.from(health)

            // Then
            assertEquals("degraded", jsonResponse.status)
            assertEquals(timestamp.toString(), jsonResponse.timestamp)
            assertEquals("us-east-1", jsonResponse.region)
            assertEquals(TEST_VERSION, jsonResponse.version)
            assertEquals("amazon.nova-lite-v1:0", jsonResponse.ai.modelName)
            assertNull(jsonResponse.ai.inferencePrefix)
            assertEquals("GLOBAL_ONLY", jsonResponse.ai.inferenceMode)
        }

        @Test
        fun `Given AIHealth with unknown region When converting to JSON response Then should preserve unknown region`() {
            // Given
            val timestamp = Instant.now()
            val health = AIHealth(
                status = "healthy",
                timestamp = timestamp,
                region = "unknown",
                version = TEST_VERSION,
                ai = AIModelHealth(
                    modelName = "amazon.nova-pro-v1:0",
                    inferencePrefix = "us",
                    inferenceMode = "AUTO"
                )
            )

            // When
            val jsonResponse = AIHealthJsonResponse.from(health)

            // Then
            assertEquals("unknown", jsonResponse.region)
        }

        @Test
        fun `Given AIHealth with geo-only inference mode When converting to JSON Then should map correctly`() {
            // Given
            val timestamp = Instant.now()
            val health = AIHealth(
                status = "healthy",
                timestamp = timestamp,
                region = "ap-southeast-1",
                version = TEST_VERSION,
                ai = AIModelHealth(
                    modelName = "amazon.nova-micro-v1:0",
                    inferencePrefix = "ap",
                    inferenceMode = "GEO_ONLY"
                )
            )

            // When
            val jsonResponse = AIHealthJsonResponse.from(health)

            // Then
            assertEquals("ap-southeast-1", jsonResponse.region)
            assertEquals("amazon.nova-micro-v1:0", jsonResponse.ai.modelName)
            assertEquals("ap", jsonResponse.ai.inferencePrefix)
            assertEquals("GEO_ONLY", jsonResponse.ai.inferenceMode)
        }
    }

    @Nested
    inner class AIModelHealthJsonMapping {

        @Test
        fun `Given AI model health with inference prefix When converting to JSON Then should map all fields correctly`() {
            // Given
            val aiModel = AIModelHealth(
                modelName = "amazon.nova-pro-v1:0",
                inferencePrefix = "eu",
                inferenceMode = "AUTO"
            )

            // When
            val jsonAI = AIModelHealthJson.from(aiModel)

            // Then
            assertEquals("amazon.nova-pro-v1:0", jsonAI.modelName)
            assertEquals("eu", jsonAI.inferencePrefix)
            assertEquals("AUTO", jsonAI.inferenceMode)
        }

        @Test
        fun `Given AI model health without inference prefix When converting to JSON Then should map null prefix`() {
            // Given
            val aiModel = AIModelHealth(
                modelName = "amazon.nova-lite-v1:0",
                inferencePrefix = null,
                inferenceMode = "GLOBAL_ONLY"
            )

            // When
            val jsonAI = AIModelHealthJson.from(aiModel)

            // Then
            assertEquals("amazon.nova-lite-v1:0", jsonAI.modelName)
            assertNull(jsonAI.inferencePrefix)
            assertEquals("GLOBAL_ONLY", jsonAI.inferenceMode)
        }

        @Test
        fun `Given AI model with geo-only mode When converting to JSON Then should preserve mode`() {
            // Given
            val aiModel = AIModelHealth(
                modelName = "amazon.nova-micro-v1:0",
                inferencePrefix = "us",
                inferenceMode = "GEO_ONLY"
            )

            // When
            val jsonAI = AIModelHealthJson.from(aiModel)

            // Then
            assertEquals("amazon.nova-micro-v1:0", jsonAI.modelName)
            assertEquals("us", jsonAI.inferencePrefix)
            assertEquals("GEO_ONLY", jsonAI.inferenceMode)
        }

        @Test
        fun `Given AI model with long model name When converting to JSON Then should preserve full model name`() {
            // Given
            val longModelName = "anthropic.claude-v3-5-sonnet-20241022-v2:0"
            val aiModel = AIModelHealth(
                modelName = longModelName,
                inferencePrefix = "us",
                inferenceMode = "AUTO"
            )

            // When
            val jsonAI = AIModelHealthJson.from(aiModel)

            // Then
            assertEquals(longModelName, jsonAI.modelName)
        }
    }

    @Nested
    inner class DataClassProperties {

        @Test
        fun `Given AIHealthJsonResponse When creating with constructor Then should set all properties`() {
            // Given / When
            val aiModel = AIModelHealthJson(
                modelName = "amazon.nova-pro-v1:0",
                inferencePrefix = "eu",
                inferenceMode = "AUTO"
            )
            val response = AIHealthJsonResponse(
                status = "healthy",
                timestamp = "2024-03-17T10:00:00Z",
                region = "eu-west-1",
                version = TEST_VERSION,
                ai = aiModel
            )

            // Then
            assertEquals("healthy", response.status)
            assertEquals("2024-03-17T10:00:00Z", response.timestamp)
            assertEquals("eu-west-1", response.region)
            assertEquals(TEST_VERSION, response.version)
            assertEquals("amazon.nova-pro-v1:0", response.ai.modelName)
            assertEquals("eu", response.ai.inferencePrefix)
            assertEquals("AUTO", response.ai.inferenceMode)
        }

        @Test
        fun `Given AIModelHealthJson When creating with constructor Then should set all properties`() {
            // Given / When
            val aiModel = AIModelHealthJson(
                modelName = "amazon.nova-lite-v1:0",
                inferencePrefix = "us",
                inferenceMode = "GEO_ONLY"
            )

            // Then
            assertEquals("amazon.nova-lite-v1:0", aiModel.modelName)
            assertEquals("us", aiModel.inferencePrefix)
            assertEquals("GEO_ONLY", aiModel.inferenceMode)
        }

        @Test
        fun `Given AIModelHealthJson with null prefix When creating with constructor Then should allow null`() {
            // Given / When
            val aiModel = AIModelHealthJson(
                modelName = "amazon.nova-pro-v1:0",
                inferencePrefix = null,
                inferenceMode = "GLOBAL_ONLY"
            )

            // Then
            assertEquals("amazon.nova-pro-v1:0", aiModel.modelName)
            assertNull(aiModel.inferencePrefix)
            assertEquals("GLOBAL_ONLY", aiModel.inferenceMode)
        }
    }
}
