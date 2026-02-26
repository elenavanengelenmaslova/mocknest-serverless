# Multi-Lambda Architecture Refactoring - Implementation Tasks

## Overview
This task list implements the refactoring from a monolithic Lambda deployment to separate Lambda functions for runtime and generation features using multiple shadow JAR tasks in a single AWS infrastructure module.

## Task List

### Phase 1: Create Lambda Handler Classes

- [ ] 1.1 Create RuntimeLambdaHandler class
  - Create `software/infra/aws/src/main/kotlin/nl/vintik/mocknest/infra/aws/runtime/RuntimeLambdaHandler.kt`
  - Implement `runtimeRouter` function bean that routes to runtime use cases
  - Handle `/__admin/*` and `/mocknest/*` paths
  - Validates: Requirements 4.1, 4.4

- [ ] 1.2 Create GenerationLambdaHandler class
  - Create `software/infra/aws/src/main/kotlin/nl/vintik/mocknest/infra/aws/generation/GenerationLambdaHandler.kt`
  - Implement `generationRouter` function bean that routes to generation use cases
  - Handle `/ai/*` paths
  - Validates: Requirements 4.2, 4.4

- [ ] 1.3 Create RuntimeApplication class
  - Create `software/infra/aws/src/main/kotlin/nl/vintik/mocknest/infra/aws/runtime/RuntimeApplication.kt`
  - Configure `@SpringBootApplication` with runtime-specific package scanning
  - Scan: `nl.vintik.mocknest.application.runtime`, `nl.vintik.mocknest.application.core`, `nl.vintik.mocknest.infra.aws.runtime`, `nl.vintik.mocknest.infra.aws.core`
  - Validates: Requirements 4.1, 4.5

- [ ] 1.4 Create GenerationApplication class
  - Create `software/infra/aws/src/main/kotlin/nl/vintik/mocknest/infra/aws/generation/GenerationApplication.kt`
  - Configure `@SpringBootApplication` with generation-specific package scanning
  - Scan: `nl.vintik.mocknest.application.generation`, `nl.vintik.mocknest.application.core`, `nl.vintik.mocknest.infra.aws.generation`, `nl.vintik.mocknest.infra.aws.core`
  - Validates: Requirements 4.2, 4.5

### Phase 2: Configure Shadow JAR Tasks

- [ ] 2.1 Add shadowJarRuntime task to build.gradle.kts
  - Update `software/infra/aws/build.gradle.kts`
  - Create `shadowJarRuntime` task with `minimize()` enabled
  - Set archive filename to `mocknest-runtime.jar`
  - Set main class to `nl.vintik.mocknest.infra.aws.runtime.RuntimeApplication`
  - Configure dependency exclusions for Spring Boot, AWS Lambda, WireMock, S3
  - Configure META-INF merging for Spring Boot
  - Set destination directory to `${project.rootDir}/build/dist`
  - Validates: Requirements 2.2, 2.3, 2.4, 3.1, 3.4, 5.1, 5.3

- [ ] 2.2 Add shadowJarGeneration task to build.gradle.kts
  - Update `software/infra/aws/build.gradle.kts`
  - Create `shadowJarGeneration` task with `minimize()` enabled
  - Set archive filename to `mocknest-generation.jar`
  - Set main class to `nl.vintik.mocknest.infra.aws.generation.GenerationApplication`
  - Configure dependency exclusions for Spring Boot, AWS Lambda, Bedrock, Koog, OpenAPI parser, S3
  - Configure META-INF merging for Spring Boot
  - Set destination directory to `${project.rootDir}/build/dist`
  - Validates: Requirements 2.2, 2.3, 2.4, 3.1, 3.4, 5.2, 5.3

- [ ] 2.3 Add buildAllLambdas task to build.gradle.kts
  - Update `software/infra/aws/build.gradle.kts`
  - Create `buildAllLambdas` task that depends on both shadow JAR tasks
  - Set description and group for task organization
  - Validates: Requirements 5.4

