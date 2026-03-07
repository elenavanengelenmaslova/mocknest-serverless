import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.gradleup.shadow")
}

springBoot {
    mainClass.set("nl.vintik.mocknest.infra.aws.runtime.RuntimeApplicationKt")
}

val smithyKotlinVersion = "1.6.2"

dependencies {
    // Clean architecture dependencies
    implementation(project(":software:domain"))
    implementation(project(":software:application"))

    // Spring Boot - exclude embedded servers
    implementation("org.springframework.boot:spring-boot-starter-web") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
        exclude(group = "org.apache.tomcat.embed")
    }
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Spring Cloud Function for AWS Lambda
    implementation("org.springframework.cloud:spring-cloud-function-adapter-aws")
    implementation("org.springframework.cloud:spring-cloud-function-kotlin")

    // Kotlin AWS SDK - S3 for runtime, Bedrock for AI features
    implementation("aws.sdk.kotlin:s3")
    implementation("aws.sdk.kotlin:bedrockruntime")
    
    // Koog Framework for AI Agent orchestration
    implementation("ai.koog:koog-agents")
    
    // HTTP client for AWS SDK
    implementation("aws.smithy.kotlin:http-client-engine-okhttp:${smithyKotlinVersion}")
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.14")

    // AWS Lambda runtime
    implementation("com.amazonaws:aws-lambda-java-core")
    implementation("com.amazonaws:aws-lambda-java-events")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

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
        enabled = false
    }
    
    register<ShadowJar>("shadowJarRuntime") {
        group = "shadow"
        description = "Create a minimized JAR for runtime Lambda function"
        
        archiveFileName.set("mocknest-runtime.jar")
        destinationDirectory.set(file("${project.rootDir}/build/dist"))
        
        // Use main source set
        from(sourceSets.main.get().output)
        configurations = listOf(project.configurations.runtimeClasspath.get())
        
        // Manual dependency exclusions for runtime Lambda
        dependencies {
            // Exclude Bedrock SDK - not needed for runtime, only for AI generation
            exclude(dependency("aws.sdk.kotlin:bedrockruntime"))
        }
        
        // Enable automatic minimization with Shadow 9.3.2
        minimize {
            // Keep all application and domain classes to ensure Spring beans are found
            exclude(project(":software:application"))
            exclude(project(":software:domain"))
            
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
            attributes["Main-Class"] = "nl.vintik.mocknest.infra.aws.runtime.RuntimeApplication"
            attributes["Start-Class"] = "nl.vintik.mocknest.infra.aws.runtime.RuntimeApplication"
        }
        
        // Merge service files
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
        
        // Exclude AI generation components from runtime Lambda JAR
        exclude("aws/sdk/kotlin/services/bedrockruntime/**")
        exclude("ai/koog/**")
        exclude("nl/vintik/mocknest/infra/aws/generation/**")
        exclude("nl/vintik/mocknest/application/generation/**")
        exclude("nl/vintik/mocknest/infra/aws/core/ai/**")
        exclude("io/swagger/**")
        exclude("org/fusesource/jansi/**")
        exclude("assets/**")
        
        // Extra exclusions to reach 30% reduction (target <= 53.9MB)
        exclude("org/springframework/boot/devtools/**")
        exclude("org/springframework/boot/test/**")
        exclude("org/springframework/test/**")
        exclude("com/google/common/util/concurrent/ExecutionError.class")
        
        // Exclude Swagger UI assets - not needed in Lambda
        exclude("assets/swagger-ui/**")
        exclude("samples/**")
        exclude("mozilla/public-suffix-list.txt")
        exclude("ucd/**")
        
        // Exclude Jetty WebSocket and HTTP/2 classes
        exclude("org/eclipse/jetty/websocket/**")
        exclude("org/eclipse/jetty/http2/**")
    }
    
    register<ShadowJar>("shadowJarGeneration") {
        group = "shadow"
        description = "Create a minimized JAR for generation Lambda function"
        
        archiveFileName.set("mocknest-generation.jar")
        destinationDirectory.set(file("${project.rootDir}/build/dist"))
        
        // Use main source set
        from(sourceSets.main.get().output)
        configurations = listOf(project.configurations.runtimeClasspath.get())
        
        // Enable automatic minimization with Shadow 9.3.2
        minimize {
            // Keep all application and domain classes to ensure Spring beans are found
            exclude(project(":software:application"))
            exclude(project(":software:domain"))
            
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
        
        // Merge service files
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
        
        // Exclude runtime-only components from generation Lambda JAR
        exclude("nl/vintik/mocknest/infra/aws/runtime/**")
        exclude("org/fusesource/jansi/**")
        exclude("assets/**")
        
        // Extra exclusions to reach 30% reduction (target <= 53.9MB)
        exclude("org/springframework/boot/devtools/**")
        exclude("org/springframework/boot/test/**")
        exclude("org/springframework/test/**")
        exclude("com/google/common/util/concurrent/ExecutionError.class")
        
        // Exclude Swagger UI assets - not needed in Lambda
        exclude("assets/swagger-ui/**")
        exclude("samples/**")
        exclude("mozilla/public-suffix-list.txt")
        exclude("ucd/**")
        
        // Exclude Jetty WebSocket and HTTP/2 classes
        exclude("org/eclipse/jetty/websocket/**")
        exclude("org/eclipse/jetty/http2/**")
    }
    
    register("buildAllLambdas") {
        group = "build"
        description = "Build both runtime and generation Lambda JARs"
        dependsOn("shadowJarRuntime", "shadowJarGeneration")
    }

    assemble {
        dependsOn("shadowJarRuntime", "shadowJarGeneration")
    }

    test {
        // Ensure Lambda JARs are built before running verification tests
        dependsOn("shadowJarRuntime", "shadowJarGeneration")
    }
}

// Copy specialized shadowJars to deployment directory for SAM
tasks.register<Copy>("copyShadowJarForDeployment") {
    dependsOn("shadowJarRuntime", "shadowJarGeneration")
    from(tasks.named("shadowJarRuntime"), tasks.named("shadowJarGeneration"))
    into("${project.rootDir}/deployment/aws/sam/build")
}