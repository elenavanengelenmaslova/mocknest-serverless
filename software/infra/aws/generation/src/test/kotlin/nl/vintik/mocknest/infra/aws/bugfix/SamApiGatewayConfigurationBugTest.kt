package nl.vintik.mocknest.infra.aws.bugfix

import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val logger = KotlinLogging.logger {}

/**
 * Bug Condition Exploration Test for SAM API Gateway Configuration Fix
 *
 * **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bugs exist.
 * **DO NOT attempt to fix the test or the code when it fails.**
 *
 * This test encodes the EXPECTED behavior (what should happen after the fix).
 * When run on unfixed code, it will fail and document the counterexamples that prove the bugs exist.
 *
 * **Validates: Requirements 1.1-1.10 (Current Behavior - Defects)**
 * **Validates: Requirements 2.1-2.12 (Expected Behavior - Correct)**
 *
 * Four bugs being tested:
 * 1. Missing API Key Configuration - No API key resources in SAM template
 * 2. Multiple Unwanted Stages - API Gateway creates multiple stages
 * 3. Misleading Parameter Naming - "StageName" with default "v1" suggests versioning
 * 4. Shadow Plugin Removing Dependencies - minimize() excludes required framework classes (FunctionInvoker, kotlin-reflect)
 */
class SamApiGatewayConfigurationBugTest {

    private val projectRoot = File(System.getProperty("user.dir")).let { dir ->
        // Navigate up from software/infra/aws/generation to project root
        if (dir.name == "generation") dir.parentFile.parentFile.parentFile.parentFile else dir
    }
    private val samTemplatePath = File(projectRoot, "deployment/aws/sam/template.yaml")
    private val buildGradlePath = File(projectRoot, "software/infra/aws/generation/build.gradle.kts")

    /**
     * Property 1: Fault Condition - API Key Resources Must Exist
     *
     * **Expected on UNFIXED code**: FAILS - no API key resources found
     * **Expected on FIXED code**: PASSES - API key resources exist
     *
     * Validates: Requirements 2.1, 2.2, 2.3, 2.9
     */
    @Test
    fun `Given SAM template WHEN deployed THEN API key resources SHALL be created`() {
        logger.info { "Testing for API key resources in SAM template" }
        
        val templateContent = samTemplatePath.readText()
        
        // Expected behavior: API key resource must exist
        assertTrue(
            templateContent.contains("AWS::ApiGateway::ApiKey"),
            "SAM template MUST contain AWS::ApiGateway::ApiKey resource. " +
            "COUNTEREXAMPLE: Missing API key resource in template."
        )
        
        // Expected behavior: Usage plan must exist
        assertTrue(
            templateContent.contains("AWS::ApiGateway::UsagePlan"),
            "SAM template MUST contain AWS::ApiGateway::UsagePlan resource. " +
            "COUNTEREXAMPLE: Missing usage plan resource in template."
        )
        
        // Expected behavior: Usage plan key must exist
        assertTrue(
            templateContent.contains("AWS::ApiGateway::UsagePlanKey"),
            "SAM template MUST contain AWS::ApiGateway::UsagePlanKey resource. " +
            "COUNTEREXAMPLE: Missing usage plan key resource in template."
        )
        
        // Expected behavior: API key output must exist
        assertTrue(
            templateContent.contains("ApiKey") && templateContent.contains("Outputs:"),
            "SAM template MUST output the API key value. " +
            "COUNTEREXAMPLE: No API key output found in template."
        )
        
        logger.info { "API key resources validation complete" }
    }

    /**
     * Property 1: Fault Condition - Single Stage Configuration
     *
     * **Expected on UNFIXED code**: FAILS - multiple stages will be created
     * **Expected on FIXED code**: PASSES - only configured stage exists
     *
     * Validates: Requirements 2.5, 2.6
     */
    @Test
    fun `Given SAM template WHEN deployed THEN only configured stage SHALL exist`() {
        logger.info { "Testing for single stage configuration in SAM template" }
        
        val templateContent = samTemplatePath.readText()
        
        // Expected behavior: Template should have explicit stage configuration
        // to prevent automatic "Stage" creation
        val hasExplicitStageConfig = templateContent.contains("OpenApiVersion") ||
                                     templateContent.contains("DefinitionBody") ||
                                     templateContent.contains("StageName:")
        
        assertTrue(
            hasExplicitStageConfig,
            "SAM template MUST have explicit stage configuration. " +
            "COUNTEREXAMPLE: No explicit stage configuration found, will create multiple stages."
        )
        
        // Expected behavior: Should not have default "Stage" stage
        // This is a heuristic - actual validation requires deployment
        logger.warn { 
            "Note: Full validation of single stage requires actual AWS deployment. " +
            "This test validates template configuration only."
        }
        
        logger.info { "Single stage configuration validation complete" }
    }

