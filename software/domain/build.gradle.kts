dependencies {
    // WireMock standalone - we need DirectCallHttpServer which is only in standalone
    // But we'll exclude Jetty components we don't need in the infra layer
    api("org.wiremock:wiremock-standalone")
    
    // Kotlinx Serialization for JSON
    api("org.jetbrains.kotlinx:kotlinx-serialization-json")
}