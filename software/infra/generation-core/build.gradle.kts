plugins {
    kotlin("jvm")
}

dependencies {
    // Clean architecture dependencies
    api(project(":software:domain"))
    api(project(":software:application"))

    // HTTP client for WSDL fetching
    api("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("com.squareup.okhttp3:okhttp-coroutines:5.3.2")

    // Coroutines
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core")
}

tasks.test {
    useJUnitPlatform()
}
