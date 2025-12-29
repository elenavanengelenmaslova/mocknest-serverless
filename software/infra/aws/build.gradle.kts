plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.jetbrains.kotlinx.kover")
}

dependencies {
    // Clean architecture dependencies
    implementation(project(":software:domain"))
    implementation(project(":software:application"))

    // Kotlin AWS SDK
    implementation("aws.sdk.kotlin:s3:1.3.77")
    implementation("aws.sdk.kotlin:lambda:1.3.77")
    implementation("aws.sdk.kotlin:apigateway:1.3.77")
    implementation("aws.sdk.kotlin:bedrock:1.3.77")
    implementation("aws.sdk.kotlin:bedrockruntime:1.3.77")

    // Spring Cloud Function for AWS Lambda
    implementation("org.springframework.cloud:spring-cloud-function-adapter-aws")

    // Testing with LocalStack
    testImplementation("org.testcontainers:testcontainers:1.20.3")
    testImplementation("org.testcontainers:junit-jupiter:1.20.3")
    testImplementation("org.testcontainers:localstack:1.20.3")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}