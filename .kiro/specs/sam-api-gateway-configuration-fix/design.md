# SAM API Gateway Configuration Fix - Bugfix Design

## Overview

This bugfix addresses four critical issues in the MockNest Serverless deployment configuration:

1. **Missing API Key Security**: The SAM template lacks API Gateway API key resources (AWS::ApiGateway::ApiKey, UsagePlan, UsagePlanKey), leaving endpoints unsecured
2. **Multiple Unwanted API Gateway Stages**: After deployment, API Gateway shows multiple stages (Stage, v1, and others) instead of just the configured stage
3. **Misleading Parameter Naming**: The "StageName" parameter with default "v1" suggests API versioning rather than deployment instance identification
4. **Shadow Plugin Removing Spring Cloud Adapter**: The Gradle Shadow plugin's minimize() feature excludes Spring Cloud Function adapter classes, causing ClassNotFoundException at runtime

The fix strategy involves:
- Adding complete API key infrastructure to the SAM template with proper resource linkage
- Configuring API Gateway to prevent automatic stage creation and remove unwanted stages
- Renaming the parameter from "StageName" to "DeploymentName" to clarify its purpose as an instance identifier
- Preserving Spring Cloud Function adapter classes in the Shadow plugin minimize() configuration

## Glossary

- **Bug_Condition (C)**: The conditions that trigger the four bugs - missing API key resources in template, automatic stage creation by API Gateway, misleading parameter name, and minimize() removing required classes
- **Property (P)**: The desired behavior - API keys properly configured and output, only configured stage exists, parameter name clearly indicates deployment instance, Lambda functions start successfully
- **Preservation**: Existing Lambda integrations, routing, CloudWatch logging, throttling, and all other SAM template functionality must remain unchanged
- **AWS::Serverless::Api**: SAM resource type that creates API Gateway REST API with simplified configuration
- **API Key**: AWS API Gateway authentication mechanism requiring x-api-key header for requests
- **Usage Plan**: AWS API Gateway resource that defines throttling and quota limits for API keys
- **Stage**: API Gateway deployment stage representing a specific version or environment of the API
- **Shadow Plugin minimize()**: Gradle Shadow plugin feature that removes unused classes from JAR files
- **Spring Cloud Function Adapter**: AWS Lambda handler class (org.springframework.cloud.function.adapter.aws.FunctionInvoker) that bridges Spring Cloud Function with AWS Lambda runtime
- **DeploymentName**: Renamed parameter representing a unique identifier for independent MockNest instances (e.g., "team-alpha", "payment-apis", "test")


## Bug Details

### Fault Condition

The bugs manifest in four distinct scenarios:

**Bug 1 - Missing API Key Configuration:**
The SAM template deploys API Gateway without API key resources, leaving endpoints accessible without authentication.

**Bug 2 - Multiple Unwanted Stages:**
After SAM deployment, API Gateway console shows multiple stages including "Stage", the configured stage name (e.g., "v1"), and potentially others created automatically.

**Bug 3 - Misleading Parameter Name:**
The "StageName" parameter with default "v1" suggests API versioning semantics, but the actual use case is deploying multiple independent MockNest instances with names like "team-alpha", "payment-apis", or "test".

**Bug 4 - Shadow Plugin Removing Required Classes:**
The Shadow plugin's minimize() feature analyzes bytecode to remove unused classes, but incorrectly identifies Spring Cloud Function adapter classes as unused because they're loaded reflectively by AWS Lambda runtime.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type DeploymentConfiguration
  OUTPUT: boolean
  
  RETURN (input.template.resources NOT CONTAINS "AWS::ApiGateway::ApiKey"
         OR input.template.resources NOT CONTAINS "AWS::ApiGateway::UsagePlan"
         OR input.template.resources NOT CONTAINS "AWS::ApiGateway::UsagePlanKey")
         OR (input.apiGateway.stages.count > 1)
         OR (input.template.parameters["StageName"].name == "StageName" 
             AND input.template.parameters["StageName"].default == "v1")
         OR (input.shadowPlugin.minimize.enabled == true
             AND "org.springframework.cloud.function.adapter.aws" NOT IN input.shadowPlugin.minimize.excludes)
