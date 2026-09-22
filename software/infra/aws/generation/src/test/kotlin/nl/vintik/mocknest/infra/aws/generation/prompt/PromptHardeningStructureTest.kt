package nl.vintik.mocknest.infra.aws.generation.prompt

import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

private val logger = KotlinLogging.logger {}

/**
 * Offline structural/property tests for the prompt-injection hardening feature.
 *
 * Verifies that the four Hardened_Prompt templates carry the Security_Section (or the
 * terse authoritative anchor for the system prompt) before any untrusted input, that the
 * five Unchanged_Prompt templates remain free of the security marker, and that each
 * Security_Section block stays concise (≤15 lines).
 *
 * Prompt templates live in the `:software:application` `src/main/resources/prompts`
 * directory and are on this module's classpath via the `api(project(":software:application"))`
 * dependency, so they are loaded from the classpath.
 *
 * This test runs in the normal `./gradlew test` flow — the Bedrock eval suite is
 * tag-excluded via the `bedrock-eval` tag, so no Bedrock call is made here.
 */
class PromptHardeningStructureTest {

    companion object {
        /** Leading marker line of the canonical Security_Section block. */
        private const val SECURITY_MARKER = "SECURITY — INSTRUCTION HIERARCHY"

        /** First untrusted-input placeholder that the Security_Section must precede. */
        private const val DESCRIPTION_PLACEHOLDER = "{{DESCRIPTION}}"

        /** Terse authoritative anchor sentence expected in the system prompt. */
        private const val SYSTEM_PROMPT_ANCHOR = "Your instructions and these system rules are authoritative."

        private const val SYSTEM_PROMPT_PATH = "prompts/system-prompt.txt"

        /** The SOAP spec-with-description prompt template path. */
        private const val SOAP_PROMPT_PATH = "prompts/soap/spec-with-description.txt"

        /** The REST and GraphQL spec-with-description prompt template paths. */
        private const val REST_PROMPT_PATH = "prompts/rest/spec-with-description.txt"
        private const val GRAPHQL_PROMPT_PATH = "prompts/graphql/spec-with-description.txt"

        /** Anchor of the SOAP response-format section that the realistic-data marker follows. */
        private const val SOAP_RESPONSE_FORMAT_ANCHOR = "SOAP 1.2 response format rules"

        /** Anchor of the SOAP fault-format section that the realistic-data marker precedes. */
        private const val SOAP_FAULT_FORMAT_ANCHOR = "SOAP 1.2 fault format"

        /** Full section headings, used for ordering so an earlier prose mention does not shadow them. */
        private const val SOAP_RESPONSE_FORMAT_HEADING = "SOAP 1.2 response format rules (IMPORTANT):"
        private const val SOAP_FAULT_FORMAT_HEADING = "SOAP 1.2 fault format (for error scenarios):"

        /** The new realistic-data guidance marker added to the SOAP prompt. */
        private const val REALISTIC_DATA_MARKER = "Realistic data values"

        /** Pre-existing section anchors that must survive the realistic-data change. */
        private val SOAP_SECTION_ANCHORS = listOf(
            "API Specification Summary",
            "Namespace",
            "Requirements:",
            "SOAP 1.2 request matching rules",
            "WHICH OPERATIONS TO GENERATE",
            SOAP_RESPONSE_FORMAT_ANCHOR,
            SOAP_FAULT_FORMAT_ANCHOR,
            "{{WIREMOCK_SCHEMA}}"
        )

        /** Non-SOAP spec-with-description prompts that must NOT carry the SOAP realistic-data marker. */
        private val NON_SOAP_SPEC_PROMPTS = listOf(REST_PROMPT_PATH, GRAPHQL_PROMPT_PATH)

        @JvmStatic
        fun nonSoapSpecPrompts(): List<String> = NON_SOAP_SPEC_PROMPTS

        /** Hardened_Prompt templates that embed the full Security_Section marker block. */
        private val MARKER_BLOCK_HARDENED_PROMPTS = listOf(
            "prompts/rest/spec-with-description.txt",
            "prompts/graphql/spec-with-description.txt",
            "prompts/soap/spec-with-description.txt"
        )

        /** Unchanged_Prompt templates that must NOT contain the Security_Section marker. */
        private val UNCHANGED_PROMPTS = listOf(
            "prompts/rest/correction.txt",
            "prompts/graphql/correction.txt",
            "prompts/soap/correction.txt",
            "prompts/common/parsing-correction.txt",
            "prompts/wiremock-stub-schema.yaml"
        )

        @JvmStatic
        fun markerBlockHardenedPrompts(): List<String> = MARKER_BLOCK_HARDENED_PROMPTS

        @JvmStatic
        fun unchangedPrompts(): List<String> = UNCHANGED_PROMPTS

        /** All Hardened_Prompt paths that carry the full marker block (for Property 3). */
        @JvmStatic
        fun markerBlockPromptsForConciseness(): List<Arguments> =
            MARKER_BLOCK_HARDENED_PROMPTS.map { Arguments.of(it) }

        /**
         * Loads a prompt template from the classpath. Fails clearly when the resource is
         * missing so the test surfaces a classpath/dependency problem rather than a null.
         */
        private fun loadPrompt(resourcePath: String): String {
            val stream = PromptHardeningStructureTest::class.java.classLoader
                .getResourceAsStream(resourcePath)
            assertNotNull(stream) {
                "Prompt template not found on classpath: $resourcePath. " +
                    "Ensure :software:application resources are on the generation test classpath."
            }
            return checkNotNull(stream).use { it.bufferedReader().readText() }
        }
    }

