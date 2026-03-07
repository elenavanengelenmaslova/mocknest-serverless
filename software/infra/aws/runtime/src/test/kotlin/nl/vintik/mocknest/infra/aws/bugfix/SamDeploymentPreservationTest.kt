package nl.vintik.mocknest.infra.aws.bugfix

import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val logger = KotlinLogging.logger {}

/**
 * Preservation Property Tests for SAM API Gateway Configuration Fix
 *
 * **CRITICAL**: These tests MUST PASS on unfixed code - passing confirms baseline behavior to preserve.
 * **IMPORTANT**: Follow observation-first methodology - observe behavior on UNFIXED code.
 *
 * These tests validate that existing deployment functionality remains unchanged after the fix.
 * They capture the behavior patterns that must be preserved from Preservation Requirements.
 *
 * **Validates: Requirements 3.1-3.9 (Unchanged Behavior - Regression Prevention)**
 *
 * Property-based testing approach generates many test cases for stronger guarantees.
 */
class SamDeploymentPreservationTest {

    private val projectRoot = File(System.getProperty("user.dir")).let { dir ->
        // Navigate up from software/infra/aws/runtime to project root
        if (dir.name == "runtime") dir.parentFile.parentFile.parentFile.parentFile else dir
    }
    private val samTemplatePath = File(projectRoot, "deployment/aws/sam/template.yaml")

    /**
     * Property 2: Preservation - Lambda Function Integrations
     *
     * **Expected on UNFIXED code**: PASSES - Lambda integrations exist
     * **Expected on FIXED code**: PASSES - Lambda integrations unchanged
     *
     * Validates: Requirements 3.1, 3.2, 3.3, 3.4
     */
    @Test
    fun `Given SAM template WHEN deployed THEN Lambda functions SHALL be integrated with API Gateway`() {
        logger.info { "Testing Lambda function integrations preservation" }
        
        val templateContent = samTemplatePath.readText()
        
        // Verify Lambda functions exist
        assertTrue(
            templateContent.contains("Type: AWS::Serverless::Function"),
            "SAM template MUST contain Lambda function definitions"
        )
        
        // Verify API Gateway integration
        assertTrue(
            templateContent.contains("Type: AWS::Serverless::Api") ||
            templateContent.contains("Events:"),
            "SAM template MUST integrate Lambda functions with API Gateway"
        )
        
        logger.info { "Lambda function integrations preserved" }
    }

    /**
     * Property 2: Preservation - API Gateway Route Configuration
     *
     * **Expected on UNFIXED code**: PASSES - routes configured correctly
     * **Expected on FIXED code**: PASSES - routes unchanged
     *
     * Validates: Requirements 3.2, 3.3, 3.4
     */
    @ParameterizedTest
    @ValueSource(strings = ["/__admin/{proxy+}", "/mocknest/{proxy+}", "/ai/{proxy+}"])
    fun `Given SAM template WHEN deployed THEN API routes SHALL be configured correctly`(route: String) {
        logger.info { "Testing route configuration preservation for: $route" }
        
        val templateContent = samTemplatePath.readText()
        
        // Verify route exists in template (either in Events or DefinitionBody)
        val routePattern = route.replace("{proxy+}", "")
        assertTrue(
            templateContent.contains(routePattern) || 
            templateContent.contains("Path: $route") ||
            templateContent.contains("/{proxy+}"),
            "SAM template MUST contain route configuration for $route"
        )
        
        logger.info { "Route $route configuration preserved" }
    }

    /**
     * Property 2: Preservation - CloudWatch Logging Configuration
     *
     * **Expected on UNFIXED code**: PASSES - logging configured
     * **Expected on FIXED code**: PASSES - logging unchanged
     *
     * Validates: Requirement 3.5
     */
    @Test
    fun `Given SAM template WHEN deployed THEN CloudWatch logging SHALL be configured`() {
        logger.info { "Testing CloudWatch logging preservation" }
        
        val templateContent = samTemplatePath.readText()
        
        // Verify CloudWatch Logs configuration
        val hasLogging = templateContent.contains("AWS::Logs::LogGroup") ||
                        templateContent.contains("AccessLogSetting") ||
                        templateContent.contains("LoggingLevel")
        
        assertTrue(
            hasLogging,
            "SAM template MUST configure CloudWatch logging"
        )
        
        logger.info { "CloudWatch logging configuration preserved" }
    }