END FUNCTION
```

### Examples

**Bug 1 - Missing API Key Configuration:**
- Deploy SAM template with current configuration
- Expected: API Gateway requires x-api-key header, CloudFormation outputs API key value
- Actual: API Gateway accepts requests without authentication, no API key exists

**Bug 2 - Multiple Unwanted Stages:**
- Deploy SAM template with StageName parameter set to "test"
- Expected: API Gateway console shows only "test" stage
- Actual: API Gateway console shows "Stage", "test", and possibly other stages

**Bug 3 - Misleading Parameter Name:**
- User wants to deploy MockNest for "team-alpha" testing
- Expected: Parameter name clearly indicates it's a deployment instance identifier
- Actual: Parameter named "StageName" with default "v1" suggests API versioning

**Bug 4 - Shadow Plugin Removing Required Classes:**
- Build Lambda JAR with minimize() enabled
- Deploy to AWS Lambda with handler org.springframework.cloud.function.adapter.aws.FunctionInvoker
- Invoke Lambda function via API Gateway
- Expected: Lambda function starts and processes request
- Actual: Lambda fails with ClassNotFoundException: org.springframework.cloud.function.adapter.aws.FunctionInvoker, API returns 502 Bad Gateway


## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- Lambda function integrations with API Gateway must continue to work exactly as before
- All route configurations (/__admin/{proxy+}, /mocknest/{proxy+}, /ai/{proxy+}) must remain unchanged
- CloudWatch logging and metrics collection must continue functioning
- API Gateway throttling settings (200 burst, 100 rate) must remain unchanged
- All existing CloudFormation outputs must continue to be available
- S3 bucket, Lambda functions, IAM roles, and DLQ must be created as before
- Custom parameter validation (alphanumeric, hyphens, underscores) must continue to work

**Scope:**
All deployment configurations that do NOT involve API key authentication, stage management, parameter naming, or Shadow plugin class exclusions should be completely unaffected by this fix. This includes:
- Lambda function code and runtime configuration
- S3 bucket lifecycle policies and versioning
- IAM role permissions and policies
- CloudWatch log group retention settings
- Dead letter queue configuration
- Environment variable configuration for Lambda functions


## Hypothesized Root Cause

Based on the bug description and analysis, the root causes are:

1. **Missing API Key Resources**: The SAM template was created without API Gateway security configuration
   - No AWS::ApiGateway::ApiKey resource defined
   - No AWS::ApiGateway::UsagePlan resource to associate keys with the API
   - No AWS::ApiGateway::UsagePlanKey resource to link keys with usage plans
   - No CloudFormation output to expose the generated API key value

2. **Automatic Stage Creation**: AWS::Serverless::Api creates multiple stages by default
   - SAM creates a "Stage" stage automatically during deployment
   - The StageName parameter creates an additional stage with the specified name
   - OpenApiVersion or DefinitionBody configuration may be needed to control stage creation
   - The template may need explicit stage configuration to prevent automatic creation

3. **Misleading Parameter Semantics**: The parameter name doesn't match its actual purpose
   - Named "StageName" which implies API Gateway versioning (v1, v2, v3)
   - Default value "v1" reinforces versioning semantics
   - Actual use case is deployment instance identification (team-alpha, payment-apis, test)
   - Users may be confused about whether to use version numbers or instance names

4. **Shadow Plugin Class Removal**: minimize() uses static bytecode analysis that misses reflective loading
   - Spring Cloud Function adapter classes are loaded by AWS Lambda runtime via reflection
   - Shadow plugin's minimize() analyzes bytecode for direct class references
   - Reflectively loaded classes appear unused and are excluded from the JAR
   - The current exclude() configuration only preserves spring-boot-autoconfigure and spring-cloud-function-context
   - Missing exclude for spring-cloud-function-adapter-aws module containing FunctionInvoker


## Correctness Properties

Property 1: Fault Condition - API Key Configuration and Stage Management

_For any_ SAM template deployment where API key resources are missing, multiple stages exist, parameter naming is misleading, or Shadow plugin excludes required classes, the fixed configuration SHALL create proper API key infrastructure with usage plans, ensure only the configured stage exists in API Gateway, use a parameter name that clearly indicates deployment instance identification, and preserve Spring Cloud Function adapter classes in the minimized JAR.

**Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 2.10, 2.11, 2.12**

Property 2: Preservation - Existing Deployment Functionality

_For any_ deployment configuration that does NOT involve API key authentication, stage management, parameter naming, or Shadow plugin class exclusions, the fixed SAM template and build configuration SHALL produce exactly the same infrastructure and behavior as the original configuration, preserving all Lambda integrations, routing, CloudWatch logging, throttling settings, and resource creation.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9**


## Fix Implementation

### Changes Required

Assuming our root cause analysis is correct:

**File 1**: `deployment/aws/sam/template.yaml`

**Changes**:

1. **Rename Parameter from StageName to DeploymentName**:
   - Change parameter name from "StageName" to "DeploymentName"
   - Update description to clarify it's a deployment instance identifier
   - Update default value from "v1" to "default" or "main"
   - Update all references throughout the template (API StageName property, outputs, etc.)

2. **Add API Key Resources**:
   - Add AWS::ApiGateway::ApiKey resource with auto-generation enabled
   - Add AWS::ApiGateway::UsagePlan resource linked to the API and stage
   - Add AWS::ApiGateway::UsagePlanKey resource to associate key with usage plan
   - Configure usage plan with throttling limits matching existing MethodSettings

3. **Configure API Gateway to Prevent Multiple Stages**:
   - Review AWS::Serverless::Api configuration for stage creation behavior
   - Add explicit stage configuration if needed to prevent automatic "Stage" creation
   - Ensure only the DeploymentName stage is created

4. **Add API Key Output**:
   - Add CloudFormation output exposing the generated API key value
   - Include description explaining how to use the key with x-api-key header

5. **Update All Parameter References**:
   - Update API StageName property to reference DeploymentName parameter
   - Update output exports to use DeploymentName
   - Update resource names and descriptions referencing the stage

**File 2**: `software/infra/aws/build.gradle.kts`

**Function**: `shadowJarRuntime` and `shadowJarGeneration` tasks

**Specific Changes**:

1. **Preserve Spring Cloud Function Adapter Classes**:
   - Add exclude for spring-cloud-function-adapter-aws dependency in minimize() block
   - Ensure FunctionInvoker and related adapter classes are preserved in the JAR
   - Keep existing excludes for spring-boot-autoconfigure and spring-cloud-function-context

2. **Verify Minimize Configuration**:
   - Confirm minimize() block syntax is correct for Shadow plugin 9.3.2
   - Ensure exclude() calls properly preserve entire dependency modules
   - Test that the minimized JAR includes all required Spring Cloud Function classes


## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, surface counterexamples that demonstrate the bugs on unfixed configuration, then verify the fixes work correctly and preserve existing behavior.

### Exploratory Fault Condition Checking

**Goal**: Surface counterexamples that demonstrate the bugs BEFORE implementing the fix. Confirm or refute the root cause analysis. If we refute, we will need to re-hypothesize.

**Test Plan**: Deploy the current SAM template and build Lambda JARs with current configuration. Observe the actual behavior to confirm the bugs exist and understand their manifestation.

**Test Cases**:

1. **Missing API Key Test**: Deploy current SAM template and verify no API key resources exist (will fail on unfixed template)
   - Check CloudFormation stack resources for AWS::ApiGateway::ApiKey
   - Check CloudFormation outputs for API key value
   - Attempt API request without x-api-key header (should succeed on unfixed, indicating bug)
   - Expected counterexample: API accepts requests without authentication

2. **Multiple Stages Test**: Deploy current SAM template with StageName="test" and check API Gateway console (will fail on unfixed template)
   - List all stages in API Gateway console
   - Expected counterexample: Multiple stages exist (Stage, test, possibly others)

3. **Misleading Parameter Test**: Review template parameters and default values (will fail on unfixed template)
   - Check parameter name is "StageName" with default "v1"
   - Expected counterexample: Parameter suggests versioning rather than instance identification

4. **Shadow Plugin Class Removal Test**: Build Lambda JARs with current minimize() configuration and deploy (will fail on unfixed build)
   - Build shadowJarRuntime and shadowJarGeneration tasks
   - Deploy to AWS Lambda
   - Invoke Lambda function via API Gateway
   - Check CloudWatch logs for ClassNotFoundException
   - Expected counterexample: Lambda fails with ClassNotFoundException for FunctionInvoker, API returns 502

**Expected Counterexamples**:
- API Gateway accepts requests without x-api-key header
- Multiple stages visible in API Gateway console
- Parameter name suggests API versioning semantics
- Lambda invocation fails with ClassNotFoundException
- Possible causes: missing CloudFormation resources, automatic stage creation, parameter naming convention, minimize() excluding reflectively loaded classes

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds, the fixed configuration produces the expected behavior.

**Pseudocode:**
```
FOR ALL deployment WHERE isBugCondition(deployment) DO
  result := deployWithFixedConfiguration(deployment)
  ASSERT apiKeyResourcesExist(result)
  ASSERT onlyConfiguredStageExists(result)
  ASSERT parameterNameIsDeploymentName(result)
  ASSERT lambdaFunctionsStartSuccessfully(result)
  ASSERT apiRequiresAuthentication(result)
