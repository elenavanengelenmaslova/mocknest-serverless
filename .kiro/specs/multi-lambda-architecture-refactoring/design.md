# Multi-Lambda Architecture Refactoring - Design

## Overview
This design implements separate Lambda functions for runtime and generation features using multiple shadow JAR tasks from a single AWS infrastructure module. Shadow JAR's `minimize()` feature automatically removes unused code, resulting in smaller deployment packages and faster cold starts.

## Architecture

### Current Architecture (Monolithic)
```
┌─────────────────────────────────────────┐
│     API Gateway (all routes)            │
└─────────────────┬───────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────┐
│   Single Lambda Function                │
│   - Runtime handler (WireMock)          │
│   - Generation handler (AI)             │
│   - All dependencies loaded             │
│   JAR Size: ~80-100 MB                  │
│   Cold Start: 8-12 seconds              │
└─────────────────────────────────────────┘
```

### Target Architecture (Multi-Lambda)
```
┌─────────────────────────────────────────┐
│          API Gateway                     │
│  Routes: /__admin/*, /mocknest/*        │
│          /ai/*                           │
└──────────┬──────────────────────┬───────┘
           │                      │
           ▼                      ▼
┌──────────────────┐   ┌──────────────────┐
│ Runtime Lambda   │   │ Generation Lambda│
│ - WireMock       │   │ - AI generation  │
│ - S3 storage     │   │ - Bedrock        │
│ - Admin API      │   │ - Koog           │
│                  │   │ - OpenAPI parser │
│ JAR: ~40-50 MB   │   │ JAR: ~35-45 MB   │
│ Cold: 4-6 sec    │   │ Cold: 5-7 sec    │
└──────────────────┘   └──────────────────┘
```

## Component Design

### 1. Lambda Handler Classes

#### RuntimeLambdaHandler
**Location:** `software/infra/aws/src/main/kotlin/nl/vintik/mocknest/infra/aws/runtime/RuntimeLambdaHandler.kt`

**Responsibilities:**
- Handle WireMock admin API requests (`/__admin/*`)
- Handle mock endpoint requests (`/mocknest/*`)
- Route to appropriate use cases
- Entry point for runtime shadow JAR minimization

**Implementation:**
```kotlin
package nl.vintik.mocknest.infra.aws.runtime

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import nl.vintik.mocknest.application.runtime.usecases.HandleAdminRequest
import nl.vintik.mocknest.application.runtime.usecases.HandleClientRequest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.function.Function

@Configuration
class RuntimeLambdaHandler(
    private val handleClientRequest: HandleClientRequest,
    private val handleAdminRequest: HandleAdminRequest
) {
    @Bean
    fun runtimeRouter(): Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
        return Function { event ->
            // Route to runtime use cases only
            // Implementation similar to current router but without AI paths
        }
    }
}
```

#### GenerationLambdaHandler
**Location:** `software/infra/aws/src/main/kotlin/nl/vintik/mocknest/infra/aws/generation/GenerationLambdaHandler.kt`

**Responsibilities:**
- Handle AI generation requests (`/ai/*`)
- Route to AI generation use cases
- Entry point for generation shadow JAR minimization

**Implementation:**
```kotlin
package nl.vintik.mocknest.infra.aws.generation

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import nl.vintik.mocknest.application.generation.usecases.HandleAIGenerationRequest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.function.Function

@Configuration
class GenerationLambdaHandler(
    private val handleAIGenerationRequest: HandleAIGenerationRequest
) {
    @Bean
    fun generationRouter(): Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
        return Function { event ->
            // Route to generation use cases only
            // Handle /ai/* paths
        }
    }
}
```

### 2. Spring Boot Application Classes

#### RuntimeApplication
**Location:** `software/infra/aws/src/main/kotlin/nl/vintik/mocknest/infra/aws/runtime/RuntimeApplication.kt`

**Purpose:** Entry point for runtime Lambda, scans only runtime-related packages

```kotlin
package nl.vintik.mocknest.infra.aws.runtime

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = [
    "nl.vintik.mocknest.application.runtime",
    "nl.vintik.mocknest.application.core",
    "nl.vintik.mocknest.infra.aws.runtime",
    "nl.vintik.mocknest.infra.aws.core"
])
class RuntimeApplication

fun main(args: Array<String>) {
    runApplication<RuntimeApplication>(*args)
}
```

#### GenerationApplication
**Location:** `software/infra/aws/src/main/kotlin/nl/vintik/mocknest/infra/aws/generation/GenerationApplication.kt`

