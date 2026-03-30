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
        if (root.localName != "definitions") {
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

        // Step 13: Validate at least one operation found
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
            hasSoap11 && hasSoap12 -> {
                warnings.add("Both SOAP 1.1 and SOAP 1.2 bindings found; defaulting to SOAP_1_1")
                Pair(SoapVersion.SOAP_1_1, warnings)
            }
            hasSoap12 -> Pair(SoapVersion.SOAP_1_2, warnings)
            hasSoap11 -> Pair(SoapVersion.SOAP_1_1, warnings)
            else -> {
                warnings.add("Defaulted to SOAP_1_1: no binding namespace found")
                Pair(SoapVersion.SOAP_1_1, warnings)
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
            val elementAttr = getElementsByLocalName(msgEl, "part").firstOrNull()
                ?.getAttribute("element")?.stripPrefix() ?: ""
            msgName to ParsedMessage(name = msgName, elementName = elementAttr)
        }
    }

    private fun extractSoapActions(root: Element): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val bindingElements = getElementsByLocalName(root, "binding")
        for (binding in bindingElements) {
            val portTypeName = binding.getAttribute("type").stripPrefix()
            val bindingOps = getElementsByLocalName(binding, "operation")
            for (bindingOp in bindingOps) {
                val opName = bindingOp.getAttribute("name")
                // Look for soap:operation or soap12:operation child
                val soapOp = bindingOp.childNodes.toList()
                    .filterIsInstance<Element>()
                    .firstOrNull { it.localName == "operation" }
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

    private fun extractXsdTypes(root: Element): Map<String, ParsedXsdType> {
        val result = mutableMapOf<String, ParsedXsdType>()
        val typesElements = getElementsByLocalName(root, "types")
        for (typesEl in typesElements) {
            val schemas = getElementsByLocalName(typesEl, "schema")
            for (schema in schemas) {
                val complexTypes = getElementsByLocalName(schema, "complexType")
                for (complexType in complexTypes) {
                    val typeName = complexType.getAttribute("name")
                    if (typeName.isBlank()) continue
                    val fields = extractXsdFields(complexType)
                    result[typeName] = ParsedXsdType(name = typeName, fields = fields)
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
                val addressEl = port.childNodes.toList()
                    .filterIsInstance<Element>()
                    .firstOrNull { it.localName == "address" }
                val location = addressEl?.getAttribute("location")
                if (!location.isNullOrBlank()) {
                    addresses.add(location)
                }
            }
        }
        return addresses
    }

    private fun extractBindingDetails(root: Element, defaultSoapVersion: SoapVersion): List<ParsedBindingDetail> {
        return getElementsByLocalName(root, "binding").map { bindingEl ->
            val name = bindingEl.getAttribute("name")
            val portTypeRef = bindingEl.getAttribute("type").stripPrefix()
            // Detect per-binding SOAP version
            val bindingVersion = run {
                var soap11 = false
                var soap12 = false
                bindingEl.childNodes.toList().filterIsInstance<Element>().forEach { child ->
                    when (child.namespaceURI) {
                        SOAP_11_BINDING_NS -> soap11 = true
                        SOAP_12_BINDING_NS -> soap12 = true
                    }
                }
                when {
                    soap12 && !soap11 -> SoapVersion.SOAP_1_2
                    else -> SoapVersion.SOAP_1_1
                }
            }
            ParsedBindingDetail(name = name, portTypeName = portTypeRef, soapVersion = bindingVersion)
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
