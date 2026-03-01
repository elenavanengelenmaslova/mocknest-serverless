package nl.vintik.mocknest.infra.aws.generation.ai

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Bug Condition Exploration Test - Property 1: Fault Condition
 * 
 * CRITICAL: This test MUST FAIL on unfixed code - failure confirms the architectural violation exists.
 * 
 * This test encodes the EXPECTED behavior after the fix:
 * - BedrockServiceAdapter should NOT contain prompt building methods
 * - BedrockServiceAdapter should NOT contain hardcoded prompt text
 * - PromptBuilderService should exist in application layer
 * - Prompt template files should exist in application resources
 * 
 * When this test FAILS, it proves the architectural violation exists (prompt building in infrastructure layer).
 * When this test PASSES (after implementation), it confirms the architectural violation is fixed.
 * 
 * DO NOT attempt to fix the test or the code when it fails - document the counterexamples instead.
 */
class PromptBuildingArchitectureViolationTest {

    @Test
    fun `Given BedrockServiceAdapter When examining methods Then should NOT contain prompt building methods`() {
        // Read the BedrockServiceAdapter source file
        val projectRoot = File(System.getProperty("user.dir")).let { dir ->
            // If we're in a submodule, navigate to project root
            var current = dir
            while (current != null && !File(current, "settings.gradle.kts").exists()) {
                current = current.parentFile
            }
            current ?: dir
        }
        
        val bedrockServiceAdapterFile = File(
            projectRoot,
            "software/infra/aws/src/main/kotlin/nl/vintik/mocknest/infra/aws/generation/ai/BedrockServiceAdapter.kt"
        )
        
        assertTrue(bedrockServiceAdapterFile.exists(), "BedrockServiceAdapter.kt should exist at ${bedrockServiceAdapterFile.absolutePath}")
        
        val sourceCode = bedrockServiceAdapterFile.readText()
        
        // Check that prompt building methods do NOT exist in infrastructure layer
        assertFalse(
            sourceCode.contains("fun buildSpecWithDescriptionPrompt"),
            "ARCHITECTURAL VIOLATION: BedrockServiceAdapter contains buildSpecWithDescriptionPrompt method - " +
            "prompt building logic should be in application layer (PromptBuilderService)"
        )
        
        assertFalse(
            sourceCode.contains("fun buildCorrectionPrompt"),
            "ARCHITECTURAL VIOLATION: BedrockServiceAdapter contains buildCorrectionPrompt method - " +
            "prompt building logic should be in application layer (PromptBuilderService)"
        )
    }

    @Test
    fun `Given BedrockServiceAdapter When examining code Then should NOT contain hardcoded prompt text`() {
        val projectRoot = File(System.getProperty("user.dir")).let { dir ->
            var current = dir
            while (current != null && !File(current, "settings.gradle.kts").exists()) {
                current = current.parentFile
            }
            current ?: dir
        }
        
        val bedrockServiceAdapterFile = File(
            projectRoot,
            "software/infra/aws/src/main/kotlin/nl/vintik/mocknest/infra/aws/generation/ai/BedrockServiceAdapter.kt"
        )
        
        assertTrue(bedrockServiceAdapterFile.exists(), "BedrockServiceAdapter.kt should exist at ${bedrockServiceAdapterFile.absolutePath}")
        
        val sourceCode = bedrockServiceAdapterFile.readText()
        
        // Check for hardcoded system prompt text
        assertFalse(
            sourceCode.contains("You are an expert API mock generator"),
            "ARCHITECTURAL VIOLATION: BedrockServiceAdapter contains hardcoded system prompt text - " +
            "prompt text should be externalized to resource files"
        )
        
        // Check for hardcoded prompt instructions
        assertFalse(
            sourceCode.contains("Generate WireMock JSON mappings based on"),
            "ARCHITECTURAL VIOLATION: BedrockServiceAdapter contains hardcoded prompt instructions - " +
            "prompt text should be externalized to resource files"
        )
        
        assertFalse(
            sourceCode.contains("The following WireMock mappings failed validation"),
            "ARCHITECTURAL VIOLATION: BedrockServiceAdapter contains hardcoded correction prompt text - " +
            "prompt text should be externalized to resource files"
        )
    }

