package nl.vintik.mocknest.application.generation.wsdl

interface WsdlParserInterface {
    /**
     * Parse a WSDL 1.1 XML document.
     * @param wsdlXml Raw WSDL XML string
     * @return Parsed WSDL structure
     * @throws nl.vintik.mocknest.domain.generation.WsdlParsingException on parse failure
     */
    fun parse(wsdlXml: String): ParsedWsdl
}
