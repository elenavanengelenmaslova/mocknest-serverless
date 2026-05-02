plugins {
    kotlin("jvm")
}

dependencies {
    // Clean architecture dependencies
    api(project(":software:domain"))
    api(project(":software:application"))

    // Kotlin AWS SDK - S3 for shared storage
    api("aws.sdk.kotlin:s3")

    // JSON processing (shared ObjectMapper)
    api("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Koin DI
    implementation("io.insert-koin:koin-core")

    // Testing
    testImplementation("io.insert-koin:koin-test-junit5")
}

tasks.test {
    useJUnitPlatform()
}
