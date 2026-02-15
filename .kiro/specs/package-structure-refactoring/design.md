# Design Document: Package Structure Refactoring

## Overview

This design document describes the approach for refactoring the MockNest Serverless package structure from `io.mocknest` to `nl.vintik.mocknest` while organizing code by capability within each architectural layer. This is a pure structural refactoring with zero code changes - only package names, import statements, and file locations will be modified.

The refactoring addresses two goals:
1. **Correct organizational ownership**: Change base package from `io.mocknest` to `nl.vintik.mocknest` to reflect proper domain ownership for open source publication
2. **Capability-based organization**: Organize code within each layer (domain, application, infrastructure) by capability (runtime, generation, analysis, core) to improve code discoverability and maintainability

This refactoring maintains clean architecture boundaries and ensures all classes remain in their current Gradle modules.

## Architecture

### Current Package Structure

The current structure uses `io.mocknest` as the base package with some capability-based organization already in place:

```
io.mocknest.domain
├── generation/          # AI mock generation domain models
└── model/              # Generic HTTP models

io.mocknest.application
├── generation/         # AI mock generation logic
│   ├── agent/
│   ├── generators/
│   ├── interfaces/
│   ├── parsers/
│   └── usecases/
├── interfaces/
│   └── storage/       # Generic storage interfaces
├── usecase/           # Generic use cases
└── wiremock/          # WireMock runtime logic
    ├── config/
    ├── extensions/
    ├── mappings/
    └── store/

io.mocknest.infra.aws
├── config/            # Spring configuration
├── function/          # Lambda handlers
├── generation/        # Bedrock AI adapters
└── storage/           # S3 storage adapters
    └── config/
```

### Target Package Structure

The target structure uses `nl.vintik.mocknest` as the base package with consistent capability-based organization across all layers:

```
nl.vintik.mocknest.domain
├── runtime/           # Serverless WireMock runtime domain models
├── generation/        # AI mock generation domain models
├── analysis/          # AI traffic analysis domain models (future)
└── core/             # Shared domain models (HTTP models, etc.)

nl.vintik.mocknest.application
├── runtime/           # Serverless WireMock runtime use cases
│   ├── config/
│   ├── extensions/
│   ├── interfaces/    # Runtime-specific interfaces (uses core storage)
│   ├── mappings/
│   ├── store/
│   └── usecases/
├── generation/        # AI mock generation use cases
│   ├── agent/
│   ├── generators/
│   ├── interfaces/    # Generation-specific interfaces
│   ├── parsers/
│   └── usecases/
├── analysis/          # AI traffic analysis use cases (future)
│   └── interfaces/    # Analysis-specific interfaces
└── core/             # Shared application logic
    └── interfaces/
        └── storage/   # Generic storage interfaces used by all capabilities

nl.vintik.mocknest.infra.aws
├── runtime/           # Runtime-specific AWS adapters
│   ├── config/        # Spring configuration
│   ├── function/      # Lambda handlers
│   └── storage/       # Runtime-specific S3 adapters (mappings, files)
├── generation/        # Generation-specific AWS adapters
│   ├── ai/           # Bedrock mock generation agents
│   └── storage/       # Generation-specific S3 adapters (specs)
├── analysis/          # Analysis-specific AWS adapters (future)
│   ├── ai/           # Bedrock traffic analysis agents
│   └── storage/       # Analysis-specific S3 adapters (traffic logs)
└── core/             # Shared AWS infrastructure
    ├── ai/           # Shared Bedrock configuration
    └── storage/       # Shared S3 configuration
        └── config/
```

### Capability Definitions

The system is organized around four capability areas:

1. **runtime**: Serverless WireMock runtime - manages WireMock server, object storage persistence, Lambda handlers, mapping normalization, and request handling
2. **generation**: AI-assisted mock generation - handles mock generation from specifications, AI model services, specification parsing, and mock generation agents
3. **analysis**: AI-powered traffic analysis - handles traffic recording, analysis, pattern detection (future capability)
4. **core**: Shared foundational code - provides generic storage interfaces, HTTP models, shared AI infrastructure, and other cross-cutting concerns

### Clean Architecture Preservation

The refactoring maintains strict clean architecture boundaries:

- **Domain layer** (`nl.vintik.mocknest.domain.*`): Contains business models and entities, no dependencies on other layers
- **Application layer** (`nl.vintik.mocknest.application.*`): Contains use cases and interfaces, depends only on domain layer
- **Infrastructure layer** (`nl.vintik.mocknest.infra.aws.*`): Contains AWS-specific implementations, depends on both application and domain layers

