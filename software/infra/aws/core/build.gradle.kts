plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    // Single shared version for the streaming-core and streaming-sse artifacts — they are
    // released together from one git tag and must stay in sync.
    val streamingVersion = "2.4.1"

    // Clean architecture dependencies
    api(project(":software:domain"))
    api(project(":software:application"))

    // Kotlin AWS SDK - S3 for shared storage
    api("aws.sdk.kotlin:s3")

    // JSON processing (shared ObjectMapper)
    api("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Kotlinx Serialization for streaming protocol
    api("org.jetbrains.kotlinx:kotlinx-serialization-json")

    // Streaming protocol library (response encoding + bounded buffer copy) and the SSE
    // padding/dribble extension. Both artifacts are released together from a single git tag,
    // so they must share one version. 2.4.1 is the earliest release that publishes
    // aws-lambda-streaming-sse to Maven Central (the sse module did not exist at 2.3.1).
    api("nl.vintik:aws-lambda-streaming-core:$streamingVersion")
    // PaddedSseWriter + PacedSseEmitter: inflate each SSE event past the Lambda streaming
    // runtime's internal buffer so small delayed chunks trickle to the client progressively.
    api("nl.vintik:aws-lambda-streaming-sse:$streamingVersion")

    // Koin DI
    implementation("io.insert-koin:koin-core")

    // Testing
    testImplementation("io.insert-koin:koin-test-junit5")
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "1g"
}
