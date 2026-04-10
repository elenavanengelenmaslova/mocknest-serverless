plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("io.spring.dependency-management")
}

dependencies {
    // Clean architecture dependencies
    api(project(":software:domain"))
    api(project(":software:application"))

    // Spring Boot - exclude embedded servers (no web starter, just core)
    api("org.springframework.boot:spring-boot-starter") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
        exclude(group = "org.apache.tomcat.embed")
    }
    api("org.springframework.boot:spring-boot-starter-validation")
    api("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Kotlin AWS SDK - S3 for storage
    api("aws.sdk.kotlin:s3")

    // Kotlin AWS SDK - SQS for async webhook dispatch
    api("aws.sdk.kotlin:sqs")
    
    // HTTP client for AWS SDK
    val smithyKotlinVersion = "1.6.9"
    api("aws.smithy.kotlin:http-client-engine-okhttp:${smithyKotlinVersion}")
    api("aws.smithy.kotlin:aws-signing-default:${smithyKotlinVersion}")
    api("com.squareup.okhttp3:okhttp:5.3.2")

    // Coroutines
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    // Spring Cloud Function for AWS Lambda
    implementation("org.springframework.cloud:spring-cloud-function-adapter-aws")
    implementation("org.springframework.cloud:spring-cloud-function-kotlin")

    // AWS Lambda runtime
    implementation("com.amazonaws:aws-lambda-java-core")
    implementation("com.amazonaws:aws-lambda-java-events")

    // Testing with LocalStack
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:localstack")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(project(":software:infra:aws:mocknest"))
    // OkHttp MockWebServer for prototype test
    testImplementation("com.squareup.okhttp3:mockwebserver:5.3.2")
    // Awaitility for async test assertions
    testImplementation("org.awaitility:awaitility-kotlin:4.3.0")
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
        
        // Exclude Reactor Netty - not needed in runtime module (generation module keeps it for patched Netty)
        exclude(group = "io.projectreactor.netty")
        
        // Exclude Kotlin compiler and reflection (use minimal reflection)
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-compiler-embeddable")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-compiler-embeddable")
        
        // Exclude XML processing we don't need
        exclude(group = "com.sun.xml.bind")
        exclude(group = "javax.xml.bind")
    }
}

tasks.test {
    useJUnitPlatform()
}
