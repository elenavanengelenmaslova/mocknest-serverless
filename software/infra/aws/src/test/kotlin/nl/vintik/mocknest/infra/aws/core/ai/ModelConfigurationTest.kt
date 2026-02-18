package nl.vintik.mocknest.infra.aws.core.ai

import ai.koog.prompt.executor.clients.bedrock.BedrockModels
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
        properties = ["bedrock.model.name=AnthropicClaude35SonnetV2"]
    )
    @Nested
    inner class ValidModelNameMapping {
        @Value($$"${bedrock.model.name}")
        lateinit var modelName: String

        @Test
        suspend fun `Given Claude 3-5 Sonnet v2 model name When getting Bedrock model Then should return AnthropicClaude35SonnetV2`() {
            val config = ModelConfiguration(modelName)
            val model = config.getBedrockModel()

            assertEquals(BedrockModels.AnthropicClaude35SonnetV2, model)
            assertEquals("AnthropicClaude35SonnetV2", config.getModelName())
        }
    }

    @SpringBootTest(
        classes = [TestConfig::class],
        properties = ["bedrock.model.name=AnthropicClaude4_5Sonnet"]
    )
    @Nested
    inner class ValidClaudeSonnetModel {
        @Value($$"${bedrock.model.name}")
        lateinit var modelName: String

        @Test
        suspend fun `Given Claude 4-5 Sonnet model name When getting Bedrock model Then should return AnthropicClaude4_5Sonnet`() {
            val config = ModelConfiguration(modelName)
            val model = config.getBedrockModel()

            assertEquals(BedrockModels.AnthropicClaude4_5Sonnet, model)
            assertEquals("AnthropicClaude4_5Sonnet", config.getModelName())
        }
    }

    @SpringBootTest(
        classes = [TestConfig::class],
        properties = ["bedrock.model.name=AmazonNovaPro"]
    )
    @Nested
    inner class ValidAmazonNovaModel {
        @Value($$"${bedrock.model.name}")
        lateinit var modelName: String

        @Test
        suspend fun `Given Amazon Nova Pro model name When getting Bedrock model Then should return AmazonNovaPro`() {
            val config = ModelConfiguration(modelName)
            val model = config.getBedrockModel()

            assertEquals(BedrockModels.AmazonNovaPro, model)
            assertEquals("AmazonNovaPro", config.getModelName())
        }
    }

    @SpringBootTest(
        classes = [TestConfig::class],
        properties = ["bedrock.model.name=MetaLlama3_1_70BInstruct"]
    )
    @Nested
    inner class ValidMetaLlamaModel {
        @Value($$"${bedrock.model.name}")
        lateinit var modelName: String

        @Test
        suspend fun `Given Meta Llama 3-1 70B model name When getting Bedrock model Then should return MetaLlama3_1_70BInstruct`() {
            val config = ModelConfiguration(modelName)
            val model = config.getBedrockModel()

            assertEquals(BedrockModels.MetaLlama3_1_70BInstruct, model)
            assertEquals("MetaLlama3_1_70BInstruct", config.getModelName())
        }
    }

    @SpringBootTest(
        classes = [TestConfig::class],
        properties = ["bedrock.model.name=InvalidModelName"]
    )
    @Nested
    inner class InvalidModelNameFallback {
        @Value($$"${bedrock.model.name}")
        lateinit var modelName: String

        @Test
        suspend fun `Given invalid model name When getting Bedrock model Then should fallback to AnthropicClaude35SonnetV2 and log warning`() {
            val config = ModelConfiguration(modelName)
            val model = config.getBedrockModel()

            // Should fallback to default
            assertEquals(BedrockModels.AnthropicClaude35SonnetV2, model)
            assertEquals("InvalidModelName", config.getModelName())
        }
    }

    @SpringBootTest(
        classes = [TestConfig::class],
        properties = ["bedrock.model.name="]
    )
    @Nested
    inner class EmptyModelNameFallback {
        @Value($$"${bedrock.model.name}")
        lateinit var modelName: String

        @Test
        suspend fun `Given empty model name When getting Bedrock model Then should fallback to AnthropicClaude35SonnetV2`() {
            val config = ModelConfiguration(modelName)
            val model = config.getBedrockModel()

            // Should fallback to default
            assertEquals(BedrockModels.AnthropicClaude35SonnetV2, model)
        }
    }

    @SpringBootTest(
        classes = [TestConfig::class]
    )
    @Nested
    inner class DefaultModelName {
        @Value($$"${bedrock.model.name:AnthropicClaude35SonnetV2}")
        lateinit var modelName: String

        @Test
        suspend fun `Given no model name property When getting Bedrock model Then should use default AnthropicClaude35SonnetV2`() {
            val config = ModelConfiguration(modelName)
            val model = config.getBedrockModel()

            assertEquals(BedrockModels.AnthropicClaude35SonnetV2, model)
            assertEquals("AnthropicClaude35SonnetV2", config.getModelName())
        }
    }

    @SpringBootTest(
        classes = [TestConfig::class],
        properties = ["bedrock.model.name=NonExistentModel123"]
    )
    @Nested
    inner class NonExistentModelName {
        @Value($$"${bedrock.model.name}")
        lateinit var modelName: String

        @Test
        suspend fun `Given non-existent model name When getting Bedrock model Then should fallback to default and log warning`() {
            val config = ModelConfiguration(modelName)
            val model = config.getBedrockModel()

            // Should fallback to default
            assertNotNull(model)
            assertEquals(BedrockModels.AnthropicClaude35SonnetV2, model)
            assertEquals("NonExistentModel123", config.getModelName())
        }
    }

    @Configuration
    class TestConfig {
        @Bean
        fun modelConfiguration(@Value("\${bedrock.model.name:AnthropicClaude35SonnetV2}") modelName: String): ModelConfiguration {
            return ModelConfiguration(modelName)
        }
    }
}