**Purpose:** Entry point for generation Lambda, scans only generation-related packages

```kotlin
package nl.vintik.mocknest.infra.aws.generation

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = [
    "nl.vintik.mocknest.application.generation",
    "nl.vintik.mocknest.application.core",
    "nl.vintik.mocknest.infra.aws.generation",
    "nl.vintik.mocknest.infra.aws.core"
])
class GenerationApplication

fun main(args: Array<String>) {
    runApplication<GenerationApplication>(*args)
}
```

### 3. Gradle Build Configuration

#### Shadow JAR Tasks
**Location:** `software/infra/aws/build.gradle.kts`

**Runtime Lambda Task:**
```kotlin
tasks.register<ShadowJar>("shadowJarRuntime") {
    archiveFileName.set("mocknest-runtime.jar")
    destinationDirectory.set(file("${project.rootDir}/build/dist"))
    
    // Use main source set
    from(sourceSets.main.get().output)
    configurations = listOf(project.configurations.runtimeClasspath.get())
    
    // Enable automatic minimization
    minimize {
        // Exclude Spring Boot and AWS Lambda essentials from minimization
        exclude(dependency("org.springframework.boot:.*"))
        exclude(dependency("org.springframework.cloud:.*"))
        exclude(dependency("com.amazonaws:aws-lambda-java-.*"))
        exclude(dependency("org.wiremock:.*"))
        exclude(dependency("aws.sdk.kotlin:s3"))
    }
    
    isZip64 = true
    
    manifest {
        attributes["Main-Class"] = "nl.vintik.mocknest.infra.aws.runtime.RuntimeApplication"
        attributes["Start-Class"] = "nl.vintik.mocknest.infra.aws.runtime.RuntimeApplication"
    }
    
    // Merge service files
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
}
```

**Generation Lambda Task:**
```kotlin
tasks.register<ShadowJar>("shadowJarGeneration") {
    archiveFileName.set("mocknest-generation.jar")
    destinationDirectory.set(file("${project.rootDir}/build/dist"))
    
    // Use main source set
    from(sourceSets.main.get().output)
    configurations = listOf(project.configurations.runtimeClasspath.get())
    
    // Enable automatic minimization
    minimize {
        // Exclude Spring Boot and AWS Lambda essentials from minimization
        exclude(dependency("org.springframework.boot:.*"))
        exclude(dependency("org.springframework.cloud:.*"))
        exclude(dependency("com.amazonaws:aws-lambda-java-.*"))
        exclude(dependency("aws.sdk.kotlin:s3"))
        exclude(dependency("aws.sdk.kotlin:bedrockruntime"))
        exclude(dependency("ai.koog:.*"))
        exclude(dependency("io.swagger.parser.v3:.*"))
    }
    
    isZip64 = true
    
    manifest {
        attributes["Main-Class"] = "nl.vintik.mocknest.infra.aws.generation.GenerationApplication"
        attributes["Start-Class"] = "nl.vintik.mocknest.infra.aws.generation.GenerationApplication"
    }
    
    // Merge service files
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
}
```

**Build All Task:**
```kotlin
tasks.register("buildAllLambdas") {
    dependsOn("shadowJarRuntime", "shadowJarGeneration")
    description = "Build both runtime and generation Lambda JARs"
    group = "build"
}
```

### 4. SAM Template Updates

#### Lambda Function Resources
**Location:** `deployment/aws/sam/template.yaml`

**Runtime Lambda Function:**
```yaml
MockNestRuntimeFunction:
  Type: AWS::Serverless::Function
  Properties:
    FunctionName: !Sub "${AWS::StackName}-runtime"
    CodeUri: ../../../build/dist/mocknest-runtime.jar
    Handler: org.springframework.cloud.function.adapter.aws.FunctionInvoker
    Role: !GetAtt MockNestLambdaRole.Arn
    ReservedConcurrentExecutions: 10
    Environment:
      Variables:
        MOCK_STORAGE_BUCKET: !Ref MockStorage
        MOCKNEST_S3_BUCKET_NAME: !Ref MockStorage
        SPRING_CLOUD_FUNCTION_DEFINITION: "runtimeRouter"
        MAIN_CLASS: "nl.vintik.mocknest.infra.aws.runtime.RuntimeApplication"
        JAVA_TOOL_OPTIONS: "-XX:+TieredCompilation -XX:TieredStopAtLevel=1"
    DeadLetterQueue:
      Type: SQS
      TargetArn: !GetAtt MockNestDLQ.Arn
    Tags:
      Application: MockNest-Serverless
      Component: Runtime
    Events:
      AdminRoutes:
        Type: Api
        Properties:
          RestApiId: !Ref MockNestApi
          Path: /__admin/{proxy+}
          Method: ANY
      MockRoutes:
        Type: Api
        Properties:
          RestApiId: !Ref MockNestApi
          Path: /mocknest/{proxy+}
          Method: ANY
```