All classes remain in their current Gradle modules (`:software:domain`, `:software:application`, `:software:infra:aws`).

## Components and Interfaces

### Refactoring Strategy

The refactoring will be performed using IDE-based automated refactoring tools to ensure safety and completeness. The process follows these principles:

1. **Automated refactoring**: Use IntelliJ IDEA's "Move Package" and "Rename Package" refactoring features
2. **Incremental validation**: Compile and test after each major step
3. **No manual edits**: Avoid manual find-replace operations that could miss references
4. **Git-based safety**: Commit after each successful step to enable easy rollback

### File Classification Mapping

Based on the current codebase analysis, here's how existing files will be classified:

#### Domain Layer Classification

**Current → Target**

`io.mocknest.domain.model` → `nl.vintik.mocknest.domain.core`:
- `HttpRequest.kt` - Generic HTTP model used across capabilities
- `HttpResponse.kt` - Generic HTTP model used across capabilities

`io.mocknest.domain.generation` → `nl.vintik.mocknest.domain.generation`:
- `AIGenerationModels.kt` - AI generation domain models
- `APISpecification.kt` - API specification domain model
- `GeneratedMock.kt` - Generated mock domain model
- `GenerationJob.kt` - Generation job domain model
- `MockGenerationRequest.kt` - Mock generation request model
- `MockNamespace.kt` - Mock namespace domain model
- `TestAgentRequest.kt` - Test agent request model
- `TestAgentResponse.kt` - Test agent response model

#### Application Layer Classification

**Current → Target**

`io.mocknest.application.interfaces.storage` → `nl.vintik.mocknest.application.core.interfaces.storage`:
- `ObjectStorageInterface.kt` - Generic storage interface used by all capabilities

`io.mocknest.application.usecase` → `nl.vintik.mocknest.application.runtime.usecases`:
- `AdminRequestUseCase.kt` - WireMock admin API use case
- `ClientRequestUseCase.kt` - Mock request handling use case
- `HandleRequest.kt` - Request routing use case

`io.mocknest.application.wiremock` → `nl.vintik.mocknest.application.runtime`:
- `config/MockNestConfig.kt` → `runtime.config/`
- `extensions/DeleteAllMappingsAndFilesFilter.kt` → `runtime.extensions/`
- `extensions/NormalizeMappingBodyFilter.kt` → `runtime.extensions/`
- `mappings/CompositeMappingsSource.kt` → `runtime.mappings/`
- `mappings/ObjectStorageMappingsSource.kt` → `runtime.mappings/`
- `store/adapters/ObjectStorageBlobStore.kt` → `runtime.store.adapters/`
- `store/adapters/ObjectStorageWireMockStores.kt` → `runtime.store.adapters/`

Note: Runtime currently uses the generic `ObjectStorageInterface` from `core.interfaces.storage`. An `application.runtime.interfaces` package will be created for consistency and future runtime-specific interfaces, but it will initially be empty or contain documentation about using core interfaces.

`io.mocknest.application.generation` → `nl.vintik.mocknest.application.generation`:
- All files remain in generation capability (already correctly organized)
- Subdirectories: `agent/`, `generators/`, `interfaces/`, `parsers/`, `usecases/`
- Note: `generation.interfaces` contains generation-specific interfaces like `GenerationStorageInterface`, `MockGeneratorInterface`, `AIModelServiceInterface`, etc.

#### Infrastructure Layer Classification

**Current → Target**

`io.mocknest.infra.aws.config` → `nl.vintik.mocknest.infra.aws.runtime.config`:
- Spring Boot configuration and component scanning

`io.mocknest.infra.aws.function` → `nl.vintik.mocknest.infra.aws.runtime.function`:
- `MockNestLambdaHandler.kt` - Lambda function handler for runtime

`io.mocknest.infra.aws.storage.config` → `nl.vintik.mocknest.infra.aws.core.storage.config`:
- `S3Configuration.kt` - Shared S3 client configuration

`io.mocknest.infra.aws.storage` → `nl.vintik.mocknest.infra.aws.runtime.storage`:
- `S3ObjectStorageAdapter.kt` - S3 adapter for runtime mappings and files

`io.mocknest.infra.aws.generation` → Split into two locations:
- `BedrockServiceAdapter.kt` → `nl.vintik.mocknest.infra.aws.generation.ai/`
- `AIGenerationConfiguration.kt` → `nl.vintik.mocknest.infra.aws.core.ai/`
- `BedrockTestKoogAgent.kt` → `nl.vintik.mocknest.infra.aws.generation.ai/`

