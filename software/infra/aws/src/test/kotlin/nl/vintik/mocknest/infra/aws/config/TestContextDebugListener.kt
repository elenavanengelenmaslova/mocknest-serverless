package nl.vintik.mocknest.infra.aws.config

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.test.context.TestContext
import org.springframework.test.context.support.AbstractTestExecutionListener

/**
 * A listener that logs detailed information about test execution,
 * specifically to help debug ApplicationContext startup failures in CI.
 */
class TestContextDebugListener : AbstractTestExecutionListener() {

    private val logger = KotlinLogging.logger {}

    override fun prepareTestInstance(testContext: TestContext) {
        logger.info { "Preparing test instance for: ${testContext.testClass.simpleName}" }
    }

    override fun beforeTestClass(testContext: TestContext) {
        logger.info { "Starting test class: ${testContext.testClass.simpleName}" }
    }
}