**Generation Lambda Function:**
```yaml
MockNestGenerationFunction:
  Type: AWS::Serverless::Function
  Condition: IsAIEnabled
  Properties:
    FunctionName: !Sub "${AWS::StackName}-generation"
    CodeUri: ../../../build/dist/mocknest-generation.jar
    Handler: org.springframework.cloud.function.adapter.aws.FunctionInvoker
    Role: !GetAtt MockNestLambdaRole.Arn
    ReservedConcurrentExecutions: 5
    Timeout: 300  # 5 minutes for AI operations
    Environment:
      Variables:
        MOCK_STORAGE_BUCKET: !Ref MockStorage
        MOCKNEST_S3_BUCKET_NAME: !Ref MockStorage
        SPRING_CLOUD_FUNCTION_DEFINITION: "generationRouter"
        MAIN_CLASS: "nl.vintik.mocknest.infra.aws.generation.GenerationApplication"
        JAVA_TOOL_OPTIONS: "-XX:+TieredCompilation -XX:TieredStopAtLevel=1"
    DeadLetterQueue:
      Type: SQS
      TargetArn: !GetAtt MockNestDLQ.Arn
    Tags:
      Application: MockNest-Serverless
      Component: Generation
    Events:
      AIRoutes:
        Type: Api
        Properties:
          RestApiId: !Ref MockNestApi
          Path: /ai/{proxy+}
          Method: ANY
```

## How Shadow JAR Minimize Works

### Reachability Analysis
1. **Entry Point:** Shadow JAR starts from the main class specified in the manifest
2. **Bytecode Scanning:** Analyzes all method calls, field accesses, and class references
3. **Transitive Closure:** Follows all reachable code paths recursively
4. **Exclusion:** Removes all classes not reachable from entry points
5. **Preservation:** Keeps explicitly excluded dependencies intact

### What Gets Removed

**Runtime Lambda (automatically removed by minimize):**
- ❌ `aws.sdk.kotlin.services.bedrockruntime.*` - Not reachable from RuntimeApplication
- ❌ `ai.koog.*` - Not reachable from RuntimeApplication
- ❌ `io.swagger.parser.*` - Not reachable from RuntimeApplication
- ❌ Unused Spring Boot auto-configurations
- ❌ Unused Jetty components

**Generation Lambda (automatically removed by minimize):**
- ❌ `org.wiremock.standalone.*` - Server classes not needed (only types used)
- ❌ `org.eclipse.jetty.server.*` - Not needed for generation
- ❌ `org.eclipse.jetty.servlet.*` - Not needed for generation
- ❌ Unused Spring Boot auto-configurations

### What Gets Kept

**Both Lambdas:**
- ✅ Spring Boot core (explicitly excluded from minimization)
- ✅ Spring Cloud Function (explicitly excluded from minimization)
- ✅ AWS Lambda runtime (explicitly excluded from minimization)
- ✅ AWS SDK S3 (explicitly excluded from minimization)
- ✅ Application and domain classes (reachable from handlers)
- ✅ Kotlin standard library (reachable from all code)

**Runtime Lambda Only:**
- ✅ WireMock (explicitly excluded from minimization)
- ✅ WireMock dependencies (reachable from WireMock)

**Generation Lambda Only:**
- ✅ Bedrock SDK (explicitly excluded from minimization)
- ✅ Koog framework (explicitly excluded from minimization)
- ✅ OpenAPI parser (explicitly excluded from minimization)

## API Gateway Routing

### Route Configuration

**Runtime Lambda Routes:**
- `/__admin/*` - WireMock admin API
- `/mocknest/*` - Mock endpoints

**Generation Lambda Routes:**
- `/ai/*` - AI generation endpoints

### Request Flow

```
User Request
    │
    ▼
API Gateway
    │
    ├─ /__admin/* ──────► Runtime Lambda ──► HandleAdminRequest
    │
    ├─ /mocknest/* ─────► Runtime Lambda ──► HandleClientRequest
    │
    └─ /ai/* ───────────► Generation Lambda ──► HandleAIGenerationRequest
```

