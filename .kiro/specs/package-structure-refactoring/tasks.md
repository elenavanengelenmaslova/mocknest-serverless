# Implementation Plan: Package Structure Refactoring

## Overview

This implementation plan provides step-by-step tasks for refactoring the MockNest Serverless package structure from `io.mocknest` to `nl.vintik.mocknest` while organizing code by capability within each architectural layer. This is a pure structural refactoring with zero code changes - only package names, import statements, file locations, and build configurations will be modified.

The refactoring will be performed incrementally with validation after each major step to ensure safety and enable easy rollback if needed.

## Tasks

- [x] 1. Preparation and Analysis
  - Document current package structure and file locations
  - Identify all files that need to be moved
  - Review code for potential issues (reflection, serialization, string-based references)
  - Create backup of current state
  - _Requirements: 11.1, 11.7_

- [x] 2. Base Package Rename (io.mocknest → nl.vintik.mocknest)
  - [x] 2.1 Rename base package using IDE refactoring
    - Use IntelliJ IDEA "Rename Package" on `io.mocknest` → `nl.vintik.mocknest`
    - Verify all package declarations updated in all modules
    - Verify all import statements updated in all modules
    - Verify directory structure updated (io/mocknest → nl/vintik/mocknest)
    - _Requirements: 1.1, 1.2, 1.3_
  
  - [x] 2.2 Update Spring Boot component scanning
    - Update `@SpringBootApplication(scanBasePackages = ...)` in Application.kt
    - Change from `"io.mocknest"` to `"nl.vintik.mocknest"`
    - _Requirements: 8.3_
  
  - [x] 2.3 Validate base package rename
    - Run `./gradlew clean build -x test` to verify compilation
    - Run `./gradlew test` to verify all tests pass
    - Review git diff to ensure only package/import changes
    - Commit changes with message: "Rename base package from io.mocknest to nl.vintik.mocknest"
    - _Requirements: 1.6, 1.7, 11.3, 11.4_

- [x] 3. Domain Layer Reorganization by Capability
  - [x] 3.1 Create domain capability packages
    - Create `nl.vintik.mocknest.domain.runtime` package
    - Create `nl.vintik.mocknest.domain.generation` package (already exists)
    - Create `nl.vintik.mocknest.domain.core` package
    - _Requirements: 2.1, 2.2, 2.4_
  
  - [x] 3.2 Move domain.model to domain.core
    - Use IDE "Move Package" to move `domain.model` → `domain.core`
    - Verify HttpRequest.kt and HttpResponse.kt moved correctly
    - Verify all import statements updated in dependent modules
    - _Requirements: 2.4, 2.6, 6.4_
  
  - [x] 3.3 Validate domain layer reorganization
    - Run `./gradlew :software:domain:build` to verify domain module compiles
    - Run `./gradlew :software:application:build` to verify application module compiles
    - Run `./gradlew :software:infra:aws:build` to verify infra module compiles
    - Run `./gradlew test` to verify all tests pass
    - Verify domain module has no dependencies on application or infra modules
    - Commit changes with message: "Organize domain layer by capability"
    - _Requirements: 1.6, 1.7, 2.7, 11.3, 11.4_

- [-] 4. Application Layer Reorganization by Capability
  - [x] 4.1 Create application capability packages
    - Create `nl.vintik.mocknest.application.runtime` package
    - Create `nl.vintik.mocknest.application.generation` package (already exists)
    - Create `nl.vintik.mocknest.application.core` package
    - _Requirements: 3.1, 3.2, 3.4_
  
  - [x] 4.2 Move core interfaces
    - Use IDE "Move Package" to move `application.interfaces.storage` → `application.core.interfaces.storage`
    - Verify ObjectStorageInterface.kt moved correctly
    - Verify all import statements updated in dependent code
    - _Requirements: 3.4, 3.6, 6.4_
  
  - [x] 4.3 Move runtime use cases
    - Use IDE "Move Package" to move `application.usecase` → `application.runtime.usecases`
    - Verify AdminRequestUseCase.kt, ClientRequestUseCase.kt, HandleRequest.kt moved
    - Verify all import statements updated
    - _Requirements: 3.1, 3.5, 6.1_
  
  - [x] 4.4 Move WireMock runtime code
    - Use IDE "Move Package" to move `application.wiremock.config` → `application.runtime.config`
    - Use IDE "Move Package" to move `application.wiremock.extensions` → `application.runtime.extensions`
    - Use IDE "Move Package" to move `application.wiremock.mappings` → `application.runtime.mappings`
    - Use IDE "Move Package" to move `application.wiremock.store` → `application.runtime.store`
    - Verify all subdirectories and files moved correctly
    - Verify all import statements updated
    - _Requirements: 3.1, 3.5, 6.1_
  
  - [x] 4.5 Create runtime interfaces package
    - Create `nl.vintik.mocknest.application.runtime.interfaces` package
    - Add package documentation explaining runtime uses core storage interfaces
    - _Requirements: 3.1, 5.1_
  
  - [-] 4.6 Validate application layer reorganization
    - Run `./gradlew :software:application:build` to verify application module compiles
    - Run `./gradlew :software:infra:aws:build` to verify infra module compiles
    - Run `./gradlew test` to verify all tests pass
    - Verify application module depends only on domain module
    - Commit changes with message: "Organize application layer by capability"
    - _Requirements: 1.6, 1.7, 3.7, 11.3, 11.4_

