package nl.vintik.mocknest.infra.aws.generation.ai.config

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals

class InferencePrefixResolverTest {

    @Nested
    inner class GeoPrefixDerivation {
        
        @ParameterizedTest
        @CsvSource(
            "eu-west-1, eu",
            "eu-west-2, eu",
            "eu-central-1, eu",
            "eu-north-1, eu",
            "eu-south-1, eu",
            "us-east-1, us",
            "us-east-2, us",
            "us-west-1, us",
            "us-west-2, us",
            "ap-southeast-1, ap",
            "ap-southeast-2, ap",
            "ap-northeast-1, ap",
            "ap-northeast-2, ap",
            "ap-south-1, ap",
            "ca-central-1, ca",
            "me-south-1, me",
            "me-central-1, me",
            "sa-east-1, sa",
            "af-south-1, af"
        )
        fun `Given AWS region When deriving geo prefix Then should return correct prefix`(
            region: String,
            expectedPrefix: String
        ) {
            val resolver = DefaultInferencePrefixResolver(region, InferenceMode.GEO_ONLY)
            val prefixes = resolver.getCandidatePrefixes()
            
            assertEquals(1, prefixes.size)
            assertEquals(expectedPrefix, prefixes[0])
        }
        
        @Test
        fun `Given unknown region prefix When deriving geo prefix Then should default to us and log warning`() {
            val resolver = DefaultInferencePrefixResolver("xx-unknown-1", InferenceMode.GEO_ONLY)
            val prefixes = resolver.getCandidatePrefixes()
            
            assertEquals(1, prefixes.size)
            assertEquals("us", prefixes[0])
        }
        
        @Test
        fun `Given malformed region When deriving geo prefix Then should default to us and log warning`() {
            val resolver = DefaultInferencePrefixResolver("invalid-region", InferenceMode.GEO_ONLY)
            val prefixes = resolver.getCandidatePrefixes()
            
            assertEquals(1, prefixes.size)
            assertEquals("us", prefixes[0])
        }
        
        @Test
        fun `Given empty region When deriving geo prefix Then should default to us and log warning`() {
            val resolver = DefaultInferencePrefixResolver("", InferenceMode.GEO_ONLY)
            val prefixes = resolver.getCandidatePrefixes()
            
            assertEquals(1, prefixes.size)
            assertEquals("us", prefixes[0])
        }
    }

    @Nested
    inner class AutoModeGeneration {
        
        @Test
        fun `Given AUTO mode with eu region When getting candidate prefixes Then should return eu then global`() {
            val resolver = DefaultInferencePrefixResolver("eu-west-1", InferenceMode.AUTO)
            val prefixes = resolver.getCandidatePrefixes()
            
            assertEquals(2, prefixes.size)
            assertEquals("eu", prefixes[0])
            assertEquals("global", prefixes[1])
        }
        
        @Test
        fun `Given AUTO mode with us region When getting candidate prefixes Then should return us then global`() {
            val resolver = DefaultInferencePrefixResolver("us-east-1", InferenceMode.AUTO)
            val prefixes = resolver.getCandidatePrefixes()
            
            assertEquals(2, prefixes.size)
            assertEquals("us", prefixes[0])
            assertEquals("global", prefixes[1])
        }
        
        @Test
        fun `Given AUTO mode with ap region When getting candidate prefixes Then should return ap then global`() {
            val resolver = DefaultInferencePrefixResolver("ap-southeast-1", InferenceMode.AUTO)
            val prefixes = resolver.getCandidatePrefixes()
            
            assertEquals(2, prefixes.size)
            assertEquals("ap", prefixes[0])
            assertEquals("global", prefixes[1])
        }
        
        @Test
        fun `Given AUTO mode with ca region When getting candidate prefixes Then should return ca then global`() {
            val resolver = DefaultInferencePrefixResolver("ca-central-1", InferenceMode.AUTO)
            val prefixes = resolver.getCandidatePrefixes()
            
            assertEquals(2, prefixes.size)
            assertEquals("ca", prefixes[0])
            assertEquals("global", prefixes[1])
        }
        
        @Test
        fun `Given AUTO mode with me region When getting candidate prefixes Then should return me then global`() {
            val resolver = DefaultInferencePrefixResolver("me-south-1", InferenceMode.AUTO)
            val prefixes = resolver.getCandidatePrefixes()
            
            assertEquals(2, prefixes.size)
            assertEquals("me", prefixes[0])
            assertEquals("global", prefixes[1])
        }
        
        @Test
        fun `Given AUTO mode with sa region When getting candidate prefixes Then should return sa then global`() {
            val resolver = DefaultInferencePrefixResolver("sa-east-1", InferenceMode.AUTO)
            val prefixes = resolver.getCandidatePrefixes()
            
            assertEquals(2, prefixes.size)
            assertEquals("sa", prefixes[0])
            assertEquals("global", prefixes[1])
        }
        
        @Test
        fun `Given AUTO mode with af region When getting candidate prefixes Then should return af then global`() {
            val resolver = DefaultInferencePrefixResolver("af-south-1", InferenceMode.AUTO)
            val prefixes = resolver.getCandidatePrefixes()
            
            assertEquals(2, prefixes.size)
            assertEquals("af", prefixes[0])
            assertEquals("global", prefixes[1])
        }
        
        @Test
        fun `Given AUTO mode with unknown region When getting candidate prefixes Then should return us then global`() {
            val resolver = DefaultInferencePrefixResolver("unknown-region-1", InferenceMode.AUTO)
            val prefixes = resolver.getCandidatePrefixes()
            
            assertEquals(2, prefixes.size)
            assertEquals("us", prefixes[0])
            assertEquals("global", prefixes[1])
        }
    }