## Deployment Process

### Build Steps
1. `./gradlew clean` - Clean previous builds
2. `./gradlew buildAllLambdas` - Build both shadow JARs
3. Verify JARs exist in `build/dist/`:
   - `mocknest-runtime.jar`
   - `mocknest-generation.jar`

### Deployment Steps
1. `cd deployment/aws/sam`
2. `./build.sh` - Builds Lambda JARs
3. `./deploy.sh` - Deploys to AWS using SAM
4. SAM uploads both JARs and creates/updates Lambda functions

### Deployment Flexibility

**Runtime Only:**
```bash
sam deploy --parameter-overrides EnableAI=false
```
- Deploys only runtime Lambda
- Generation Lambda not created
- Smaller infrastructure footprint

**Runtime + Generation:**
```bash
sam deploy --parameter-overrides EnableAI=true
```
- Deploys both Lambdas
- Full AI capabilities available

## Testing Strategy

### Unit Tests
**Location:** `software/infra/aws/src/test/kotlin`

**No changes required:**
- All existing tests remain in the same module
- Tests can validate both handlers
- Spring Boot test slices work as before

### Integration Tests
**Runtime Lambda Integration Test:**
```kotlin
@SpringBootTest(classes = [RuntimeApplication::class])
class RuntimeLambdaIntegrationTest {
    @Autowired
    private lateinit var runtimeRouter: Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>
    
    @Test
    fun `should handle admin request`() {
        val event = APIGatewayProxyRequestEvent()
            .withPath("/__admin/mappings")
            .withHttpMethod("GET")
        
        val response = runtimeRouter.apply(event)
        
        assertEquals(200, response.statusCode)
    }
}
```

**Generation Lambda Integration Test:**
```kotlin
@SpringBootTest(classes = [GenerationApplication::class])
class GenerationLambdaIntegrationTest {
    @Autowired
    private lateinit var generationRouter: Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>
    
    @Test
    fun `should handle AI generation request`() {
        val event = APIGatewayProxyRequestEvent()
            .withPath("/ai/generate")
            .withHttpMethod("POST")
            .withBody("""{"spec": "..."}""")
        
        val response = generationRouter.apply(event)
        
        assertEquals(200, response.statusCode)
    }
}
```

### LocalStack Integration Tests
**No changes required:**
- Existing LocalStack tests continue to work
- S3 integration tests validate both Lambdas
- TestContainers setup remains the same

## Migration Path

### Phase 1: Create Handler Classes
1. Create `RuntimeLambdaHandler.kt`
2. Create `GenerationLambdaHandler.kt`
3. Create `RuntimeApplication.kt`
4. Create `GenerationApplication.kt`
5. Extract routing logic from existing `MockNestLambdaHandler`

### Phase 2: Configure Shadow JAR Tasks
1. Add `shadowJarRuntime` task to `build.gradle.kts`
2. Add `shadowJarGeneration` task to `build.gradle.kts`
3. Add `buildAllLambdas` task
4. Test builds locally

### Phase 3: Update SAM Template
1. Add `MockNestRuntimeFunction` resource
2. Add `MockNestGenerationFunction` resource
3. Update API Gateway event mappings
4. Update IAM roles if needed

### Phase 4: Update Deployment Scripts
1. Modify `build.sh` to build both JARs
2. Verify `deploy.sh` works with new template
3. Test deployment to dev environment

### Phase 5: Validation
1. Deploy to test environment
2. Verify runtime Lambda handles admin/mock requests
3. Verify generation Lambda handles AI requests
4. Measure JAR sizes and cold start times
5. Run full test suite

## Performance Expectations

### JAR Size Comparison

| Lambda | Current (Monolithic) | With minimize() | Reduction |
|--------|---------------------|-----------------|-----------|
| Runtime | ~80-100 MB | ~40-50 MB | 50% |
| Generation | ~80-100 MB | ~35-45 MB | 55% |

### Cold Start Comparison

| Lambda | Current (Monolithic) | With minimize() | Improvement |
|--------|---------------------|-----------------|-------------|
| Runtime | 8-12 seconds | 4-6 seconds | 50% |
| Generation | 8-12 seconds | 5-7 seconds | 42% |

### Memory Usage
- Runtime Lambda: 1024 MB (same as current)
- Generation Lambda: 1024 MB (same as current)
- Can be tuned independently after deployment

## Rollback Strategy