- [ ] 5. Infrastructure Layer Reorganization by Capability
  - [ ] 5.1 Create infrastructure capability packages
    - Create `nl.vintik.mocknest.infra.aws.runtime` package
    - Create `nl.vintik.mocknest.infra.aws.generation` package
    - Create `nl.vintik.mocknest.infra.aws.core` package
    - Create `nl.vintik.mocknest.infra.aws.core.storage` package
    - Create `nl.vintik.mocknest.infra.aws.core.ai` package
    - Create `nl.vintik.mocknest.infra.aws.runtime.storage` package
    - Create `nl.vintik.mocknest.infra.aws.generation.ai` package
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 13.1, 13.2, 14.1, 14.2_
  
  - [ ] 5.2 Move runtime infrastructure code
    - Use IDE "Move Package" to move `infra.aws.config` → `infra.aws.runtime.config`
    - Use IDE "Move Package" to move `infra.aws.function` → `infra.aws.runtime.function`
    - Move `infra.aws.Application.kt` → `infra.aws.runtime.Application.kt`
    - Verify all files moved correctly
    - Verify all import statements updated
    - _Requirements: 4.1, 4.5, 6.1_
  
  - [ ] 5.3 Move core storage configuration
    - Move `infra.aws.storage.config.S3Configuration.kt` → `infra.aws.core.storage.config.S3Configuration.kt`
    - Verify this is shared S3 client configuration used by all capabilities
    - Verify all import statements updated
    - _Requirements: 4.4, 13.1, 13.5_
  
  - [ ] 5.4 Move runtime storage adapter
    - Move `infra.aws.storage.S3ObjectStorageAdapter.kt` → `infra.aws.runtime.storage.S3ObjectStorageAdapter.kt`
    - Verify this implements ObjectStorageInterface for runtime mappings and files
    - Verify all import statements updated
    - _Requirements: 4.1, 13.2, 13.6_
  
  - [ ] 5.5 Move core AI configuration
    - Move `infra.aws.generation.AIGenerationConfiguration.kt` → `infra.aws.core.ai.AIGenerationConfiguration.kt`
    - Verify this is shared Bedrock client configuration
    - Verify all import statements updated
    - _Requirements: 4.4, 14.1, 14.4, 14.8_
  
  - [ ] 5.6 Move generation AI implementations
    - Move `infra.aws.generation.BedrockServiceAdapter.kt` → `infra.aws.generation.ai.BedrockServiceAdapter.kt`
    - Move `infra.aws.generation.BedrockTestKoogAgent.kt` → `infra.aws.generation.ai.BedrockTestKoogAgent.kt`
    - Verify these are generation-specific AI implementations
    - Verify all import statements updated
    - _Requirements: 4.2, 14.2, 14.5_
  
  - [ ] 5.7 Validate infrastructure layer reorganization
    - Run `./gradlew :software:infra:aws:build` to verify infra module compiles
    - Run `./gradlew test` to verify all tests pass
    - Verify infra module depends on both application and domain modules
    - Commit changes with message: "Organize infrastructure layer by capability with storage and AI sub-packages"
    - _Requirements: 1.6, 1.7, 4.7, 11.3, 11.4_

- [ ] 6. Build Configuration Updates
  - [ ] 6.1 Update Gradle group property
    - Update `group = "com.mocknest"` → `group = "nl.vintik.mocknest"` in root build.gradle.kts
    - Verify change applied to all subprojects
    - _Requirements: 8.1_
  
  - [ ] 6.2 Update Kover exclusion patterns
    - Update Kover exclusions from `"io.mocknest.*.interfaces.*"` to `"nl.vintik.mocknest.*.interfaces.*"`
    - Update any other package-based patterns in build configuration
    - _Requirements: 8.2_
  
  - [ ] 6.3 Validate build configuration
    - Run `./gradlew clean build` to verify full build succeeds
    - Run `./gradlew test` to verify all tests pass
    - Run `./gradlew shadowJar` to verify Lambda package builds correctly
    - Verify build artifacts use group `nl.vintik.mocknest`
    - Check JAR manifest and metadata for correct group
    - Commit changes with message: "Update build configuration to use nl.vintik.mocknest group"
    - _Requirements: 8.5, 8.6, 8.8, 11.3, 11.4_

