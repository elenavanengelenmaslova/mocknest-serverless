dependencies {
    // Spring Boot core types for HTTP modeling
    api("org.springframework:spring-web") // for HttpMethod
    api("org.wiremock:wiremock-standalone") // Version managed globally
    
    // Kotlinx Serialization for JSON
    api("org.jetbrains.kotlinx:kotlinx-serialization-json")
}