package nl.vintik.mocknest.infra.aws.generation.ai.eval

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Structural validation tests for the GraphQL eval scenarios in the multi-protocol dataset.
 *
 * Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 4.1, 4.2, 4.6, 7.1, 7.2
 * Correctness Properties: 2, 3, 4
 */
@Tag("graphql-eval")
class GraphqlEvalDatasetValidationTest {

    companion object {
        private val mapper = jacksonObjectMapper()

        private val dataset: JsonNode by lazy {
            val stream = GraphqlEvalDatasetValidationTest::class.java
                .getResourceAsStream("/eval/multi-protocol-eval-dataset.json")
            assertNotNull(stream, "multi-protocol-eval-dataset.json not found on classpath")
            mapper.readTree(stream)
        }

        private val allExamples: List<JsonNode> by lazy {
            dataset["examples"].toList()
        }

        private val graphqlScenarios: List<JsonNode> by lazy {
            allExamples.filter { it["metadata"]["protocol"].asText() == "GraphQL" }
        }

        private val existingGraphqlInputs = listOf(
            "graphql-pokemon-pikachu",
            "graphql-books-two-books"
        )

        private val newGraphqlScenarios: List<JsonNode> by lazy {
            graphqlScenarios.filter { it["input"].asText() !in existingGraphqlInputs }
        }

        private val newIntrospectionSpecFiles = listOf(
            "eval/ecommerce-graphql-introspection.json",
            "eval/taskmanagement-graphql-introspection.json"
        )

        private val vaguePhrases = listOf(
            "looks correct",
            "reasonable output",
            "seems right",
            "appears valid",
            "should be fine"
        )

        @JvmStatic
        fun graphqlScenarios(): List<JsonNode> = graphqlScenarios

        @JvmStatic
        fun newGraphqlScenarios(): List<JsonNode> = newGraphqlScenarios
    }

    /**
     * **Validates: Requirements 2.1, 2.3, 7.2**
     */
    @Nested
    inner class ScenarioCount {

        @Test
        fun `Given dataset When counting new GraphQL scenarios Then at least 12 exist beyond pokemon and books`() {
            assertTrue(
                newGraphqlScenarios.size >= 12,
                "Expected at least 12 new GraphQL scenarios beyond pokemon and books, but found ${newGraphqlScenarios.size}"
            )
        }

        @Test
        fun `Given dataset When checking new introspection schemas Then each is referenced by at least one scenario`() {
            val referencedSpecFiles = newGraphqlScenarios
                .map { it["metadata"]["specFile"].asText() }
                .toSet()

            newIntrospectionSpecFiles.forEach { specFile ->
                assertTrue(
                    specFile in referencedSpecFiles,
                    "Introspection schema file $specFile is not referenced by any new GraphQL scenario. " +
                        "Referenced spec files: $referencedSpecFiles"
                )
            }
        }
    }

    /**
     * **Feature: graphql-eval-test-expansion, Property 2: GraphQL scenario metadata validity**
     * **Validates: Requirements 2.2**
     */
    @Nested
    inner class MetadataValidity {

        @ParameterizedTest
        @MethodSource("nl.vintik.mocknest.infra.aws.generation.ai.eval.GraphqlEvalDatasetValidationTest#graphqlScenarios")
        fun `Given GraphQL scenario When checking metadata Then protocol is GraphQL and format is GRAPHQL`(scenario: JsonNode) {
            val input = scenario["input"].asText()
            val metadata = scenario["metadata"]

            assertEquals(
                "GraphQL", metadata["protocol"].asText(),
                "Scenario '$input' should have protocol GraphQL"
            )
            assertEquals(
                "GRAPHQL", metadata["format"].asText(),
                "Scenario '$input' should have format GRAPHQL"
            )
        }

        @ParameterizedTest
        @MethodSource("nl.vintik.mocknest.infra.aws.generation.ai.eval.GraphqlEvalDatasetValidationTest#graphqlScenarios")
        fun `Given GraphQL scenario When checking specFile Then it resolves to an existing classpath resource`(scenario: JsonNode) {
            val input = scenario["input"].asText()
            val specFile = scenario["metadata"]["specFile"].asText()

            assertTrue(
                specFile.isNotBlank(),
                "Scenario '$input' should have a non-blank specFile"
            )

            val resource = javaClass.getResourceAsStream("/$specFile")
            assertNotNull(
                resource,
                "Scenario '$input' references specFile '$specFile' which does not exist on the classpath"
            )
            resource.close()
        }
    }

