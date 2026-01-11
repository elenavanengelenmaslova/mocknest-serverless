plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    // Domain dependency
    implementation(project(":software:domain"))

    // Spring Boot (but not as executable)
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // WireMock
    implementation("org.wiremock:wiremock-standalone")

    // JSON processing
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Koog Framework for AI Agent orchestration
    implementation("ai.koog:koog-agents:0.6.0")

    // OpenAPI specification parsing
    implementation("io.swagger.parser.v3:swagger-parser:2.1.37")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}