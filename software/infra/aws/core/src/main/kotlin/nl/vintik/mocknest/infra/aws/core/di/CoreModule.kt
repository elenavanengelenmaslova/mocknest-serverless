package nl.vintik.mocknest.infra.aws.core.di

import aws.sdk.kotlin.services.s3.S3Client
import nl.vintik.mocknest.application.core.mapper
import org.koin.dsl.module

/**
 * Shared Koin module providing beans used across all Lambda handlers.
 *
 * Defined as a function (not a global `val`) because `module {}` preallocates
 * factories — functions create fresh instances when needed.
 */
fun coreModule() = module {

    // Jackson ObjectMapper — shared across all handlers
    single { mapper }

    // S3 client — replaces S3Configuration @Bean with @Profile("!local")
    single {
        S3Client { region = System.getenv("AWS_REGION") ?: "eu-west-1" }
    }
}
