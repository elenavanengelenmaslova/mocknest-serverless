package nl.vintik.mocknest.infra.aws.core.ai

import ai.koog.prompt.executor.clients.bedrock.BedrockInferencePrefixes
import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import ai.koog.prompt.executor.clients.bedrock.withInferenceProfile
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ModelConfigurationTest {

    @SpringBootTest(
        classes = [TestConfig::class],
        properties = [
            "bedrock.model.name=AnthropicClaude35SonnetV2",
            "bedrock.inference.prefix=global"
        ]
    )
    @Nested
    inner class ValidModelNameMapping {
        @Value($$"${bedrock.model.name}")
        lateinit var modelName: String

        @Value($$"${bedrock.inference.prefix}")
        lateinit var inferenceProfilePrefix: String

        @Test
        suspend fun `Given Claude 3-5 Sonnet v2 model name When getting Bedrock model Then should return model with GLOBAL prefix`() {
            val config = ModelConfiguration(modelName, inferenceProfilePrefix)
            val model = config.getModel()

            assertEquals(BedrockModels.AnthropicClaude35SonnetV2.withInferenceProfile(BedrockInferencePrefixes.GLOBAL.prefix).id, model.id)
            assertEquals("AnthropicClaude35SonnetV2", config.getModelName())
        }
    }

    @SpringBootTest(
        classes = [TestConfig::class],
        properties = [
            "bedrock.model.name=AnthropicClaude4_5Sonnet",
            "bedrock.inference.prefix=global"
        ]
    )
    @Nested
    inner class ValidClaudeSonnetModel {
        @Value($$"${bedrock.model.name}")
        lateinit var modelName: String

        @Value($$"${bedrock.inference.prefix}")
        lateinit var inferenceProfilePrefix: String

        @Test
        suspend fun `Given Claude 4-5 Sonnet model name When getting Bedrock model Then should return model with GLOBAL prefix`() {
            val config = ModelConfiguration(modelName, inferenceProfilePrefix)
            val model = config.getModel()

            assertEquals(BedrockModels.AnthropicClaude4_5Sonnet.withInferenceProfile(BedrockInferencePrefixes.GLOBAL.prefix).id, model.id)
            assertEquals("AnthropicClaude4_5Sonnet", config.getModelName())
        }
    }

    @SpringBootTest(
        classes = [TestConfig::class],
        properties = [
            "bedrock.model.name=AmazonNovaPro",
            "bedrock.inference.prefix=global"
        ]
    )
    @Nested
    inner class ValidAmazonNovaModel {
        @Value($$"${bedrock.model.name}")
        lateinit var modelName: String

        @Value($$"${bedrock.inference.prefix}")
        lateinit var inferenceProfilePrefix: String

        @Test
        suspend fun `Given Amazon Nova Pro model name When getting Bedrock model Then should return model with GLOBAL prefix`() {
            val config = ModelConfiguration(modelName, inferenceProfilePrefix)
            val model = config.getModel()

            assertEquals(BedrockModels.AmazonNovaPro.withInferenceProfile(BedrockInferencePrefixes.GLOBAL.prefix).id, model.id)
            assertEquals("AmazonNovaPro", config.getModelName())
        }
    }

    @SpringBootTest(
        classes = [TestConfig::class],
        properties = [
            "bedrock.model.name=MetaLlama3_1_70BInstruct",
            "bedrock.inference.prefix=global"
        ]
    )
    @Nested
    inner class ValidMetaLlamaModel {
        @Value($$"${bedrock.model.name}")
        lateinit var modelName: String

        @Value($$"${bedrock.inference.prefix}")
        lateinit var inferenceProfilePrefix: String

        @Test
        suspend fun `Given Meta Llama 3-1 70B model name When getting Bedrock model Then should return model with GLOBAL prefix`() {
            val config = ModelConfiguration(modelName, inferenceProfilePrefix)
            val model = config.getModel()

            assertEquals(BedrockModels.MetaLlama3_1_70BInstruct.withInferenceProfile(BedrockInferencePrefixes.GLOBAL.prefix).id, model.id)
            assertEquals("MetaLlama3_1_70BInstruct", config.getModelName())
        }
    }

    @SpringBootTest(
        classes = [TestConfig::class],
        properties = [
            "bedrock.model.name=InvalidModelName",
            "bedrock.inference.prefix=global"
        ]
    )
    @Nested
    inner class InvalidModelNameFallback {
        @Value($$"${bedrock.model.name}")
        lateinit var modelName: String

        @Value($$"${bedrock.inference.prefix}")
        lateinit var inferenceProfilePrefix: String

        @Test
        suspend fun `Given invalid model name When getting Bedrock model Then should fallback to AmazonNovaPro with GLOBAL prefix and log warning`() {
            val config = ModelConfiguration(modelName, inferenceProfilePrefix)
            val model = config.getModel()

            // Should fallback to default with GLOBAL prefix
            assertEquals(BedrockModels.AmazonNovaPro.withInferenceProfile(BedrockInferencePrefixes.GLOBAL.prefix).id, model.id)
            assertEquals("InvalidModelName", config.getModelName())
        }
    }

    @SpringBootTest(
        classes = [TestConfig::class],
        properties = [
            "bedrock.model.name=",
            "bedrock.inference.prefix=global"
        ]
    )
    @Nested
    inner class EmptyModelNameFallback {
        @Value($$"${bedrock.model.name}")
        lateinit var modelName: String

        @Value($$"${bedrock.inference.prefix}")
        lateinit var inferenceProfilePrefix: String

        @Test
        suspend fun `Given empty model name When getting Bedrock model Then should fallback to AmazonNovaPro with GLOBAL prefix`() {
            val config = ModelConfiguration(modelName, inferenceProfilePrefix)
            val model = config.getModel()

            // Should fallback to default with GLOBAL prefix
            assertEquals(BedrockModels.AmazonNovaPro.withInferenceProfile(BedrockInferencePrefixes.GLOBAL.prefix).id, model.id)
        }
    }

    @SpringBootTest(
        classes = [TestConfig::class],
        properties = ["bedrock.inference.prefix=global"]
    )
    @Nested
    inner class DefaultModelName {
        @Value($$"${bedrock.model.name:AmazonNovaPro}")
        lateinit var modelName: String

        @Value($$"${bedrock.inference.prefix}")
        lateinit var inferenceProfilePrefix: String

        @Test
        suspend fun `Given no model name property When getting Bedrock model Then should use default AmazonNovaPro with GLOBAL prefix`() {
            val config = ModelConfiguration(modelName, inferenceProfilePrefix)
            val model = config.getModel()

            assertEquals(BedrockModels.AmazonNovaPro.withInferenceProfile(BedrockInferencePrefixes.GLOBAL.prefix).id, model.id)
            assertEquals("AmazonNovaPro", config.getModelName())
        }
    }

    @SpringBootTest(
        classes = [TestConfig::class],
        properties = [
            "bedrock.model.name=NonExistentModel123",
            "bedrock.inference.prefix=global"
        ]
    )
    @Nested
    inner class NonExistentModelName {
        @Value($$"${bedrock.model.name}")
        lateinit var modelName: String

        @Value($$"${bedrock.inference.prefix}")
        lateinit var inferenceProfilePrefix: String

        @Test
        suspend fun `Given non-existent model name When getting Bedrock model Then should fallback to default with GLOBAL prefix and log warning`() {
            val config = ModelConfiguration(modelName, inferenceProfilePrefix)
            val model = config.getModel()

            // Should fallback to default with GLOBAL prefix
            assertNotNull(model)
            assertEquals(BedrockModels.AmazonNovaPro.withInferenceProfile(BedrockInferencePrefixes.GLOBAL.prefix).id, model.id)
            assertEquals("NonExistentModel123", config.getModelName())
        }
    }

    @Configuration
    class TestConfig {
        @Bean
        fun modelConfiguration(
            @Value($$"${bedrock.model.name:AmazonNovaPro}") modelName: String,
            @Value($$"${bedrock.inference.prefix:global}") inferenceProfilePrefix: String
        ): ModelConfiguration {
            return ModelConfiguration(modelName, inferenceProfilePrefix)
        }
    }
}
