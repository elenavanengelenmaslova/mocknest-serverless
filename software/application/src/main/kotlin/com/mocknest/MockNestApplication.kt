package com.mocknest

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MockNestApplication

fun main(args: Array<String>) {
    runApplication<MockNestApplication>(*args)
}