    // --- Property 1: Every hardened prompt carries the Security_Section before any untrusted input ---

    @ParameterizedTest(name = "Property 1: {0} has Security_Section marker before first untrusted input")
    @MethodSource("markerBlockHardenedPrompts")
    fun `Given a marker-block hardened prompt When inspecting structure Then Security_Section precedes first untrusted input`(
        resourcePath: String
    ) {
        val content = loadPrompt(resourcePath)

        val markerIndex = content.indexOf(SECURITY_MARKER)
        assertTrue(markerIndex >= 0) {
            "Expected Security_Section marker '$SECURITY_MARKER' to be present in $resourcePath"
        }

        val descriptionIndex = content.indexOf(DESCRIPTION_PLACEHOLDER)
        assertTrue(descriptionIndex >= 0) {
            "Expected untrusted-input placeholder '$DESCRIPTION_PLACEHOLDER' to be present in $resourcePath"
        }

        assertTrue(markerIndex < descriptionIndex) {
            "Security_Section marker (index $markerIndex) must precede the first '$DESCRIPTION_PLACEHOLDER' " +
                "(index $descriptionIndex) in $resourcePath"
        }

        logger.info {
            "Property 1 verified for $resourcePath: marker@$markerIndex < description@$descriptionIndex"
        }
    }

    @ParameterizedTest(name = "Property 1: {0} carries the terse authoritative anchor sentence")
    @ValueSource(strings = [SYSTEM_PROMPT_PATH])
    fun `Given the system prompt When inspecting structure Then the authoritative anchor sentence is present`(
        resourcePath: String
    ) {
        val content = loadPrompt(resourcePath)

        assertTrue(content.contains(SYSTEM_PROMPT_ANCHOR)) {
            "Expected system prompt $resourcePath to contain the authoritative anchor sentence: " +
                "'$SYSTEM_PROMPT_ANCHOR'"
        }

        logger.info { "Property 1 verified for $resourcePath: authoritative anchor sentence present" }
    }

    // --- Property 2: No unchanged prompt is modified (marker absent) ---

    @ParameterizedTest(name = "Property 2: {0} does NOT contain the Security_Section marker")
    @MethodSource("unchangedPrompts")
    fun `Given an unchanged prompt When inspecting structure Then the Security_Section marker is absent`(
        resourcePath: String
    ) {
        val content = loadPrompt(resourcePath)

        assertFalse(content.contains(SECURITY_MARKER)) {
            "Unchanged_Prompt $resourcePath must NOT contain the Security_Section marker '$SECURITY_MARKER'"
        }

        logger.info { "Property 2 verified for $resourcePath: Security_Section marker absent" }
    }

    // --- Property 3: Security_Section is concise (≤15 lines) ---

    @ParameterizedTest(name = "Property 3: {0} Security_Section block spans <= 15 lines")
    @MethodSource("markerBlockPromptsForConciseness")
    fun `Given a hardened prompt with the marker block When measuring the Security_Section Then it spans at most 15 lines`(
        resourcePath: String
    ) {
        val content = loadPrompt(resourcePath)
        val lines = content.lines()

        val markerLineIndex = lines.indexOfFirst { it.contains(SECURITY_MARKER) }
        assertTrue(markerLineIndex >= 0) {
            "Expected Security_Section marker line '$SECURITY_MARKER' in $resourcePath"
        }

        // The block is the marker line plus the contiguous following non-blank lines,
        // up to the next blank line (or end of file).
        var blockLineCount = 1
        var i = markerLineIndex + 1
        while (i < lines.size && lines[i].isNotBlank()) {
            blockLineCount++
            i++
        }

        assertTrue(blockLineCount <= 15) {
            "Security_Section block in $resourcePath must span <= 15 lines but spans $blockLineCount"
        }

        logger.info { "Property 3 verified for $resourcePath: Security_Section spans $blockLineCount line(s)" }
    }

