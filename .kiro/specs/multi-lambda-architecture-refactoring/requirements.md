# Multi-Lambda Architecture Refactoring - Requirements

## Overview
Refactor the current monolithic AWS Lambda deployment into separate Lambda functions for runtime and generation features by creating multiple shadow JAR tasks from a single AWS infrastructure module. Each JAR will have a different Lambda handler entry point and use `minimize()` to automatically exclude unused dependencies.

## Background
Currently, both runtime and generation features are deployed as a single Lambda function. This creates several issues:
- Unnecessary cold start overhead (runtime Lambda loads Bedrock/Koog, generation Lambda loads full WireMock)
- Difficult to scale features independently
- Larger deployment package size than necessary

## Approach
**Single Module + Multiple Shadow JARs:**
- Keep all existing modules unchanged (`domain`, `application`, `infra/aws`)
- Create two shadow JAR tasks in `infra/aws/build.gradle.kts`:
  - `shadowJarRuntime` - Entry point: Runtime Lambda handler
  - `shadowJarGeneration` - Entry point: Generation Lambda handler
- Use `minimize()` on each task to automatically exclude unused code
- SAM template defines two Lambda functions pointing to different JARs

## User Stories

### 1. Independent Lambda Deployments
**As a** DevOps engineer  
**I want** each feature deployed as a separate Lambda function  
**So that** I can scale, monitor, and maintain each feature independently

**Acceptance Criteria:**
- 1.1 Two separate Lambda functions exist: `MockNestRuntime` and `MockNestGeneration`
- 1.2 Each Lambda has its own deployment artifact (JAR file) built from the same module
- 1.3 Each Lambda can be deployed independently without affecting others
- 1.4 SAM template defines both Lambda functions with appropriate configurations
- 1.5 Each Lambda has appropriate memory, timeout, and concurrency settings for its workload

### 2. Multiple Shadow JAR Tasks in Single Module
**As a** developer  
**I want** multiple shadow JAR tasks in the same Gradle module  
**So that** I can create optimized JARs without splitting modules

**Acceptance Criteria:**
- 2.1 `software/infra/aws` remains a single module (no splitting)
- 2.2 Two shadow JAR tasks are defined:
  - `shadowJarRuntime` - Builds `mocknest-runtime.jar`
  - `shadowJarGeneration` - Builds `mocknest-generation.jar`
- 2.3 Each task specifies a different main class (Lambda handler)
- 2.4 Each task uses `minimize()` to remove unused code
- 2.5 No changes to domain or application modules

### 3. Automatic Minimization per Lambda
**As a** developer  
**I want** each Lambda JAR to automatically include only used classes  
**So that** package sizes are minimized without manual configuration

**Acceptance Criteria:**
- 3.1 Both shadow JAR tasks use `minimize()` for automatic dead code elimination
- 3.2 Runtime Lambda JAR automatically excludes:
  - Bedrock SDK classes (not reachable from runtime handler)
  - Koog framework classes (not reachable from runtime handler)
  - OpenAPI parser classes (not reachable from runtime handler)
- 3.3 Generation Lambda JAR automatically excludes:
  - WireMock standalone server classes (only needs WireMock types)
  - Jetty server components (not needed for generation)
- 3.4 Each task explicitly includes required dependencies via `dependencies { include(...) }`

### 4. Lambda Handler Entry Points
**As a** developer  
**I want** separate Lambda handler classes for each feature  
**So that** minimize() can analyze reachability from the correct entry point

**Acceptance Criteria:**
- 4.1 Runtime Lambda handler exists (e.g., `RuntimeLambdaHandler`)
- 4.2 Generation Lambda handler exists (e.g., `GenerationLambdaHandler`)
- 4.3 Each handler is specified as the main class in its shadow JAR task
- 4.4 Handlers route to appropriate Spring Cloud Functions
- 4.5 Both handlers can coexist in the same module

### 5. Build Configuration
**As a** DevOps engineer  
**I want** separate optimized build artifacts for each Lambda  
**So that** deployments are efficient and independent