    /**
     * Property 1: Fault Condition - Parameter Naming
     *
     * **Expected on UNFIXED code**: FAILS - parameter named "StageName" with default "v1"
     * **Expected on FIXED code**: PASSES - parameter named "DeploymentName" with appropriate default
     *
     * Validates: Requirements 2.7, 2.8
     */
    @Test
    fun `Given SAM template WHEN reviewed THEN parameter SHALL be named DeploymentName`() {
        logger.info { "Testing parameter naming in SAM template" }
        
        val templateContent = samTemplatePath.readText()
        
        // Expected behavior: Parameter should be named "DeploymentName"
        assertTrue(
            templateContent.contains("DeploymentName:"),
            "SAM template MUST have parameter named 'DeploymentName'. " +
            "COUNTEREXAMPLE: Parameter named 'StageName' found, which suggests API versioning."
        )
        
        // Expected behavior: Should NOT have "StageName" parameter
        assertFalse(
            templateContent.contains("StageName:") && 
            templateContent.contains("Type: String") &&
            templateContent.contains("Default: 'v1'"),
            "SAM template MUST NOT have 'StageName' parameter with default 'v1'. " +
            "COUNTEREXAMPLE: Found 'StageName' parameter suggesting versioning semantics."
        )
        
        // Expected behavior: Default should suggest instance identification, not versioning
        val hasVersioningDefault = templateContent.contains("Default: 'v1'") ||
                                   templateContent.contains("Default: 'v2'")
        
        assertFalse(
            hasVersioningDefault,
            "Parameter default MUST NOT suggest API versioning (v1, v2, etc.). " +
            "COUNTEREXAMPLE: Found versioning-style default value."
        )
        
        logger.info { "Parameter naming validation complete" }
    }

    /**
     * Property 1: Fault Condition - Spring Cloud Function Adapter Preservation
     *
     * **Expected on UNFIXED code**: FAILS - adapter classes not excluded from minimize()
     * **Expected on FIXED code**: PASSES - adapter classes preserved
     *
     * Validates: Requirements 2.10, 2.11, 2.12
     */
    @Test
    fun `Given Shadow plugin minimize WHEN building JAR THEN Spring Cloud adapter SHALL be preserved`() {
        logger.info { "Testing Shadow plugin minimize configuration" }
        
        val buildGradleContent = buildGradlePath.readText()
        
        // Expected behavior: minimize() block should exclude spring-cloud-function-adapter-aws
        val hasMinimizeBlock = buildGradleContent.contains("minimize {") ||
                               buildGradleContent.contains("minimize{")
        
        assertTrue(
            hasMinimizeBlock,
            "Build configuration MUST have minimize block. " +
            "COUNTEREXAMPLE: No minimize block found."
        )
        
        // Expected behavior: Should exclude spring-cloud-function-adapter-aws
        val hasAdapterExclude = buildGradleContent.contains("spring-cloud-function-adapter-aws")
        
        assertTrue(
            hasAdapterExclude,
            "Shadow plugin minimize MUST exclude spring-cloud-function-adapter-aws. " +
            "COUNTEREXAMPLE: No exclude for adapter found, will cause ClassNotFoundException."
        )

        // Expected behavior: Should exclude kotlin-reflect
        val hasKotlinReflectExclude = buildGradleContent.contains("kotlin-reflect")

        assertTrue(
            hasKotlinReflectExclude,
            "Shadow plugin minimize MUST exclude kotlin-reflect. " +
            "COUNTEREXAMPLE: No exclude for kotlin-reflect found, will cause KotlinReflectionNotSupportedError."
        )
        
        logger.info { "Shadow plugin configuration validation complete" }
    }

    /**
     * Property 1: Fault Condition - API Key Authentication Required
     *
     * **Expected on UNFIXED code**: FAILS - no API key requirement configured
     * **Expected on FIXED code**: PASSES - API key required for endpoints
     *
     * Validates: Requirements 2.4
     */
    @Test
    fun `Given SAM template WHEN deployed THEN API Gateway SHALL require API key authentication`() {
        logger.info { "Testing API key authentication requirement" }
        
        val templateContent = samTemplatePath.readText()
        
        // Expected behavior: API should have Auth configuration or ApiKeyRequired
        val hasAuthConfig = templateContent.contains("Auth:") ||
                           templateContent.contains("ApiKeyRequired: true") ||
                           (templateContent.contains("AWS::ApiGateway::ApiKey") &&
                            templateContent.contains("AWS::ApiGateway::UsagePlan"))
        
        assertTrue(
            hasAuthConfig,
            "SAM template MUST configure API key authentication. " +
            "COUNTEREXAMPLE: No API key authentication configuration found."
        )
        
        logger.info { "API key authentication requirement validation complete" }
    }