    @Nested
    inner class GlobalOnlyModeGeneration {
        
        @Test
        fun `Given GLOBAL_ONLY mode with eu region When getting candidate prefixes Then should return only global`() {
            val resolver = DefaultInferencePrefixResolver("eu-west-1", InferenceMode.GLOBAL_ONLY)
            val prefixes = resolver.getCandidatePrefixes()
            
            assertEquals(1, prefixes.size)
            assertEquals("global", prefixes[0])
        }
        
        @Test
        fun `Given GLOBAL_ONLY mode with us region When getting candidate prefixes Then should return only global`() {
            val resolver = DefaultInferencePrefixResolver("us-east-1", InferenceMode.GLOBAL_ONLY)
            val prefixes = resolver.getCandidatePrefixes()
            
            assertEquals(1, prefixes.size)
            assertEquals("global", prefixes[0])
        }
        
        @Test
        fun `Given GLOBAL_ONLY mode with ap region When getting candidate prefixes Then should return only global`() {
            val resolver = DefaultInferencePrefixResolver("ap-southeast-1", InferenceMode.GLOBAL_ONLY)
            val prefixes = resolver.getCandidatePrefixes()
            
            assertEquals(1, prefixes.size)
            assertEquals("global", prefixes[0])
        }
        
        @Test
        fun `Given GLOBAL_ONLY mode with unknown region When getting candidate prefixes Then should return only global`() {
            val resolver = DefaultInferencePrefixResolver("unknown-region-1", InferenceMode.GLOBAL_ONLY)
            val prefixes = resolver.getCandidatePrefixes()
            
            assertEquals(1, prefixes.size)
            assertEquals("global", prefixes[0])
        }
    }

    @Nested
    inner class GeoOnlyModeGeneration {
        
        @Test
        fun `Given GEO_ONLY mode with eu region When getting candidate prefixes Then should return only eu`() {
            val resolver = DefaultInferencePrefixResolver("eu-west-1", InferenceMode.GEO_ONLY)
            val prefixes = resolver.getCandidatePrefixes()
            
            assertEquals(1, prefixes.size)
            assertEquals("eu", prefixes[0])
        }
        
        @Test
        fun `Given GEO_ONLY mode with us region When getting candidate prefixes Then should return only us`() {
            val resolver = DefaultInferencePrefixResolver("us-east-1", InferenceMode.GEO_ONLY)
            val prefixes = resolver.getCandidatePrefixes()
            
            assertEquals(1, prefixes.size)
            assertEquals("us", prefixes[0])
        }
        
        @Test
        fun `Given GEO_ONLY mode with ap region When getting candidate prefixes Then should return only ap`() {
            val resolver = DefaultInferencePrefixResolver("ap-southeast-1", InferenceMode.GEO_ONLY)
            val prefixes = resolver.getCandidatePrefixes()
            
            assertEquals(1, prefixes.size)
            assertEquals("ap", prefixes[0])
        }
        
        @Test
        fun `Given GEO_ONLY mode with ca region When getting candidate prefixes Then should return only ca`() {
            val resolver = DefaultInferencePrefixResolver("ca-central-1", InferenceMode.GEO_ONLY)
            val prefixes = resolver.getCandidatePrefixes()
            
            assertEquals(1, prefixes.size)
            assertEquals("ca", prefixes[0])
        }
        
        @Test
        fun `Given GEO_ONLY mode with me region When getting candidate prefixes Then should return only me`() {
            val resolver = DefaultInferencePrefixResolver("me-south-1", InferenceMode.GEO_ONLY)
            val prefixes = resolver.getCandidatePrefixes()
            
            assertEquals(1, prefixes.size)
            assertEquals("me", prefixes[0])
        }
        
        @Test
        fun `Given GEO_ONLY mode with sa region When getting candidate prefixes Then should return only sa`() {
            val resolver = DefaultInferencePrefixResolver("sa-east-1", InferenceMode.GEO_ONLY)
            val prefixes = resolver.getCandidatePrefixes()
            
            assertEquals(1, prefixes.size)
            assertEquals("sa", prefixes[0])
        }
        
        @Test
        fun `Given GEO_ONLY mode with af region When getting candidate prefixes Then should return only af`() {
            val resolver = DefaultInferencePrefixResolver("af-south-1", InferenceMode.GEO_ONLY)
            val prefixes = resolver.getCandidatePrefixes()
            
            assertEquals(1, prefixes.size)
            assertEquals("af", prefixes[0])
        }
        
        @Test
        fun `Given GEO_ONLY mode with unknown region When getting candidate prefixes Then should return only us`() {
            val resolver = DefaultInferencePrefixResolver("unknown-region-1", InferenceMode.GEO_ONLY)
            val prefixes = resolver.getCandidatePrefixes()
            
            assertEquals(1, prefixes.size)
            assertEquals("us", prefixes[0])
        }
    }

    @Nested
    inner class DeployRegionProperty {
        
        @Test
        fun `Given resolver When accessing deployRegion property Then should return configured region`() {
            val resolver = DefaultInferencePrefixResolver("eu-west-1", InferenceMode.AUTO)
            
            assertEquals("eu-west-1", resolver.deployRegion)
        }
        
        @Test
        fun `Given resolver with different region When accessing deployRegion property Then should return that region`() {
            val resolver = DefaultInferencePrefixResolver("us-east-1", InferenceMode.AUTO)
            
            assertEquals("us-east-1", resolver.deployRegion)
        }
    }
}
