# Lambda Spring Context Isolation Bugfix Design

## Overview

MockNest Serverless currently deploys two separate AWS Lambda functions (runtime and generation) from a single monolithic `software/infra/aws` module. Both Lambda functions fail during initialization with `NoSuchBeanDefinitionException` for the `FunctionCatalog` bean because they lack proper Spring Boot application context isolation.

The fix splits the monolithic `software/infra/aws` module into three separate Gradle modules:
- `software/infra/aws/core` - Shared infrastructure (S3, WireMock, AWS SDK configuration)
- `software/infra/aws/runtime` - Runtime Lambda with its own Spring Boot application context
- `software/infra/aws/generation` - Generation Lambda with its own Spring Boot application context

Each Lambda module will have its own Spring Boot application class, function beans, and deployment JAR, ensuring proper Spring Cloud Function initialization with isolated `FunctionCatalog` instances.

## Glossary

- **Bug_Condition (C)**: Lambda initialization fails when Spring Cloud Function adapter cannot locate the `FunctionCatalog` bean
- **Property (P)**: Each Lambda successfully initializes with its own Spring context and `FunctionCatalog` containing function-specific beans
- **Preservation**: All existing runtime behavior (WireMock serving, S3 storage, AI generation) remains unchanged
- **FunctionCatalog**: Spring Cloud Function bean that manages function definitions and routing
- **Spring Boot Application Context**: The container that manages Spring beans for a specific application
- **Shadow JAR**: Fat JAR containing all dependencies, created by the Gradle Shadow plugin for Lambda deployment
- **Clean Architecture Boundaries**: Dependency flow rule where infrastructure → application → domain (never reversed)

## Bug Details

### Bug Condition

The bug manifests when either Lambda function attempts to initialize its Spring Cloud Function adapter. The `FunctionInvoker` class tries to locate the `FunctionCatalog` bean but fails because the Spring context is not properly initialized for the specific Lambda's function beans.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type LambdaInitializationEvent
  OUTPUT: boolean
  
  RETURN input.lambdaType IN ['runtime', 'generation']
         AND input.moduleStructure == 'monolithic'
         AND input.springContextInitialized == true
         AND NOT functionCatalogBeanExists(input.springContext)