### Phase 3: Update SAM Template

- [ ] 3.1 Add MockNestRuntimeFunction to SAM template
  - Update `deployment/aws/sam/template.yaml`
  - Define `MockNestRuntimeFunction` resource
  - Set `CodeUri` to `../../../build/dist/mocknest-runtime.jar`
  - Configure environment variables including `SPRING_CLOUD_FUNCTION_DEFINITION: "runtimeRouter"`
  - Set `ReservedConcurrentExecutions: 10`
  - Configure API Gateway events for `/__admin/{proxy+}` and `/mocknest/{proxy+}`
  - Validates: Requirements 1.1, 1.4, 6.1, 6.3

- [ ] 3.2 Add MockNestGenerationFunction to SAM template
  - Update `deployment/aws/sam/template.yaml`
  - Define `MockNestGenerationFunction` resource 
  - Set `CodeUri` to `../../../build/dist/mocknest-generation.jar`
  - Configure environment variables including `SPRING_CLOUD_FUNCTION_DEFINITION: "generationRouter"`
  - Set `ReservedConcurrentExecutions: 5` and `Timeout: 300`
  - Configure API Gateway events for `/ai/{proxy+}`
  - Validates: Requirements 1.1, 1.4, 6.2, 6.3

- [ ] 3.3 Update IAM permissions in SAM template
  - Update `deployment/aws/sam/template.yaml`
  - Ensure `MockNestLambdaRole` has appropriate permissions for both Lambdas
  - Verify S3 access permissions
  - Verify Bedrock access permissions (for generation Lambda)
  - Validates: Requirements 6.5

### Phase 4: Update Deployment Scripts

- [ ] 4.1 Update build.sh script
  - Update `deployment/aws/sam/build.sh`
  - Modify to call `./gradlew buildAllLambdas` instead of single shadow JAR task
  - Verify both JARs are copied to deployment directory
  - Validates: Requirements 5.5

- [ ] 4.2 Verify deploy.sh script compatibility
  - Review `deployment/aws/sam/deploy.sh`
  - Ensure script works with updated SAM template
  - Test parameter passing for `EnableAI` flag
  - Validates: Requirements 1.3

### Phase 5: Testing and Validation

- [ ] 5.1 Write unit tests for RuntimeLambdaHandler
  - Create test in `software/infra/aws/src/test/kotlin`
  - Test routing to `HandleAdminRequest` for admin paths
  - Test routing to `HandleClientRequest` for mock paths
  - Verify generation use cases are not invoked
  - Validates: Requirements 4.3, 7.1, 7.2

- [ ] 5.2 Write unit tests for GenerationLambdaHandler
  - Create test in `software/infra/aws/src/test/kotlin`
  - Test routing to `HandleAIGenerationRequest` for AI paths
  - Verify runtime use cases are not invoked
  - Validates: Requirements 4.3, 7.1, 7.2

- [ ] 5.3 Write integration tests for both Lambda handlers
  - Create integration tests using Spring Boot test support
  - Test runtime Lambda with LocalStack S3
  - Test generation Lambda with LocalStack S3 and Bedrock
  - Validates: Requirements 7.3

- [ ] 5.4 Write JAR size verification tests
  - Create test to verify runtime JAR is at least 30% smaller than monolithic JAR
  - Create test to verify generation JAR is at least 30% smaller than monolithic JAR
  - Validates: Requirements 3.2, 3.3, Design Property 1

- [ ] 5.5 Write dependency exclusion verification tests
  - Create test to verify runtime JAR does not contain Bedrock SDK classes
  - Create test to verify runtime JAR does not contain Koog framework classes
  - Create test to verify generation JAR does not contain WireMock server classes
  - Validates: Requirements 3.2, 3.3, Design Property 2