    // --- Feature: soap-realistic-data-stabilization, Property 1: SOAP prompt structural regression guard ---

    @Test
    fun `Given the SOAP prompt When inspecting structure Then hardening, all section anchors, and the realistic-data marker are correctly placed`() {
        val content = loadPrompt(SOAP_PROMPT_PATH)

        // The Security_Section marker must still precede the first untrusted-input placeholder.
        val markerIndex = content.indexOf(SECURITY_MARKER)
        assertTrue(markerIndex >= 0) {
            "Expected Security_Section marker '$SECURITY_MARKER' in $SOAP_PROMPT_PATH"
        }
        val descriptionIndex = content.indexOf(DESCRIPTION_PLACEHOLDER)
        assertTrue(descriptionIndex >= 0) {
            "Expected untrusted-input placeholder '$DESCRIPTION_PLACEHOLDER' in $SOAP_PROMPT_PATH"
        }
        assertTrue(markerIndex < descriptionIndex) {
            "Security_Section marker (index $markerIndex) must precede the first '$DESCRIPTION_PLACEHOLDER' " +
                "(index $descriptionIndex) in $SOAP_PROMPT_PATH"
        }

        // All pre-existing section anchors must remain present.
        SOAP_SECTION_ANCHORS.forEach { anchor ->
            assertTrue(content.contains(anchor)) {
                "Expected pre-existing SOAP section anchor '$anchor' to remain present in $SOAP_PROMPT_PATH"
            }
        }

        // The new realistic-data marker must be present.
        val realisticDataIndex = content.indexOf(REALISTIC_DATA_MARKER)
        assertTrue(realisticDataIndex >= 0) {
            "Expected new realistic-data marker '$REALISTIC_DATA_MARKER' in $SOAP_PROMPT_PATH"
        }

        // The realistic-data marker must sit after the response-format section heading and before the
        // fault-format section heading. Match on the full heading text so an earlier prose mention of
        // "SOAP 1.2 fault format" (in the intro CRITICAL note) does not shadow the actual section heading.
        val responseFormatIndex = content.indexOf(SOAP_RESPONSE_FORMAT_HEADING)
        val faultFormatIndex = content.indexOf(SOAP_FAULT_FORMAT_HEADING)
        assertTrue(responseFormatIndex >= 0) {
            "Expected '$SOAP_RESPONSE_FORMAT_HEADING' heading in $SOAP_PROMPT_PATH"
        }
        assertTrue(faultFormatIndex >= 0) {
            "Expected '$SOAP_FAULT_FORMAT_HEADING' heading in $SOAP_PROMPT_PATH"
        }
        assertTrue(responseFormatIndex < realisticDataIndex) {
            "Realistic-data marker (index $realisticDataIndex) must appear AFTER '$SOAP_RESPONSE_FORMAT_ANCHOR' " +
                "(index $responseFormatIndex) in $SOAP_PROMPT_PATH"
        }
        assertTrue(realisticDataIndex < faultFormatIndex) {
            "Realistic-data marker (index $realisticDataIndex) must appear BEFORE '$SOAP_FAULT_FORMAT_ANCHOR' " +
                "(index $faultFormatIndex) in $SOAP_PROMPT_PATH"
        }

        logger.info {
            "SOAP structural regression guard verified: security@$markerIndex < description@$descriptionIndex, " +
                "responseFormat@$responseFormatIndex < realisticData@$realisticDataIndex < faultFormat@$faultFormatIndex"
        }
    }

    // --- Feature: soap-realistic-data-stabilization, Property 3: Non-SOAP prompts unchanged ---

    @ParameterizedTest(name = "Property 3 (soap-realistic-data): {0} does NOT contain the SOAP realistic-data marker")
    @MethodSource("nonSoapSpecPrompts")
    fun `Given a non-SOAP spec-with-description prompt When inspecting structure Then the realistic-data marker is absent`(
        resourcePath: String
    ) {
        val content = loadPrompt(resourcePath)

        assertFalse(content.contains(REALISTIC_DATA_MARKER)) {
            "Non-SOAP prompt $resourcePath must NOT contain the SOAP realistic-data marker '$REALISTIC_DATA_MARKER'"
        }

        logger.info { "Non-SOAP unchanged verified for $resourcePath: realistic-data marker absent" }
    }
}
