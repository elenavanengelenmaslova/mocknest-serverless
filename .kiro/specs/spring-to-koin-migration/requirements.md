# Requirements Document

## Introduction

This document specifies the requirements for migrating MockNest Serverless from Spring Cloud Function / Spring Boot dependency injection to Koin, a lightweight Kotlin-native DI framework. The migration is motivated by the fact that Spring is used exclusively for dependency injection — no Spring Web, Spring Data, or other Spring features are leveraged. Replacing Spring with Koin will reduce JAR size, improve Lambda cold-start performance, simplify packaging (no Spring service file merging), and align the DI framework with the Kotlin-native technology stack.

This is a pure refactoring effort: all existing functionality, API contracts, serialization behavior, and post-deploy test compatibility must be preserved.

## Glossary

- **Koin_DI**: The Koin dependency injection framework for Kotlin, providing module-based bean definitions without annotation processing or reflection-heavy startup
- **Spring_DI**: The Spring Framework dependency injection container, currently used via `@Configuration`, `@Bean`, `@Component`, `@Service`, and `@Value` annotations
- **Spring_Cloud_Function**: The Spring Cloud Function adapter for AWS Lambda that provides `FunctionInvoker` as the Lambda handler entry point and routes to Spring beans via `SPRING_CLOUD_FUNCTION_DEFINITION`
- **Lambda_Handler**: A class implementing `com.amazonaws.services.lambda.runtime.RequestHandler` that serves as the direct entry point for AWS Lambda invocations, replacing `FunctionInvoker`
- **SAM_Template**: The AWS SAM `template.yaml` file defining Lambda functions, API Gateway, and other AWS resources
- **Shadow_JAR**: The fat JAR produced by the Shadow Gradle plugin, packaging all runtime dependencies into a single deployable artifact
- **SnapStart**: AWS Lambda SnapStart feature that creates a snapshot of the initialized Lambda execution environment to reduce cold-start latency
- **Priming_Hook**: Code that executes during SnapStart snapshot creation to warm up resources (S3 clients, parsers, validators)
- **Koin_Module**: A Koin `module { }` block that declares bean definitions using `single { }`, `factory { }`, and `scoped { }` DSL functions
- **Post_Deploy_Tests**: Existing end-to-end tests that validate the deployed Lambda functions via API Gateway, which must pass without modification after migration
- **Migration_Log**: A `migration.md` document recording all problems, decisions, and solutions encountered during the migration for a future article
- **Build_System**: The Gradle multi-module build configuration including root `build.gradle.kts`, module-specific build files, and `settings.gradle.kts`
- **Profile**: A mechanism to conditionally activate beans based on the Lambda function type (runtime, generation, async); currently implemented via Spring `@Profile`, to be replaced with Koin qualifiers or conditional module loading
- **Environment_Variable**: OS-level variables set in the SAM template that configure Lambda behavior (e.g., `BEDROCK_MODEL_NAME`, `MOCKNEST_S3_BUCKET_NAME`, `AWS_REGION`)

## Requirements

### Requirement 1: Remove Spring Framework Dependencies

**User Story:** As a developer, I want to remove all Spring Framework dependencies from the project, so that the JAR size is reduced and cold-start performance improves.

#### Acceptance Criteria

1. WHEN the migration is complete, THE Build_System SHALL have zero compile-time or runtime dependencies on Spring Boot, Spring Cloud Function, Spring Cloud, or Spring Framework artifacts across all modules, including `:software:domain`, `:software:application`, `:software:infra:generation-core`, and all `:software:infra:aws:*` modules
2. WHEN the migration is complete, THE Build_System SHALL declare Koin_DI as the dependency injection framework in all modules that require DI
3. WHEN the migration is complete, THE Build_System SHALL remove the `kotlin("plugin.spring")` Gradle plugin from all modules
4. WHEN the migration is complete, THE Build_System SHALL remove the `org.springframework.boot` and `io.spring.dependency-management` Gradle plugins from all modules
5. WHEN the migration is complete, THE Build_System SHALL remove the Spring Boot and Spring Cloud BOM imports from the root `build.gradle.kts` dependency management block
6. WHEN the migration is complete, THE `:software:domain` module SHALL remove the `api("org.springframework:spring-web")` dependency
7. WHEN the migration is complete, THE `:software:application` module SHALL remove `spring-boot-starter`, `spring-boot-starter-web`, `spring-boot-starter-test`, and `coroutines-reactor` dependencies

### Requirement 2: Replace Spring DI Annotations with Koin Modules