END FUNCTION
```

### Examples

- **Runtime Lambda Initialization**: When the runtime Lambda cold starts, Spring Cloud Function adapter calls `FunctionInvoker.start()` which attempts to autowire `FunctionCatalog`. The bean is not found because the Spring context was initialized from the shared module without proper function bean registration. Expected: Runtime Lambda initializes successfully with `runtimeRouter` function bean. Actual: `NoSuchBeanDefinitionException` thrown.

- **Generation Lambda Initialization**: When the generation Lambda cold starts, Spring Cloud Function adapter calls `FunctionInvoker.start()` which attempts to autowire `FunctionCatalog`. The bean is not found because the Spring context was initialized from the shared module without proper function bean registration. Expected: Generation Lambda initializes successfully with `generationRouter` function bean. Actual: `NoSuchBeanDefinitionException` thrown.

- **Separate JAR Packaging**: When Gradle builds `shadowJarRuntime` and `shadowJarGeneration` tasks from the monolithic `software/infra/aws` module, both JARs contain the same Spring Boot application classes but with different exclusions. The Spring Boot application class in each JAR doesn't properly initialize the function beans specific to that Lambda. Expected: Each JAR contains its own Spring Boot application class that initializes only the relevant function beans. Actual: Both JARs share the same application classes, leading to initialization failures.

- **Edge Case - Module Dependency Resolution**: When both Lambda modules depend on `software/infra/aws/core`, Gradle must correctly resolve transitive dependencies without creating circular dependencies or duplicate classes. Expected: Clean dependency resolution with shared infrastructure code available to both Lambdas. Actual: Should work correctly with proper module structure.

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- WireMock runtime must continue to serve mocked endpoints with S3-backed storage
- AI generation must continue to generate mocks using Bedrock and store them in S3
- S3 storage operations must continue using existing ObjectStorage implementations
- WireMock configuration must continue using custom stores and extensions
- Clean architecture boundaries must remain enforced (infrastructure → application → domain)
- Gradle build must continue producing deployable Lambda JARs with all dependencies
- SAM template must continue deploying API Gateway, Lambda functions, and S3 buckets

**Scope:**
All runtime behavior that does NOT involve Lambda initialization and Spring context setup should be completely unaffected by this fix. This includes:
- Mock request handling and response serving
- Admin API operations (mapping CRUD, request inspection)
- AI generation request processing
- S3 read/write operations for mappings, files, and specifications
- WireMock engine behavior and extensions

## Hypothesized Root Cause

Based on the bug description and analysis of the working monolithic configuration, the root cause is:

1. **Shared Source Set with Multiple Application Classes**: The monolithic `software/infra/aws` module contains both `RuntimeApplication` and `GenerationApplication` Spring Boot classes in the same source set. When Spring Boot initializes, it may discover both application classes and become confused about which one to use, or component scanning may pick up beans from both contexts.

2. **Component Scanning Conflicts**: Both application classes exist in the same compiled output, so even though the Shadow JAR tasks specify different main classes, Spring's component scanning may discover and attempt to initialize beans from both runtime and generation packages, leading to conflicts or missing bean registrations.

3. **Spring Boot Auto-Configuration Ambiguity**: When both application classes are present in the JAR (even if one is excluded via Shadow JAR exclusions), Spring Boot's auto-configuration may not correctly determine which `@SpringBootApplication` class to use as the primary configuration source.

4. **Missing Spring Boot Application Context Separation**: Spring Cloud Function requires each Lambda to have its own isolated application context with properly registered function beans. The monolithic module structure with shared source sets prevents true isolation - even with separate Shadow JAR tasks, both application classes are compiled together.

**Evidence from Working Configuration**: The monolithic setup previously worked with a single `shadowJar` task that had:
- `mergeServiceFiles()` - Critical for Spring service provider discovery
- `append("META-INF/spring.handlers")` and other Spring metadata - Required for Spring Boot auto-configuration
- `minimize { exclude(...) }` - Preserves Spring Boot auto-configuration classes
- Single Main-Class attribute pointing to one application class

The current broken setup has TWO Shadow JAR tasks with different Main-Class attributes but the SAME source set, which likely causes Spring initialization ambiguity.

## Correctness Properties

Property 1: Bug Condition - Lambda Initialization Success

_For any_ Lambda initialization event where the Lambda is deployed from its own dedicated module (runtime or generation), the Spring Cloud Function adapter SHALL successfully locate the `FunctionCatalog` bean, initialize the function router, and complete Lambda cold start without throwing `NoSuchBeanDefinitionException`.

**Validates: Requirements 2.1, 2.2, 2.3, 2.4**

Property 2: Preservation - Runtime Behavior Unchanged

_For any_ runtime operation that is NOT Lambda initialization (mock serving, admin API, S3 storage, WireMock behavior), the fixed code SHALL produce exactly the same behavior as the original code, preserving all existing functionality for request handling, storage operations, and WireMock engine behavior.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7**

## Fix Implementation

### Changes Required

The fix involves restructuring the Gradle module hierarchy and ensuring each Lambda has its own isolated Spring Boot application context.

#### 1. Module Structure Changes

**File**: `settings.gradle.kts`

**Specific Changes**:
1. **Replace monolithic module**: Change `include(":software:infra:aws")` to three separate modules:
   - `include(":software:infra:aws:core")` - Shared infrastructure
   - `include(":software:infra:aws:runtime")` - Runtime Lambda module
   - `include(":software:infra:aws:generation")` - Generation Lambda module

#### 2. Create Core Module

**Directory**: `software/infra/aws/core/`

**Files to Create**:
- `build.gradle.kts` - Core module build configuration
- `src/main/kotlin/nl/vintik/mocknest/infra/aws/core/storage/` - Move S3 storage implementations
- `src/main/kotlin/nl/vintik/mocknest/infra/aws/core/wiremock/` - Move WireMock configuration (if exists)
- `src/main/resources/application-core.properties` - Shared configuration

**Specific Changes**:
1. **Core module dependencies**: Include only shared infrastructure dependencies:
   - Spring Boot starter (without web)
   - Kotlin AWS SDK (S3)
   - WireMock standalone
   - Domain and application modules
2. **No Spring Boot application class**: Core module provides beans but doesn't define an application entry point
3. **Move shared code**: Relocate all code from `infra/aws/core/` package to this module

#### 3. Create Runtime Module

**Directory**: `software/infra/aws/runtime/`

**Files to Create**:
- `build.gradle.kts` - Runtime module build configuration with Shadow JAR task
- `src/main/kotlin/nl/vintik/mocknest/infra/aws/runtime/RuntimeApplication.kt` - Spring Boot application class
- `src/main/kotlin/nl/vintik/mocknest/infra/aws/runtime/function/RuntimeLambdaHandler.kt` - Function bean configuration
- `src/main/kotlin/nl/vintik/mocknest/infra/aws/runtime/storage/` - Runtime-specific storage adapters
- `src/main/resources/application.properties` - Runtime-specific configuration

**Specific Changes**:
1. **Runtime module dependencies**:
   - Depend on `:software:infra:aws:core`
   - Depend on `:software:domain` and `:software:application`
   - Include Spring Cloud Function adapter
   - Include AWS Lambda runtime
   - Exclude Bedrock SDK (not needed for runtime)
2. **Spring Boot application class**: Define `RuntimeApplication` with component scanning limited to runtime packages
3. **Shadow JAR configuration**: Copy the EXACT working configuration from monolithic module:
   ```kotlin
   val shadowJar by getting(ShadowJar::class) {
       archiveFileName.set("mocknest-runtime.jar")
       destinationDirectory.set(file("${project.rootDir}/build/dist"))
       
       from(sourceSets.main.get().output)
       configurations = listOf(project.configurations.runtimeClasspath.get())
       
       // Exclude Bedrock SDK
       dependencies {
           exclude(dependency("aws.sdk.kotlin:bedrockruntime"))
       }
       
       minimize {
           exclude(project(":software:application"))
           exclude(project(":software:domain"))
           exclude(project(":software:infra:aws:core"))
           exclude(dependency("org.springframework.boot:spring-boot-autoconfigure"))
           exclude(dependency("org.springframework.cloud:spring-cloud-function-context"))
           exclude(dependency("org.springframework.cloud:spring-cloud-function-adapter-aws"))
           exclude(dependency("org.jetbrains.kotlin:kotlin-reflect"))
           exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib"))
       }
       
       isZip64 = true
       
       manifest {
           attributes["Main-Class"] = "nl.vintik.mocknest.infra.aws.runtime.RuntimeApplication"
           attributes["Start-Class"] = "nl.vintik.mocknest.infra.aws.runtime.RuntimeApplication"
       }
       
       // CRITICAL: These make Spring Boot work in fat JAR
       mergeServiceFiles()
       append("META-INF/spring.handlers")
       append("META-INF/spring.schemas")
       append("META-INF/spring.tooling")
       append("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
       append("META-INF/spring.factories")
       
       // Exclude unnecessary files
       exclude("META-INF/*.SF")
       exclude("META-INF/*.DSA")
       exclude("META-INF/*.RSA")
       exclude("META-INF/LICENSE*")
       exclude("META-INF/NOTICE*")
       exclude("META-INF/maven/**")
       exclude("module-info.class")
       
       // Size optimization exclusions
       exclude("org/springframework/boot/devtools/**")
       exclude("org/springframework/boot/test/**")
       exclude("org/springframework/test/**")
       exclude("assets/swagger-ui/**")
       exclude("samples/**")
       exclude("mozilla/public-suffix-list.txt")
       exclude("ucd/**")
       exclude("org/eclipse/jetty/websocket/**")
       exclude("org/eclipse/jetty/http2/**")
   }
   ```
4. **Move runtime code**: Relocate all code from `infra/aws/runtime/` package to this module

#### 4. Create Generation Module

**Directory**: `software/infra/aws/generation/`

**Files to Create**:
- `build.gradle.kts` - Generation module build configuration with Shadow JAR task
- `src/main/kotlin/nl/vintik/mocknest/infra/aws/generation/GenerationApplication.kt` - Spring Boot application class
- `src/main/kotlin/nl/vintik/mocknest/infra/aws/generation/function/GenerationLambdaHandler.kt` - Function bean configuration
- `src/main/kotlin/nl/vintik/mocknest/infra/aws/generation/storage/` - Generation-specific storage adapters
- `src/main/kotlin/nl/vintik/mocknest/infra/aws/generation/ai/` - AI generation implementations
- `src/main/resources/application.properties` - Generation-specific configuration

**Specific Changes**:
1. **Generation module dependencies**:
   - Depend on `:software:infra:aws:core`
   - Depend on `:software:domain` and `:software:application`
   - Include Spring Cloud Function adapter
   - Include AWS Lambda runtime
   - Include Bedrock SDK and Koog framework
2. **Spring Boot application class**: Define `GenerationApplication` with component scanning limited to generation packages
3. **Shadow JAR configuration**: Copy the EXACT working configuration from monolithic module:
   ```kotlin
   val shadowJar by getting(ShadowJar::class) {
       archiveFileName.set("mocknest-generation.jar")
       destinationDirectory.set(file("${project.rootDir}/build/dist"))
       
       from(sourceSets.main.get().output)
       configurations = listOf(project.configurations.runtimeClasspath.get())
       
       minimize {
           exclude(project(":software:application"))
           exclude(project(":software:domain"))
           exclude(project(":software:infra:aws:core"))
           exclude(dependency("org.springframework.boot:spring-boot-autoconfigure"))
           exclude(dependency("org.springframework.cloud:spring-cloud-function-context"))
           exclude(dependency("org.springframework.cloud:spring-cloud-function-adapter-aws"))
           exclude(dependency("org.jetbrains.kotlin:kotlin-reflect"))
           exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib"))
       }
       
       isZip64 = true
       
       manifest {
           attributes["Main-Class"] = "nl.vintik.mocknest.infra.aws.generation.GenerationApplication"
           attributes["Start-Class"] = "nl.vintik.mocknest.infra.aws.generation.GenerationApplication"
       }
       
       // CRITICAL: These make Spring Boot work in fat JAR
       mergeServiceFiles()
       append("META-INF/spring.handlers")
       append("META-INF/spring.schemas")
       append("META-INF/spring.tooling")
       append("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
       append("META-INF/spring.factories")
       
       // Exclude unnecessary files
       exclude("META-INF/*.SF")
       exclude("META-INF/*.DSA")
       exclude("META-INF/*.RSA")
       exclude("META-INF/LICENSE*")
       exclude("META-INF/NOTICE*")
       exclude("META-INF/maven/**")
       exclude("module-info.class")
       
       // Size optimization exclusions
       exclude("org/springframework/boot/devtools/**")
       exclude("org/springframework/boot/test/**")
       exclude("org/springframework/test/**")
       exclude("assets/swagger-ui/**")
       exclude("samples/**")
       exclude("mozilla/public-suffix-list.txt")
       exclude("ucd/**")
       exclude("org/eclipse/jetty/websocket/**")
       exclude("org/eclipse/jetty/http2/**")
   }
   ```
4. **Move generation code**: Relocate all code from `infra/aws/generation/` package to this module

#### 5. Update SAM Template

**File**: `deployment/aws/sam/template.yaml`

**Specific Changes**:
1. **Update CodeUri paths**: Change Lambda function `CodeUri` to point to new JAR locations:
   - Runtime Lambda: `../../../build/dist/mocknest-runtime.jar` → `../../../software/infra/aws/runtime/build/libs/mocknest-runtime.jar`
   - Generation Lambda: `../../../build/dist/mocknest-generation.jar` → `../../../software/infra/aws/generation/build/libs/mocknest-generation.jar`
2. **Update Handler references**: Ensure `Handler` property points to correct function names:
   - Runtime: `org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest` with `SPRING_CLOUD_FUNCTION_DEFINITION=runtimeRouter`
   - Generation: `org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest` with `SPRING_CLOUD_FUNCTION_DEFINITION=generationRouter`

#### 6. Update Build Scripts

**File**: `deployment/aws/sam/build.sh`

**Specific Changes**:
1. **Update Gradle build command**: Change from `./gradlew :software:infra:aws:buildAllLambdas` to:
   ```bash
   ./gradlew :software:infra:aws:runtime:shadowJar :software:infra:aws:generation:shadowJar
   ```
2. **Update JAR copy logic**: Copy JARs from new module locations to SAM build directory

#### 7. Update GitHub Actions Workflows

**Files**: `.github/workflows/workflow-build.yml`

**Specific Changes**:
1. **Update build step**: Change Gradle task from `:software:infra:aws:buildAllLambdas` to build both modules:
   ```yaml
   - name: Build Lambda JARs
     run: ./gradlew :software:infra:aws:runtime:shadowJar :software:infra:aws:generation:shadowJar
   ```

### Migration Plan

The migration follows this sequence to minimize disruption:

1. **Create core module structure** (no code moves yet)
2. **Create runtime module structure** (no code moves yet)
3. **Create generation module structure** (no code moves yet)
4. **Update settings.gradle.kts** to include new modules
5. **Move shared code to core module** (storage, WireMock config)
6. **Move runtime code to runtime module** (application class, handler, storage)
7. **Move generation code to generation module** (application class, handler, storage, AI)
8. **Delete old monolithic module** (`software/infra/aws`)
9. **Update SAM template** with new JAR paths
10. **Update build scripts** with new Gradle tasks
11. **Update GitHub Actions** with new build commands
12. **Run integration tests** to verify behavior preservation

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, surface counterexamples that demonstrate the bug on unfixed code, then verify the fix works correctly and preserves existing behavior.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate the bug BEFORE implementing the fix. Confirm or refute the root cause analysis. If we refute, we will need to re-hypothesize.

**Test Plan**: Attempt to deploy and invoke both Lambda functions using the current monolithic module structure. Observe the `NoSuchBeanDefinitionException` failures during cold start initialization. Examine CloudWatch logs to confirm the exact failure point in Spring Cloud Function initialization.

**Test Cases**:
1. **Runtime Lambda Cold Start Test**: Deploy runtime Lambda from monolithic module, invoke via API Gateway, observe initialization failure (will fail on unfixed code)
2. **Generation Lambda Cold Start Test**: Deploy generation Lambda from monolithic module, invoke via API Gateway, observe initialization failure (will fail on unfixed code)
3. **Spring Context Inspection Test**: Add debug logging to observe Spring context initialization and bean registration, confirm `FunctionCatalog` is missing (will fail on unfixed code)
4. **Component Scanning Test**: Verify which packages are scanned during Spring context initialization, identify missing or conflicting scan paths (may reveal additional issues on unfixed code)

**Expected Counterexamples**:
- Lambda initialization fails with `NoSuchBeanDefinitionException` for `FunctionCatalog`
- Possible causes: shared module structure, incorrect component scanning, missing Spring Boot auto-configuration, conflicting application classes

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds (Lambda initialization), the fixed function produces the expected behavior (successful initialization with FunctionCatalog).

**Pseudocode:**
```
FOR ALL lambdaType IN ['runtime', 'generation'] DO
  result := initializeLambda_fixed(lambdaType)
  ASSERT result.springContextInitialized == true
  ASSERT result.functionCatalogExists == true
  ASSERT result.functionBeanRegistered == true
  ASSERT result.initializationException == null
END FOR
```

**Test Plan**: Deploy both Lambda functions from their new dedicated modules. Invoke each Lambda via API Gateway and verify successful cold start initialization. Examine CloudWatch logs to confirm Spring context initialization completes without errors and `FunctionCatalog` bean is properly registered.

**Test Cases**:
1. **Runtime Lambda Initialization Success**: Deploy runtime Lambda from `software/infra/aws/runtime` module, invoke via API Gateway, verify successful initialization and request handling
2. **Generation Lambda Initialization Success**: Deploy generation Lambda from `software/infra/aws/generation` module, invoke via API Gateway, verify successful initialization and request handling
3. **FunctionCatalog Bean Verification**: Add debug logging to confirm `FunctionCatalog` bean exists in Spring context after initialization
4. **Function Bean Registration**: Verify `runtimeRouter` and `generationRouter` function beans are properly registered in their respective `FunctionCatalog` instances

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold (runtime operations after initialization), the fixed function produces the same result as the original function.

**Pseudocode:**
```
FOR ALL operation WHERE NOT isBugCondition(operation) DO
  ASSERT executeOperation_original(operation) = executeOperation_fixed(operation)
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because:
- It generates many test cases automatically across the input domain
- It catches edge cases that manual unit tests might miss
- It provides strong guarantees that behavior is unchanged for all non-initialization operations

**Test Plan**: Observe behavior on UNFIXED code first for mock serving, admin API, and AI generation operations, then write property-based tests capturing that behavior. Run the same tests on FIXED code and verify identical results.

**Test Cases**:
1. **Mock Serving Preservation**: Verify runtime Lambda serves mocked endpoints identically before and after fix (observe unfixed behavior, then test fixed)
2. **Admin API Preservation**: Verify admin API operations (create/update/delete mappings, request inspection) work identically (observe unfixed behavior, then test fixed)
3. **AI Generation Preservation**: Verify generation Lambda processes AI requests identically (observe unfixed behavior, then test fixed)
4. **S3 Storage Preservation**: Verify S3 read/write operations for mappings and files work identically (observe unfixed behavior, then test fixed)
5. **WireMock Behavior Preservation**: Verify WireMock engine behavior (matching, response serving, extensions) works identically (observe unfixed behavior, then test fixed)

### Unit Tests

- Test Spring Boot application class initialization for each Lambda module
- Test component scanning configuration includes correct packages
- Test function bean registration in Spring context
- Test Shadow JAR packaging includes all required dependencies
- Test module dependency resolution (core → runtime, core → generation)
- Test that runtime JAR excludes Bedrock SDK
- Test that generation JAR includes Bedrock SDK and Koog framework

### Property-Based Tests

- Generate random mock requests and verify runtime Lambda handles them identically before and after fix
- Generate random admin API requests and verify identical behavior before and after fix
- Generate random AI generation requests and verify identical behavior before and after fix
- Test that all S3 operations produce identical results across many scenarios

### Integration Tests

- Test full deployment using SAM template with new module structure
- Test runtime Lambda cold start and warm invocation
- Test generation Lambda cold start and warm invocation
- Test that both Lambdas can access shared S3 buckets
- Test that WireMock configuration loads correctly in runtime Lambda
- Test that Bedrock client initializes correctly in generation Lambda
- Test end-to-end mock serving flow (create mapping via admin API, serve mock via runtime Lambda)
- Test end-to-end AI generation flow (submit generation request, verify mock created in S3)
