import org.gradle.api.tasks.testing.Test

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("io.spring.dependency-management")
}

dependencies {
    // Clean architecture dependencies
    api(project(":software:domain"))
    api(project(":software:application"))

    // Generic infra (non-AWS) generation utilities — includes WsdlContentFetcher
    implementation(project(":software:infra:generation-core"))

    // Security: Force patched Netty version to fix HTTP Request Smuggling vulnerability
    // CVE-2025-58056 / GHSA-fghv-69vj-qj49: Incorrect parsing of chunk extensions in HTTP/1.1 chunked encoding
    // Fixed in Netty 4.2.5.Final+
    constraints {
        implementation("io.netty:netty-codec-http:4.2.12.Final") {
            because("Fixes CVE-2025-58056: Incorrect parsing of chunk extensions in HTTP/1.1 chunked encoding")
        }
        implementation("io.netty:netty-codec-http2:4.2.12.Final") {
            because("Fixes CVE-2025-58056: Incorrect parsing of chunk extensions in HTTP/1.1 chunked encoding")
        }
        implementation("io.netty:netty-codec-http3:4.2.12.Final") {
            because("Fixes CVE-2025-58056: Incorrect parsing of chunk extensions in HTTP/1.1 chunked encoding")
        }
    }

    // Spring Boot - exclude embedded servers (no web starter, just core)
    api("org.springframework.boot:spring-boot-starter") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
        exclude(group = "org.apache.tomcat.embed")
    }
    api("org.springframework.boot:spring-boot-starter-validation")
    api("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Kotlin AWS SDK - S3 for storage and Bedrock for AI
    api("aws.sdk.kotlin:s3")
    api("aws.sdk.kotlin:bedrockruntime")
    
    // HTTP client for AWS SDK
    val smithyKotlinVersion = "1.6.9"
    api("aws.smithy.kotlin:http-client-engine-okhttp:${smithyKotlinVersion}")
    api("com.squareup.okhttp3:okhttp:5.3.2")

    // OkHttp coroutines support for GraphQL introspection client
    implementation("com.squareup.okhttp3:okhttp-coroutines:5.3.2")

    // Coroutines
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    
    // Koog Framework for AI Agent orchestration
    implementation("ai.koog:koog-agents")

    // Spring Cloud Function for AWS Lambda
    implementation("org.springframework.cloud:spring-cloud-function-adapter-aws")
    implementation("org.springframework.cloud:spring-cloud-function-kotlin")

    // Kotlin AWS SDK - Bedrock for AI features
    implementation("aws.sdk.kotlin:bedrockruntime")
    
    // Koog Framework for AI Agent orchestration
    implementation("ai.koog:koog-agents")

    // AWS Lambda runtime
    implementation("com.amazonaws:aws-lambda-java-core")
    implementation("com.amazonaws:aws-lambda-java-events")

    // Testing with LocalStack
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:localstack")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(project(":software:infra:aws:mocknest"))

    // Dokimos LLM evaluation framework (test-scope only)
    // Verified compatible with Koog 0.8.0 — dokimos-koog 0.14.2 compiles and works
    // correctly against the Koog 0.8.0 API (MultiLLMPromptExecutor, AIAgent, etc.)
    val dokimosVersion = "0.14.2"
    testImplementation("dev.dokimos:dokimos-core:$dokimosVersion")
    testImplementation("dev.dokimos:dokimos-kotlin:$dokimosVersion")
    testImplementation("dev.dokimos:dokimos-junit:$dokimosVersion")
    testImplementation("dev.dokimos:dokimos-koog:$dokimosVersion")
}

configurations {
    runtimeClasspath {
        // Exclude embedded servers - not needed in Lambda
        exclude(group = "org.apache.tomcat.embed")
        exclude(group = "io.undertow")
        
        // Exclude Jetty WebSocket and HTTP/2 - not needed for mocking
        exclude(group = "org.eclipse.jetty.websocket")
        exclude(group = "org.eclipse.jetty.http2")
        
        // Exclude duplicate Guava - WireMock has shaded version
        exclude(group = "com.google.guava", module = "guava")
        
        // Exclude unnecessary HTTP clients
        exclude(group = "org.apache.httpcomponents")
        exclude(group = "org.apache.httpcomponents.client5")
        exclude(group = "org.apache.httpcomponents.core5")
        
        // Exclude Redis client - not used
        exclude(group = "io.lettuce")
        
        // Note: io.projectreactor.netty is NOT excluded - we use patched Netty 4.2.12.Final via constraints
        
        // Exclude Kotlin compiler and reflection (use minimal reflection)
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-compiler-embeddable")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-compiler-embeddable")
        
        // Exclude XML processing we don't need
        exclude(group = "com.sun.xml.bind")
        exclude(group = "javax.xml.bind")
    }
}

tasks.test {
    useJUnitPlatform {
        excludeTags("bedrock-eval")
    }
}

tasks.register<Test>("bedrockEval") {
    description = "Run Bedrock prompt evaluation tests (requires BEDROCK_EVAL_ENABLED=true)"
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("bedrock-eval")
    }
    // Forward eval-related environment variables to the forked test JVM
    listOf("BEDROCK_EVAL_ENABLED", "BEDROCK_EVAL_ITERATIONS", "AWS_REGION").forEach { key ->
        System.getenv(key)?.let { environment(key, it) }
    }
}
