import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.gradleup.shadow")
}

springBoot {
    mainClass.set("nl.vintik.mocknest.infra.aws.generation.GenerationApplicationKt")
}

dependencies {
    // Core module dependency
    implementation(project(":software:infra:aws:core"))

    // Spring Cloud Function for AWS Lambda
    implementation("org.springframework.cloud:spring-cloud-function-adapter-aws")
    implementation("org.springframework.cloud:spring-cloud-function-kotlin")

    // Kotlin AWS SDK - Bedrock for AI features
    implementation("aws.sdk.kotlin:bedrockruntime")
    
    // Koog Framework for AI Agent orchestration
    implementation("ai.koog:koog-agents")

    // AWS Lambda runtime
    implementation("com.amazonaws:aws-lambda-java-core")
    implementation("com.amazonaws:aws-lambda-java-events")

    // Testing with LocalStack
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:localstack")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

configurations {
    runtimeClasspath {
        // Exclude embedded servers - not needed in Lambda
        exclude(group = "org.apache.tomcat.embed")
        exclude(group = "io.undertow")
        
        // Exclude Jetty WebSocket and HTTP/2 - not needed for mocking
        exclude(group = "org.eclipse.jetty.websocket")
        exclude(group = "org.eclipse.jetty.http2")
        
        // Exclude duplicate Guava - WireMock has shaded version
        exclude(group = "com.google.guava", module = "guava")
        
        // Exclude unnecessary HTTP clients
        exclude(group = "org.apache.httpcomponents")
        exclude(group = "org.apache.httpcomponents.client5")
        exclude(group = "org.apache.httpcomponents.core5")
        
        // Exclude Redis client - not used
        exclude(group = "io.lettuce")
        exclude(group = "io.projectreactor.netty")
        
        // Exclude Kotlin compiler and reflection (use minimal reflection)
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-compiler-embeddable")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-compiler-embeddable")
        
        // Exclude XML processing we don't need
        exclude(group = "com.sun.xml.bind")
        exclude(group = "javax.xml.bind")
    }
}

tasks {
    bootJar {
        enabled = false
    }

    bootRun {
        enabled = false
    }

    val shadowJar by getting(ShadowJar::class) {
        archiveFileName.set("mocknest-generation.jar")
        destinationDirectory.set(file("${project.buildDir}/libs"))
        
        from(sourceSets.main.get().output)
        configurations = listOf(project.configurations.runtimeClasspath.get())
        
        minimize {
            // Keep all application and domain classes to ensure Spring beans are found
            exclude(project(":software:application"))
            exclude(project(":software:domain"))
            exclude(project(":software:infra:aws:core"))
            
            // Only exclude absolute essentials that minimize() might incorrectly remove
            exclude(dependency("org.springframework.boot:spring-boot-autoconfigure"))
            exclude(dependency("org.springframework.cloud:spring-cloud-function-context"))
            exclude(dependency("org.springframework.cloud:spring-cloud-function-adapter-aws"))
            // Preserve Kotlin reflection and stdlib for Spring and AI components
            exclude(dependency("org.jetbrains.kotlin:kotlin-reflect"))
            exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib"))
        }
        
        isZip64 = true
        
        manifest {
            attributes["Main-Class"] = "nl.vintik.mocknest.infra.aws.generation.GenerationApplication"
            attributes["Start-Class"] = "nl.vintik.mocknest.infra.aws.generation.GenerationApplication"
        }
        
        // CRITICAL: These make Spring Boot work in fat JAR
        mergeServiceFiles()
        append("META-INF/spring.handlers")
        append("META-INF/spring.schemas")
        append("META-INF/spring.tooling")
        append("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
        append("META-INF/spring.factories")
        
        // Exclude unnecessary files
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
        exclude("META-INF/LICENSE*")
        exclude("META-INF/NOTICE*")
        exclude("META-INF/maven/**")
        exclude("module-info.class")
        
        // Size optimization exclusions
        exclude("org/springframework/boot/devtools/**")
        exclude("org/springframework/boot/test/**")
        exclude("org/springframework/test/**")
        exclude("assets/swagger-ui/**")
        exclude("samples/**")
        exclude("mozilla/public-suffix-list.txt")
        exclude("ucd/**")
        exclude("org/eclipse/jetty/websocket/**")
        exclude("org/eclipse/jetty/http2/**")
    }

    assemble {
        dependsOn("shadowJar")
    }
}
