import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.gradleup.shadow")
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
    val shadowJar by getting(ShadowJar::class) {
        archiveFileName.set("mocknest-serverless-aws.jar")
        destinationDirectory.set(file("${project.rootDir}/build/dist"))
        isZip64 = true
        manifest {
            attributes["Main-Class"] = "org.springframework.cloud.function.adapter.aws.FunctionInvoker"
        }
        
        // Merge service files
        mergeServiceFiles()
        append("META-INF/spring.handlers")
        append("META-INF/spring.schemas")
        append("META-INF/spring.tooling")
        append("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
        append("META-INF/spring/org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration.imports")
        append("META-INF/spring.factories")
        
        // Exclude unnecessary files to reduce size
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
        exclude("META-INF/LICENSE*")
        exclude("META-INF/NOTICE*")
        exclude("META-INF/maven/**")
        exclude("**/*.kotlin_metadata")
        exclude("**/*.kotlin_builtins")
        exclude("module-info.class")
        
        // Exclude Swagger UI assets - not needed in Lambda
        exclude("assets/swagger-ui/**")
        exclude("samples/**")
        exclude("mozilla/public-suffix-list.txt")
        
        // Exclude Jetty WebSocket and HTTP/2 classes
        exclude("org/eclipse/jetty/websocket/**")
        exclude("org/eclipse/jetty/http2/**")
        
        // Exclude unnecessary Jetty components
        exclude("org/eclipse/jetty/alpn/**")
        exclude("org/eclipse/jetty/jmx/**")
        exclude("org/eclipse/jetty/annotations/**")
        exclude("org/eclipse/jetty/jaas/**")
        exclude("org/eclipse/jetty/jndi/**")
        exclude("org/eclipse/jetty/plus/**")
        exclude("org/eclipse/jetty/proxy/**")
        exclude("org/eclipse/jetty/rewrite/**")
        exclude("org/eclipse/jetty/servlets/**")
        exclude("org/eclipse/jetty/webapp/**")
        exclude("org/eclipse/jetty/xml/**")
        
        // Exclude Redis client - not used
        exclude("io/lettuce/**")
        exclude("META-INF/services/io.lettuce.**")
    }
}

// Copy the shadowJar to deployment directory for SAM
tasks.register<Copy>("copyShadowJarForDeployment") {
    dependsOn("shadowJar")
    from(tasks.named("shadowJar"))
    into("${project.rootDir}/deployment/aws/sam/build")
}