### If Issues Occur
1. Revert SAM template to single Lambda configuration
2. Deploy using original `mocknest-serverless-aws.jar`
3. All functionality returns to monolithic deployment

### Rollback is Safe Because
- No changes to domain or application layers
- No changes to business logic
- Only infrastructure and packaging changes
- Original JAR can still be built using existing `shadowJar` task

## Future Enhancements

### Analysis Lambda (Future)
When analysis feature is ready:
1. Create `AnalysisLambdaHandler.kt`
2. Create `AnalysisApplication.kt`
3. Add `shadowJarAnalysis` task
4. Add `MockNestAnalysisFunction` to SAM template
5. Route `/ai/analyze/*` paths to analysis Lambda

### Native Compilation (Future)
Consider GraalVM native-image for even faster cold starts:
- Runtime Lambda: < 1 second cold start
- Generation Lambda: < 2 seconds cold start
- Requires additional configuration and testing

## Correctness Properties

### Property 1: JAR Size Reduction
**Validates: Requirements 3.2, 3.3**

**Property:** Each minimized Lambda JAR must be at least 30% smaller than the monolithic JAR

**Test Strategy:**
```kotlin
@Test
fun `runtime JAR should be at least 30 percent smaller than monolithic JAR`() {
    val monolithicSize = File("build/dist/mocknest-serverless-aws.jar").length()
    val runtimeSize = File("build/dist/mocknest-runtime.jar").length()
    
    val reduction = (monolithicSize - runtimeSize).toDouble() / monolithicSize
    
    assertTrue(reduction >= 0.30, "Runtime JAR reduction was ${reduction * 100}%, expected >= 30%")
}

@Test
fun `generation JAR should be at least 30 percent smaller than monolithic JAR`() {
    val monolithicSize = File("build/dist/mocknest-serverless-aws.jar").length()
    val generationSize = File("build/dist/mocknest-generation.jar").length()
    
    val reduction = (monolithicSize - generationSize).toDouble() / monolithicSize
    
    assertTrue(reduction >= 0.30, "Generation JAR reduction was ${reduction * 100}%, expected >= 30%")
}
```

### Property 2: Dependency Exclusion
**Validates: Requirements 3.2, 3.3**

**Property:** Runtime JAR must not contain Bedrock, Koog, or OpenAPI parser classes

**Test Strategy:**
```kotlin
@Test
fun `runtime JAR should not contain Bedrock SDK classes`() {
    val jarFile = JarFile("build/dist/mocknest-runtime.jar")
    val bedrockClasses = jarFile.entries().asSequence()
        .filter { it.name.startsWith("aws/sdk/kotlin/services/bedrockruntime/") }
        .toList()
    
    assertTrue(bedrockClasses.isEmpty(), "Runtime JAR contains ${bedrockClasses.size} Bedrock classes")
}

@Test
fun `runtime JAR should not contain Koog framework classes`() {
    val jarFile = JarFile("build/dist/mocknest-runtime.jar")
    val koogClasses = jarFile.entries().asSequence()
        .filter { it.name.startsWith("ai/koog/") }
        .toList()
    
    assertTrue(koogClasses.isEmpty(), "Runtime JAR contains ${koogClasses.size} Koog classes")
}

@Test
fun `generation JAR should not contain WireMock server classes`() {
    val jarFile = JarFile("build/dist/mocknest-generation.jar")
    val wiremockServerClasses = jarFile.entries().asSequence()
        .filter { it.name.startsWith("org/wiremock/standalone/") }
        .toList()
    
    assertTrue(wiremockServerClasses.isEmpty(), "Generation JAR contains ${wiremockServerClasses.size} WireMock server classes")
}
```

### Property 3: Handler Routing
**Validates: Requirements 4.1, 4.2, 4.3, 4.4**

**Property:** Each Lambda handler must route requests only to its designated use cases

**Test Strategy:**
```kotlin
@Test
fun `runtime handler should route admin requests to HandleAdminRequest`() {
    val event = APIGatewayProxyRequestEvent()
        .withPath("/__admin/mappings")
        .withHttpMethod("GET")
    
    val response = runtimeRouter.apply(event)
    
    // Verify HandleAdminRequest was called (via mock or spy)
    verify(handleAdminRequest).invoke(any(), any())
    verify(handleAIGenerationRequest, never()).invoke(any(), any())
}

@Test
fun `generation handler should route AI requests to HandleAIGenerationRequest`() {
    val event = APIGatewayProxyRequestEvent()
        .withPath("/ai/generate")
        .withHttpMethod("POST")
    
    val response = generationRouter.apply(event)
    
    // Verify HandleAIGenerationRequest was called
    verify(handleAIGenerationRequest).invoke(any(), any())
}
```

