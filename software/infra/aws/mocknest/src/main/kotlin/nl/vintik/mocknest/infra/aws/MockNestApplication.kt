package nl.vintik.mocknest.infra.aws

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(
    scanBasePackages = [
        "nl.vintik.mocknest.application.runtime",
        "nl.vintik.mocknest.application.generation",
        "nl.vintik.mocknest.application.core",
        "nl.vintik.mocknest.infra.aws.runtime",
        "nl.vintik.mocknest.infra.aws.generation",
        "nl.vintik.mocknest.infra.aws.core"
    ]
)
class MockNestApplication

fun main(args: Array<String>) {
    runApplication<MockNestApplication>(*args)
}