    /**
     * **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6**
     */
    @Nested
    inner class ComplexityLevelDistribution {

        @Test
        fun `Given GraphQL scenarios When classifying by complexity Then at least 3 Basic scenarios exist`() {
            val basicScenarios = newGraphqlScenarios.filter {
                it["input"].asText().contains("basic")
            }
            assertTrue(
                basicScenarios.size >= 3,
                "Expected at least 3 Basic GraphQL scenarios, but found ${basicScenarios.size}: " +
                    basicScenarios.map { it["input"].asText() }
            )
        }

        @Test
        fun `Given GraphQL scenarios When classifying by complexity Then at least 1 Filtered scenario exists`() {
            val filteredScenarios = newGraphqlScenarios.filter {
                it["input"].asText().contains("filtered")
            }
            assertTrue(
                filteredScenarios.isNotEmpty(),
                "Expected at least 1 Filtered GraphQL scenario, but found none"
            )
        }

        @Test
        fun `Given GraphQL scenarios When classifying by complexity Then at least 1 Error scenario exists`() {
            val errorScenarios = newGraphqlScenarios.filter {
                val input = it["input"].asText()
                input.contains("error") || input.contains("notfound")
            }
            assertTrue(
                errorScenarios.isNotEmpty(),
                "Expected at least 1 Error GraphQL scenario, but found none"
            )
        }

        @Test
        fun `Given GraphQL scenarios When classifying by complexity Then at least 1 Realistic scenario exists`() {
            val realisticScenarios = newGraphqlScenarios.filter {
                it["input"].asText().contains("realistic")
            }
            assertTrue(
                realisticScenarios.isNotEmpty(),
                "Expected at least 1 Realistic GraphQL scenario, but found none"
            )
        }

        @Test
        fun `Given GraphQL scenarios When classifying by complexity Then at least 1 Consistency scenario exists`() {
            val consistencyScenarios = newGraphqlScenarios.filter {
                it["input"].asText().contains("consistency")
            }
            assertTrue(
                consistencyScenarios.isNotEmpty(),
                "Expected at least 1 Consistency GraphQL scenario, but found none"
            )
        }

        @Test
        fun `Given GraphQL scenarios When classifying by complexity Then at least 1 Edge Case scenario exists`() {
            val edgeCaseScenarios = newGraphqlScenarios.filter {
                it["input"].asText().contains("edge")
            }
            assertTrue(
                edgeCaseScenarios.isNotEmpty(),
                "Expected at least 1 Edge Case GraphQL scenario, but found none"
            )
        }
    }

    /**
     * **Feature: graphql-eval-test-expansion, Property 3: Semantic check quality**
     * **Validates: Requirements 4.1, 4.2**
     */
    @Nested
    inner class SemanticCheckQuality {

        @ParameterizedTest
        @MethodSource("nl.vintik.mocknest.infra.aws.generation.ai.eval.GraphqlEvalDatasetValidationTest#graphqlScenarios")
        fun `Given GraphQL scenario When checking semanticCheck Then it contains concrete verifiable criteria`(scenario: JsonNode) {
            val input = scenario["input"].asText()
            val semanticCheck = scenario["metadata"]["semanticCheck"].asText()

            // Must contain at least one concrete verifiable element:
            // operation name (camelCase GraphQL operation), field name, enum value, or specific value
            val hasOperationName = Regex("operationName\\s+'\\w+'")
                .containsMatchIn(semanticCheck)
            val hasGraphqlField = Regex("\\bdata\\.\\w+")
                .containsMatchIn(semanticCheck)
            val hasFieldName = Regex("\\b(id|name|title|status|price|email|priority|category|totalAmount|errors)\\b")
                .containsMatchIn(semanticCheck)
            val hasEnumValue = Regex("\\b(PENDING|SHIPPED|DELIVERED|CANCELLED|ELECTRONICS|CLOTHING|FOOD|HOME|SPORTS|TODO|IN_PROGRESS|IN_REVIEW|DONE|LOW|MEDIUM|HIGH|CRITICAL)\\b")
                .containsMatchIn(semanticCheck)
            val hasConcreteValue = Regex("\\b\\d+\\b").containsMatchIn(semanticCheck)

            assertTrue(
                hasOperationName || hasGraphqlField || hasFieldName || hasEnumValue || hasConcreteValue,
                "Scenario '$input' semanticCheck must contain at least one concrete verifiable element " +
                    "(operation name, GraphQL field path, field name, enum value, or concrete value)"
            )
        }

        @ParameterizedTest
        @MethodSource("nl.vintik.mocknest.infra.aws.generation.ai.eval.GraphqlEvalDatasetValidationTest#graphqlScenarios")
        fun `Given GraphQL scenario When checking semanticCheck Then it does not contain vague phrases`(scenario: JsonNode) {
            val input = scenario["input"].asText()
            val semanticCheck = scenario["metadata"]["semanticCheck"].asText().lowercase()

            vaguePhrases.forEach { phrase ->
                assertTrue(
                    !semanticCheck.contains(phrase),
                    "Scenario '$input' semanticCheck contains vague phrase '$phrase'"
                )
            }
        }
    }

