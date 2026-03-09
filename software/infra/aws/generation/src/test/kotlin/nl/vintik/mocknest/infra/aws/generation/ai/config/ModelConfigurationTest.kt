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
        fun `Given Claude 3-5 Sonnet v2 model name When getting Bedrock model Then should return model with GLOBAL prefix`() {
            val resolver = DefaultInferencePrefixResolver(deployRegion, InferenceMode.AUTO)
            val config = ModelConfiguration(modelName, resolver)
            val model = config.getModel()

            assertEquals(BedrockModels.AnthropicClaude35SonnetV2.withInferenceProfile(BedrockInferencePrefixes.GLOBAL.prefix).id, model.id)
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
        fun `Given Claude 4-5 Sonnet model name When getting Bedrock model Then should return model with GLOBAL prefix`() {
            val resolver = DefaultInferencePrefixResolver(deployRegion, InferenceMode.AUTO)
            val config = ModelConfiguration(modelName, resolver)
            val model = config.getModel()

            assertEquals(BedrockModels.AnthropicClaude4_5Sonnet.withInferenceProfile(BedrockInferencePrefixes.GLOBAL.prefix).id, model.id)
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
        fun `Given Amazon Nova Pro model name When getting Bedrock model Then should return model with GLOBAL prefix`() {
            val resolver = DefaultInferencePrefixResolver(deployRegion, InferenceMode.AUTO)
            val config = ModelConfiguration(modelName, resolver)
            val model = config.getModel()

            assertEquals(BedrockModels.AmazonNovaPro.withInferenceProfile(BedrockInferencePrefixes.GLOBAL.prefix).id, model.id)
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
        fun `Given Meta Llama 3-1 70B model name When getting Bedrock model Then should return model with GLOBAL prefix`() {
            val resolver = DefaultInferencePrefixResolver(deployRegion, InferenceMode.AUTO)
            val config = ModelConfiguration(modelName, resolver)
            val model = config.getModel()

            assertEquals(BedrockModels.MetaLlama3_1_70BInstruct.withInferenceProfile(BedrockInferencePrefixes.GLOBAL.prefix).id, model.id)
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
        fun `Given invalid model name When getting Bedrock model Then should fallback to AmazonNovaPro with GLOBAL prefix and log warning`() {
            val resolver = DefaultInferencePrefixResolver(deployRegion, InferenceMode.AUTO)
            val config = ModelConfiguration(modelName, resolver)
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
        fun `Given empty model name When getting Bedrock model Then should fallback to AmazonNovaPro with GLOBAL prefix`() {
            val resolver = DefaultInferencePrefixResolver(deployRegion, InferenceMode.AUTO)
            val config = ModelConfiguration(modelName, resolver)
            val model = config.getModel()

            // Should fallback to default with GLOBAL prefix
            assertEquals(BedrockModels.AmazonNovaPro.withInferenceProfile(BedrockInferencePrefixes.GLOBAL.prefix).id, model.id)
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
        fun `Given no model name property When getting Bedrock model Then should use default AmazonNovaPro with GLOBAL prefix`() {
            val resolver = DefaultInferencePrefixResolver(deployRegion, InferenceMode.AUTO)
            val config = ModelConfiguration(modelName, resolver)
            val model = config.getModel()

            assertEquals(BedrockModels.AmazonNovaPro.withInferenceProfile(BedrockInferencePrefixes.GLOBAL.prefix).id, model.id)
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
        fun `Given non-existent model name When getting Bedrock model Then should fallback to default with GLOBAL prefix and log warning`() {
            val resolver = DefaultInferencePrefixResolver(deployRegion, InferenceMode.AUTO)
            val config = ModelConfiguration(modelName, resolver)
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
        fun `Given AUTO mode When first prefix succeeds Then should use global prefix`() {
            val resolver = DefaultInferencePrefixResolver("eu-west-1", InferenceMode.AUTO)
            val config = ModelConfiguration("AmazonNovaPro", resolver)
            
            val model = config.getModel()
            
            // AUTO mode tries global first, which should succeed
            assertEquals(BedrockModels.AmazonNovaPro.withInferenceProfile("global").id, model.id)
            assertEquals("global", config.getCachedPrefix())
        }
        
        @Test
        fun `Given GLOBAL_ONLY mode When getting model Then should only try global prefix`() {
            val resolver = DefaultInferencePrefixResolver("eu-west-1", InferenceMode.GLOBAL_ONLY)
            val config = ModelConfiguration("AmazonNovaPro", resolver)
            
            val model = config.getModel()
            
            assertEquals(BedrockModels.AmazonNovaPro.withInferenceProfile("global").id, model.id)
            assertEquals("global", config.getCachedPrefix())
        }
        
        @Test
        fun `Given GEO_ONLY mode When getting model Then should use geo-specific prefix`() {
            val resolver = DefaultInferencePrefixResolver("eu-west-1", InferenceMode.GEO_ONLY)
            val config = ModelConfiguration("AmazonNovaPro", resolver)
            
            val model = config.getModel()
            
            assertEquals(BedrockModels.AmazonNovaPro.withInferenceProfile("eu").id, model.id)
            assertEquals("eu", config.getCachedPrefix())
        }
        
        @Test
        fun `Given us-east-1 region When using GEO_ONLY mode Then should use us prefix`() {
            val resolver = DefaultInferencePrefixResolver("us-east-1", InferenceMode.GEO_ONLY)
            val config = ModelConfiguration("AmazonNovaPro", resolver)
            
            val model = config.getModel()
            
            assertEquals(BedrockModels.AmazonNovaPro.withInferenceProfile("us").id, model.id)
            assertEquals("us", config.getCachedPrefix())
        }
        
        @Test
        fun `Given ap-southeast-1 region When using GEO_ONLY mode Then should use ap prefix`() {
            val resolver = DefaultInferencePrefixResolver("ap-southeast-1", InferenceMode.GEO_ONLY)
            val config = ModelConfiguration("AmazonNovaPro", resolver)
            
            val model = config.getModel()
            
            assertEquals(BedrockModels.AmazonNovaPro.withInferenceProfile("ap").id, model.id)
            assertEquals("ap", config.getCachedPrefix())
        }
    }
    
    @Nested
    inner class PrefixCaching {
        
        @Test
        fun `Given successful model configuration When calling getModel multiple times Then should use cached prefix`() {
            val resolver = DefaultInferencePrefixResolver("eu-west-1", InferenceMode.AUTO)
            val config = ModelConfiguration("AmazonNovaPro", resolver)
            
            // First call
            val model1 = config.getModel()
            val cachedPrefix1 = config.getCachedPrefix()
            
            // Second call should use cached prefix
            val model2 = config.getModel()
            val cachedPrefix2 = config.getCachedPrefix()
            
            assertEquals(model1.id, model2.id)
            assertEquals(cachedPrefix1, cachedPrefix2)
            assertEquals("global", cachedPrefix2)
        }
        
        @Test
        fun `Given cached prefix When getting model Then should not retry other prefixes`() {
            val resolver = DefaultInferencePrefixResolver("eu-west-1", InferenceMode.AUTO)
            val config = ModelConfiguration("AmazonNovaPro", resolver)
            
            // First call caches the prefix
            config.getModel()
            assertEquals("global", config.getCachedPrefix())
            
            // Subsequent calls should use cached prefix immediately
            val model = config.getModel()
            assertEquals(BedrockModels.AmazonNovaPro.withInferenceProfile("global").id, model.id)
        }
    }
    
    @Nested
    inner class ModelNameFallback {
        
        @Test
        fun `Given invalid model name When getting model Then should fallback to AmazonNovaPro`() {
            val resolver = DefaultInferencePrefixResolver("eu-west-1", InferenceMode.AUTO)
            val config = ModelConfiguration("InvalidModelName", resolver)
            
            val model = config.getModel()
            
            // Should fallback to AmazonNovaPro
            assertEquals(BedrockModels.AmazonNovaPro.withInferenceProfile("global").id, model.id)
            assertEquals("InvalidModelName", config.getModelName())
            assertEquals(false, config.isOfficiallySupported())
        }
        
        @Test
        fun `Given empty model name When getting model Then should fallback to AmazonNovaPro`() {
            val resolver = DefaultInferencePrefixResolver("eu-west-1", InferenceMode.AUTO)
            val config = ModelConfiguration("", resolver)
            
            val model = config.getModel()
            
            assertEquals(BedrockModels.AmazonNovaPro.withInferenceProfile("global").id, model.id)
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
        
        @Test
        fun `Given uncached configuration When getting cached prefix Then should return null`() {
            val resolver = DefaultInferencePrefixResolver("eu-west-1", InferenceMode.AUTO)
            val config = ModelConfiguration("AmazonNovaPro", resolver)
            
            assertEquals(null, config.getCachedPrefix())
        }
        
        @Test
        fun `Given cached configuration When getting cached prefix Then should return cached value`() {
            val resolver = DefaultInferencePrefixResolver("eu-west-1", InferenceMode.AUTO)
            val config = ModelConfiguration("AmazonNovaPro", resolver)
            
            config.getModel() // This caches the prefix
            
            assertEquals("global", config.getCachedPrefix())
        }
    }
}
