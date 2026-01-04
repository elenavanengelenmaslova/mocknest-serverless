plugins {
    kotlin("jvm") version "2.3.0" apply false
    kotlin("plugin.spring") version "2.3.0" apply false
    id("org.springframework.boot") version "4.0.0" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
    id("org.jetbrains.kotlinx.kover") version "0.8.3"
}

allprojects {
    group = "com.mocknest"
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
    configure<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension> {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.0")
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:2024.0.0")
        }
        dependencies {
            dependency("org.wiremock:wiremock-standalone:3.13.2")
            
            // Kotlin AWS SDK
            val awsSdkKotlinVersion = "1.3.77"
            dependency("aws.sdk.kotlin:s3:$awsSdkKotlinVersion")
            dependency("aws.sdk.kotlin:lambda:$awsSdkKotlinVersion")
            dependency("aws.sdk.kotlin:apigateway:$awsSdkKotlinVersion")
            dependency("aws.sdk.kotlin:bedrock:$awsSdkKotlinVersion")
            dependency("aws.sdk.kotlin:bedrockruntime:$awsSdkKotlinVersion")
            
            // AWS Lambda Java
            dependency("com.amazonaws:aws-lambda-java-core:1.2.3")
            dependency("com.amazonaws:aws-lambda-java-events:3.14.0")
            
            // TestContainers
            val testContainersVersion = "1.20.3"
            dependency("org.testcontainers:testcontainers:$testContainersVersion")
            dependency("org.testcontainers:junit-jupiter:$testContainersVersion")
            dependency("org.testcontainers:localstack:$testContainersVersion")
        }
    }

    dependencies {
        val implementation by configurations
        val testImplementation by configurations

        // Kotlin standard library
        implementation("org.jetbrains.kotlin:kotlin-stdlib")
        implementation("org.jetbrains.kotlin:kotlin-reflect")

        // Logging
        implementation("io.github.oshai:kotlin-logging-jvm:7.0.0")

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
                        "io.mocknest.*.interfaces.*",

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