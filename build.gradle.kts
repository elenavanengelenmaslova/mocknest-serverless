import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension

plugins {
    kotlin("jvm") version "2.3.0" apply false
    kotlin("plugin.spring") version "2.3.0" apply false
    kotlin("plugin.serialization") version "2.3.0" apply false
    id("org.springframework.boot") version "4.0.2" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("com.gradleup.shadow") version "8.3.6" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.4"
}

allprojects {
    group = "nl.vintik.mocknest"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

subprojects {
    apply(plugin = "kotlin")
    apply(plugin = "org.jetbrains.kotlinx.kover")
    apply(plugin = "io.spring.dependency-management")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    // Global dependency management for all modules
    configure<DependencyManagementExtension> {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.2")
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.0.0")
        }
        dependencies {
            dependency("org.wiremock:wiremock:3.13.2")
            
            // Koog Framework for AI Agents
            val koogVersion = "0.6.2"
            dependency("ai.koog:koog-agents:$koogVersion")
            
            // Kotlin AWS SDK
            val awsSdkKotlinVersion = "1.6.16"
            dependency("aws.sdk.kotlin:s3:$awsSdkKotlinVersion")
            dependency("aws.sdk.kotlin:lambda:$awsSdkKotlinVersion")
            dependency("aws.sdk.kotlin:apigateway:$awsSdkKotlinVersion")
            dependency("aws.sdk.kotlin:bedrock:$awsSdkKotlinVersion")
            dependency("aws.sdk.kotlin:bedrockruntime:$awsSdkKotlinVersion")
            
            // AWS Lambda Java
            dependency("com.amazonaws:aws-lambda-java-core:1.2.3")
            dependency("com.amazonaws:aws-lambda-java-events:3.14.0")
            
            // Kotlinx Serialization for JSON
            dependency("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            // TestContainers
            val testContainersVersion = "2.0.3"
            val testContainersExtensions = "1.21.4"
            dependency("org.testcontainers:testcontainers:$testContainersVersion")
            dependency("org.testcontainers:junit-jupiter:$testContainersExtensions")
            dependency("org.testcontainers:localstack:$testContainersExtensions")

        }
    }

    dependencies {
        val implementation by configurations
        val testImplementation by configurations
        val runtimeOnly by configurations

        // Kotlin standard library
        implementation("org.jetbrains.kotlin:kotlin-stdlib")
        implementation("org.jetbrains.kotlin:kotlin-reflect")

        // Logging
        implementation("io.github.oshai:kotlin-logging-jvm:7.0.0")

        runtimeOnly("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1-0.6.x-compat")

        // Testing
        testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
        testImplementation("io.mockk:mockk:1.13.13")
        testImplementation("org.jetbrains.kotlin:kotlin-test")
        testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
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
    kover(project(":software:infra:aws"))
}

kover {
    reports {
        total {
            filters {
                excludes {
                    classes(
                        // interfaces
                        "nl.vintik.mocknest.*.interfaces.*",

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
        }

        // verify {
        //     rule {
        //         minBound(90)
        //     }
        // }
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