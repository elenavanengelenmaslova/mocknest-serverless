package nl.vintik.mocknest.infra.aws.core.ai.config

import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import ai.koog.prompt.executor.clients.bedrock.withInferenceProfile
import nl.vintik.mocknest.infra.aws.generation.ai.config.DefaultInferencePrefixResolver
import nl.vintik.mocknest.infra.aws.generation.ai.config.InferenceMode
import nl.vintik.mocknest.infra.aws.generation.ai.config.ModelConfiguration
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ModelConfigurationTest {

    @Nested
    inner class ValidModelNameMapping {

        @Test
        fun `Given Claude 4-5 Sonnet model name When getting Bedrock model Then should return model with EU prefix`() {
            val resolver = DefaultInferencePrefixResolver("eu-west-1", InferenceMode.AUTO)
            val config = ModelConfiguration("AnthropicClaude4_5Sonnet", resolver)
            val model = config.getModel()

            assertEquals(BedrockModels.AnthropicClaude4_5Sonnet.withInferenceProfile("eu").id, model.id)
            assertEquals("AnthropicClaude4_5Sonnet", config.getModelName())
        }
    }

    @Nested
    inner class ValidAmazonNovaModel {

        @Test
        fun `Given Amazon Nova Pro model name When getting Bedrock model Then should return model with EU prefix`() {
            val resolver = DefaultInferencePrefixResolver("eu-west-1", InferenceMode.AUTO)
            val config = ModelConfiguration("AmazonNovaPro", resolver)
            val model = config.getModel()

            assertEquals(BedrockModels.AmazonNovaPro.withInferenceProfile("eu").id, model.id)
            assertEquals("AmazonNovaPro", config.getModelName())
        }
    }

    @Nested
    inner class ValidMetaLlamaModel {

        @Test
        fun `Given Meta Llama 3-1 70B model name When getting Bedrock model Then should return model with EU prefix`() {
            val resolver = DefaultInferencePrefixResolver("eu-west-1", InferenceMode.AUTO)
            val config = ModelConfiguration("MetaLlama3_1_70BInstruct", resolver)
            val model = config.getModel()

            assertEquals(BedrockModels.MetaLlama3_1_70BInstruct.withInferenceProfile("eu").id, model.id)
            assertEquals("MetaLlama3_1_70BInstruct", config.getModelName())
        }
    }

    @Nested
    inner class InvalidModelNameFallback {

        @Test
        fun `Given invalid model name When getting Bedrock model Then should fallback to AmazonNovaPro with EU prefix`() {
            val resolver = DefaultInferencePrefixResolver("eu-west-1", InferenceMode.AUTO)
            val config = ModelConfiguration("InvalidModelName", resolver)
            val model = config.getModel()

            assertEquals(BedrockModels.AmazonNovaPro.withInferenceProfile("eu").id, model.id)
            assertEquals("InvalidModelName", config.getModelName())
        }
    }

    @Nested
    inner class EmptyModelNameFallback {

        @Test
        fun `Given empty model name When getting Bedrock model Then should fallback to AmazonNovaPro with EU prefix`() {
            val resolver = DefaultInferencePrefixResolver("eu-west-1", InferenceMode.AUTO)
            val config = ModelConfiguration("", resolver)
            val model = config.getModel()

            assertEquals(BedrockModels.AmazonNovaPro.withInferenceProfile("eu").id, model.id)
        }
    }

    @Nested
    inner class DefaultModelName {

        @Test
        fun `Given default model name When getting Bedrock model Then should use AmazonNovaPro with EU prefix`() {
            val resolver = DefaultInferencePrefixResolver("eu-west-1", InferenceMode.AUTO)
            val config = ModelConfiguration("AmazonNovaPro", resolver)
            val model = config.getModel()

            assertEquals(BedrockModels.AmazonNovaPro.withInferenceProfile("eu").id, model.id)
            assertEquals("AmazonNovaPro", config.getModelName())
        }
    }

    @Nested
    inner class NonExistentModelName {

        @Test
        fun `Given non-existent model name When getting Bedrock model Then should fallback to default with EU prefix`() {
            val resolver = DefaultInferencePrefixResolver("eu-west-1", InferenceMode.AUTO)
            val config = ModelConfiguration("NonExistentModel123", resolver)
            val model = config.getModel()

            assertNotNull(model)
            assertEquals(BedrockModels.AmazonNovaPro.withInferenceProfile("eu").id, model.id)
            assertEquals("NonExistentModel123", config.getModelName())
        }
    }

    @Nested
    inner class PrefixRetryLogic {

        @Test
        fun `Given AUTO mode When getting model Then should use geo prefix first`() {
            val resolver = DefaultInferencePrefixResolver("eu-west-1", InferenceMode.AUTO)
            val config = ModelConfiguration("AmazonNovaPro", resolver)

            val model = config.getModel()

            assertEquals(BedrockModels.AmazonNovaPro.withInferenceProfile("eu").id, model.id)
        }

        @Test
        fun `Given GLOBAL_ONLY mode When getting model Then should only try global prefix`() {
            val resolver = DefaultInferencePrefixResolver("eu-west-1", InferenceMode.GLOBAL_ONLY)
            val config = ModelConfiguration("AmazonNovaPro", resolver)

            val model = config.getModel()

            assertEquals(BedrockModels.AmazonNovaPro.withInferenceProfile("global").id, model.id)
        }

        @Test
        fun `Given GEO_ONLY mode When getting model Then should use geo-specific prefix`() {
            val resolver = DefaultInferencePrefixResolver("eu-west-1", InferenceMode.GEO_ONLY)
            val config = ModelConfiguration("AmazonNovaPro", resolver)

            val model = config.getModel()

            assertEquals(BedrockModels.AmazonNovaPro.withInferenceProfile("eu").id, model.id)
        }

        @Test
        fun `Given us-east-1 region When using GEO_ONLY mode Then should use us prefix`() {
            val resolver = DefaultInferencePrefixResolver("us-east-1", InferenceMode.GEO_ONLY)
            val config = ModelConfiguration("AmazonNovaPro", resolver)

            val model = config.getModel()

            assertEquals(BedrockModels.AmazonNovaPro.withInferenceProfile("us").id, model.id)
        }

        @Test
        fun `Given ap-southeast-1 region When using GEO_ONLY mode Then should use ap prefix`() {
            val resolver = DefaultInferencePrefixResolver("ap-southeast-1", InferenceMode.GEO_ONLY)
            val config = ModelConfiguration("AmazonNovaPro", resolver)

            val model = config.getModel()

            assertEquals(BedrockModels.AmazonNovaPro.withInferenceProfile("ap").id, model.id)
        }
    }

    @Nested
    inner class PrefixConfiguration {

        @Test
        fun `Given model configuration When calling getModel multiple times Then should return same model`() {
            val resolver = DefaultInferencePrefixResolver("eu-west-1", InferenceMode.AUTO)
            val config = ModelConfiguration("AmazonNovaPro", resolver)

            val model1 = config.getModel()
            val model2 = config.getModel()

            assertEquals(model1.id, model2.id)
        }

        @Test
        fun `Given configured prefix When getting model Then should use first candidate prefix`() {
            val resolver = DefaultInferencePrefixResolver("eu-west-1", InferenceMode.AUTO)
            val config = ModelConfiguration("AmazonNovaPro", resolver)

            val model = config.getModel()
            assertEquals(BedrockModels.AmazonNovaPro.withInferenceProfile("eu").id, model.id)
        }
    }

    @Nested
    inner class ModelNameFallback {

        @Test
        fun `Given invalid model name When getting model Then should fallback to AmazonNovaPro`() {
            val resolver = DefaultInferencePrefixResolver("eu-west-1", InferenceMode.AUTO)
            val config = ModelConfiguration("InvalidModelName", resolver)

            val model = config.getModel()

            assertEquals(BedrockModels.AmazonNovaPro.withInferenceProfile("eu").id, model.id)
            assertEquals("InvalidModelName", config.getModelName())
            assertEquals(false, config.isOfficiallySupported())
        }

        @Test
        fun `Given empty model name When getting model Then should fallback to AmazonNovaPro`() {
            val resolver = DefaultInferencePrefixResolver("eu-west-1", InferenceMode.AUTO)
            val config = ModelConfiguration("", resolver)

            val model = config.getModel()

            assertEquals(BedrockModels.AmazonNovaPro.withInferenceProfile("eu").id, model.id)
        }

        @Test
        fun `Given AmazonNovaPro model name When checking official support Then should return true`() {
            val resolver = DefaultInferencePrefixResolver("eu-west-1", InferenceMode.AUTO)
            val config = ModelConfiguration("AmazonNovaPro", resolver)

            assertEquals(true, config.isOfficiallySupported())
        }

        @Test
        fun `Given non-AmazonNovaPro model name When checking official support Then should return false`() {
            val resolver = DefaultInferencePrefixResolver("eu-west-1", InferenceMode.AUTO)
            val config = ModelConfiguration("AnthropicClaude35SonnetV2", resolver)

            assertEquals(false, config.isOfficiallySupported())
        }
    }

    @Nested
    inner class HelperMethods {

        @Test
        fun `Given model configuration When getting model name Then should return configured name`() {
            val resolver = DefaultInferencePrefixResolver("eu-west-1", InferenceMode.AUTO)
            val config = ModelConfiguration("AmazonNovaPro", resolver)

            assertEquals("AmazonNovaPro", config.getModelName())
        }
    }
}