**User Story:** As a developer, I want all Spring DI annotations replaced with Koin module definitions, so that the application uses a lightweight Kotlin-native DI framework.

#### Acceptance Criteria

1. WHEN the migration is complete, THE Koin_Module definitions SHALL provide equivalent beans for all 7 `@Configuration` classes: MockNestConfig, BedrockConfiguration, AIGenerationConfiguration, SoapGenerationConfig, GraphQLGenerationConfig, GenerationLambdaHandler, and WebhookInfraConfig
2. WHEN the migration is complete, THE Koin_Module definitions SHALL provide equivalent beans for all 8 `@Component` classes: OpenAPIMockValidator, GenerationPrimingHook, S3GenerationStorageAdapter, ModelConfiguration, AwsAIHealthUseCase, AdminRequestUseCase, ClientRequestUseCase, and RuntimeAsyncHandler
3. WHEN the migration is complete, THE Koin_Module definitions SHALL provide equivalent beans for the PromptBuilderService `@Service` class
4. WHEN the migration is complete, THE Koin_Module definitions SHALL provide equivalent beans for all 15+ `@Bean` methods currently defined across configuration classes
5. WHEN the migration is complete, THE Koin_Module definitions SHALL replace all `@Value` property injection with direct reads from environment variables or a Koin-compatible configuration mechanism
6. WHEN the migration is complete, THE Koin_Module definitions SHALL replicate the conditional bean activation currently provided by `@Profile("runtime")`, `@Profile("generation")`, and `@Profile("!local")` annotations

### Requirement 3: Implement Direct Lambda Handlers

**User Story:** As a developer, I want direct AWS Lambda handler classes that replace Spring Cloud Function's `FunctionInvoker`, so that Lambda invocations no longer require Spring context initialization.

#### Acceptance Criteria

1. THE Lambda_Handler for the Runtime function SHALL implement `RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>` and initialize Koin_DI with the runtime module set
2. THE Lambda_Handler for the Generation function SHALL implement `RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>` and initialize Koin_DI with the generation module set
3. THE Lambda_Handler for the RuntimeAsync function SHALL implement `RequestHandler<SQSEvent, Unit>` and initialize Koin_DI with the async module set
4. WHEN a Lambda_Handler receives a request, THE Lambda_Handler SHALL delegate to the same routing logic currently implemented in `runtimeRouter`, `generationRouter`, and `runtimeAsyncRouter` beans
5. WHEN a Lambda_Handler initializes, THE Lambda_Handler SHALL start Koin_DI exactly once per Lambda container lifecycle, reusing the Koin application across warm invocations

### Requirement 4: Update SAM Template Configuration

**User Story:** As a developer, I want the SAM template updated to reference the new direct Lambda handlers, so that AWS Lambda invokes the correct entry points after migration.

#### Acceptance Criteria

1. WHEN the migration is complete, THE SAM_Template SHALL reference the fully qualified class name of the new Runtime Lambda_Handler in the `Handler` property for MockNestRuntimeFunction and MockNestRuntimeFunctionIam
2. WHEN the migration is complete, THE SAM_Template SHALL reference the fully qualified class name of the new Generation Lambda_Handler in the `Handler` property for MockNestGenerationFunction and MockNestGenerationFunctionIam
3. WHEN the migration is complete, THE SAM_Template SHALL reference the fully qualified class name of the new RuntimeAsync Lambda_Handler in the `Handler` property for MockNestRuntimeAsyncFunction and MockNestRuntimeAsyncFunctionIam
4. WHEN the migration is complete, THE SAM_Template SHALL remove the `SPRING_CLOUD_FUNCTION_DEFINITION` environment variable from all Lambda function definitions
5. WHEN the migration is complete, THE SAM_Template SHALL remove the `MAIN_CLASS` environment variable from all Lambda function definitions
6. WHEN the migration is complete, THE SAM_Template SHALL remove the `SPRING_PROFILES_ACTIVE` environment variable from all Lambda function definitions
7. THE SAM_Template SHALL pass `sam validate --template-file deployment/aws/sam/template.yaml --region eu-west-1` with exit code 0

### Requirement 5: Simplify Shadow JAR Packaging

**User Story:** As a developer, I want the Shadow JAR build simplified by removing Spring-specific packaging workarounds, so that the build is cleaner and the artifact is smaller.

#### Acceptance Criteria

