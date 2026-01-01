package io.mocknest.infra.aws

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = [
    "io.mocknest"
])
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}