plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlinx.kover")
}

dependencies {
    // Domain layer should have minimal dependencies
    // Only core business logic and domain models
}