package nl.vintik.mocknest.infra.aws.generation.prompt

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

/**
 * Offline property test for the prompt-injection hardening feature (Property 4).
 *
 * Verifies structural invariants of the two evaluation datasets WITHOUT calling Bedrock:
 *  - the injection dataset and the quality dataset use disjoint `input` names;
 *  - the injection dataset holds at least five scenarios;
 *  - all four attack categories are represented in the injection dataset.
 *
 * Both dataset JSONs live under `src/test/resources/eval/` and are loaded from the
 * classpath, matching the convention used by [PromptHardeningStructureTest]. Parsing
 * uses kotlinx.serialization's runtime [Json] API (lenient / ignore-unknown) so only the
 * `input` name and `metadata.attackType` fields are read and unrelated fields are tolerated.
 *
 * This test runs in the normal `./gradlew test` flow — no Bedrock call is made.
 *
 * Validates: Requirements 5.2, 5.3.
 */
class InjectionDatasetInvariantTest {

    companion object {
        private const val INJECTION_DATASET = "eval/injection-eval-dataset.json"
        private const val QUALITY_DATASET = "eval/multi-protocol-eval-dataset.json"

        private const val MIN_INJECTION_SCENARIOS = 5

        /** The four attack categories that must all be represented in the injection dataset. */
        private val REQUIRED_ATTACK_CATEGORIES = setOf(
            "legit-plus-malicious",
            "meta-injection",
            "internal-extraction",
            "format-override"
        )

        /** Deterministic seed so randomized disjointness sampling is reproducible. */
        private const val RANDOM_SEED = 20240517L

        /** Minimum number of candidate name-pair checks for the disjointness property. */
        private const val MIN_DISJOINTNESS_ITERATIONS = 100

        /** Lenient parser: tolerate unrelated fields, read only what we need. */
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /**
         * Loads a dataset JSON from the classpath. Fails clearly when the resource is
         * missing so the test surfaces a classpath problem rather than a null.
         */
        private fun loadDataset(resourcePath: String): String {
            val stream = InjectionDatasetInvariantTest::class.java.classLoader
                .getResourceAsStream(resourcePath)
            assertNotNull(stream) {
                "Dataset not found on classpath: $resourcePath. " +
                    "Ensure eval resources are on the generation test classpath."
            }
            return checkNotNull(stream).use { it.bufferedReader().readText() }
        }

        /** Parses the ordered list of `input` names from a dataset's `examples` array. */
        private fun inputNames(resourcePath: String): List<String> {
            val root = json.parseToJsonElement(loadDataset(resourcePath)).jsonObject
            val examples = root["examples"]?.jsonArray
            assertNotNull(examples) { "Dataset $resourcePath must have an 'examples' array" }
            return checkNotNull(examples).map { example ->
                val input = example.jsonObject["input"]?.jsonPrimitive?.content
                assertNotNull(input) { "Every example in $resourcePath must have an 'input' name" }
                checkNotNull(input)
            }
        }

        /** Parses the `metadata.attackType` value for every example in a dataset. */
        private fun attackTypes(resourcePath: String): List<String> {
            val root = json.parseToJsonElement(loadDataset(resourcePath)).jsonObject
            val examples = checkNotNull(root["examples"]?.jsonArray) {
                "Dataset $resourcePath must have an 'examples' array"
            }
            return examples.mapNotNull { example ->
                example.jsonObject["metadata"]?.jsonObject
                    ?.get("attackType")?.jsonPrimitive?.content
            }
        }
    }

    // --- Property 4a: the two datasets use disjoint `input` name sets ---

    @Test
    fun `Given both eval datasets When comparing input names Then the name sets are disjoint`() {
        val injectionNames = inputNames(INJECTION_DATASET).toSet()
        val qualityNames = inputNames(QUALITY_DATASET).toSet()

        val intersection = injectionNames intersect qualityNames

        assertEquals(
            emptySet<String>(),
            intersection
        ) {
            "Injection and quality dataset 'input' names must be disjoint but share: $intersection"
        }

        logger.info {
            "Property 4a verified: ${injectionNames.size} injection names disjoint from " +
                "${qualityNames.size} quality names"
        }
    }

