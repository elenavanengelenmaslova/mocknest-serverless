package nl.vintik.mocknest.application.generation.wsdl

interface WsdlContentFetcherInterface {
    /**
     * Fetch WSDL XML content from a remote URL.
     * @param url The WSDL endpoint URL
     * @return Raw WSDL XML as a string
     * @throws nl.vintik.mocknest.domain.generation.WsdlFetchException on failure
     */
    suspend fun fetch(url: String): String
}
