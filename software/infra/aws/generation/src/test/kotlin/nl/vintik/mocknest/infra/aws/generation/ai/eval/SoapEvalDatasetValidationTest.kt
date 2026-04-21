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
 * Structural validation tests for the SOAP eval scenarios in the multi-protocol dataset.
 *
 * Validates: Requirements 2.1, 2.2, 2.3, 2.5, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 4.1, 4.2, 4.6, 7.1, 7.2
 * Correctness Properties: 2, 3, 4
 */
@Tag("soap-eval-wsdl")
class SoapEvalDatasetValidationTest {

    companion object {
        private val mapper = jacksonObjectMapper()

        private val dataset: JsonNode by lazy {
            val stream = SoapEvalDatasetValidationTest::class.java
                .getResourceAsStream("/eval/multi-protocol-eval-dataset.json")
            assertNotNull(stream, "multi-protocol-eval-dataset.json not found on classpath")
            mapper.readTree(stream)
        }

        private val allExamples: List<JsonNode> by lazy {
            dataset["examples"].toList()
        }

        private val soapScenarios: List<JsonNode> by lazy {
            allExamples.filter { it["metadata"]["protocol"].asText() == "SOAP" }
        }

        private val newSoapScenarios: List<JsonNode> by lazy {
            soapScenarios.filter { it["input"].asText() != "soap-calculator-all-operations" }
        }

        private val newWsdlSpecFiles = listOf(
            "eval/banking-service-soap12.wsdl",
            "eval/inventory-warehouse-soap12.wsdl",
            "eval/notification-messaging-soap12.wsdl"
        )

        private val vaguePhrases = listOf(
            "looks correct",
            "reasonable output",
            "seems right",
            "appears valid",
            "should be fine"
        )

        @JvmStatic
        fun soapScenarios(): List<JsonNode> = soapScenarios

        @JvmStatic
        fun newSoapScenarios(): List<JsonNode> = newSoapScenarios
    }

    /**
     * **Validates: Requirements 2.1, 2.3, 7.2**
     */
    @Nested
    inner class ScenarioCount {

        @Test
        fun `Given dataset When counting new SOAP scenarios Then at least 8 exist beyond calculator`() {
            assertTrue(
                newSoapScenarios.size >= 8,
                "Expected at least 8 new SOAP scenarios beyond calculator, but found ${newSoapScenarios.size}"
            )
        }

        @Test
        fun `Given dataset When checking new WSDLs Then each is referenced by at least one scenario`() {
            val referencedSpecFiles = newSoapScenarios
                .map { it["metadata"]["specFile"].asText() }
                .toSet()

            newWsdlSpecFiles.forEach { wsdlFile ->
                assertTrue(
                    wsdlFile in referencedSpecFiles,
                    "WSDL file $wsdlFile is not referenced by any new SOAP scenario. " +
                        "Referenced spec files: $referencedSpecFiles"
                )
            }
        }
    }

