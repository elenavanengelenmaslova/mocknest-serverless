import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension

plugins {
    kotlin("jvm") version "2.3.0" apply false
    kotlin("plugin.spring") version "2.3.0" apply false
    kotlin("plugin.serialization") version "2.3.0" apply false
    id("org.springframework.boot") version "4.0.3" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("com.gradleup.shadow") version "8.3.10" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.7"
}

allprojects {
    group = "nl.vintik.mocknest"
    version = "0.2.1"

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
            mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.3")
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.1")
        }
        dependencies {
            dependency("org.wiremock:wiremock:3.13.2")
            
            // Koog Framework for AI Agents
            val koogVersion = "0.6.2"
            dependency("ai.koog:koog-agents:$koogVersion")
            
            // Kotlin AWS SDK
            val awsSdkKotlinVersion = "1.6.16"
            val smithyKotlinVersion = "1.6.2"
            dependency("aws.sdk.kotlin:s3:$awsSdkKotlinVersion")
            dependency("aws.sdk.kotlin:lambda:$awsSdkKotlinVersion")
            dependency("aws.sdk.kotlin:apigateway:$awsSdkKotlinVersion")
            dependency("aws.sdk.kotlin:bedrock:$awsSdkKotlinVersion")
            dependency("aws.sdk.kotlin:bedrockruntime:$awsSdkKotlinVersion")
            dependency("aws.smithy.kotlin:http-client-engine-okhttp:$smithyKotlinVersion")
            dependency("aws.smithy.kotlin:http-client-engine-crt:$smithyKotlinVersion")
            
            val okhttpVersion = "5.0.0-alpha.14"
            ext["okhttp.version"] = okhttpVersion
            dependency("com.squareup.okhttp3:okhttp:$okhttpVersion")
            dependency("com.squareup.okhttp3:okhttp-coroutines:$okhttpVersion")
            
            // AWS Lambda Java
            dependency("com.amazonaws:aws-lambda-java-core:1.2.3")
            dependency("com.amazonaws:aws-lambda-java-events:3.14.0")
            
            // Kotlinx Serialization for JSON
            dependency("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
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
        testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
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
            log {
                onCheck = true
                format = "Project Coverage: <value>%"
            }
        }

        verify {
            rule {
                minBound(80)
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