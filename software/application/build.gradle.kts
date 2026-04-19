plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.serialization")
}

// Generate version.properties from Gradle version
tasks.processResources {
    filesMatching("**/version.properties") {
        expand("version" to project.version)
    }
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

    // WireMock - use shaded Guava, exclude external Guava
    implementation("org.wiremock:wiremock-standalone") {
        exclude(group = "com.google.guava", module = "guava")
    }

    // JSON processing
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")

    // Koog Framework for AI Agent orchestration
    implementation("ai.koog:koog-agents")

    // OpenAPI specification parsing
    implementation("io.swagger.parser.v3:swagger-parser:2.1.39")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("ai.koog:agents-test")
}