`io.mocknest.infra.aws.Application` → `nl.vintik.mocknest.infra.aws.runtime`:
- Main Spring Boot application class

### Test File Organization

Test files will mirror the production package structure:

- Tests in `software/domain/src/test/kotlin/io/mocknest/domain/generation/` → `nl.vintik.mocknest.domain.generation/`
- Tests in `software/application/src/test/kotlin/io/mocknest/application/` → `nl.vintik.mocknest.application.*/`
- Tests in `software/infra/aws/src/test/kotlin/io/mocknest/infra/aws/` → `nl.vintik.mocknest.infra.aws.*/`

## Data Models

### Package Declaration Changes

Every Kotlin source file will have its package declaration updated:

**Before:**
```kotlin
package io.mocknest.domain.model
```

**After:**
```kotlin
package nl.vintik.mocknest.domain.core
```

### Import Statement Changes

All import statements referencing `io.mocknest` packages will be updated:

**Before:**
```kotlin
import io.mocknest.domain.model.HttpRequest
import io.mocknest.application.interfaces.storage.ObjectStorageInterface
```

**After:**
```kotlin
import nl.vintik.mocknest.domain.core.HttpRequest
import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
```

### Directory Structure Changes

File system directories will be reorganized to match package names:

**Before:**
```
software/domain/src/main/kotlin/io/mocknest/domain/model/HttpRequest.kt
```

