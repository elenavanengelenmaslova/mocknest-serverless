import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    id("com.gradleup.shadow")
}

dependencies {
    // Module dependencies
    implementation(project(":software:infra:aws:runtime"))
    implementation(project(":software:infra:aws:generation"))

    // Koin DI
    implementation("io.insert-koin:koin-core")

    // AWS Lambda runtime
    implementation("com.amazonaws:aws-lambda-java-core")
    implementation("com.amazonaws:aws-lambda-java-events")

    // Testing
    testImplementation("io.insert-koin:koin-test-junit5")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:localstack")
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
        archiveFileName.set("mocknest-serverless.jar")
        destinationDirectory.set(file("${project.rootDir}/build/dist"))
        
        from(sourceSets.main.get().output)
        configurations = listOf(project.configurations.runtimeClasspath.get())
        
        isZip64 = true
        
        // Exclude unnecessary files
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
        exclude("META-INF/LICENSE*")
        exclude("META-INF/NOTICE*")
        exclude("META-INF/maven/**")
        exclude("module-info.class")
        
        // Size optimization exclusions
        exclude("assets/swagger-ui/**")
        exclude("samples/**")
        exclude("mozilla/public-suffix-list.txt")
        exclude("ucd/**")
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
    }

    test {
        dependsOn(shadowJar)
        useJUnitPlatform()
    }

    assemble {
        dependsOn("shadowJar")
    }
}
