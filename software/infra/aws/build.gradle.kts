plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("org.jetbrains.kotlinx.kover")
}

// Configure the main class for Spring Boot
springBoot {
    mainClass.set("io.mocknest.infra.aws.ApplicationKt")
}

dependencies {
    // Clean architecture dependencies
    implementation(project(":software:domain"))
    implementation(project(":software:application"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter")

    // Spring Cloud Function for AWS Lambda
    implementation("org.springframework.cloud:spring-cloud-function-adapter-aws")
    implementation("org.springframework.cloud:spring-cloud-function-kotlin")

    // Kotlin AWS SDK (versions managed globally)
    implementation("aws.sdk.kotlin:s3")
    implementation("aws.sdk.kotlin:lambda")
    implementation("aws.sdk.kotlin:apigateway")
    implementation("aws.sdk.kotlin:bedrock")
    implementation("aws.sdk.kotlin:bedrockruntime")

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
        exclude("org.apache.httpcomponents")
    }
}

// Copy the bootJar to deployment directory for SAM
tasks.register<Copy>("copyBootJarForDeployment") {
    dependsOn("bootJar")
    from(tasks.bootJar.get().archiveFile)
    into("${project.rootDir}/deployment/sam/build")
    rename { "mocknest-serverless-aws.jar" }
}