    /**
     * **Feature: graphql-eval-test-expansion, Property 4: Semantic check CONTEXT preamble**
     * **Validates: Requirements 4.6**
     */
    @Nested
    inner class SemanticCheckContextPreamble {

        @ParameterizedTest
        @MethodSource("nl.vintik.mocknest.infra.aws.generation.ai.eval.GraphqlEvalDatasetValidationTest#newGraphqlScenarios")
        fun `Given new GraphQL scenario When checking semanticCheck Then it contains CONTEXT preamble`(scenario: JsonNode) {
            val input = scenario["input"].asText()
            val semanticCheck = scenario["metadata"]["semanticCheck"].asText()

            assertTrue(
                semanticCheck.startsWith("CONTEXT:"),
                "Scenario '$input' semanticCheck must start with a CONTEXT preamble, " +
                    "but starts with: '${semanticCheck.take(50)}...'"
            )
        }
    }

    /**
     * **Validates: Requirements 2.4, 7.1**
     */
    @Nested
    inner class ExistingGraphqlScenariosUnchanged {

        @Test
        fun `Given dataset When finding pokemon-pikachu scenario Then it exists and is unchanged`() {
            val pokemonScenario = graphqlScenarios.find {
                it["input"].asText() == "graphql-pokemon-pikachu"
            }
            assertNotNull(pokemonScenario, "Pokemon pikachu GraphQL scenario must exist in the dataset")

            val metadata = pokemonScenario["metadata"]
            assertEquals("GraphQL", metadata["protocol"].asText())
            assertEquals("eval/pokemon-graphql-introspection.json", metadata["specFile"].asText())
            assertEquals("GRAPHQL", metadata["format"].asText())
            assertEquals("pokemon-eval", metadata["namespace"].asText())
            assertEquals(
                "Generate a mock for the pokemon query returning Pikachu with id 25, name pikachu, " +
                    "height 4, weight 60, type electric, and at least 2 abilities (static and lightning-rod). " +
                    "Include all required fields from the schema including types, abilities, and moves arrays.",
                metadata["description"].asText()
            )
            assertEquals(
                "Strictly verify: 1) There must be a pokemon query mock. 2) The response must contain " +
                    "data.pokemon with name pikachu and id 25. 3) The types array must contain at least one " +
                    "entry with type.name electric. 4) The abilities array must have at least 2 entries. " +
                    "Score 0 if pikachu is missing or id is wrong.",
                metadata["semanticCheck"].asText()
            )
        }

        @Test
        fun `Given dataset When finding books-two-books scenario Then it exists and is unchanged`() {
            val booksScenario = graphqlScenarios.find {
                it["input"].asText() == "graphql-books-two-books"
            }
            assertNotNull(booksScenario, "Books two-books GraphQL scenario must exist in the dataset")

            val metadata = booksScenario["metadata"]
            assertEquals("GraphQL", metadata["protocol"].asText())
            assertEquals("eval/books-graphql-introspection.json", metadata["specFile"].asText())
            assertEquals("GRAPHQL", metadata["format"].asText())
            assertEquals("books-eval", metadata["namespace"].asText())
            assertEquals(
                "Generate a mock for the books query returning exactly 2 books. Book 1: id 1, title " +
                    "'The Great Gatsby', author name 'F. Scott Fitzgerald', genre 'fiction'. Book 2: id 2, " +
                    "title '1984', author name 'George Orwell', genre 'dystopian'.",
                metadata["description"].asText()
            )
            assertEquals(
                "Strictly verify: 1) There must be a books query mock. 2) The response data.books must " +
                    "be an array with exactly 2 items. 3) One book must have title 'The Great Gatsby' and " +
                    "the other '1984'. Score 0 if any book is missing or titles are wrong.",
                metadata["semanticCheck"].asText()
            )
        }
    }
}