    @Test
    fun `Given both datasets When sampling at least 100 candidate name pairs Then every pair differs`() {
        val injectionNames = inputNames(INJECTION_DATASET)
        val qualityNames = inputNames(QUALITY_DATASET)

        assertTrue(injectionNames.isNotEmpty()) { "Injection dataset must have at least one input name" }
        assertTrue(qualityNames.isNotEmpty()) { "Quality dataset must have at least one input name" }

        // Deterministic randomized sampling over both name sets. We run at least 100
        // iterations (topped up beyond the full cartesian product when it is smaller) so
        // the disjointness property is exercised across many candidate pairs. A seeded
        // Random keeps failures reproducible for easy debugging.
        val random = Random(RANDOM_SEED)
        val iterations = maxOf(MIN_DISJOINTNESS_ITERATIONS, injectionNames.size * qualityNames.size)

        var checked = 0
        repeat(iterations) {
            val injectionName = injectionNames[random.nextInt(injectionNames.size)]
            val qualityName = qualityNames[random.nextInt(qualityNames.size)]
            assertTrue(injectionName != qualityName) {
                "Candidate pair must differ but both were '$injectionName' " +
                    "(iteration ${checked + 1})"
            }
            checked++
        }

        assertTrue(checked >= MIN_DISJOINTNESS_ITERATIONS) {
            "Expected at least $MIN_DISJOINTNESS_ITERATIONS disjointness checks but ran $checked"
        }

        logger.info { "Property 4a verified: $checked candidate name-pair checks all differ" }
    }

    // --- Property 4b: the injection dataset has at least five scenarios ---

    @Test
    fun `Given the injection dataset When counting scenarios Then there are at least five`() {
        val injectionNames = inputNames(INJECTION_DATASET)

        assertTrue(injectionNames.size >= MIN_INJECTION_SCENARIOS) {
            "Injection dataset must have >= $MIN_INJECTION_SCENARIOS scenarios but has ${injectionNames.size}"
        }

        logger.info { "Property 4b verified: injection dataset has ${injectionNames.size} scenarios" }
    }

    @Test
    fun `Given the injection dataset When collecting input names Then every name is unique`() {
        val injectionNames = inputNames(INJECTION_DATASET)

        assertEquals(
            injectionNames.size,
            injectionNames.toSet().size
        ) {
            "Injection dataset 'input' names must be unique but found duplicates in $injectionNames"
        }

        logger.info { "Property 4b verified: all ${injectionNames.size} injection input names are unique" }
    }

    // --- Property 4c: all four attack categories are represented ---

    @Test
    fun `Given the injection dataset When classifying by attackType Then all four attack categories are represented`() {
        val presentCategories = attackTypes(INJECTION_DATASET).toSet()

        val missing = REQUIRED_ATTACK_CATEGORIES - presentCategories
        assertEquals(
            emptySet<String>(),
            missing
        ) {
            "Injection dataset must represent all four attack categories but is missing: $missing " +
                "(present: $presentCategories)"
        }

        logger.info { "Property 4c verified: all four attack categories present ($presentCategories)" }
    }

    @ParameterizedTest(name = "Property 4c: attack category ''{0}'' is represented in the injection dataset")
    @ValueSource(strings = ["legit-plus-malicious", "meta-injection", "internal-extraction", "format-override"])
    fun `Given the injection dataset When looking for a required attack category Then it is present`(
        category: String
    ) {
        val presentCategories = attackTypes(INJECTION_DATASET).toSet()

        assertTrue(presentCategories.contains(category)) {
            "Injection dataset must contain a scenario with attackType '$category' but present are: $presentCategories"
        }

        logger.info { "Property 4c verified: attack category '$category' present" }
    }
}
