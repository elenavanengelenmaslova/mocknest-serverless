package nl.vintik.mocknest.infra.aws.generation.ai.eval

import nl.vintik.mocknest.application.generation.wsdl.WsdlParser
import nl.vintik.mocknest.application.generation.wsdl.WsdlSchemaReducer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Structural validation tests for WSDL specification files used in the SOAP eval suite.
 *
 * Validates: Requirements 1.3, 1.4, 1.5, 1.6, 1.7, 6.4, 6.5
 * Correctness Properties: 1, 5, 6
 */
@Tag("soap-eval-wsdl")
class SoapWsdlSpecValidationTest {

    private val wsdlParser = WsdlParser()
    private val wsdlSchemaReducer = WsdlSchemaReducer()

    private val allWsdlFiles = listOf(
        "calculator-soap12.wsdl",
        "banking-service-soap12.wsdl",
        "inventory-warehouse-soap12.wsdl",
        "notification-messaging-soap12.wsdl"
    )

    private fun loadWsdlContent(wsdlFile: String): String {
        val stream = javaClass.getResourceAsStream("/eval/$wsdlFile")
        assertNotNull(stream, "WSDL file not found on classpath: eval/$wsdlFile")
        return stream.bufferedReader().readText()
    }

    /**
     * **Validates: Requirements 1.6, 1.7**
     * **Feature: soap-eval-test-expansion, Property 1: WSDL parsing and reduction validity**
     */
    @Nested
    inner class WsdlParsingAndReduction {

        @ParameterizedTest
        @ValueSource(
            strings = [
                "calculator-soap12.wsdl",
                "banking-service-soap12.wsdl",
                "inventory-warehouse-soap12.wsdl",
                "notification-messaging-soap12.wsdl"
            ]
        )
        fun `Given WSDL file When parsing Then should succeed without exception`(wsdlFile: String) {
            val wsdlXml = loadWsdlContent(wsdlFile)
            val parsedWsdl = wsdlParser.parse(wsdlXml)

            assertNotNull(parsedWsdl)
            assertTrue(parsedWsdl.targetNamespace.isNotBlank(), "targetNamespace should not be blank for $wsdlFile")
            assertTrue(parsedWsdl.operations.isNotEmpty(), "operations should not be empty for $wsdlFile")
        }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "calculator-soap12.wsdl",
                "banking-service-soap12.wsdl",
                "inventory-warehouse-soap12.wsdl",
                "notification-messaging-soap12.wsdl"
            ]
        )
        fun `Given WSDL file When reducing Then CompactWsdl should have non-empty operations`(wsdlFile: String) {
            val wsdlXml = loadWsdlContent(wsdlFile)
            val parsedWsdl = wsdlParser.parse(wsdlXml)
            val compactWsdl = wsdlSchemaReducer.reduce(parsedWsdl)

            assertNotNull(compactWsdl)
            assertTrue(
                compactWsdl.operations.isNotEmpty(),
                "CompactWsdl operations should not be empty for $wsdlFile"
            )
            assertTrue(
                compactWsdl.targetNamespace.isNotBlank(),
                "CompactWsdl targetNamespace should not be blank for $wsdlFile"
            )
        }
    }

    /**
     * **Validates: Requirements 1.3, 1.4, 1.5**
     */
    @Nested
    inner class WsdlStructuralRequirements {

        private val builtInXsdTypes = setOf(
            "string", "int", "integer", "long", "short", "byte",
            "float", "double", "decimal", "boolean",
            "date", "dateTime", "time",
            "anyURI", "base64Binary", "hexBinary", "anyType",
            "token", "normalizedString",
            "positiveInteger", "negativeInteger", "nonNegativeInteger", "nonPositiveInteger",
            "unsignedLong", "unsignedInt", "unsignedShort", "unsignedByte"
        )

        @Test
        fun `Given all WSDLs When checking Then at least one has nested complex types`() {
            val hasNestedTypes = allWsdlFiles.any { wsdlFile ->
                val wsdlXml = loadWsdlContent(wsdlFile)
                val parsedWsdl = wsdlParser.parse(wsdlXml)
                val typeNames = parsedWsdl.xsdTypes.keys

                parsedWsdl.xsdTypes.values.any { xsdType ->
                    xsdType.fields.any { field ->
                        field.type !in builtInXsdTypes && field.type in typeNames
                    }
                }
            }

            assertTrue(
                hasNestedTypes,
                "At least one WSDL should have nested complex types (a type field referencing another complex type)"
            )
        }

        @Test
        fun `Given all WSDLs When checking Then at least one has 5 or more operations`() {
            val hasLargeOperationCount = allWsdlFiles.any { wsdlFile ->
                val wsdlXml = loadWsdlContent(wsdlFile)
                val parsedWsdl = wsdlParser.parse(wsdlXml)
                parsedWsdl.operations.size >= 5
            }

            assertTrue(
                hasLargeOperationCount,
                "At least one WSDL should have 5 or more operations"
            )
        }

        @Test
        fun `Given all WSDLs When checking Then at least one has enumeration restrictions`() {
            val hasEnumerations = allWsdlFiles.any { wsdlFile ->
                val wsdlXml = loadWsdlContent(wsdlFile)
                wsdlXml.contains("<xsd:simpleType") &&
                    wsdlXml.contains("<xsd:restriction") &&
                    wsdlXml.contains("<xsd:enumeration")
            }

            assertTrue(
                hasEnumerations,
                "At least one WSDL should have enumeration restrictions (xsd:simpleType with xsd:restriction and xsd:enumeration)"
            )
        }
    }

    /**
     * **Validates: Requirements 6.4, 6.5**
     * **Feature: soap-eval-test-expansion, Property 5: Unique targetNamespace per WSDL**
     * **Feature: soap-eval-test-expansion, Property 6: WSDL service address presence**
     */
    @Nested
    inner class WsdlNamespaceAndAddressUniqueness {

        @Test
        fun `Given all WSDLs When checking targetNamespaces Then all are unique`() {
            val namespaces = allWsdlFiles.map { wsdlFile ->
                val wsdlXml = loadWsdlContent(wsdlFile)
                val parsedWsdl = wsdlParser.parse(wsdlXml)
                parsedWsdl.targetNamespace
            }

            assertEquals(
                namespaces.size,
                namespaces.toSet().size,
                "All WSDL targetNamespaces should be unique, but found duplicates: ${namespaces.groupBy { it }.filter { it.value.size > 1 }.keys}"
            )
        }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "calculator-soap12.wsdl",
                "banking-service-soap12.wsdl",
                "inventory-warehouse-soap12.wsdl",
                "notification-messaging-soap12.wsdl"
            ]
        )
        fun `Given WSDL file When checking service address Then should be non-empty`(wsdlFile: String) {
            val wsdlXml = loadWsdlContent(wsdlFile)
            val parsedWsdl = wsdlParser.parse(wsdlXml)

            assertTrue(
                parsedWsdl.servicePortAddresses.isNotEmpty(),
                "WSDL $wsdlFile should have at least one service port address"
            )
            parsedWsdl.servicePortAddresses.forEach { address ->
                assertTrue(
                    address.isNotBlank(),
                    "Service port address should not be blank for $wsdlFile"
                )
            }
        }
    }
}
