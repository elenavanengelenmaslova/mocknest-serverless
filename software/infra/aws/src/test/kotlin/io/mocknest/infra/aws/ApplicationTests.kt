package io.mocknest.infra.aws

import io.mocknest.infra.aws.config.AwsLocalStackTestConfiguration
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "spring.main.allow-bean-definition-overriding=true",
        "spring.cloud.function.web.export.enabled=false",
        "aws.s3.bucket-name=test-bucket"
    ]
)
@ActiveProfiles("test")
@Import(AwsLocalStackTestConfiguration::class)
@Testcontainers
class ApplicationTests {

    @Test
    fun contextLoads() {
        // This test verifies that the Spring Boot application context loads successfully
        // with LocalStack TestContainers configuration
    }
}