END FOR
```

**Test Cases**:

1. **API Key Configuration Test**: Deploy fixed SAM template and verify API key infrastructure
   - Verify AWS::ApiGateway::ApiKey resource exists in CloudFormation stack
   - Verify AWS::ApiGateway::UsagePlan resource exists and is linked to API
   - Verify AWS::ApiGateway::UsagePlanKey resource exists and links key to plan
   - Verify CloudFormation output contains API key value
   - Attempt API request without x-api-key header (should fail with 403)
   - Attempt API request with valid x-api-key header (should succeed)

2. **Single Stage Test**: Deploy fixed SAM template with DeploymentName="team-alpha"
   - List all stages in API Gateway console
   - Verify only "team-alpha" stage exists
   - Verify no "Stage" or other unwanted stages exist

3. **Parameter Naming Test**: Review fixed template parameters
   - Verify parameter is named "DeploymentName" (not "StageName")
   - Verify default value suggests instance identification (e.g., "default" or "main")
   - Verify description clearly explains deployment instance purpose

4. **Lambda Function Startup Test**: Build and deploy Lambda JARs with fixed minimize() configuration
   - Build shadowJarRuntime and shadowJarGeneration with fixed excludes
   - Deploy to AWS Lambda
   - Invoke Lambda functions via API Gateway
   - Verify successful response (not 502)
   - Check CloudWatch logs for successful startup (no ClassNotFoundException)


### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed configuration produces the same result as the original configuration.

**Pseudocode:**
```
FOR ALL deployment WHERE NOT isBugCondition(deployment) DO
  ASSERT fixedConfiguration(deployment) = originalConfiguration(deployment)
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because:
- It generates many test cases automatically across the deployment configuration space
- It catches edge cases that manual tests might miss (different parameter values, resource combinations)
- It provides strong guarantees that behavior is unchanged for all non-buggy configurations

