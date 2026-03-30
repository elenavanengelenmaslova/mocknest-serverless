package nl.vintik.mocknest.application.generation.wsdl

import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.domain.generation.CompactWsdl
import nl.vintik.mocknest.domain.generation.WsdlOperation
import nl.vintik.mocknest.domain.generation.WsdlPortType
import nl.vintik.mocknest.domain.generation.WsdlXsdField
import nl.vintik.mocknest.domain.generation.WsdlXsdType

private val logger = KotlinLogging.logger {}

private val BUILT_IN_XSD_TYPES = setOf(
    "string", "int", "integer", "long", "short", "byte",
    "float", "double", "decimal", "boolean",
    "date", "dateTime", "time",
    "anyURI", "base64Binary", "hexBinary", "anyType",
    "token", "normalizedString",
    "positiveInteger", "negativeInteger", "nonNegativeInteger", "nonPositiveInteger",
    "unsignedLong", "unsignedInt", "unsignedShort", "unsignedByte"
)

class WsdlSchemaReducer : WsdlSchemaReducerInterface {

    override fun reduce(parsedWsdl: ParsedWsdl): CompactWsdl {
        val portTypes = parsedWsdl.portTypes.map { WsdlPortType(name = it.name) }

        val operations = parsedWsdl.operations.map { op ->
            WsdlOperation(
                name = op.name,
                soapAction = op.soapAction,
                inputMessage = op.inputMessageName,
                outputMessage = op.outputMessageName,
                portTypeName = op.portTypeName
            )
        }

        val referencedNames = parsedWsdl.operations
            .flatMap { listOf(it.inputMessageName, it.outputMessageName) }
            .toMutableSet()

        val xsdTypes = collectReachableTypes(referencedNames, parsedWsdl)

        val compactWsdl = CompactWsdl(
            serviceName = parsedWsdl.serviceName,
            targetNamespace = parsedWsdl.targetNamespace,
            soapVersion = parsedWsdl.soapVersion,
            portTypes = portTypes,
            operations = operations,
            xsdTypes = xsdTypes,
            serviceAddress = parsedWsdl.servicePortAddresses.firstOrNull()
        )

        logger.info {
            "WSDL reduction complete: ${parsedWsdl.operations.size} operations, " +
                "${xsdTypes.size} reachable XSD types (of ${parsedWsdl.xsdTypes.size} total). " +
                "Compact size: ${compactWsdl.prettyPrint().length} chars"
        }

        return compactWsdl
    }

    private fun collectReachableTypes(
        referencedNames: Set<String>,
        parsedWsdl: ParsedWsdl
    ): Map<String, WsdlXsdType> {
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque(referencedNames)
        val result = mutableMapOf<String, WsdlXsdType>()

        while (queue.isNotEmpty()) {
            val name = queue.removeFirst()
            if (name in visited) continue
            visited.add(name)

            val parsedType = parsedWsdl.xsdTypes[name] ?: continue

            val fields = parsedType.fields.map { WsdlXsdField(name = it.name, type = it.type) }
            result[name] = WsdlXsdType(name = name, fields = fields)

            for (field in parsedType.fields) {
                if (field.type !in visited && !isBuiltInXsdType(field.type)) {
                    queue.add(field.type)
                }
            }
        }

        return result
    }

    private fun isBuiltInXsdType(type: String): Boolean = type in BUILT_IN_XSD_TYPES
}
