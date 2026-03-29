package nl.vintik.mocknest.application.generation.wsdl

import nl.vintik.mocknest.domain.generation.SoapVersion

/**
 * Full intermediate WSDL structure produced by the parser before reduction.
 * Internal to the application layer — not exposed outside.
 */
data class ParsedWsdl(
    val serviceName: String,
    val targetNamespace: String,
    val soapVersion: SoapVersion,
    val portTypes: List<ParsedPortType>,
    val operations: List<ParsedOperation>,
    val messages: Map<String, ParsedMessage>,
    val xsdTypes: Map<String, ParsedXsdType>,
    val servicePortAddresses: List<String>,
    val bindingDetails: List<ParsedBindingDetail>,
    val warnings: List<String>
)

/**
 * A WSDL port type with its operation names.
 */
data class ParsedPortType(
    val name: String,
    val operationNames: List<String>
)

/**
 * A WSDL operation with its binding metadata.
 */
data class ParsedOperation(
    val name: String,
    val portTypeName: String,
    val soapAction: String,
    val inputMessageName: String,
    val outputMessageName: String
)

/**
 * A WSDL message with its element reference.
 */
data class ParsedMessage(
    val name: String,
    val elementName: String
)

/**
 * An XSD complex type with its fields.
 */
data class ParsedXsdType(
    val name: String,
    val fields: List<ParsedXsdField>
)

/**
 * A field within an XSD complex type.
 */
data class ParsedXsdField(
    val name: String,
    val type: String
)

/**
 * Binding detail for a WSDL binding element (excluded from compact form).
 */
data class ParsedBindingDetail(
    val name: String,
    val portTypeName: String,
    val soapVersion: SoapVersion
)