1. WHEN the migration is complete, THE Shadow_JAR configuration SHALL remove the `mergeServiceFiles()` call that was required for Spring
2. WHEN the migration is complete, THE Shadow_JAR configuration SHALL remove all `append()` calls for Spring metadata files (`META-INF/spring.handlers`, `META-INF/spring.schemas`, `META-INF/spring.tooling`, `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, `META-INF/spring/org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration.imports`, `META-INF/spring.factories`)
3. WHEN the migration is complete, THE Shadow_JAR configuration SHALL remove the exclusion of Spring Boot devtools and test classes (`org/springframework/boot/devtools/**`, `org/springframework/boot/test/**`, `org/springframework/test/**`)
4. WHEN the migration is complete, THE Shadow_JAR SHALL produce a valid deployable artifact that AWS Lambda can execute

### Requirement 6: Preserve SnapStart and Priming Behavior

**User Story:** As a developer, I want SnapStart and priming behavior preserved after migration, so that cold-start performance optimizations continue to work.

#### Acceptance Criteria

1. WHEN the Lambda_Handler initializes in a SnapStart environment, THE Priming_Hook SHALL execute the same warm-up logic currently triggered by `ApplicationReadyEvent` (S3 client, Bedrock client, specification parsers, prompt builder, mock validators, WSDL components, GraphQL components)
2. WHEN the Lambda_Handler initializes in a non-SnapStart environment, THE Priming_Hook SHALL skip priming logic, matching current behavior
3. THE SAM_Template SHALL retain `SnapStart: ApplyOn: PublishedVersions` and `AutoPublishAlias: live` for all Lambda functions
4. THE Priming_Hook and reload hooks SHALL preserve the existing CRaC lifecycle behavior, including:
   - initialization-time priming executed before SnapStart snapshot creation
   - `afterRestore()` logic to reload mocks and restore runtime state after SnapStart restore

### Requirement 7: Preserve All Existing Functionality

**User Story:** As a developer, I want all existing functionality preserved exactly as-is after migration, so that the refactoring introduces no behavioral changes.

#### Acceptance Criteria

1. THE Runtime Lambda_Handler SHALL route requests to WireMock admin API, mock endpoints, and health check endpoints identically to the current `runtimeRouter` bean
2. THE Generation Lambda_Handler SHALL route requests to AI generation endpoints and health check endpoints identically to the current `generationRouter` bean
3. THE RuntimeAsync Lambda_Handler SHALL process SQS webhook events identically to the current `runtimeAsyncRouter` bean, including SigV4 signing for `aws_iam` auth mode
4. THE WireMock server configuration SHALL initialize with the same extensions, stores, and mappings source as the current MockNestConfig
5. THE S3GenerationStorageAdapter SHALL connect to S3 with the same bucket name and region configuration as the current `@Value`-injected properties
6. THE BedrockConfiguration SHALL create a BedrockRuntimeClient with the same region and optional custom endpoint support as the current implementation
7. THE ModelConfiguration SHALL resolve the Bedrock model name and inference prefix identically to the current `@Value`-injected properties

### Requirement 8: Migrate Integration Test Infrastructure

**User Story:** As a developer, I want the integration test infrastructure migrated from Spring Boot Test to Koin Test, so that tests validate the Koin-based DI configuration.

#### Acceptance Criteria

1. WHEN the migration is complete, THE integration tests SHALL replace `@SpringBootTest` with Koin Test utilities for DI container initialization
2. WHEN the migration is complete, THE integration tests SHALL replace `@Autowired` injection with Koin `inject()` or `get()` for obtaining test dependencies
3. WHEN the migration is complete, THE integration tests SHALL replace `@TestPropertySource` and `@DynamicPropertySource` with Koin-compatible property configuration or direct environment variable setup
4. WHEN the migration is complete, THE integration tests SHALL continue to use LocalStack TestContainers for S3 and other AWS service testing
5. WHEN the migration is complete, THE integration tests SHALL validate that Koin_Module definitions correctly wire all dependencies for runtime, generation, and async profiles

### Requirement 9: Replace Spring HTTP Types

**User Story:** As a developer, I want Spring HTTP types replaced with non-Spring alternatives, so that no Spring dependency remains in the codebase.

#### Acceptance Criteria

1. WHEN the migration is complete, THE codebase SHALL replace all usages of `org.springframework.http.HttpMethod` with an equivalent non-Spring type (e.g., a Kotlin enum or the AWS Lambda events library types), including in domain models (`HttpRequest`, `APISpecification`, `GeneratedMock`), application layer use cases, and all tests
2. WHEN the migration is complete, THE codebase SHALL replace all usages of `org.springframework.http.HttpStatus` and `org.springframework.http.HttpStatusCode` with an equivalent non-Spring type (e.g., integer status codes or a Kotlin enum), including in the domain model `HttpResponse`
3. WHEN the migration is complete, THE codebase SHALL replace all usages of `org.springframework.http.HttpHeaders` and `org.springframework.util.MultiValueMap` with `Map<String, List<String>>` or an equivalent non-Spring type, including in the domain model `HttpResponse`
4. WHEN the migration is complete, THE codebase SHALL have zero imports from `org.springframework` packages in any source file across all layers (domain, application, infrastructure, and tests)

### Requirement 10: Replace Spring java.util.function.Function Router Pattern

**User Story:** As a developer, I want the Spring Cloud Function router pattern replaced with direct handler methods, so that Lambda request routing no longer depends on `java.util.function.Function` beans.

#### Acceptance Criteria

1. WHEN the migration is complete, THE Runtime Lambda_Handler SHALL contain the request routing logic currently in the `runtimeRouter` `Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>` bean, implemented as a direct method
2. WHEN the migration is complete, THE Generation Lambda_Handler SHALL contain the request routing logic currently in the `generationRouter` `Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>` bean, implemented as a direct method
3. WHEN the migration is complete, THE RuntimeAsync Lambda_Handler SHALL contain the event processing logic currently in the `runtimeAsyncRouter` `Function<SQSEvent, Unit>` bean, implemented as a direct method
4. WHEN the migration is complete, THE codebase SHALL have zero usages of `java.util.function.Function` as a Lambda routing mechanism

### Requirement 11: Reduce JAR Size

**User Story:** As a developer, I want the Shadow JAR size reduced after removing Spring dependencies, so that deployment artifacts are smaller and upload times are faster.

#### Acceptance Criteria

1. WHEN the migration is complete, THE Shadow_JAR size SHALL be smaller than the pre-migration JAR size
2. WHEN the migration is complete, THE Shadow_JAR SHALL contain zero Spring Framework class files
3. WHEN the migration is complete, THE Shadow_JAR SHALL contain zero Spring Cloud Function class files
4. WHEN the migration is complete, THE Shadow_JAR SHALL contain zero Spring Boot class files

### Requirement 12: Document Migration Process

**User Story:** As a developer, I want all migration problems, decisions, and solutions documented in a migration log, so that the experience can be shared in a future article.

#### Acceptance Criteria

1. THE Migration_Log SHALL be created at `docs/migration.md` at the start of the migration process, using a "The Good, The Bad, and The Unexpected" article structure matching the tone and format of the existing Koog + Bedrock lessons-learned article
2. THE Migration_Log SHALL record each problem encountered during migration as a numbered lesson with description, root cause, and solution — written in a conversational, developer-to-developer style
3. THE Migration_Log SHALL record the pre-migration and post-migration JAR sizes for comparison in a metrics table
4. THE Migration_Log SHALL record any unexpected behavioral differences discovered during testing as lessons in "The Unexpected" section
5. THE Migration_Log SHALL record the Koin module structure and how it maps to the previous Spring configuration

### Requirement 13: Validate Performance After Migration

**User Story:** As a developer, I want performance validated after migration using Lambda Power Tuner, so that cold-start and warm-invocation performance are confirmed to be equal or better.

#### Acceptance Criteria

1. WHEN the migration is complete, THE Post_Deploy_Tests SHALL pass without any modifications to the test scripts or test assertions
2. THE Migration_Log SHALL record Lambda Power Tuner results for the Runtime function before and after migration
3. THE Migration_Log SHALL record Lambda Power Tuner results for the Generation function before and after migration
4. THE Migration_Log SHALL record Lambda Power Tuner results for the RuntimeAsync function before and after migration
5. IF the post-migration cold-start time is more than 20% slower than pre-migration for any Lambda function, THEN THE Migration_Log SHALL document the root cause and remediation steps

### Requirement 14: Maintain Build and CI/CD Compatibility

**User Story:** As a developer, I want the Gradle build and GitHub Actions CI/CD pipelines to work after migration, so that the development workflow is uninterrupted.

#### Acceptance Criteria

1. WHEN the migration is complete, THE Build_System SHALL compile all modules successfully with `./gradlew clean build`
2. WHEN the migration is complete, THE Build_System SHALL pass all unit and integration tests with `./gradlew clean test`
3. WHEN the migration is complete, THE Build_System SHALL produce the Shadow_JAR with `./gradlew :software:infra:aws:mocknest:shadowJar`
4. WHEN the migration is complete, THE Build_System SHALL maintain 90%+ aggregated code coverage as enforced by Kover
5. IF the GitHub Actions workflows reference Spring-specific build steps, THEN THE Build_System SHALL update those workflows to remove Spring-specific steps