**Acceptance Criteria:**
- 5.1 `shadowJarRuntime` task produces `mocknest-runtime.jar`
- 5.2 `shadowJarGeneration` task produces `mocknest-generation.jar`
- 5.3 Each task configuration:
  ```kotlin
  tasks.register<ShadowJar>("shadowJarRuntime") {
      archiveFileName.set("mocknest-runtime.jar")
      from(sourceSets.main.get().output)
      configurations = listOf(project.configurations.runtimeClasspath.get())
      minimize()
      manifest {
          attributes["Main-Class"] = "nl.vintik.mocknest.infra.aws.runtime.RuntimeLambdaHandler"
      }
  }
  ```
- 5.4 Build process can build all JARs or individual JARs
- 5.5 Both JARs are copied to deployment directory

### 6. API Gateway Integration
**As a** user  
**I want** API Gateway to route requests to the appropriate Lambda  
**So that** each feature is accessible through its own endpoint

**Acceptance Criteria:**
- 6.1 Runtime Lambda handles all WireMock admin API and mock endpoint requests (all paths except `/ai/*`)
- 6.2 Generation Lambda handles all `/ai/*` endpoints
- 6.3 SAM template defines two Lambda functions:
  - `MockNestRuntimeFunction` with `CodeUri: build/dist/mocknest-runtime.jar`
  - `MockNestGenerationFunction` with `CodeUri: build/dist/mocknest-generation.jar`
- 6.4 API Gateway routes are configured correctly
- 6.5 Each Lambda has appropriate IAM permissions

### 7. Testing Strategy
**As a** developer  
**I want** tests to remain in the existing module  
**So that** no test refactoring is needed

**Acceptance Criteria:**
- 7.1 All tests remain in `software/infra/aws/src/test`
- 7.2 Tests can validate both Lambda handlers
- 7.3 Integration tests work with both runtime and generation features
- 7.4 No changes to test structure or organization
- 7.5 Overall test coverage remains at 90%+

## Simplified Module Structure

```
software/
├── domain/                          # UNCHANGED - single module
│   ├── domain.core.*               # Common models
│   ├── domain.runtime.*            # Runtime models (includes WireMock types)
│   └── domain.generation.*         # Generation models
│
├── application/                     # UNCHANGED - single module
│   ├── application.core.*          # Common interfaces
│   ├── application.runtime.*       # Runtime use cases
│   └── application.generation.*    # Generation use cases, OpenAPI parsers
│
└── infra/
    ├── aws-runtime/                # NEW - Runtime Lambda module
    │   ├── Lambda handler (entry point for minimization)
    │   ├── Runtime-specific Spring config
    │   └── Dependencies: WireMock, S3 (no Bedrock, no Koog)
    │
    └── aws-generation/             # NEW - Generation Lambda module
        ├── Lambda handler (entry point for minimization)
        ├── Generation-specific Spring config
        └── Dependencies: Bedrock, Koog, OpenAPI parser, S3 (no WireMock standalone)
```

## Dependency Flow with Smart Packaging

```
┌─────────────────────────────────────────────┐
│    software/domain (UNCHANGED)              │
│  - All domain code in one module            │
│  - Package-organized by feature             │
└─────────────────────────────────────────────┘
                    ▲
                    │
┌─────────────────────────────────────────────┐
│    software/application (UNCHANGED)          │
│  - All application code in one module       │
│  - Package-organized by feature             │
└─────────────────────────────────────────────┘
                    ▲
          ┌─────────┴─────────┐
          │                   │
┌─────────────────┐  ┌─────────────────┐
│ infra/          │  │ infra/          │
│ aws-runtime     │  │ aws-generation  │
│                 │  │                 │
│ Runtime Handler │  │ Generation      │
│ (entry point)   │  │ Handler         │
│                 │  │ (entry point)   │
│ + WireMock      │  │ + Bedrock       │
│ + S3            │  │ + Koog          │
│                 │  │ + OpenAPI       │
│                 │  │ + S3            │
└─────────────────┘  └─────────────────┘
        │                    │
        ▼                    ▼
   ProGuard/R8          ProGuard/R8
   Minimization         Minimization
        │                    │
        ▼                    ▼
┌─────────────────┐  ┌─────────────────┐
│ mocknest-       │  │ mocknest-       │
│ runtime.jar     │  │ generation.jar  │
│ (minimized)     │  │ (minimized)     │
└─────────────────┘  └─────────────────┘
```