    /**
     * Property 2: Preservation - API Gateway Throttling Settings
     *
     * **Expected on UNFIXED code**: PASSES - throttling configured (200 burst, 100 rate)
     * **Expected on FIXED code**: PASSES - throttling unchanged
     *
     * Validates: Requirement 3.6
     */
    @Test
    fun `Given SAM template WHEN deployed THEN API Gateway throttling SHALL be configured`() {
        logger.info { "Testing API Gateway throttling preservation" }
        
        val templateContent = samTemplatePath.readText()
        
        // Verify throttling configuration exists
        val hasThrottling = templateContent.contains("ThrottleSettings") ||
                           templateContent.contains("BurstLimit") ||
                           templateContent.contains("RateLimit") ||
                           templateContent.contains("MethodSettings")
        
        assertTrue(
            hasThrottling,
            "SAM template MUST configure API Gateway throttling"
        )
        
        // Verify specific throttling values if present
        if (templateContent.contains("BurstLimit")) {
            assertTrue(
                templateContent.contains("200"),
                "Throttling burst limit should be 200"
            )
        }
        
        if (templateContent.contains("RateLimit")) {
            assertTrue(
                templateContent.contains("100"),
                "Throttling rate limit should be 100"
            )
        }
        
        logger.info { "API Gateway throttling configuration preserved" }
    }

    /**
     * Property 2: Preservation - S3 Bucket Resource Creation
     *
     * **Expected on UNFIXED code**: PASSES - S3 bucket configured
     * **Expected on FIXED code**: PASSES - S3 bucket unchanged
     *
     * Validates: Requirement 3.9
     */
    @Test
    fun `Given SAM template WHEN deployed THEN S3 bucket SHALL be created`() {
        logger.info { "Testing S3 bucket resource preservation" }
        
        val templateContent = samTemplatePath.readText()
        
        // Verify S3 bucket resource
        assertTrue(
            templateContent.contains("AWS::S3::Bucket"),
            "SAM template MUST create S3 bucket resource"
        )
        
        // Verify bucket configuration (versioning, lifecycle, etc.)
        val hasBucketConfig = templateContent.contains("VersioningConfiguration") ||
                             templateContent.contains("LifecycleConfiguration") ||
                             templateContent.contains("BucketName")
        
        assertTrue(
            hasBucketConfig,
            "S3 bucket MUST have configuration properties"
        )
        
        logger.info { "S3 bucket resource creation preserved" }
    }

    /**
     * Property 2: Preservation - IAM Role Resource Creation
     *
     * **Expected on UNFIXED code**: PASSES - IAM roles configured
     * **Expected on FIXED code**: PASSES - IAM roles unchanged
     *
     * Validates: Requirement 3.9
     */
    @Test
    fun `Given SAM template WHEN deployed THEN IAM roles SHALL be created`() {
        logger.info { "Testing IAM role resource preservation" }
        
        val templateContent = samTemplatePath.readText()
        
        // Verify IAM role configuration (SAM creates roles automatically or explicit)
        val hasIAMConfig = templateContent.contains("AWS::IAM::Role") ||
                          templateContent.contains("Policies:") ||
                          templateContent.contains("Role:")
        
        assertTrue(
            hasIAMConfig,
            "SAM template MUST configure IAM roles for Lambda functions"
        )
        
        logger.info { "IAM role resource creation preserved" }
    }

    /**
     * Property 2: Preservation - Dead Letter Queue Configuration
     *
     * **Expected on UNFIXED code**: PASSES - DLQ configured
     * **Expected on FIXED code**: PASSES - DLQ unchanged
     *
     * Validates: Requirement 3.9
     */
    @Test
    fun `Given SAM template WHEN deployed THEN Dead Letter Queue SHALL be configured`() {
        logger.info { "Testing DLQ configuration preservation" }
        
        val templateContent = samTemplatePath.readText()
        
        // Verify DLQ configuration
        val hasDLQ = templateContent.contains("DeadLetterQueue") ||
                    templateContent.contains("AWS::SQS::Queue") ||
                    templateContent.contains("DLQ")
        
        assertTrue(
            hasDLQ,
            "SAM template MUST configure Dead Letter Queue"
        )
        
        logger.info { "DLQ configuration preserved" }
    }