### Property 4: Independent Deployment
**Validates: Requirements 1.2, 1.3**

**Property:** Each Lambda JAR must be deployable independently without the other

**Test Strategy:**
```kotlin
@Test
fun `runtime JAR should contain all required classes for runtime operations`() {
    val jarFile = JarFile("build/dist/mocknest-runtime.jar")
    
    // Verify essential runtime classes are present
    assertNotNull(jarFile.getEntry("nl/vintik/mocknest/infra/aws/runtime/RuntimeApplication.class"))
    assertNotNull(jarFile.getEntry("nl/vintik/mocknest/infra/aws/runtime/RuntimeLambdaHandler.class"))
    assertNotNull(jarFile.getEntry("nl/vintik/mocknest/application/runtime/usecases/HandleAdminRequest.class"))
    assertNotNull(jarFile.getEntry("nl/vintik/mocknest/application/runtime/usecases/HandleClientRequest.class"))
}

@Test
fun `generation JAR should contain all required classes for generation operations`() {
    val jarFile = JarFile("build/dist/mocknest-generation.jar")
    
    // Verify essential generation classes are present
    assertNotNull(jarFile.getEntry("nl/vintik/mocknest/infra/aws/generation/GenerationApplication.class"))
    assertNotNull(jarFile.getEntry("nl/vintik/mocknest/infra/aws/generation/GenerationLambdaHandler.class"))
    assertNotNull(jarFile.getEntry("nl/vintik/mocknest/application/generation/usecases/HandleAIGenerationRequest.class"))
}
```

### Property 5: API Gateway Routing
**Validates: Requirements 6.1, 6.2, 6.3**

**Property:** API Gateway must route requests to the correct Lambda based on path

**Test Strategy:**
```kotlin
@Test
fun `API Gateway should route admin paths to runtime Lambda`() {
    // Integration test with LocalStack API Gateway
    val response = apiGatewayClient.invoke(
        path = "/__admin/mappings",
        method = "GET"
    )
    
    // Verify runtime Lambda was invoked (check CloudWatch logs or X-Ray traces)
    val logs = cloudWatchClient.getLogEvents(logGroupName = "/aws/lambda/runtime")
    assertTrue(logs.any { it.message.contains("Processing admin request") })
}

@Test
fun `API Gateway should route AI paths to generation Lambda`() {
    // Integration test with LocalStack API Gateway
    val response = apiGatewayClient.invoke(
        path = "/ai/generate",
        method = "POST",
        body = """{"spec": "..."}"""
    )
    
    // Verify generation Lambda was invoked
    val logs = cloudWatchClient.getLogEvents(logGroupName = "/aws/lambda/generation")
    assertTrue(logs.any { it.message.contains("Processing AI generation request") })
}
```

## Risk Mitigation

### Risk: Shadow JAR minimize() removes required classes
**Mitigation:**
- Explicitly exclude essential dependencies from minimization
- Comprehensive integration tests validate all functionality
- Test both JARs in LocalStack before deployment

### Risk: Spring Boot auto-configuration breaks
**Mitigation:**
- Exclude Spring Boot from minimization
- Use explicit `scanBasePackages` in `@SpringBootApplication`
- Test Spring context initialization in integration tests

### Risk: Cold start times don't improve as expected
**Mitigation:**
- Measure actual cold start times in test environment
- Adjust Lambda memory settings if needed
- Consider additional optimizations (tiered compilation, SnapStart)

### Risk: Deployment complexity increases
**Mitigation:**
- Automated build script handles both JARs
- SAM template manages both Lambdas
- Rollback to monolithic deployment is straightforward

## Success Criteria

1. ✅ Both Lambda JARs build successfully
2. ✅ Runtime JAR is 40-50 MB (50% reduction)
3. ✅ Generation JAR is 35-45 MB (55% reduction)
4. ✅ Runtime Lambda cold start < 6 seconds (50% improvement)
5. ✅ Generation Lambda cold start < 8 seconds (40% improvement)
6. ✅ All existing tests pass without modification
7. ✅ API Gateway routes requests correctly
8. ✅ Both Lambdas can be deployed independently
9. ✅ No changes to business logic or domain models
10. ✅ Test coverage remains at 90%+