## Shadow JAR Minimize Strategy

### How Shadow JAR `minimize()` Works
Shadow JAR's `minimize()` feature performs automatic dependency analysis:

1. **Bytecode Analysis:** Scans all classes starting from your main class/handler
2. **Reachability Detection:** Identifies which classes are actually used via method calls, field access, etc.
3. **Automatic Exclusion:** Removes all unreachable classes from the final JAR
4. **Dependency Preservation:** Keeps explicitly included dependencies via `dependencies { include(...) }`

### Configuration Example

**Runtime Lambda (`infra/aws-runtime/build.gradle.kts`):**
```kotlin
tasks {
    shadowJar {
        archiveFileName.set("mocknest-runtime.jar")
        minimize()
        
        // Explicitly keep required dependencies
        dependencies {
            // Spring Boot essentials
            include(dependency("org.springframework.boot:.*"))
            include(dependency("org.springframework.cloud:.*"))
            
            // AWS Lambda runtime
            include(dependency("com.amazonaws:aws-lambda-java-.*"))
            
            // WireMock (needed for runtime)
            include(dependency("org.wiremock:.*"))
            
            // AWS SDK S3 (needed for storage)
            include(dependency("aws.sdk.kotlin:s3"))
        }
    }
}
```

**Generation Lambda (`infra/aws-generation/build.gradle.kts`):**
```kotlin
tasks {
    shadowJar {
        archiveFileName.set("mocknest-generation.jar")
        minimize()
        
        // Explicitly keep required dependencies
        dependencies {
            // Spring Boot essentials
            include(dependency("org.springframework.boot:.*"))
            include(dependency("org.springframework.cloud:.*"))
            
            // AWS Lambda runtime
            include(dependency("com.amazonaws:aws-lambda-java-.*"))
            
            // AWS SDK for AI and storage
            include(dependency("aws.sdk.kotlin:s3"))
            include(dependency("aws.sdk.kotlin:bedrockruntime"))
            
            // AI framework
            include(dependency("ai.koog:.*"))
            
            // OpenAPI parser
            include(dependency("io.swagger.parser.v3:.*"))
        }
    }
}
```

### What Gets Automatically Removed

**Runtime Lambda (automatically excluded by minimize()):**
- ❌ Bedrock SDK classes (not reachable from runtime handler)
- ❌ Koog framework classes (not reachable from runtime handler)
- ❌ OpenAPI parser classes (not reachable from runtime handler)
- ❌ Unused Spring Boot auto-configurations
- ❌ Unused Jetty components

**Generation Lambda (automatically excluded by minimize()):**
- ❌ WireMock standalone server classes (only WireMock types are used)
- ❌ Jetty server components (not needed for generation)
- ❌ Unused Spring Boot auto-configurations
- ❌ Unused AWS SDK services

### Benefits of Shadow JAR Minimize

| Feature | Benefit |
|---------|---------|
| **Automatic** | No manual class exclusion lists to maintain |
| **Safe** | Bytecode analysis ensures used classes are kept |
| **Fast** | Much faster than ProGuard (no obfuscation) |
| **Simple** | One line: `minimize()` |
| **Predictable** | No reflection issues like ProGuard |
| **Gradle-native** | No additional plugins needed |

### Expected Results

**Without minimize():**
- Runtime Lambda: ~80-100 MB (includes everything)
- Generation Lambda: ~80-100 MB (includes everything)

**With minimize():**
- Runtime Lambda: ~40-50 MB (excludes Bedrock, Koog, OpenAPI parser)
- Generation Lambda: ~35-45 MB (excludes WireMock server, Jetty)

**Cold start improvement:**
- Current: 8-12 seconds
- Expected: 4-6 seconds (runtime), 5-7 seconds (generation)

## Non-Functional Requirements

### Performance
- Cold start time for runtime Lambda should be < 5 seconds
- Cold start time for generation Lambda should be < 10 seconds
- Each Lambda should only load dependencies it needs

### Maintainability
- Clear module boundaries with no circular dependencies
- Each module has a single, well-defined responsibility
- Shared code is minimal and truly common across features

### Scalability
- Each Lambda can scale independently based on its workload
- Runtime Lambda can handle high request volumes
- Generation Lambda can handle long-running AI operations

