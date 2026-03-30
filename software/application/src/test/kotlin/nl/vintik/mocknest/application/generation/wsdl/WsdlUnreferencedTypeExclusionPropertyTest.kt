package nl.vintik.mocknest.application.generation.wsdl

import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val logger = KotlinLogging.logger {}

/**
 * Property-6: Unreferenced XSD Type Exclusion
 *
 * For ANY WSDL document containing XSD types that are not referenced by any operation's
 * input or output message, those types must NOT appear in CompactWsdl.xsdTypes.
 *
 * Validates: Requirements 4.6
 */
@Tag("soap-wsdl-ai-generation")
@Tag("Property-6")
class WsdlUnreferencedTypeExclusionPropertyTest {

    private val parser = WsdlParser()
    private val reducer = WsdlSchemaReducer()

    private fun loadWsdl(filename: String): String =
        this::class.java.getResource("/wsdl/$filename")?.readText()
            ?: throw IllegalArgumentException("WSDL test file not found: $filename")

    /**
     * Computes the set of XSD type names reachable from operation messages via transitive field references.
     * Mirrors the logic in WsdlSchemaReducer.collectReachableTypes.
     */
    private fun computeReachableTypeNames(parsedWsdl: ParsedWsdl): Set<String> {
        val builtInTypes = setOf(
            "string", "int", "integer", "long", "short", "byte",
            "float", "double", "decimal", "boolean",
            "date", "dateTime", "time",
            "anyURI", "base64Binary", "hexBinary", "anyType",
            "token", "normalizedString",
            "positiveInteger", "negativeInteger", "nonNegativeInteger", "nonPositiveInteger",
            "unsignedLong", "unsignedInt", "unsignedShort", "unsignedByte"
        )

        val visited = mutableSetOf<String>()
        val queue = ArrayDeque(
            parsedWsdl.operations.flatMap { listOf(it.inputMessageName, it.outputMessageName) }
        )

        while (queue.isNotEmpty()) {
            val name = queue.removeFirst()
            if (name in visited) continue
            visited.add(name)

            val parsedType = parsedWsdl.xsdTypes[name] ?: continue
            for (field in parsedType.fields) {
                if (field.type !in visited && field.type !in builtInTypes) {
                    queue.add(field.type)
                }
            }
        }

        return visited.filter { parsedWsdl.xsdTypes.containsKey(it) }.toSet()
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "unreferenced-types-soap11.wsdl",
        "complex-types-soap11.wsdl",
        "nested-xsd-soap11.wsdl",
        "large-service.wsdl",
        "multi-porttype-soap11.wsdl"
    ])
    fun `Given a WSDL with XSD types When reducing to CompactWsdl Then unreferenced types are absent from xsdTypes`(filename: String) {
        val wsdlXml = loadWsdl(filename)
        val parsedWsdl = parser.parse(wsdlXml)
        val compactWsdl = reducer.reduce(parsedWsdl)

        val reachableTypeNames = computeReachableTypeNames(parsedWsdl)
        val allDefinedTypeNames = parsedWsdl.xsdTypes.keys
        val unreferencedTypeNames = allDefinedTypeNames - reachableTypeNames

        logger.info {
            "[$filename] All defined types: $allDefinedTypeNames, " +
                "reachable: $reachableTypeNames, unreferenced: $unreferencedTypeNames"
        }

        unreferencedTypeNames.forEach { typeName ->
            assertFalse(
                compactWsdl.xsdTypes.containsKey(typeName),
                "[$filename] Unreferenced XSD type '$typeName' must not appear in CompactWsdl.xsdTypes"
            )
        }
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "unreferenced-types-soap11.wsdl",
        "complex-types-soap11.wsdl",
        "nested-xsd-soap11.wsdl",
        "large-service.wsdl",
        "multi-porttype-soap11.wsdl"
    ])
    fun `Given a WSDL with XSD types When reducing to CompactWsdl Then only reachable types appear in xsdTypes`(filename: String) {
        val wsdlXml = loadWsdl(filename)
        val parsedWsdl = parser.parse(wsdlXml)
        val compactWsdl = reducer.reduce(parsedWsdl)

        val reachableTypeNames = computeReachableTypeNames(parsedWsdl)

        logger.info {
            "[$filename] Compact xsdTypes keys: ${compactWsdl.xsdTypes.keys}, reachable: $reachableTypeNames"
        }

        compactWsdl.xsdTypes.keys.forEach { typeName ->
            assertTrue(
                reachableTypeNames.contains(typeName),
                "[$filename] CompactWsdl.xsdTypes contains '$typeName' which is not reachable from any operation message"
            )
        }
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "unreferenced-types-soap11.wsdl",
        "complex-types-soap11.wsdl"
    ])
    fun `Given a WSDL with known unreferenced types When reducing to CompactWsdl Then at least one type is excluded`(filename: String) {
        val wsdlXml = loadWsdl(filename)
        val parsedWsdl = parser.parse(wsdlXml)
        val compactWsdl = reducer.reduce(parsedWsdl)

        val reachableTypeNames = computeReachableTypeNames(parsedWsdl)
        val allDefinedTypeNames = parsedWsdl.xsdTypes.keys
        val unreferencedTypeNames = allDefinedTypeNames - reachableTypeNames

        logger.info {
            "[$filename] Excluded type count: ${unreferencedTypeNames.size}, names: $unreferencedTypeNames"
        }

        assertTrue(
            unreferencedTypeNames.isNotEmpty(),
            "[$filename] Expected at least one unreferenced type to be excluded, but all types were reachable. " +
                "This test file must contain unreferenced XSD types to be meaningful."
        )

        unreferencedTypeNames.forEach { typeName ->
            assertFalse(
                compactWsdl.xsdTypes.containsKey(typeName),
                "[$filename] Unreferenced type '$typeName' must be excluded from CompactWsdl.xsdTypes"
            )
        }
    }
}
