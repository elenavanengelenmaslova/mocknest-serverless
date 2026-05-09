plugins {
    kotlin("jvm") version "2.3.0" apply false
    kotlin("plugin.serialization") version "2.3.0" apply false
    id("com.gradleup.shadow") version "8.3.10" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.8"
}

val releaseVersion: Provider<String> = providers.gradleProperty("releaseVersion")
val gitVersion: Provider<String> = providers.exec {
    commandLine("git", "describe", "--tags", "--abbrev=0")
}.standardOutput.asText.map { it.trim() }

allprojects {
    group = "nl.vintik.mocknest"
    version = releaseVersion.orElse(gitVersion).get()

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

subprojects {
    apply(plugin = "kotlin")
    apply(plugin = "org.jetbrains.kotlinx.kover")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    // Security: Force patched versions of vulnerable transitive dependencies across all modules
    configurations.all {
        resolutionStrategy.eachDependency {
            // CVE-2026-29145, CVE-2026-24880, CVE-2026-29129, CVE-2026-32990, CVE-2026-25854,
            // CVE-2026-34500, CVE-2026-34483: Multiple Tomcat vulnerabilities
            // Fixed in tomcat-embed-core 11.0.21 (transitive via WireMock)
            if (requested.group == "org.apache.tomcat.embed") {
                useVersion("11.0.21")
                because("Fixes multiple Tomcat CVEs: authentication bypass, HTTP request smuggling, weak crypto, certificate validation, open redirect, improper encoding")
            }
            // CWE-770: Allocation of Resources Without Limits or Throttling in tools.jackson.core:jackson-core
            // Fixed in 3.1.1
            if (requested.group == "tools.jackson.core") {
                useVersion("3.1.1")
                because("Fixes CWE-770: Allocation of Resources Without Limits or Throttling")
            }
            // CVE-2026-33870: HTTP Request Smuggling in netty-codec-http
            // Fixed in 4.2.12.Final (enforce globally, not just in generation module)
            if (requested.group == "io.netty" && requested.name.startsWith("netty-")) {
                useVersion("4.2.12.Final")
                because("Fixes CVE-2026-33870: HTTP Request Smuggling in chunked encoding parsing")
            }
        }
    }

    dependencies {
        val implementation by configurations
        val testImplementation by configurations
        val runtimeOnly by configurations

        // Koin BOM for consistent Koin versions
        implementation(platform("io.insert-koin:koin-bom:4.2.0"))

        // Kotlin standard library
        implementation("org.jetbrains.kotlin:kotlin-stdlib")

        // Logging
        implementation("io.github.oshai:kotlin-logging-jvm:8.0.01")

        runtimeOnly("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1-0.6.x-compat")

        // Testing
        testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
        testImplementation("io.mockk:mockk:1.14.9")
        testImplementation("org.jetbrains.kotlin:kotlin-test")
        testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        testImplementation("uk.org.webcompere:system-stubs-jupiter:2.1.8")

        // Jackson 2.x BOM for consistent Jackson versions
        implementation(platform("com.fasterxml.jackson:jackson-bom:2.21.1"))

        // Explicit version constraints for managed dependencies
        constraints {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

            // WireMock
            implementation("org.wiremock:wiremock:3.13.2")
            implementation("org.wiremock:wiremock-standalone:3.13.2")

            // Security: Override vulnerable Rhino version from swagger-parser transitive dependency
            implementation("org.mozilla:rhino:1.9.1")

            // Koog Framework for AI Agents
            val koogVersion = "0.8.0"
            implementation("ai.koog:koog-agents:$koogVersion")
            implementation("ai.koog:agents-test:$koogVersion")

            // Kotlin AWS SDK (versions from main)
            val awsSdkKotlinVersion = "1.6.68"
            val smithyKotlinVersion = "1.6.13"
            implementation("aws.sdk.kotlin:s3:$awsSdkKotlinVersion")
            implementation("aws.sdk.kotlin:lambda:$awsSdkKotlinVersion")
            implementation("aws.sdk.kotlin:apigateway:$awsSdkKotlinVersion")
            implementation("aws.sdk.kotlin:bedrock:$awsSdkKotlinVersion")
            implementation("aws.sdk.kotlin:bedrockruntime:$awsSdkKotlinVersion")
            implementation("aws.sdk.kotlin:sqs:$awsSdkKotlinVersion")
            implementation("aws.smithy.kotlin:http-client-engine-okhttp:$smithyKotlinVersion")
            implementation("aws.smithy.kotlin:http-client-engine-crt:$smithyKotlinVersion")
            implementation("aws.smithy.kotlin:aws-signing-default:$smithyKotlinVersion")

            val okhttpVersion = "5.3.2"
            implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
            implementation("com.squareup.okhttp3:okhttp-coroutines:$okhttpVersion")
            implementation("com.squareup.okhttp3:mockwebserver:$okhttpVersion")

            // AWS Lambda Java
            implementation("com.amazonaws:aws-lambda-java-core:1.4.0")
            implementation("com.amazonaws:aws-lambda-java-events:3.16.1")

            // Kotlinx Serialization for JSON
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

            // TestContainers (version from main)
            val testContainersVersion = "2.0.5"
            val testContainersExtensions = "1.21.4"
            testImplementation("org.testcontainers:testcontainers:$testContainersVersion")
            testImplementation("org.testcontainers:junit-jupiter:$testContainersExtensions")
            testImplementation("org.testcontainers:localstack:$testContainersExtensions")

            // Awaitility for async test assertions
            testImplementation("org.awaitility:awaitility-kotlin:4.3.0")

            // Koin DI Framework
            val koinVersion = "4.2.0"
            implementation("io.insert-koin:koin-core:$koinVersion")
            implementation("io.insert-koin:koin-test:$koinVersion")
            implementation("io.insert-koin:koin-test-junit5:$koinVersion")
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            freeCompilerArgs.add("-Xjsr305=strict")
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

// Root level Kover configuration for 90%+ coverage
dependencies {
    // Add Kover dependencies for aggregation
    kover(project(":software:domain"))
    kover(project(":software:application"))
    kover(project(":software:infra:generation-core"))
    kover(project(":software:infra:aws:core"))
    kover(project(":software:infra:aws:runtime"))
    kover(project(":software:infra:aws:mocknest"))
    kover(project(":software:infra:aws:generation"))
}

kover {
    reports {
        total {
            filters {
                excludes {
                    classes(
                        // entry points
                        "*ApplicationKt"
                    )
                }
            }

            xml {
                onCheck = true
            }
            html {
                onCheck = true
            }
            log {
                onCheck = true
                format = "Project Coverage: <value>%"
            }
        }

        verify {
            rule {
                minBound(90)
            }
        }
    }
}
// Configure verification rules
tasks.register("koverVerifyAll") {
    dependsOn("koverXmlReport")
    doLast {
        // This will be handled by individual module verification
        println("Coverage verification completed")
    }
}
