package nl.vintik.mocknest.domain.generation

/**
 * Compact representation of a WSDL document optimized for AI consumption.
 * Contains only service metadata, operation signatures, and referenced XSD types.
 */
data class CompactWsdl(
    val serviceName: String,
    val targetNamespace: String,
    val soapVersion: SoapVersion,
    val portTypes: List<WsdlPortType>,
    val operations: List<WsdlOperation>,
    val xsdTypes: Map<String, WsdlXsdType>,
    val serviceAddress: String? = null
) {
    init {
        require(serviceName.isNotBlank()) { "Service name cannot be blank" }
        require(targetNamespace.isNotBlank()) { "Target namespace cannot be blank" }
        require(operations.isNotEmpty()) { "WSDL must have at least one operation" }
    }

    /**
     * Pretty-print the compact WSDL as human-readable text for round-trip testing.
     */
    fun prettyPrint(): String {
        val sb = StringBuilder()
        sb.appendLine("service: $serviceName")
        sb.appendLine("targetNamespace: $targetNamespace")
        sb.appendLine("soapVersion: ${soapVersion.name}")
        sb.appendLine()
        sb.appendLine("portTypes:")
        portTypes.forEach { pt ->
            sb.appendLine("  ${pt.name}")
        }
        sb.appendLine()
        sb.appendLine("operations:")
        operations.forEach { op ->
            sb.appendLine("  ${op.name}:")
            sb.appendLine("    soapAction: ${op.soapAction}")
            sb.appendLine("    input: ${op.inputMessage}")
            sb.appendLine("    output: ${op.outputMessage}")
        }
        if (xsdTypes.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("types:")
            xsdTypes.forEach { (name, type) ->
                sb.appendLine("  $name:")
                type.fields.forEach { field ->
                    sb.appendLine("    ${field.name}: ${field.type}")
                }
            }
        }
        return sb.toString().trim()
    }
}

/**
 * SOAP protocol version with its envelope namespace and content type.
 */
enum class SoapVersion(val envelopeNamespace: String, val contentType: String) {
    SOAP_1_1(
        envelopeNamespace = "http://schemas.xmlsoap.org/soap/envelope/",
        contentType = "text/xml"
    ),
    SOAP_1_2(
        envelopeNamespace = "http://www.w3.org/2003/05/soap-envelope",
        contentType = "application/soap+xml"
    )
}

/**
 * A WSDL port type (groups related operations).
 */
data class WsdlPortType(
    val name: String
) {
    init {
        require(name.isNotBlank()) { "Port type name cannot be blank" }
    }
}

/**
 * A WSDL operation with its SOAP binding metadata.
 */
data class WsdlOperation(
    val name: String,
    val soapAction: String,
    val inputMessage: String,
    val outputMessage: String,
    val portTypeName: String
) {
    init {
        require(name.isNotBlank()) { "Operation name cannot be blank" }
        require(inputMessage.isNotBlank()) { "Input message cannot be blank" }
        require(outputMessage.isNotBlank()) { "Output message cannot be blank" }
        require(portTypeName.isNotBlank()) { "Port type name cannot be blank" }
    }
}

/**
 * An XSD complex type referenced by WSDL message parts.
 */
data class WsdlXsdType(
    val name: String,
    val fields: List<WsdlXsdField>
) {
    init {
        require(name.isNotBlank()) { "XSD type name cannot be blank" }
    }
}

/**
 * A field within an XSD complex type.
 */
data class WsdlXsdField(
    val name: String,
    val type: String
) {
    init {
        require(name.isNotBlank()) { "Field name cannot be blank" }
        require(type.isNotBlank()) { "Field type cannot be blank" }
    }
}
