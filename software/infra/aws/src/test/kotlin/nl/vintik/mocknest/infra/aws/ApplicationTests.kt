package nl.vintik.mocknest.infra.aws

import nl.vintik.mocknest.infra.aws.config.AwsLocalStackTestConfiguration
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.function.context.FunctionCatalog
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "spring.main.allow-bean-definition-overriding=true",
        "spring.cloud.function.web.export.enabled=false",
        "storage.bucket.name=test-bucket"
    ]
)
@ActiveProfiles("test")
@Import(AwsLocalStackTestConfiguration::class)
@Testcontainers
class ApplicationTests {

    @Autowired
    private lateinit var functionCatalog: FunctionCatalog

    @Test
    fun contextLoads() {
        // This test verifies that the Spring Boot application context loads successfully
        // with LocalStack TestContainers configuration
        assertNotNull(functionCatalog, "FunctionCatalog should be available in the context")
    }
}