import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.gradleup.shadow")
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

tasks {
    named<ShadowJar>("shadowJar") {
        archiveFileName.set("mocknest-serverless-aws.jar")
        mergeServiceFiles()
        append("META-INF/spring.handlers")
        append("META-INF/spring.schemas")
        append("META-INF/spring.tooling")
        append("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
        append("META-INF/spring/org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration.imports")
        append("META-INF/spring.factories")
        
        exclude("META-INF/LICENSE", "META-INF/NOTICE", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
        
        isZip64 = true
    }
}

// Copy the shadowJar to deployment directory for SAM
tasks.register<Copy>("copyShadowJarForDeployment") {
    dependsOn("shadowJar")
    from(tasks.named("shadowJar"))
    into("${project.rootDir}/deployment/aws/sam/build")
}