- [ ] 5.6 Write handler routing verification tests
  - Create test to verify runtime handler routes admin requests correctly
  - Create test to verify generation handler routes AI requests correctly
  - Use mocks or spies to verify correct use case invocation
  - Validates: Design Property 3

- [ ] 5.7 Write independent deployment verification tests
  - Create test to verify runtime JAR contains all required runtime classes
  - Create test to verify generation JAR contains all required generation classes
  - Validates: Requirements 1.2, Design Property 4

- [ ] 5.8 Run full test suite and verify coverage
  - Execute `./gradlew test`
  - Execute `./gradlew koverHtmlReport`
  - Verify overall test coverage remains at 90%+
  - Validates: Requirements 7.5

### Phase 6: Build and Deployment Validation

- [ ] 6.1 Build both Lambda JARs locally
  - Execute `./gradlew clean buildAllLambdas`
  - Verify `build/dist/mocknest-runtime.jar` exists
  - Verify `build/dist/mocknest-generation.jar` exists
  - Check JAR sizes meet reduction targets
  - Validates: Requirements 5.1, 5.2

- [ ] 6.2 Test SAM build process
  - Execute `cd deployment/aws/sam && ./build.sh`
  - Verify build completes successfully
  - Verify both JARs are in correct locations
  - Validates: Requirements 5.4, 5.5

- [ ] 6.3 Deploy to test environment
  - Execute `cd deployment/aws/sam && ./deploy.sh`
  - Deploy 
  - Verify both Lambda functions are created
  - Verify API Gateway routes are configured correctly
  - Validates: Requirements 1.3, 1.4, 6.3, 6.4

- [ ] 6.4 Test runtime Lambda endpoints
  - Test `/__admin/mappings` endpoint
  - Test `/mocknest/*` mock endpoints
  - Verify responses are correct
  - Validates: Requirements 6.1

- [ ] 6.5 Test generation Lambda endpoints
  - Test `/ai/generate` endpoint
  - Verify AI generation functionality works
  - Validates: Requirements 6.2

- [ ] 6.6 Measure cold start times
  - Measure runtime Lambda cold start time
  - Measure generation Lambda cold start time
  - Verify improvements meet targets (runtime < 6s, generation < 8s)
  - Validates: Non-functional requirements

### Phase 7: Documentation and Cleanup

- [ ] 7.1 Update README.md
  - Document new multi-Lambda architecture
  - Update deployment instructions
  - Add information about independent Lambda scaling

- [ ] 7.2 Update deployment documentation
  - Update `docs/DEPLOYMENT.md` with new build and deploy process
  - Document how to deploy runtime-only vs. runtime+generation
  - Add troubleshooting section for multi-Lambda deployment

- [ ] 7.3 Update architecture diagrams
  - Update architecture diagrams to show separate Lambda functions
  - Update API Gateway routing diagram
  - Add to `docs/` directory

- [ ] 7.4 Clean up deprecated code (if any)
  - Remove old monolithic Lambda handler if no longer needed
  - Remove old shadow JAR task if replaced
  - Update any references in comments or documentation

## Success Criteria Checklist

- [ ] Both Lambda JARs build successfully
- [ ] Runtime JAR is 40-50 MB (50% reduction from monolithic)
- [ ] Generation JAR is 35-45 MB (55% reduction from monolithic)
- [ ] Runtime Lambda cold start < 6 seconds
- [ ] Generation Lambda cold start < 8 seconds
- [ ] All existing tests pass without modification
- [ ] API Gateway routes requests correctly to appropriate Lambdas
- [ ] Both Lambdas can be deployed independently
- [ ] No changes to business logic or domain models
- [ ] Test coverage remains at 90%+

## Notes

- All tasks should be completed in order within each phase
- Each phase should be completed before moving to the next
- Testing tasks (Phase 5) are critical and should not be skipped
- Deployment validation (Phase 6) should be done in a test environment first
- Keep the existing monolithic deployment as a rollback option until multi-Lambda deployment is fully validated
