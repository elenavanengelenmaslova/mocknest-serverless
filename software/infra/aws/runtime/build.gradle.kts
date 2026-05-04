plugins {
    kotlin("jvm")
}

dependencies {
    // Clean architecture dependencies
    api(project(":software:domain"))
    api(project(":software:application"))
    api(project(":software:infra:aws:core"))

    api("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Koin DI
    implementation("io.insert-koin:koin-core")

    // Kotlin AWS SDK - S3 for storage
    api("aws.sdk.kotlin:s3")

    // Kotlin AWS SDK - SQS for async webhook dispatch
    api("aws.sdk.kotlin:sqs")
    
    // HTTP client for AWS SDK
    val smithyKotlinVersion = "1.6.13"
    api("aws.smithy.kotlin:http-client-engine-okhttp:${smithyKotlinVersion}")
    api("aws.smithy.kotlin:aws-signing-default:${smithyKotlinVersion}")
    api("com.squareup.okhttp3:okhttp:5.3.2")

    // Coroutines
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    // AWS Lambda runtime
    implementation("com.amazonaws:aws-lambda-java-core")
    implementation("com.amazonaws:aws-lambda-java-events")

    // CRaC API for SnapStart lifecycle hooks (beforeCheckpoint / afterRestore)
    implementation("org.crac:crac:1.5.0")

    // Testing with LocalStack
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:localstack")
    testImplementation("io.insert-koin:koin-test-junit5")
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