    @Test
    fun `Given application layer When examining structure Then PromptBuilderService should exist`() {
        val projectRoot = File(System.getProperty("user.dir")).let { dir ->
            var current = dir
            while (current != null && !File(current, "settings.gradle.kts").exists()) {
                current = current.parentFile
            }
            current ?: dir
        }
        
        val promptBuilderServiceFile = File(
            projectRoot,
            "software/application/src/main/kotlin/nl/vintik/mocknest/application/generation/services/PromptBuilderService.kt"
        )
        
        assertTrue(
            promptBuilderServiceFile.exists(),
            "ARCHITECTURAL VIOLATION: PromptBuilderService does not exist in application layer at ${promptBuilderServiceFile.absolutePath} - " +
            "prompt building logic should be moved from infrastructure to application layer"
        )
    }

    @Test
    fun `Given application resources When examining structure Then prompt template files should exist`() {
        val projectRoot = File(System.getProperty("user.dir")).let { dir ->
            var current = dir
            while (current != null && !File(current, "settings.gradle.kts").exists()) {
                current = current.parentFile
            }
            current ?: dir
        }
        
        val systemPromptFile = File(
            projectRoot,
            "software/application/src/main/resources/prompts/system-prompt.txt"
        )
        val specWithDescriptionFile = File(
            projectRoot,
            "software/application/src/main/resources/prompts/spec-with-description.txt"
        )
        val correctionFile = File(
            projectRoot,
            "software/application/src/main/resources/prompts/correction.txt"
        )
        
        assertTrue(
            systemPromptFile.exists(),
            "ARCHITECTURAL VIOLATION: system-prompt.txt does not exist in application resources at ${systemPromptFile.absolutePath} - " +
            "prompt text should be externalized to resource files"
        )
        
        assertTrue(
            specWithDescriptionFile.exists(),
            "ARCHITECTURAL VIOLATION: spec-with-description.txt does not exist in application resources at ${specWithDescriptionFile.absolutePath} - " +
            "prompt text should be externalized to resource files"
        )
        
        assertTrue(
            correctionFile.exists(),
            "ARCHITECTURAL VIOLATION: correction.txt does not exist in application resources at ${correctionFile.absolutePath} - " +
            "prompt text should be externalized to resource files"
        )
    }

    @Test
    fun `Given BedrockServiceAdapter When examining createAgent Then should load system prompt from PromptBuilderService`() {
        val projectRoot = File(System.getProperty("user.dir")).let { dir ->
            var current = dir
            while (current != null && !File(current, "settings.gradle.kts").exists()) {
                current = current.parentFile
            }
            current ?: dir
        }
        
        val bedrockServiceAdapterFile = File(
            projectRoot,
            "software/infra/aws/src/main/kotlin/nl/vintik/mocknest/infra/aws/generation/ai/BedrockServiceAdapter.kt"
        )
        
        assertTrue(bedrockServiceAdapterFile.exists(), "BedrockServiceAdapter.kt should exist at ${bedrockServiceAdapterFile.absolutePath}")
        
        val sourceCode = bedrockServiceAdapterFile.readText()
        
        // Check that createAgent uses PromptBuilderService instead of hardcoded prompt
        val createAgentMethod = sourceCode.substringAfter("fun createAgent()", "")
        
        if (createAgentMethod.isEmpty()) {
            fail("createAgent method not found in BedrockServiceAdapter")
        }
        
        assertTrue(
            createAgentMethod.contains("promptBuilder.loadSystemPrompt()") ||
            createAgentMethod.contains("promptBuilderService.loadSystemPrompt()"),
            "ARCHITECTURAL VIOLATION: createAgent does not use PromptBuilderService.loadSystemPrompt() - " +
            "system prompt should be loaded from PromptBuilderService, not hardcoded"
        )
    }
}
