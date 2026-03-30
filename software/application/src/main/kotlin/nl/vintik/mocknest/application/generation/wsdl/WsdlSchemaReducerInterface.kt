package nl.vintik.mocknest.application.generation.wsdl

import nl.vintik.mocknest.domain.generation.CompactWsdl

interface WsdlSchemaReducerInterface {
    /**
     * Reduce a parsed WSDL to compact form suitable for AI consumption.
     * @param parsedWsdl Parsed WSDL structure
     * @return Compact WSDL representation
     */
    fun reduce(parsedWsdl: ParsedWsdl): CompactWsdl
}
