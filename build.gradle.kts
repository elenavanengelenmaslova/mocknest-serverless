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

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
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
kover {
    reports {
        total {
            xml {
                onCheck = true // Generate XML on check task
            }
            html {
                onCheck = true // Generate HTML on check task
            }
        }
        verify {
            rule {
                minBound(90)
            }
        }
    }
}