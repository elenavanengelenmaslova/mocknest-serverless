package nl.vintik.mocknest.application.generation.wsdl

import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.domain.generation.SoapVersion
import nl.vintik.mocknest.domain.generation.WsdlParsingException
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

private val logger = KotlinLogging.logger {}

private const val WSDL_11_NS = "http://schemas.xmlsoap.org/wsdl/"
private const val SOAP_11_BINDING_NS = "http://schemas.xmlsoap.org/wsdl/soap/"
private const val SOAP_12_BINDING_NS = "http://schemas.xmlsoap.org/wsdl/soap12/"

class WsdlParser : WsdlParserInterface {

    override fun parse(wsdlXml: String): ParsedWsdl {
        val document = runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                // Prevent XXE attacks
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                isXIncludeAware = false
                isExpandEntityReferences = false
            }
            val builder = factory.newDocumentBuilder()
            builder.parse(InputSource(StringReader(wsdlXml)))
        }.getOrElse { e ->
            val msg = when (e) {
                is SAXParseException -> "Malformed XML at line ${e.lineNumber}: ${e.message}"
                else -> "Malformed XML: ${e.message}"
            }
            throw WsdlParsingException(msg, e)
        }

        // Step 2: Verify root element is definitions
        val root = document.documentElement
        if (root.localName != "definitions" || root.namespaceURI != WSDL_11_NS) {
            throw WsdlParsingException("Not a valid WSDL 1.1 document: missing definitions element")
        }

        // Step 3: Extract targetNamespace
        val targetNamespace = root.getAttribute("targetNamespace")
            .takeIf { it.isNotBlank() }
            ?: throw WsdlParsingException("WSDL missing required targetNamespace attribute")

        // Step 4: Extract service name
        val serviceName = getElementsByLocalName(document.documentElement, "service")
            .firstOrNull()
            ?.getAttribute("name")
            ?.takeIf { it.isNotBlank() }
            ?: targetNamespace

        // Step 5: Detect SOAP version from binding elements
        val (soapVersion, warnings) = detectSoapVersion(document.documentElement)

        // Step 6: Extract portType elements
        val portTypes = extractPortTypes(document.documentElement)

        // Step 7: Extract message elements
        val messages = extractMessages(document.documentElement)

        // Step 8: Extract binding SOAPAction values
        val soapActions = extractSoapActions(document.documentElement)

        // Step 9: Build operations from portTypes + soapActions + messages
        val operations = buildOperations(portTypes, soapActions, messages)

        // Step 10: Extract inline XSD types
        val xsdTypes = extractXsdTypes(document.documentElement)

        // Step 11: Extract service port addresses
        val servicePortAddresses = extractServicePortAddresses(document.documentElement)

        // Step 12: Extract binding details
        val bindingDetails = extractBindingDetails(document.documentElement, soapVersion)

        // Step 13: Build operation bindings map
        val operationBindings = buildOperationBindings(operations, bindingDetails)

        // Step 14: Validate at least one operation found
        if (operations.isEmpty()) {
            throw WsdlParsingException("WSDL contains no operations")
        }

        logger.info {
            "WSDL parsed: service=$serviceName, portTypes=${portTypes.size}, " +
                "operations=${operations.size}, messages=${messages.size}, xsdTypes=${xsdTypes.size}"
        }

        return ParsedWsdl(
            serviceName = serviceName,
            targetNamespace = targetNamespace,
            soapVersion = soapVersion,
            portTypes = portTypes.map { (name, ops) -> ParsedPortType(name, ops.map { it.name }) },
            operations = operations,
            messages = messages,
            xsdTypes = xsdTypes,
            servicePortAddresses = servicePortAddresses,
            bindingDetails = bindingDetails,
            operationBindings = operationBindings,
            warnings = warnings
        )
    }

    private fun detectSoapVersion(root: Element): Pair<SoapVersion, List<String>> {
        val warnings = mutableListOf<String>()
        var hasSoap11 = false
        var hasSoap12 = false

        val bindingElements = getElementsByLocalName(root, "binding")
        for (binding in bindingElements) {
            // Check child elements for SOAP namespace attributes
            val children = binding.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child.nodeType == Node.ELEMENT_NODE) {
                    when (child.namespaceURI) {
                        SOAP_11_BINDING_NS -> hasSoap11 = true
                        SOAP_12_BINDING_NS -> hasSoap12 = true
                    }
                }
            }
        }

        return when {
            hasSoap12 -> {
                if (hasSoap11) {
                    warnings.add("WSDL contains both SOAP 1.1 and SOAP 1.2 bindings; selecting SOAP 1.2 only")
                    logger.info { "Mixed SOAP versions detected; selecting SOAP 1.2, ignoring SOAP 1.1 bindings" }
                }
                Pair(SoapVersion.SOAP_1_2, warnings)
            }
            hasSoap11 -> {
                throw WsdlParsingException("Only SOAP 1.2 is supported")
            }
            else -> {
                throw WsdlParsingException(
                    "No SOAP binding namespace found; non-SOAP WSDL bindings are not supported"
                )
            }
        }
    }

    private data class PortTypeData(val name: String, val operations: List<OperationData>)
    private data class OperationData(
        val name: String,
        val inputMessageName: String,
        val outputMessageName: String
    )

    private fun extractPortTypes(root: Element): List<PortTypeData> {
        return getElementsByLocalName(root, "portType").map { portTypeEl ->
            val ptName = portTypeEl.getAttribute("name")
            val ops = getElementsByLocalName(portTypeEl, "operation").map { opEl ->
                val opName = opEl.getAttribute("name")
                val inputMsg = getElementsByLocalName(opEl, "input").firstOrNull()
                    ?.getAttribute("message")?.stripPrefix() ?: ""
                val outputMsg = getElementsByLocalName(opEl, "output").firstOrNull()
                    ?.getAttribute("message")?.stripPrefix() ?: ""
                OperationData(opName, inputMsg, outputMsg)
            }
            PortTypeData(ptName, ops)
        }
    }

    private fun extractMessages(root: Element): Map<String, ParsedMessage> {
        return getElementsByLocalName(root, "message").associate { msgEl ->
            val msgName = msgEl.getAttribute("name")
            val part = getElementsByLocalName(msgEl, "part").firstOrNull()
            val refName = part?.getAttribute("element")?.stripPrefix()?.takeIf { it.isNotBlank() }
                ?: part?.getAttribute("type")?.stripPrefix()?.takeIf { it.isNotBlank() }
                ?: ""
            msgName to ParsedMessage(name = msgName, elementName = refName)
        }
    }

    private fun extractSoapActions(root: Element): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val bindingElements = getElementsByLocalName(root, "binding")
        for (binding in bindingElements) {
            // Only extract from SOAP 1.2 bindings
            val isSoap12 = binding.childNodes.toList()
                .filterIsInstance<Element>()
                .any { it.namespaceURI == SOAP_12_BINDING_NS }
            if (!isSoap12) continue

            val portTypeName = binding.getAttribute("type").stripPrefix()
            val bindingOps = getElementsByLocalName(binding, "operation")
            for (bindingOp in bindingOps) {
                val opName = bindingOp.getAttribute("name")
                // Look for soap12:operation child
                val soapOp = bindingOp.childNodes.toList()
                    .filterIsInstance<Element>()
                    .firstOrNull { it.localName == "operation" && it.namespaceURI == SOAP_12_BINDING_NS }
                val soapAction = soapOp?.getAttribute("soapAction") ?: ""
                if (opName.isNotBlank() && portTypeName.isNotBlank()) {
                    result["$portTypeName#$opName"] = soapAction
                }
            }
        }
        return result
    }

    private fun buildOperations(
        portTypes: List<PortTypeData>,
        soapActions: Map<String, String>,
        messages: Map<String, ParsedMessage>
    ): List<ParsedOperation> {
        return portTypes.flatMap { portType ->
            portType.operations.map { op ->
                val soapAction = soapActions["${portType.name}#${op.name}"] ?: ""
                val inputMsgName = messages[op.inputMessageName]?.elementName
                    ?.takeIf { it.isNotBlank() } ?: op.inputMessageName
                val outputMsgName = messages[op.outputMessageName]?.elementName
                    ?.takeIf { it.isNotBlank() } ?: op.outputMessageName
                ParsedOperation(
                    name = op.name,
                    portTypeName = portType.name,
                    soapAction = soapAction,
                    inputMessageName = inputMsgName,
                    outputMessageName = outputMsgName
                )
            }
        }
    }

    // buildOperationBindings currently returns the first matching binding by portTypeName,
    // which may be incorrect if multiple ParsedBindingDetail entries share the same portTypeName.
    // Future enhancement: disambiguate by binding name, SOAP version, or explicit matching criteria
    // to correlate ParsedBindingDetail to ParsedOperation more precisely.
    private fun buildOperationBindings(
        operations: List<ParsedOperation>,
        bindingDetails: List<ParsedBindingDetail>
    ): Map<String, ParsedBindingDetail> {
        val result = mutableMapOf<String, ParsedBindingDetail>()
        for (operation in operations) {
            val bindingKey = "${operation.portTypeName}#${operation.name}"
            val binding = bindingDetails.find { it.portTypeName == operation.portTypeName }
            if (binding != null) {
                result[bindingKey] = binding
            }
        }
        return result
    }

    private fun extractXsdTypes(root: Element): Map<String, ParsedXsdType> {
        val result = mutableMapOf<String, ParsedXsdType>()
        val typesElements = getElementsByLocalName(root, "types")
        for (typesEl in typesElements) {
            val schemas = getElementsByLocalName(typesEl, "schema")
            for (schema in schemas) {
                // Named complexType definitions
                val complexTypes = getElementsByLocalName(schema, "complexType")
                for (complexType in complexTypes) {
                    val typeName = complexType.getAttribute("name")
                    if (typeName.isBlank()) continue
                    val fields = extractXsdFields(complexType)
                    result[typeName] = ParsedXsdType(name = typeName, fields = fields)
                }

                // Top-level xsd:element declarations (document-literal / wrapped style)
                val topLevelElements = schema.childNodes.toList()
                    .filterIsInstance<Element>()
                    .filter { it.localName == "element" }
                for (element in topLevelElements) {
                    val elementName = element.getAttribute("name")
                    if (elementName.isBlank() || elementName in result) continue

                    // Inline complexType child
                    val inlineComplexType = element.childNodes.toList()
                        .filterIsInstance<Element>()
                        .firstOrNull { it.localName == "complexType" }
                    if (inlineComplexType != null) {
                        val fields = extractXsdFields(inlineComplexType)
                        result[elementName] = ParsedXsdType(name = elementName, fields = fields)
                    } else {
                        // type= attribute referencing a named complexType
                        val typeRef = element.getAttribute("type").stripPrefix().takeIf { it.isNotBlank() }
                        if (typeRef != null && typeRef in result) {
                            result[elementName] = ParsedXsdType(name = elementName, fields = result[typeRef]!!.fields)
                        }
                    }
                }
            }
        }
        return result
    }

    private fun extractXsdFields(complexType: Element): List<ParsedXsdField> {
        // Look for sequence or all child elements
        val container = complexType.childNodes.toList()
            .filterIsInstance<Element>()
            .firstOrNull { it.localName == "sequence" || it.localName == "all" }
            ?: return emptyList()

        return container.childNodes.toList()
            .filterIsInstance<Element>()
            .filter { it.localName == "element" }
            .mapNotNull { el ->
                val name = el.getAttribute("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val type = el.getAttribute("type").stripPrefix().takeIf { it.isNotBlank() } ?: "string"
                ParsedXsdField(name = name, type = type)
            }
    }

    private fun extractServicePortAddresses(root: Element): List<String> {
        val addresses = mutableListOf<String>()
        val serviceElements = getElementsByLocalName(root, "service")
        for (service in serviceElements) {
            val ports = getElementsByLocalName(service, "port")
            for (port in ports) {
                // Only extract addresses from SOAP 1.2 ports
                val addressEl = port.childNodes.toList()
                    .filterIsInstance<Element>()
                    .firstOrNull { it.localName == "address" && it.namespaceURI == SOAP_12_BINDING_NS }
                val location = addressEl?.getAttribute("location")
                if (!location.isNullOrBlank()) {
                    addresses.add(location)
                }
            }
        }
        return addresses
    }

    private fun extractBindingDetails(root: Element, defaultSoapVersion: SoapVersion): List<ParsedBindingDetail> {
        // First, build a map of binding name to service address
        val bindingToAddress = mutableMapOf<String, String>()
        val serviceElements = getElementsByLocalName(root, "service")
        for (service in serviceElements) {
            val ports = getElementsByLocalName(service, "port")
            for (port in ports) {
                val bindingRef = port.getAttribute("binding").stripPrefix()
                // Only extract addresses from SOAP 1.2 ports
                val addressEl = port.childNodes.toList()
                    .filterIsInstance<Element>()
                    .firstOrNull { it.localName == "address" && it.namespaceURI == SOAP_12_BINDING_NS }
                val location = addressEl?.getAttribute("location")
                if (!location.isNullOrBlank() && bindingRef.isNotBlank()) {
                    bindingToAddress[bindingRef] = location
                }
            }
        }

        return getElementsByLocalName(root, "binding").mapNotNull { bindingEl ->
            val name = bindingEl.getAttribute("name")
            val portTypeRef = bindingEl.getAttribute("type").stripPrefix()
            // Only include SOAP 1.2 bindings
            val isSoap12 = bindingEl.childNodes.toList().filterIsInstance<Element>()
                .any { it.namespaceURI == SOAP_12_BINDING_NS }
            if (!isSoap12) return@mapNotNull null
            val serviceAddress = bindingToAddress[name]
            ParsedBindingDetail(
                name = name,
                portTypeName = portTypeRef,
                soapVersion = SoapVersion.SOAP_1_2,
                serviceAddress = serviceAddress
            )
        }
    }

    // Helper: get all descendant elements with a given local name (non-recursive via NodeList)
    private fun getElementsByLocalName(parent: Element, localName: String): List<Element> {
        val nodeList: NodeList = parent.getElementsByTagNameNS("*", localName)
        return (0 until nodeList.length).mapNotNull { i ->
            nodeList.item(i) as? Element
        }
    }

    private fun NodeList.toList(): List<Node> = (0 until length).map { item(it) }

    private fun String.stripPrefix(): String {
        val idx = indexOf(':')
        return if (idx >= 0) substring(idx + 1) else this
    }
}
