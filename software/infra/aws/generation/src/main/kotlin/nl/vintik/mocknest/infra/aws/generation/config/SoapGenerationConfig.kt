package nl.vintik.mocknest.infra.aws.generation.config

import nl.vintik.mocknest.application.generation.interfaces.SpecificationParserInterface
import nl.vintik.mocknest.application.generation.parsers.WsdlSpecificationParser
import nl.vintik.mocknest.application.generation.validators.SoapMockValidator
import nl.vintik.mocknest.application.generation.wsdl.WsdlContentFetcherInterface
import nl.vintik.mocknest.application.generation.wsdl.WsdlParser
import nl.vintik.mocknest.application.generation.wsdl.WsdlParserInterface
import nl.vintik.mocknest.application.generation.wsdl.WsdlSchemaReducer
import nl.vintik.mocknest.application.generation.wsdl.WsdlSchemaReducerInterface
import nl.vintik.mocknest.infra.generation.wsdl.WsdlContentFetcher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring configuration for SOAP/WSDL-specific mock generation components.
 * Registers all SOAP infrastructure beans following clean architecture boundaries.
 *
 * [WsdlSpecificationParser] is automatically registered in [nl.vintik.mocknest.application.generation.parsers.CompositeSpecificationParserImpl]
 * via the existing `List<SpecificationParserInterface>` injection.
 *
 * [SoapMockValidator] is automatically registered in [nl.vintik.mocknest.application.generation.validators.CompositeMockValidator]
 * via the existing `List<MockValidatorInterface>` injection.
 */
@Configuration
class SoapGenerationConfig {

    /**
     * WSDL content fetcher — infrastructure layer implementation using OkHttp.
     * Validates URL safety before any network call (SSRF protection).
     */
    @Bean
    fun wsdlContentFetcher(): WsdlContentFetcherInterface {
        return WsdlContentFetcher()
    }

    /**
     * WSDL parser — application layer, uses JDK DocumentBuilder (no new XML deps).
     */
    @Bean
    fun wsdlParser(): WsdlParserInterface {
        return WsdlParser()
    }

    /**
     * WSDL schema reducer — application layer, converts ParsedWsdl to CompactWsdl.
     */
    @Bean
    fun wsdlSchemaReducer(): WsdlSchemaReducerInterface {
        return WsdlSchemaReducer()
    }

    /**
     * WSDL specification parser — auto-registered via List<SpecificationParserInterface> injection.
     */
    @Bean
    fun wsdlSpecificationParser(
        contentFetcher: WsdlContentFetcherInterface,
        wsdlParser: WsdlParserInterface,
        schemaReducer: WsdlSchemaReducerInterface
    ): SpecificationParserInterface {
        return WsdlSpecificationParser(contentFetcher, wsdlParser, schemaReducer)
    }

    /**
     * SOAP mock validator — auto-registered via List<MockValidatorInterface> injection.
     */
    @Bean
    fun soapMockValidator(): SoapMockValidator {
        return SoapMockValidator()
    }
}