**Test Plan**: Observe behavior on UNFIXED configuration first for Lambda integrations, routing, and other functionality, then write property-based tests capturing that behavior.

**Test Cases**:

1. **Lambda Integration Preservation**: Verify Lambda function integrations continue to work after fix
   - Deploy fixed template and invoke /__admin/{proxy+} routes
   - Verify requests route to runtime Lambda function correctly
   - Verify /mocknest/{proxy+} routes to runtime Lambda function
   - Verify /ai/{proxy+} routes to generation Lambda function
   - Compare behavior with unfixed template (should be identical except for API key requirement)

2. **CloudWatch Logging Preservation**: Verify logging continues to work after fix
   - Deploy fixed template and make API requests
   - Check CloudWatch log groups for API Gateway logs
   - Check CloudWatch log groups for Lambda function logs
   - Verify log format and retention settings match unfixed template

3. **Throttling Settings Preservation**: Verify API Gateway throttling continues to work after fix
   - Deploy fixed template
   - Check API Gateway MethodSettings for throttling configuration
   - Verify burst limit is 200 and rate limit is 100 (unchanged)

4. **Resource Creation Preservation**: Verify all existing resources are still created after fix
   - Deploy fixed template
   - Verify S3 bucket is created with versioning and lifecycle policies
   - Verify Lambda functions are created with correct configuration
   - Verify IAM roles and policies are created
   - Verify DLQ is created
   - Compare resource list with unfixed template (should be identical plus API key resources)

5. **Output Preservation**: Verify all existing outputs are still available after fix
   - Deploy fixed template
   - Check CloudFormation outputs for MockNestApiUrl, MockNestApiId, MockStorageBucket, etc.
   - Verify all original outputs exist (plus new API key output)

6. **Parameter Validation Preservation**: Verify parameter validation continues to work after fix
   - Attempt to deploy with invalid DeploymentName (e.g., with spaces or special characters)
   - Verify deployment fails with constraint error (same as unfixed template)

### Unit Tests

- Test SAM template validation with cfn-lint or similar tools
- Test that API key resources have correct properties and dependencies
- Test that DeploymentName parameter accepts valid values and rejects invalid ones
- Test Shadow plugin minimize() configuration preserves required dependencies
- Test JAR contents include Spring Cloud Function adapter classes

### Property-Based Tests

- Generate random deployment configurations and verify API key infrastructure is created
- Generate random DeploymentName values (valid format) and verify single stage creation
- Generate random Lambda configurations and verify functions start successfully
- Test that all non-API-key, non-stage, non-parameter configurations produce identical results

### Integration Tests

- Deploy full stack with fixed configuration to AWS
- Test complete request flow: API Gateway → Lambda → S3 → Response
- Test API key authentication with valid and invalid keys
- Test multiple independent deployments with different DeploymentName values
- Verify no stage proliferation across multiple deployments and updates
- Test Lambda cold start and warm invocations with minimized JARs
