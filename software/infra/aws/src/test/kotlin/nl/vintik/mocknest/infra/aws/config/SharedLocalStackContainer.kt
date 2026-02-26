package nl.vintik.mocknest.infra.aws.config

import io.github.oshai.kotlinlogging.KotlinLogging
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

/**
 * Shared LocalStack container singleton for all integration tests.
 * This ensures we only start one LocalStack container per test run, improving performance.
 */
object SharedLocalStackContainer {

    private val logger = KotlinLogging.logger {}

    val container: LocalStackContainer by lazy {
        logger.info { "Starting shared LocalStack container..." }

        LocalStackContainer(DockerImageName.parse("localstack/localstack:4.12.0"))
            .withServices(
                LocalStackContainer.Service.S3,
            )
            .waitingFor(
                Wait.forHttp("/_localstack/health")
                    .forStatusCode(200)
                    .withStartupTimeout(java.time.Duration.ofMinutes(2))
            )
            .withLogConsumer { outputFrame -> 
                print("[LOCALSTACK] ${outputFrame.utf8String}")
            }
            .also { container ->
                container.start()
                logger.info { "LocalStack container started successfully" }

                // Add shutdown hook to ensure cleanup
                Runtime.getRuntime().addShutdownHook(Thread {
                    logger.info { "Stopping LocalStack container..." }
                    container.stop()
                })
            }
    }

    /**
     * Get the S3 endpoint URL for the shared container
     */
    fun getS3EndpointUrl(): String = container.getEndpointOverride(LocalStackContainer.Service.S3).toString()

    /**
     * Get the access key for the shared container
     */
    fun getAccessKey(): String = container.accessKey

    /**
     * Get the secret key for the shared container
     */
    fun getSecretKey(): String = container.secretKey
}