    /**
     * Property 2: Preservation - CloudFormation Outputs
     *
     * **Expected on UNFIXED code**: PASSES - outputs defined
     * **Expected on FIXED code**: PASSES - existing outputs unchanged (new API key output added)
     *
     * Validates: Requirement 3.7
     */
    @ParameterizedTest
    @ValueSource(strings = [
        "MockNestApiUrl",
        "MockNestApiId", 
        "MockStorageBucket"
    ])
    fun `Given SAM template WHEN deployed THEN CloudFormation outputs SHALL be available`(outputName: String) {
        logger.info { "Testing CloudFormation output preservation for: $outputName" }
        
        val templateContent = samTemplatePath.readText()
        
        // Verify output exists
        assertTrue(
            templateContent.contains(outputName),
            "SAM template MUST contain output: $outputName"
        )
        
        // Verify Outputs section exists
        assertTrue(
            templateContent.contains("Outputs:"),
            "SAM template MUST have Outputs section"
        )
        
        logger.info { "CloudFormation output $outputName preserved" }
    }

    /**
     * Property 2: Preservation - Parameter Validation
     *
     * **Expected on UNFIXED code**: PASSES - parameter validation configured
     * **Expected on FIXED code**: PASSES - validation unchanged (parameter renamed but validation preserved)
     *
     * Validates: Requirement 3.8
     */
    @Test
    fun `Given SAM template WHEN custom parameter provided THEN validation SHALL apply`() {
        logger.info { "Testing parameter validation preservation" }
        
        val templateContent = samTemplatePath.readText()
        
        // Verify parameter validation exists (AllowedPattern or similar)
        val hasValidation = templateContent.contains("AllowedPattern") ||
                           templateContent.contains("AllowedValues") ||
                           templateContent.contains("ConstraintDescription")
        
        assertTrue(
            hasValidation,
            "SAM template MUST have parameter validation"
        )
        
        // Verify alphanumeric, hyphens, underscores pattern if AllowedPattern exists
        if (templateContent.contains("AllowedPattern")) {
            // Check for the pattern that allows alphanumeric, hyphens, and underscores
            // The pattern in YAML is: ^[a-zA-Z0-9\\-_]+$
            val hasAlphanumericPattern = templateContent.contains("[a-zA-Z0-9") &&
                                        templateContent.contains("_")
            
            assertTrue(
                hasAlphanumericPattern,
                "Parameter validation MUST accept alphanumeric, hyphens, and underscores"
            )
        }
        
        logger.info { "Parameter validation preserved" }
    }

    /**
     * Property 2: Preservation - Lambda Runtime Configuration
     *
     * **Expected on UNFIXED code**: PASSES - runtime configured
     * **Expected on FIXED code**: PASSES - runtime unchanged
     *
     * Validates: Requirements 3.1, 3.9
     */
    @Test
    fun `Given SAM template WHEN Lambda functions defined THEN runtime configuration SHALL be correct`() {
        logger.info { "Testing Lambda runtime configuration preservation" }
        
        val templateContent = samTemplatePath.readText()
        
        // Verify Java runtime
        assertTrue(
            templateContent.contains("Runtime: java"),
            "Lambda functions MUST use Java runtime"
        )
        
        // Verify memory configuration
        assertTrue(
            templateContent.contains("MemorySize:"),
            "Lambda functions MUST have memory configuration"
        )
        
        // Verify timeout configuration
        assertTrue(
            templateContent.contains("Timeout:"),
            "Lambda functions MUST have timeout configuration"
        )
        
        logger.info { "Lambda runtime configuration preserved" }
    }

    /**
     * Property 2: Preservation - Environment Variables Configuration
     *
     * **Expected on UNFIXED code**: PASSES - environment variables configured
     * **Expected on FIXED code**: PASSES - environment variables unchanged
     *
     * Validates: Requirements 3.1, 3.9
     */
    @Test
    fun `Given SAM template WHEN Lambda functions defined THEN environment variables SHALL be configured`() {
        logger.info { "Testing environment variables preservation" }
        
        val templateContent = samTemplatePath.readText()
        
        // Verify environment variables section
        assertTrue(
            templateContent.contains("Environment:") &&
            templateContent.contains("Variables:"),
            "Lambda functions MUST have environment variables configured"
        )
        
        // Verify S3 bucket reference in environment variables
        assertTrue(
            templateContent.contains("BUCKET") || 
            templateContent.contains("S3_BUCKET") ||
            templateContent.contains("Ref: MockStorageBucket"),
            "Environment variables MUST reference S3 bucket"
        )
        
        logger.info { "Environment variables configuration preserved" }
    }
}
