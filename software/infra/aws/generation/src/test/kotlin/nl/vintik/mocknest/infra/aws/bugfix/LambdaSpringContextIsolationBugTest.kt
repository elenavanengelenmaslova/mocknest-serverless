package nl.vintik.mocknest.infra.aws.bugfix

import nl.vintik.mocknest.infra.aws.generation.GenerationApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.boot.SpringApplication
import org.springframework.cloud.function.context.FunctionCatalog
import org.springframework.context.ConfigurableApplicationContext
import kotlin.test.assertNotNull
import kotlin.test.fail

/**
 * Bug Condition Exploration Test for Generation Lambda Spring Context Isolation
 * 
 * **CRITICAL**: This test is EXPECTED TO FAIL on unfixed code (monolithic module structure).
 * Test failure confirms the bug exists.
 * 
 * **Bug Description**: Generation Lambda function fails during initialization
 * with `NoSuchBeanDefinitionException` for `FunctionCatalog` bean because it shared the same
 * monolithic `software/infra/aws` module without proper Spring context isolation.
 * 
 * **Root Cause Hypothesis**: Shared source set with multiple application classes causes Spring
 * Boot initialization ambiguity. When both RuntimeApplication and GenerationApplication exist
 * in the same compiled output, Spring's component scanning may discover and attempt to initialize
 * beans from both contexts, leading to conflicts or missing bean registrations.
 *
 * **Expected Behavior After Fix**: Each Lambda successfully initializes with its own Spring
 * context and FunctionCatalog containing function-specific beans.
 * 
 * **Test Strategy**: Test Generation Lambda initialization and verify that FunctionCatalog
 * bean is present in the Spring context.
 * 
 * **Validates Requirements**: 2.1, 2.2, 2.3, 2.4 from bugfix.md
 */
class LambdaSpringContextIsolationBugTest {

    private var context: ConfigurableApplicationContext? = null

    @AfterEach
    fun cleanup() {
        context?.close()
        context = null
    }

    /**
     * Property 1: Bug Condition - Generation Lambda Initialization Success with FunctionCatalog
     * 
     * For Generation Lambda initialization event where the Lambda is deployed from its own dedicated
     * module, the Spring Cloud Function adapter SHALL successfully locate the FunctionCatalog bean,
     * initialize the function router, and complete Lambda cold start without throwing
     * NoSuchBeanDefinitionException.
     * 
     * **EXPECTED OUTCOME ON UNFIXED CODE**: This test WILL FAIL because the monolithic
     * module structure causes Spring context initialization to fail with missing FunctionCatalog.
     * 
     * **EXPECTED OUTCOME ON FIXED CODE**: This test WILL PASS because Generation Lambda has its
     * own isolated Spring context with properly registered FunctionCatalog bean.
     */
    @Test
    fun `Property 1 - Given Generation Lambda initialization When using dedicated module Then FunctionCatalog bean should exist in Spring context`() {
        try {
            // GIVEN: Generation Lambda initialization with dedicated module structure
            val applicationClass = GenerationApplication::class.java
            
            println("\n=== Testing Generation Lambda Initialization ===")
            println("Application class: ${applicationClass.name}")
            
            // WHEN: Spring context initializes
            val app = SpringApplication(applicationClass)
            app.setAdditionalProfiles("test")
            
            // Disable web environment for Lambda context
            app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE)
            
            // Mock AWS environment variables that Lambda would provide
            System.setProperty("AWS_REGION", "us-east-1")
            System.setProperty("AWS_LAMBDA_FUNCTION_NAME", "test-function")
            
            println("Initializing Spring context...")
            context = app.run(
                "--spring.main.allow-bean-definition-overriding=true",
                "--spring.cloud.function.definition=generationRouter",
                "--s3.bucket.mappings=test-mappings-bucket",
                "--s3.bucket.files=test-files-bucket",
                "--s3.bucket.specs=test-specs-bucket"
            )
            
            println("Spring context initialized successfully")
            println("Active profiles: ${context!!.environment.activeProfiles.joinToString()}")
            println("Bean definition count: ${context!!.beanDefinitionCount}")
            
            // THEN: FunctionCatalog bean should exist in Spring context
            println("\nVerifying FunctionCatalog bean exists...")
            
            val functionCatalog = try {
                context!!.getBean(FunctionCatalog::class.java)
            } catch (e: NoSuchBeanDefinitionException) {
                println("ERROR: FunctionCatalog bean not found!")
                println("Exception: ${e.message}")
                println("\nAvailable beans:")
                context!!.beanDefinitionNames.sorted().forEach { beanName ->
                    println("  - $beanName: ${context!!.getBean(beanName)::class.java.name}")
                }
                throw AssertionError(
                    "Bug confirmed: FunctionCatalog bean missing for Generation Lambda. " +
                    "This is the expected failure on unfixed code with monolithic module structure. " +
                    "Root cause: Shared source set with multiple application classes causes " +
                    "Spring Boot initialization ambiguity.",
                    e
                )
            }
            
            assertNotNull(functionCatalog, "FunctionCatalog bean should not be null")
            println("✓ FunctionCatalog bean found: ${functionCatalog::class.java.name}")
            
            // Verify the function catalog is properly initialized
            val expectedFunctionName = "generationRouter"
            
            // Try to lookup the function to verify it's registered
            println("Looking up function: $expectedFunctionName")
            val function = functionCatalog.lookup<Any>(expectedFunctionName)
            println("Lookup result: $function (type: ${function?.javaClass?.name})")
            
            if (function == null) {
                println("ERROR: Function '$expectedFunctionName' returned null from FunctionCatalog!")
                println("\nThis confirms the bug: FunctionCatalog bean exists but function beans are not registered.")
                
                throw AssertionError(
                    "Bug confirmed: Function '$expectedFunctionName' not registered in FunctionCatalog for Generation Lambda. " +
                    "FunctionCatalog bean exists but is empty/incomplete. " +
                    "This is the expected failure on unfixed code with monolithic module structure."
                )
            }
            
            // Try to verify the function bean exists in the Spring context directly
            val functionBeanName = expectedFunctionName
            val hasFunctionBean = try {
                context!!.getBean(functionBeanName)
                true
            } catch (e: Exception) {
                println("ERROR: Function bean '$functionBeanName' not found in Spring context!")
                println("Exception: ${e.message}")
                false
            }
            
            if (!hasFunctionBean) {
                println("\nThis confirms the bug: Function bean '$expectedFunctionName' is not registered in Spring context.")
                
                throw AssertionError(
                    "Bug confirmed: Function bean '$expectedFunctionName' not found in Spring context for Generation Lambda. " +
                    "This is the expected failure on unfixed code with monolithic module structure."
                )
            }
            
            println("✓ Function bean '$expectedFunctionName' registered in Spring context")
            println("=== Generation Lambda initialization PASSED ===\n")
            
        } catch (e: AssertionError) {
            // Re-throw assertion errors (these are expected failures on unfixed code)
            throw e
        } catch (e: Exception) {
            // Catch any other initialization failures
            println("ERROR: Unexpected exception during Generation Lambda initialization")
            println("Exception type: ${e::class.java.name}")
            println("Exception message: ${e.message}")
            e.printStackTrace()
            
            fail(
                "Bug confirmed: Generation Lambda initialization failed with unexpected exception. " +
                "This may indicate Spring context initialization issues with monolithic module structure. " +
                "Exception: ${e.message}",
                e
            )
        }
    }
}