**After:**
```
software/domain/src/main/kotlin/nl/vintik/mocknest/domain/core/HttpRequest.kt
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Module Boundary Preservation
*For any* class in the codebase, its Gradle module location (domain, application, or infra-aws) should remain unchanged after refactoring.

**Validates: Requirements 0.6**

### Property 2: Compilation Success
*For any* valid build configuration, the project should compile successfully without errors after the refactoring is complete.

**Validates: Requirements 1.6**

### Property 3: Test Suite Success
*For any* existing test in the test suite, the test should pass after the refactoring is complete, demonstrating behavioral equivalence.

**Validates: Requirements 1.7, 0.12**

### Property 4: Clean Architecture Boundaries
*For any* class in the domain module, it should have no dependencies on classes from the application or infrastructure modules.

**Validates: Requirements 2.7**

### Property 5: Build Configuration Correctness
*For any* build artifact produced, it should use the Maven group coordinate `nl.vintik.mocknest` instead of `com.mocknest`.

**Validates: Requirements 8.1, 8.8**

### Property 6: Dependency Resolution
*For any* class with dependencies on other classes, all import statements should resolve correctly after refactoring, demonstrating that all references were updated.

**Validates: Requirements 0.11**

## Error Handling

### Refactoring Failure Scenarios

The refactoring process must handle several potential failure scenarios:

1. **Compilation Failures**: If compilation fails after a refactoring step, the specific errors must be identified and the step must be rolled back
2. **Test Failures**: If tests fail after refactoring, the failing tests must be identified and analyzed to determine if they indicate a refactoring error
3. **Missing References**: If the IDE refactoring tool misses any references, manual inspection and correction will be required
4. **Resource File Issues**: If resource files contain package-based paths, they must be identified and updated manually

### Validation Strategy

After each major refactoring step:

1. **Compile all modules**: Run `./gradlew clean build -x test` to verify compilation
2. **Run all tests**: Run `./gradlew test` to verify behavioral equivalence
3. **Check for warnings**: Review compiler warnings for potential issues
4. **Inspect git diff**: Review changes to ensure only package declarations, imports, and file paths changed

### Rollback Mechanism

Each major refactoring step will be committed to git, allowing easy rollback:

1. Commit after base package rename
2. Commit after domain layer reorganization
3. Commit after application layer reorganization
4. Commit after infrastructure layer reorganization
5. Commit after build configuration updates
6. Commit after documentation updates

## Testing Strategy

### Dual Testing Approach

This refactoring will use both manual validation and automated testing:

- **Manual validation**: Code review of git diffs to ensure only structural changes
- **Automated validation**: Compilation and test execution to verify behavioral equivalence

### Unit Testing

Unit tests serve as regression tests for this refactoring:

- All existing unit tests must pass without modification (except for package declarations)
- Unit tests validate that individual components still work correctly
- Test failures indicate potential refactoring errors that must be investigated

### Integration Testing

Integration tests validate that components still work together correctly:

- All existing integration tests must pass without modification (except for package declarations)
- Integration tests with LocalStack validate AWS adapter functionality
- Test failures indicate potential issues with dependency injection or component wiring

### Property-Based Testing

While this refactoring doesn't introduce new property-based tests, the existing test suite serves as a comprehensive validation:

- Existing tests cover various input scenarios and edge cases
- All tests passing demonstrates behavioral equivalence
- No new property-based tests are needed for this structural refactoring

### Validation Checklist

After refactoring completion, validate:

- [ ] All modules compile successfully
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] No compiler warnings related to imports or package declarations
- [ ] Spring Boot application starts successfully
- [ ] Component scanning finds all beans
- [ ] Lambda handler can be invoked
- [ ] Git diff shows only package declarations, imports, and file paths changed
- [ ] No class implementations, method bodies, or business logic changed
- [ ] Build artifacts use correct Maven coordinates (`nl.vintik.mocknest`)
- [ ] Documentation references updated package names

## Implementation Approach

### Phase 1: Preparation and Analysis

**Objective**: Understand the current structure and plan the refactoring steps

**Steps**:
1. Analyze current package structure and create file classification mapping
2. Identify all files that need to be moved
3. Identify potential issues (resource files, reflection, serialization)
4. Create detailed refactoring plan with step-by-step instructions
5. Backup current state and create feature branch

**Validation**: Classification mapping complete, potential issues documented

### Phase 2: Base Package Rename

**Objective**: Rename base package from `io.mocknest` to `nl.vintik.mocknest`

**Steps**:
1. Use IDE "Rename Package" refactoring on `io.mocknest` → `nl.vintik.mocknest`
2. Verify all package declarations updated
3. Verify all import statements updated
4. Verify directory structure updated
5. Update Spring Boot `scanBasePackages` annotation
6. Compile all modules
7. Run all tests
8. Commit changes

**Validation**: Compilation succeeds, all tests pass, git diff shows only package/import changes

### Phase 3: Domain Layer Reorganization

**Objective**: Organize domain layer by capability

**Steps**:
1. Create new capability packages: `domain.runtime`, `domain.generation`, `domain.core`
2. Move `domain.model` → `domain.core` using IDE refactoring
3. Keep `domain.generation` as-is (already correct)
4. Compile domain module
5. Compile dependent modules (application, infra-aws)
6. Run all tests
7. Commit changes

**Validation**: Compilation succeeds, all tests pass, domain layer organized by capability

### Phase 4: Application Layer Reorganization

**Objective**: Organize application layer by capability

**Steps**:
1. Create new capability packages: `application.runtime`, `application.generation`, `application.core`
2. Move `application.interfaces.storage` → `application.core.interfaces.storage`
3. Move `application.usecase` → `application.runtime.usecases`
4. Move `application.wiremock` → `application.runtime` (preserving subdirectories)
5. Create `application.runtime.interfaces` package (initially empty, for future runtime-specific interfaces)
6. Keep `application.generation` as-is (already correct, includes `generation.interfaces`)
7. Compile application module
8. Compile dependent module (infra-aws)
9. Run all tests
10. Commit changes

**Validation**: Compilation succeeds, all tests pass, application layer organized by capability with consistent interfaces structure

### Phase 5: Infrastructure Layer Reorganization

**Objective**: Organize infrastructure layer by capability with storage and AI sub-packages

**Steps**:
1. Create new capability packages: `infra.aws.runtime`, `infra.aws.generation`, `infra.aws.core`
2. Create storage sub-packages: `runtime.storage`, `core.storage.config`
3. Create AI sub-packages: `generation.ai`, `core.ai`
4. Move `infra.aws.config` → `infra.aws.runtime.config`
5. Move `infra.aws.function` → `infra.aws.runtime.function`
6. Move `infra.aws.storage.config.S3Configuration` → `infra.aws.core.storage.config`
7. Move `infra.aws.storage.S3ObjectStorageAdapter` → `infra.aws.runtime.storage`
8. Move `infra.aws.generation.AIGenerationConfiguration` → `infra.aws.core.ai`
9. Move `infra.aws.generation.BedrockServiceAdapter` → `infra.aws.generation.ai`
10. Move `infra.aws.generation.BedrockTestKoogAgent` → `infra.aws.generation.ai`
11. Move `infra.aws.Application` → `infra.aws.runtime`
12. Compile infra-aws module
13. Run all tests
14. Commit changes

**Validation**: Compilation succeeds, all tests pass, infrastructure layer organized by capability

### Phase 6: Build Configuration Updates

**Objective**: Update build configurations to use new Maven coordinates

**Steps**:
1. Update `group` property in root `build.gradle.kts`: `com.mocknest` → `nl.vintik.mocknest`
2. Update Kover exclusion patterns to use new package names
3. Verify no other build files reference old package names
4. Run full build: `./gradlew clean build`
5. Verify build artifacts use correct group
6. Run all tests
7. Commit changes

**Validation**: Build succeeds, artifacts use `nl.vintik.mocknest` group, all tests pass

### Phase 7: Documentation Updates

**Objective**: Update documentation to reflect new package structure

**Steps**:
1. Update README.md with new package examples
2. Update architecture steering document (`.kiro/steering/02-architecture.md`)
3. Update usage guidelines (`.kiro/steering/05-kiro-usage.md`)
4. Update any code examples in documentation
5. Update any diagrams showing package structure
6. Commit changes

**Validation**: Documentation consistent with actual code structure

### Phase 8: Final Validation

**Objective**: Comprehensive validation of refactoring completion

**Steps**:
1. Run full clean build: `./gradlew clean build`
2. Run all tests: `./gradlew test`
3. Run coverage report: `./gradlew koverHtmlReport`
4. Verify coverage meets 90% threshold: `./gradlew koverVerify`
5. Start Spring Boot application locally
6. Verify component scanning works
7. Build Lambda deployment package: `./gradlew shadowJar`
8. Review complete git diff
9. Verify only structural changes (no logic changes)
10. Create pull request with detailed description

**Validation**: All checks pass, refactoring complete and safe

## Notes

### Backward Compatibility Considerations

This refactoring introduces breaking changes for:

1. **Serialized Data**: If any data is serialized with Java serialization and includes package names, it will not deserialize correctly after refactoring
   - **Mitigation**: MockNest Serverless uses JSON serialization (Jackson/Kotlinx), which doesn't include package names
   - **Action**: No migration needed

2. **API Contracts**: If any API responses expose fully-qualified class names, they will change
   - **Mitigation**: MockNest Serverless APIs use DTOs and don't expose internal class names
   - **Action**: No migration needed

3. **Configuration Files**: If any configuration files reference package names for component scanning
   - **Mitigation**: Update `@SpringBootApplication(scanBasePackages = ...)` annotation
   - **Action**: Included in Phase 2

4. **Lambda Configuration**: If Lambda function configuration references handler class with package
   - **Mitigation**: Update SAM template and deployment scripts
   - **Action**: Update `deployment/aws/sam/template.yaml` handler references

### Future Capability Addition

When adding new capabilities (e.g., `analysis` for traffic analysis):

1. Create capability sub-packages in each layer:
   - `nl.vintik.mocknest.domain.analysis`
   - `nl.vintik.mocknest.application.analysis`
   - `nl.vintik.mocknest.infra.aws.analysis`

2. Create storage sub-package if needed:
   - `nl.vintik.mocknest.infra.aws.analysis.storage`

3. Create AI sub-package if needed:
   - `nl.vintik.mocknest.infra.aws.analysis.ai`

4. Follow the same pattern as existing capabilities

### IDE Refactoring Tool Usage

**IntelliJ IDEA Refactoring Features**:

1. **Rename Package**: Right-click package → Refactor → Rename
   - Updates package declarations
   - Updates all import statements
   - Moves files to new directory structure
   - Searches for string references

2. **Move Package**: Drag and drop or Right-click → Refactor → Move
   - Moves entire package to new location
   - Updates all references automatically

3. **Find Usages**: Right-click → Find Usages
   - Verify all references found before refactoring
   - Check for reflection or string-based references

**Best Practices**:
- Always use "Search in comments and strings" option
- Review preview before applying changes
- Commit after each successful refactoring step
- Use "Analyze → Inspect Code" to find potential issues

### Risk Mitigation

**Low Risk**: This refactoring is low-risk because:
- No code logic changes
- IDE automated refactoring is reliable
- Compilation catches missing references
- Comprehensive test suite validates behavior
- Git enables easy rollback

**Potential Issues**:
- Reflection-based code might break (none identified in current codebase)
- String-based package references might be missed (check configuration files)
- Resource files with package paths need manual update (none identified)

**Mitigation Strategy**:
- Incremental approach with validation after each step
- Comprehensive testing after each phase
- Git commits enable rollback to any point
- Code review of complete diff before merging
