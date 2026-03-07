package nl.vintik.mocknest.infra.aws.generation

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(
    scanBasePackages = [
        "nl.vintik.mocknest.application.generation",
        "nl.vintik.mocknest.application.core",
        "nl.vintik.mocknest.infra.aws.generation",
        "nl.vintik.mocknest.infra.aws.core"
    ]
)
class GenerationApplication

fun main(args: Array<String>) {
    runApplication<GenerationApplication>(*args)
}