- [ ] 7. Documentation Updates
  - [ ] 7.1 Update README.md
    - Update any package name references in code examples
    - Update any Maven coordinate examples
    - Verify all code snippets use new package names
    - _Requirements: 9.1, 9.4_
  
  - [ ] 7.2 Update architecture steering document
    - Update `.kiro/steering/02-architecture.md` with new package structure
    - Update package structure examples and diagrams
    - Ensure consistency with actual code structure
    - _Requirements: 9.2, 9.3, 9.5, 9.6_
  
  - [ ] 7.3 Update usage guidelines
    - Update `.kiro/steering/05-kiro-usage.md` with new package examples
    - Update any code generation examples
    - Update any package organization guidance
    - _Requirements: 9.2, 9.4_
  
  - [ ] 7.4 Validate documentation updates
    - Review all documentation for consistency
    - Verify no references to old package names remain
    - Commit changes with message: "Update documentation to reflect new package structure"
    - _Requirements: 9.6_

- [ ] 8. SAM Template and Deployment Configuration Updates
  - [ ] 8.1 Update SAM template handler references
    - Update Lambda function handler references in `deployment/aws/sam/template.yaml`
    - Change handler class references from `io.mocknest` to `nl.vintik.mocknest`
    - Verify handler format: `nl.vintik.mocknest.infra.aws.runtime.function.MockNestLambdaHandler`
    - _Requirements: 10.6_
  
  - [ ] 8.2 Validate SAM template
    - Run `sam validate` to verify template syntax
    - Review template for any other package references
    - Commit changes with message: "Update SAM template for new package structure"
    - _Requirements: 10.6_

- [ ] 9. Final Validation and Testing
  - [ ] 9.1 Run comprehensive build
    - Run `./gradlew clean build` for full clean build
    - Verify all modules compile successfully
    - Verify no compilation warnings related to imports or packages
    - _Requirements: 1.6, 11.3_
  
  - [ ] 9.2 Run full test suite
    - Run `./gradlew test` to execute all tests
    - Verify all unit tests pass
    - Verify all integration tests pass
    - Verify no test failures or errors
    - _Requirements: 1.7, 11.4_
  
  - [ ] 9.3 Run coverage verification
    - Run `./gradlew koverHtmlReport` to generate coverage report
    - Run `./gradlew koverVerify` to verify 90% threshold
    - Verify coverage meets project standards
    - _Requirements: 0.12_
  
  - [ ] 9.4 Test Spring Boot application startup
    - Start application locally: `./gradlew :software:infra:aws:bootRun`
    - Verify application starts without errors
    - Verify component scanning finds all beans
    - Verify WireMock server initializes correctly
    - Stop application
    - _Requirements: 0.12, 8.3_
  
  - [ ] 9.5 Build Lambda deployment package
    - Run `./gradlew shadowJar` to build Lambda package
    - Verify JAR created in `build/dist/mocknest-serverless-aws.jar`
    - Verify JAR contains correct package structure
    - Inspect JAR manifest for correct main class reference
    - _Requirements: 8.8_
  
- [ ] 10. Checkpoint - Final Review
  - Ensure all validation steps passed
  - Ensure documentation is updated and accurate
  - Ensure no breaking changes to runtime behavior
  - Ask the user if any questions or concerns arise 

## Notes

### Refactoring Safety

- Each major phase includes validation steps (compile + test)
- Each phase is committed separately to enable easy rollback
- IDE automated refactoring ensures all references are updated
- Comprehensive test suite validates behavioral equivalence
- Git diff review ensures only structural changes

### Backward Compatibility

This refactoring introduces breaking changes for:
- Serialized data with Java serialization (not used - MockNest uses JSON)
- API contracts exposing class names (not applicable - APIs use DTOs)
- Lambda handler configuration (updated in SAM template)
- Component scanning configuration (updated in Application.kt)

### Future Capability Addition

When adding new capabilities (e.g., `analysis`):
1. Create capability sub-packages in each layer (domain, application, infra)
2. Create storage sub-package if needed (`infra.aws.{capability}.storage`)
3. Create AI sub-package if needed (`infra.aws.{capability}.ai`)
4. Follow the same pattern as existing capabilities

### IDE Refactoring Tools

Use IntelliJ IDEA refactoring features:
- "Rename Package" for base package rename
- "Move Package" for capability reorganization
- "Find Usages" to verify all references before moving
- Enable "Search in comments and strings" option
- Review preview before applying changes
- Use "Analyze → Inspect Code" to find potential issues
