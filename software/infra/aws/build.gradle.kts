plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("io.spring.dependency-management")
}

dependencies {
    // Depend on the new modules for testing
    testImplementation(project(":software:infra:aws:core"))
    testImplementation(project(":software:infra:aws:runtime"))
    testImplementation(project(":software:infra:aws:generation"))
    
    // Spring Cloud Function for testing
    testImplementation("org.springframework.cloud:spring-cloud-function-context")
    
    // Koog and Bedrock for AI tests
    testImplementation("ai.koog:koog-agents")
    testImplementation("aws.sdk.kotlin:bedrockruntime")
    
    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:localstack")
    testImplementation("com.amazonaws:aws-lambda-java-core")
    testImplementation("com.amazonaws:aws-lambda-java-events")
}

// This module only exists for integration tests
tasks {
    jar {
        enabled = false
    }
    
    test {
        // Skip tests by default - these are integration tests that require LocalStack
        enabled = false
    }
}
