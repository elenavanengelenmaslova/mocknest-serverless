package nl.vintik.mocknest.infra.aws

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = [
    "nl.vintik.mocknest"
])
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}