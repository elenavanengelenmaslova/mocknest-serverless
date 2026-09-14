plugins {
    kotlin("jvm")
}

dependencies {
    // Clean architecture dependencies
    api(project(":software:domain"))
    api(project(":software:application"))

    // HTTP client for WSDL fetching — versions from okhttp-bom (root build.gradle.kts)
    api("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.okhttp3:okhttp-coroutines")

    // Coroutines
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core")
}

tasks.test {
    useJUnitPlatform()
}