    /**
     * **Feature: soap-eval-test-expansion, Property 2: SOAP scenario metadata validity**
     * **Validates: Requirements 2.2**
     */
    @Nested
    inner class MetadataValidity {

        @ParameterizedTest
        @MethodSource("nl.vintik.mocknest.infra.aws.generation.ai.eval.SoapEvalDatasetValidationTest#soapScenarios")
        fun `Given SOAP scenario When checking metadata Then protocol is SOAP and format is WSDL`(scenario: JsonNode) {
            val input = scenario["input"].asText()
            val metadata = scenario["metadata"]

            assertEquals(
                "SOAP", metadata["protocol"].asText(),
                "Scenario '$input' should have protocol SOAP"
            )
            assertEquals(
                "WSDL", metadata["format"].asText(),
                "Scenario '$input' should have format WSDL"
            )
        }

        @ParameterizedTest
        @MethodSource("nl.vintik.mocknest.infra.aws.generation.ai.eval.SoapEvalDatasetValidationTest#soapScenarios")
        fun `Given SOAP scenario When checking specFile Then it resolves to an existing classpath resource`(scenario: JsonNode) {
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
        fun `Given SOAP scenarios When classifying by complexity Then at least 3 Basic scenarios exist`() {
            val basicScenarios = newSoapScenarios.filter {
                it["input"].asText().contains("basic")
            }
            assertTrue(
                basicScenarios.size >= 3,
                "Expected at least 3 Basic SOAP scenarios, but found ${basicScenarios.size}: " +
                    basicScenarios.map { it["input"].asText() }
            )
        }

        @Test
        fun `Given SOAP scenarios When classifying by complexity Then at least 1 Filtered scenario exists`() {
            val filteredScenarios = newSoapScenarios.filter {
                it["input"].asText().contains("filtered")
            }
            assertTrue(
                filteredScenarios.isNotEmpty(),
                "Expected at least 1 Filtered SOAP scenario, but found none"
            )
        }

        @Test
        fun `Given SOAP scenarios When classifying by complexity Then at least 1 Error scenario exists`() {
            val errorScenarios = newSoapScenarios.filter {
                val input = it["input"].asText()
                input.contains("error") || input.contains("fault")
            }
            assertTrue(
                errorScenarios.isNotEmpty(),
                "Expected at least 1 Error SOAP scenario, but found none"
            )
        }

        @Test
        fun `Given SOAP scenarios When classifying by complexity Then at least 1 Realistic scenario exists`() {
            val realisticScenarios = newSoapScenarios.filter {
                it["input"].asText().contains("realistic")
            }
            assertTrue(
                realisticScenarios.isNotEmpty(),
                "Expected at least 1 Realistic SOAP scenario, but found none"
            )
        }

        @Test
        fun `Given SOAP scenarios When classifying by complexity Then at least 1 Consistency scenario exists`() {
            val consistencyScenarios = newSoapScenarios.filter {
                it["input"].asText().contains("consistency")
            }
            assertTrue(
                consistencyScenarios.isNotEmpty(),
                "Expected at least 1 Consistency SOAP scenario, but found none"
            )
        }

        @Test
        fun `Given SOAP scenarios When classifying by complexity Then at least 1 Edge Case scenario exists`() {
            val edgeCaseScenarios = newSoapScenarios.filter {
                val input = it["input"].asText()
                input.contains("edge") || input.contains("xpath")
            }
            assertTrue(
                edgeCaseScenarios.isNotEmpty(),
                "Expected at least 1 Edge Case SOAP scenario, but found none"
            )
        }
    }

    /**
     * **Feature: soap-eval-test-expansion, Property 3: Semantic check quality**
     * **Validates: Requirements 4.1, 4.2**
     */
    @Nested
    inner class SemanticCheckQuality {

        @ParameterizedTest
        @MethodSource("nl.vintik.mocknest.infra.aws.generation.ai.eval.SoapEvalDatasetValidationTest#soapScenarios")
        fun `Given SOAP scenario When checking semanticCheck Then it contains concrete verifiable criteria`(scenario: JsonNode) {
            val input = scenario["input"].asText()
            val semanticCheck = scenario["metadata"]["semanticCheck"].asText()

            // Must contain at least one concrete verifiable element:
            // operation name (PascalCase word), SOAP action URL, XML element name, namespace URI,
            // or specific numeric/string values
            val hasOperationName = Regex("\\b[A-Z][a-zA-Z]{2,}\\b")
                .containsMatchIn(semanticCheck)
            val hasSoapActionUrl = semanticCheck.contains("http://example.com/")
            val hasXmlElement = Regex("\\b[a-z][a-zA-Z]*(?:Id|Name|Status|Type|Amount|Price)\\b")
                .containsMatchIn(semanticCheck)
            val hasNamespaceUri = semanticCheck.contains("namespace")
            val hasConcreteValue = Regex("\\b\\d+\\b").containsMatchIn(semanticCheck)

            assertTrue(
                hasOperationName || hasSoapActionUrl || hasXmlElement || hasNamespaceUri || hasConcreteValue,
                "Scenario '$input' semanticCheck must contain at least one concrete verifiable element " +
                    "(operation name, SOAP action URL, XML element name, namespace URI, or concrete value)"
            )
        }

        @ParameterizedTest
        @MethodSource("nl.vintik.mocknest.infra.aws.generation.ai.eval.SoapEvalDatasetValidationTest#soapScenarios")
        fun `Given SOAP scenario When checking semanticCheck Then it does not contain vague phrases`(scenario: JsonNode) {
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
     * **Feature: soap-eval-test-expansion, Property 4: Semantic check CONTEXT preamble**
     * **Validates: Requirements 4.6**
     */
    @Nested
    inner class SemanticCheckContextPreamble {

        @ParameterizedTest
        @MethodSource("nl.vintik.mocknest.infra.aws.generation.ai.eval.SoapEvalDatasetValidationTest#newSoapScenarios")
        fun `Given new SOAP scenario When checking semanticCheck Then it contains CONTEXT preamble`(scenario: JsonNode) {
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
     * **Validates: Requirements 2.5, 7.1**
     */
    @Nested
    inner class CalculatorScenarioUnchanged {

        @Test
        fun `Given dataset When finding calculator scenario Then it exists and is unchanged`() {
            val calculatorScenario = soapScenarios.find {
                it["input"].asText() == "soap-calculator-all-operations"
            }
            assertNotNull(calculatorScenario, "Calculator SOAP scenario must exist in the dataset")

            val metadata = calculatorScenario["metadata"]
            assertEquals("SOAP", metadata["protocol"].asText())
            assertEquals("eval/calculator-soap12.wsdl", metadata["specFile"].asText())
            assertEquals("WSDL", metadata["format"].asText())
            assertEquals("calculator-eval", metadata["namespace"].asText())
            assertEquals(
                "Generate mocks for all 3 operations: Add(10, 5) returning 15, Subtract(10, 5) returning 5, " +
                    "and Multiply(10, 5) returning 50. Each operation must have the correct result.",
                metadata["description"].asText()
            )
            assertEquals(
                "Strictly verify: 1) There must be exactly 3 mappings, one per operation. " +
                    "2) Add result must be 15. 3) Subtract result must be 5. 4) Multiply result must be 50. " +
                    "Score 0 if any result is wrong or any operation is missing.",
                metadata["semanticCheck"].asText()
            )
        }
    }
}