### Cost Optimization
- Smaller deployment packages reduce cold start times
- Independent scaling reduces unnecessary compute costs
- Users can deploy only the features they need

## Out of Scope
- Changes to business logic or feature functionality
- New features or capabilities
- Changes to external APIs or contracts
- Performance optimizations beyond module separation

## Success Criteria
- All three Lambda functions deploy successfully
- All existing tests pass
- API Gateway correctly routes requests to appropriate Lambdas
- Cold start times are improved compared to monolithic deployment
- Module dependencies follow clean architecture principles
- No circular dependencies exist
- Test coverage remains at 90%+


## Migration Strategy

### Phase 1: Create Infrastructure Modules (No Domain/Application Changes)
1. Create `software/infra/aws-runtime` module
2. Create `software/infra/aws-generation` module
3. Update `settings.gradle.kts` to include new modules
4. **No changes to domain or application modules**

### Phase 2: Split Lambda Handlers
1. Move runtime Lambda handler to `infra/aws-runtime`
2. Move generation Lambda handler to `infra/aws-generation`
3. Move runtime-specific Spring configuration to `infra/aws-runtime`
4. Move generation-specific Spring configuration to `infra/aws-generation`
5. Keep all use cases, parsers, and domain models in existing modules

### Phase 3: Configure Dependencies and Packaging
1. Configure `infra/aws-runtime` dependencies (WireMock, S3, Spring Boot)
2. Configure `infra/aws-generation` dependencies (Bedrock, Koog, OpenAPI parser, S3, Spring Boot)
3. Add `minimize()` to shadow JAR tasks in both modules
4. Configure `dependencies { include(...) }` blocks to preserve required libraries

### Phase 4: Update Deployment Configuration
1. Update SAM template with two Lambda functions
2. Configure API Gateway routes (runtime for all except `/ai/*`, generation for `/ai/*`)
3. Set up IAM permissions for each Lambda
4. Update deployment scripts to build and deploy both JARs

### Phase 5: Testing and Validation
1. Run all tests to ensure functionality is preserved
2. Validate each Lambda can be deployed independently
3. Test API Gateway routing
4. Measure and compare JAR sizes and cold start times
5. Validate overall system behavior

## Expected Outcomes

### JAR Size Reduction
**Current monolithic JAR:** ~80-100 MB

**Expected with shadow JAR `minimize()`:**
- Runtime Lambda: ~40-50 MB (automatically excludes Bedrock, Koog, OpenAPI parser)
- Generation Lambda: ~35-45 MB (automatically excludes WireMock server, Jetty)

**Reduction:** 50-60% smaller JARs

### Cold Start Improvement
**Current:** 8-12 seconds (loading all features)

**Expected:**
- Runtime Lambda: 4-6 seconds (smaller JAR, fewer classes to load)
- Generation Lambda: 5-7 seconds (smaller JAR, fewer classes to load)

### Deployment Flexibility
- Deploy only runtime Lambda for users who don't need AI features
- Scale runtime and generation independently based on usage patterns
- Add analysis Lambda in future without refactoring domain/application

## Non-Functional Requirements

### Performance
- Cold start time for runtime Lambda should be < 6 seconds
- Cold start time for generation Lambda should be < 8 seconds
- Each Lambda should only load classes it actually uses

### Maintainability
- Clear module boundaries at infrastructure layer only
- Domain and application remain unified for easier development
- Packaging strategy is automated and reproducible

### Scalability
- Each Lambda can scale independently based on its workload
- Runtime Lambda can handle high request volumes
- Generation Lambda can handle long-running AI operations

### Cost Optimization
- Smaller deployment packages reduce cold start times and costs
- Independent scaling reduces unnecessary compute costs
- Users can deploy only the features they need

## Out of Scope
- Changes to business logic or feature functionality
- Splitting domain or application modules
- New features or capabilities
- Changes to external APIs or contracts
- Native compilation (GraalVM) - future consideration

## Success Criteria
- Both Lambda functions deploy successfully
- All existing tests pass without modification
- API Gateway correctly routes requests to appropriate Lambdas
- JAR sizes are reduced by at least 30% compared to monolithic deployment
- Cold start times are improved by at least 40%
- No changes required to domain or application code
- Test coverage remains at 90%+