    /**
     * Integration Test: Verify Lambda Handler Configuration
     *
     * **Expected on UNFIXED code**: FAILS - handler points to FunctionInvoker but JAR missing classes
     * **Expected on FIXED code**: PASSES - handler correctly configured and classes preserved
     *
     * Validates: Requirements 2.11, 2.12
     */
    @Test
    fun `Given SAM template WHEN Lambda functions configured THEN handler SHALL be FunctionInvoker`() {
        logger.info { "Testing Lambda handler configuration" }
        
        val templateContent = samTemplatePath.readText()
        
        // Expected behavior: Lambda functions should use FunctionInvoker handler
        val handlerPattern = "Handler: org.springframework.cloud.function.adapter.aws.FunctionInvoker"
        
        assertTrue(
            templateContent.contains(handlerPattern),
            "Lambda functions MUST use FunctionInvoker handler. " +
            "COUNTEREXAMPLE: FunctionInvoker handler not found in template."
        )
        
        // Count occurrences - should be at least 2 (runtime and generation)
        val handlerCount = templateContent.split(handlerPattern).size - 1
        
        assertTrue(
            handlerCount >= 2,
            "Template MUST have at least 2 Lambda functions with FunctionInvoker handler. " +
            "COUNTEREXAMPLE: Found only $handlerCount handler(s)."
        )
        
        logger.info { "Lambda handler configuration validation complete" }
    }

    /**
     * Comprehensive Bug Condition Check
     *
     * This test aggregates all bug conditions into a single property check.
     * It will fail if ANY of the four bugs exist.
     *
     * **Expected on UNFIXED code**: FAILS with detailed counterexamples
     * **Expected on FIXED code**: PASSES
     */
    @Test
    fun `Given deployment configuration WHEN all bugs fixed THEN all expected behaviors SHALL be satisfied`() {
        logger.info { "Running comprehensive bug condition check" }
        
        val templateContent = samTemplatePath.readText()
        val buildGradleContent = buildGradlePath.readText()
        
        val bugs = mutableListOf<String>()
        
        // Bug 1: Missing API Key Configuration
        if (!templateContent.contains("AWS::ApiGateway::ApiKey")) {
            bugs.add("Bug 1: Missing AWS::ApiGateway::ApiKey resource")
        }
        if (!templateContent.contains("AWS::ApiGateway::UsagePlan")) {
            bugs.add("Bug 1: Missing AWS::ApiGateway::UsagePlan resource")
        }
        if (!templateContent.contains("AWS::ApiGateway::UsagePlanKey")) {
            bugs.add("Bug 1: Missing AWS::ApiGateway::UsagePlanKey resource")
        }
        
        // Bug 2: Multiple Unwanted Stages (heuristic check)
        if (!templateContent.contains("OpenApiVersion") && 
            !templateContent.contains("DefinitionBody")) {
            bugs.add("Bug 2: No explicit stage configuration, will create multiple stages")
        }
        
        // Bug 3: Misleading Parameter Naming
        if (templateContent.contains("StageName:") && 
            templateContent.contains("Default: 'v1'")) {
            bugs.add("Bug 3: Parameter named 'StageName' with default 'v1' suggests versioning")
        }
        if (!templateContent.contains("DeploymentName:")) {
            bugs.add("Bug 3: Parameter not named 'DeploymentName'")
        }
        
        // Bug 4: Shadow Plugin Removing Dependencies
        if (!buildGradleContent.contains("spring-cloud-function-adapter-aws") ||
            !buildGradleContent.contains("kotlin-reflect")) {
            bugs.add("Bug 4: Shadow plugin minimize() does not exclude spring-cloud-function-adapter-aws and kotlin-reflect")
        }
        
        // Report all bugs found
        if (bugs.isNotEmpty()) {
            val counterexamples = bugs.joinToString("\n  - ", prefix = "\n  - ")
            logger.error { "COUNTEREXAMPLES FOUND (bugs exist):$counterexamples" }
            
            throw AssertionError(
                "Bug condition exploration test FAILED as expected on unfixed code. " +
                "Found ${bugs.size} bug(s):$counterexamples\n\n" +
                "This is the CORRECT outcome for unfixed code - these failures prove the bugs exist."
            )
        }
        
        logger.info { "All bug conditions resolved - expected behavior satisfied" }
    }
}
