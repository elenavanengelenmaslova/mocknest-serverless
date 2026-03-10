package nl.vintik.mocknest.infra.aws.core.ai.config

import ai.koog.prompt.executor.clients.bedrock.BedrockInferencePrefixes
import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import ai.koog.prompt.executor.clients.bedrock.withInferenceProfile
import nl.vintik.mocknest.infra.aws.generation.ai.config.DefaultInferencePrefixResolver
import nl.vintik.mocknest.infra.aws.generation.ai.config.InferenceMode
import nl.vintik.mocknest.infra.aws.generation.ai.config.InferencePrefixResolver
import nl.vintik.mocknest.infra.aws.generation.ai.config.ModelConfiguration
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
            "bedrock.inference.mode=AUTO",
            "AWS_REGION=eu-west-1"
        ]
    )
    @Nested
    inner class ValidModelNameMapping {
        @Value($$"${bedrock.model.name}")
        lateinit var modelName: String

        @Value("\${AWS_REGION}")
        lateinit var deployRegion: String

        @Test
        fun `Given Claude 3-5 Sonnet v2 model name When getting Bedrock model Then should return model with EU prefix`() {
            val resolver = DefaultInferencePrefixResolver(deployRegion, InferenceMode.AUTO)
            val config = ModelConfiguration(modelName, resolver)
            val model = config.getModel()

            assertEquals(BedrockModels.AnthropicClaude35SonnetV2.withInferenceProfile("eu").id, model.id)
            assertEquals("AnthropicClaude35SonnetV2", config.getModelName())
        }
    }

    @SpringBootTest(
        classes = [TestConfig::class],
        properties = [
            "bedrock.model.name=AnthropicClaude4_5Sonnet",
            "bedrock.inference.mode=AUTO",
            "AWS_REGION=eu-west-1"
        ]
    )
    @Nested
    inner class ValidClaudeSonnetModel {
        @Value($$"${bedrock.model.name}")
        lateinit var modelName: String

        @Value("\${AWS_REGION}")
        lateinit var deployRegion: String

        @Test
        fun `Given Claude 4-5 Sonnet model name When getting Bedrock model Then should return model with EU prefix`() {
            val resolver = DefaultInferencePrefixResolver(deployRegion, InferenceMode.AUTO)
            val config = ModelConfiguration(modelName, resolver)
            val model = config.getModel()

            assertEquals(BedrockModels.AnthropicClaude4_5Sonnet.withInferenceProfile("eu").id, model.id)
            assertEquals("AnthropicClaude4_5Sonnet", config.getModelName())
        }
    }

    @SpringBootTest(
        classes = [TestConfig::class],
        properties = [
            "bedrock.model.name=AmazonNovaPro",
            "bedrock.inference.mode=AUTO",
            "AWS_REGION=eu-west-1"
        ]
    )
    @Nested
    inner class ValidAmazonNovaModel {
        @Value($$"${bedrock.model.name}")
        lateinit var modelName: String

        @Value("\${AWS_REGION}")
        lateinit var deployRegion: String

        @Test
        fun `Given Amazon Nova Pro model name When getting Bedrock model Then should return model with EU prefix`() {
            val resolver = DefaultInferencePrefixResolver(deployRegion, InferenceMode.AUTO)
            val config = ModelConfiguration(modelName, resolver)
            val model = config.getModel()

            assertEquals(BedrockModels.AmazonNovaPro.withInferenceProfile("eu").id, model.id)
            assertEquals("AmazonNovaPro", config.getModelName())
        }
    }

    @SpringBootTest(
        classes = [TestConfig::class],
        properties = [
            "bedrock.model.name=MetaLlama3_1_70BInstruct",
            "bedrock.inference.mode=AUTO",
            "AWS_REGION=eu-west-1"
        ]
    )
    @Nested
    inner class ValidMetaLlamaModel {
        @Value($$"${bedrock.model.name}")
        lateinit var modelName: String

        @Value("\${AWS_REGION}")
        lateinit var deployRegion: String

        @Test
        fun `Given Meta Llama 3-1 70B model name When getting Bedrock model Then should return model with EU prefix`() {
            val resolver = DefaultInferencePrefixResolver(deployRegion, InferenceMode.AUTO)
            val config = ModelConfiguration(modelName, resolver)
            val model = config.getModel()

            assertEquals(BedrockModels.MetaLlama3_1_70BInstruct.withInferenceProfile("eu").id, model.id)
            assertEquals("MetaLlama3_1_70BInstruct", config.getModelName())
        }
    }

    @SpringBootTest(
        classes = [TestConfig::class],
        properties = [
            "bedrock.model.name=InvalidModelName",
            "bedrock.inference.mode=AUTO",
            "AWS_REGION=eu-west-1"
        ]
    )
    @Nested
    inner class InvalidModelNameFallback {
        @Value($$"${bedrock.model.name}")
        lateinit var modelName: String

        @Value("\${AWS_REGION}")
        lateinit var deployRegion: String

        @Test
        fun `Given invalid model name When getting Bedrock model Then should fallback to AmazonNovaPro with EU prefix and log warning`() {
            val resolver = DefaultInferencePrefixResolver(deployRegion, InferenceMode.AUTO)
            val config = ModelConfiguration(modelName, resolver)
            val model = config.getModel()

            // Should fallback to default with EU prefix
            assertEquals(BedrockModels.AmazonNovaPro.withInferenceProfile("eu").id, model.id)
            assertEquals("InvalidModelName", config.getModelName())
        }
    }

    @SpringBootTest(
        classes = [TestConfig::class],
        properties = [
            "bedrock.model.name=",
            "bedrock.inference.mode=AUTO",
            "AWS_REGION=eu-west-1"
        ]
    )
    @Nested
    inner class EmptyModelNameFallback {
        @Value($$"${bedrock.model.name}")
        lateinit var modelName: String

        @Value("\${AWS_REGION}")
        lateinit var deployRegion: String

        @Test
        fun `Given empty model name When getting Bedrock model Then should fallback to AmazonNovaPro with EU prefix`() {
            val resolver = DefaultInferencePrefixResolver(deployRegion, InferenceMode.AUTO)
            val config = ModelConfiguration(modelName, resolver)
            val model = config.getModel()

            // Should fallback to default with EU prefix
            assertEquals(BedrockModels.AmazonNovaPro.withInferenceProfile("eu").id, model.id)
        }
    }

    @SpringBootTest(
        classes = [TestConfig::class],
        properties = [
            "bedrock.inference.mode=AUTO",
            "AWS_REGION=eu-west-1"
        ]
    )
    @Nested
    inner class DefaultModelName {
        @Value($$"${bedrock.model.name:AmazonNovaPro}")
        lateinit var modelName: String

        @Value("\${AWS_REGION}")
        lateinit var deployRegion: String

        @Test
        fun `Given no model name property When getting Bedrock model Then should use default AmazonNovaPro with EU prefix`() {
            val resolver = DefaultInferencePrefixResolver(deployRegion, InferenceMode.AUTO)
            val config = ModelConfiguration(modelName, resolver)
            val model = config.getModel()

            assertEquals(BedrockModels.AmazonNovaPro.withInferenceProfile("eu").id, model.id)
            assertEquals("AmazonNovaPro", config.getModelName())
        }
    }

    @SpringBootTest(
        classes = [TestConfig::class],
        properties = [
            "bedrock.model.name=NonExistentModel123",
            "bedrock.inference.mode=AUTO",
            "AWS_REGION=eu-west-1"
        ]
    )
    @Nested
    inner class NonExistentModelName {
        @Value($$"${bedrock.model.name}")
        lateinit var modelName: String

        @Value("\${AWS_REGION}")
        lateinit var deployRegion: String

        @Test
        fun `Given non-existent model name When getting Bedrock model Then should fallback to default with EU prefix and log warning`() {
            val resolver = DefaultInferencePrefixResolver(deployRegion, InferenceMode.AUTO)
            val config = ModelConfiguration(modelName, resolver)
            val model = config.getModel()

            // Should fallback to default with EU prefix
            assertNotNull(model)
            assertEquals(BedrockModels.AmazonNovaPro.withInferenceProfile("eu").id, model.id)
            assertEquals("NonExistentModel123", config.getModelName())
        }
    }

    @Configuration
    class TestConfig {
        @Bean
        fun inferencePrefixResolver(
            @Value("\${AWS_REGION:eu-west-1}") deployRegion: String,
            @Value($$"${bedrock.inference.mode:AUTO}") inferenceMode: String
        ): InferencePrefixResolver {
            val mode = InferenceMode.valueOf(inferenceMode.uppercase())
            return DefaultInferencePrefixResolver(deployRegion, mode)
        }

        @Bean
        fun modelConfiguration(
            @Value($$"${bedrock.model.name:AmazonNovaPro}") modelName: String,
            inferencePrefixResolver: InferencePrefixResolver
        ): ModelConfiguration {
            return ModelConfiguration(modelName, inferencePrefixResolver)
        }
    }
    
    @Nested
    inner class PrefixRetryLogic {
        
        @Test
        fun `Given AUTO mode When getting model Then should use geo prefix first`() {
            val resolver = DefaultInferencePrefixResolver("eu-west-1", InferenceMode.AUTO)
            val config = ModelConfiguration("AmazonNovaPro", resolver)
            
            val model = config.getModel()
            
            // AUTO mode tries geo prefix first (eu for eu-west-1)
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
            
            // Multiple calls should return the same model configuration
            val model1 = config.getModel()
            val model2 = config.getModel()
            
            assertEquals(model1.id, model2.id)
        }
        
        @Test
        fun `Given configured prefix When getting model Then should use first candidate prefix`() {
            val resolver = DefaultInferencePrefixResolver("eu-west-1", InferenceMode.AUTO)
            val config = ModelConfiguration("AmazonNovaPro", resolver)
            
            // Should use first candidate prefix (eu for AUTO mode)
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
            
            // Should fallback to AmazonNovaPro with eu prefix (AUTO mode)
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
