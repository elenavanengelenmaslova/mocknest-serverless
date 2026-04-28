# Implementation Plan: Spring Cloud Function to Koin Migration

## Overview

This plan follows the bottom-up migration strategy from the design document: Domain → Application → Infrastructure → Build → SAM → Tests. Each task builds incrementally on the previous one, ensuring the codebase compiles and tests pass at each checkpoint. The migration log (`docs/migration.md`) is created first and updated throughout.

## Tasks

- [x] 1. Create migration log and record pre-migration baseline
  - [x] 1.1 Create `docs/migration.md` with "The Good, The Bad, and The Unexpected" article structure
    - Use the same conversational, lesson-based writing style as the Koog + Bedrock article (numbered lessons, code examples, real metrics)
    - Structure with sections: Introduction (why we migrated), The Good (wins — JAR size, cold start, simplicity), The Bad (pain points — what broke, what was harder than expected), The Unexpected (surprises — things tutorials don't tell you about Koin/Lambda/SnapStart)
    - Include a metrics table: pre-migration JAR size (83 MB), Spring dependency breakdown (28 MB Spring classes, 4.5 MB Reactor, 0.4 MB Spring Cloud), post-migration JAR size (TBD), Lambda Power Tuner before/after results (TBD)
    - Include a Koin module mapping section showing how Spring `@Configuration` classes map to Koin modules
    - Write in English, matching the tone and format of the previous article
    - _Requirements: 12.1, 12.3, 12.5_

  - [x] 1.2 Run `./gradlew clean build` and confirm all tests pass before starting migration
    - Record the baseline test count and coverage in `docs/migration.md`
    - _Requirements: 14.1, 14.2_

- [x] 2. Replace Spring HTTP types in the domain layer with custom Kotlin types
  - [x] 2.1 Create `HttpMethod` enum in `software/domain/src/main/kotlin/nl/vintik/mocknest/domain/core/HttpMethod.kt`
    - Implement `GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS, TRACE` entries
    - Add `companion object` with case-insensitive `valueOf(method: String)` factory
    - _Requirements: 9.1_

  - [x] 2.2 Create `HttpStatusCode` value class in `software/domain/src/main/kotlin/nl/vintik/mocknest/domain/core/HttpStatusCode.kt`
    - Implement `@JvmInline value class HttpStatusCode(val value: Int)` with `require(value in 100..599)`
    - Add `fun value(): Int` for API compatibility with Spring's `HttpStatusCode.value()`
    - Add companion object constants: `OK(200)`, `CREATED(201)`, `BAD_REQUEST(400)`, `NOT_FOUND(404)`, `INTERNAL_SERVER_ERROR(500)`
    - _Requirements: 9.2_

  - [x] 2.3 Update `HttpRequest.kt` to use custom `HttpMethod` instead of `org.springframework.http.HttpMethod`
    - Change import from `org.springframework.http.HttpMethod` to `nl.vintik.mocknest.domain.core.HttpMethod`
    - _Requirements: 9.1_

  - [x] 2.4 Update `HttpResponse.kt` to use custom `HttpStatusCode` and `Map<String, List<String>>` headers
    - Replace `org.springframework.http.HttpStatusCode` with `nl.vintik.mocknest.domain.core.HttpStatusCode`
    - Replace `MultiValueMap<String, String>?` with `Map<String, List<String>>?`
    - Remove Spring imports
    - _Requirements: 9.2, 9.3_

  - [x] 2.5 Update `APISpecification.kt` — change `EndpointDefinition.method` from Spring `HttpMethod` to custom `HttpMethod`
    - _Requirements: 9.1_

  - [x] 2.6 Update `GeneratedMock.kt` — change `EndpointInfo.method` from Spring `HttpMethod` to custom `HttpMethod`
    - _Requirements: 9.1_

  - [x] 2.7 Remove `api("org.springframework:spring-web")` from `software/domain/build.gradle.kts`
    - _Requirements: 1.6_

  - [x] 2.8 Write property tests for domain HTTP types (Property 1: Domain HTTP type round-trip)
    - **Property 1: Domain HTTP type round-trip**
    - Use `@ParameterizedTest` with `@ValueSource` for all 8 HTTP method strings — verify `HttpMethod.valueOf(s).name` round-trips
    - Use `@ParameterizedTest` with `@MethodSource` for status codes 100–599 — verify `HttpStatusCode(n).value()` returns `n`
    - Test that `HttpStatusCode` outside 100–599 throws `IllegalArgumentException`
    - **Validates: Requirements 9.1, 9.2**

  - [x] 2.9 Write property tests for header map single-value conversion (Property 2: Header map single-value conversion)
    - **Property 2: Header map single-value conversion**
    - Use `@ParameterizedTest` with `@MethodSource` generating diverse `Map<String, List<String>>` instances
    - Verify `mapValues { it.value.first() }` produces a `Map<String, String>` with same keys and first values
    - **Validates: Requirements 9.3**

  - [x] 2.10 Run `./gradlew :software:domain:test` and confirm domain tests pass
    - _Requirements: 14.2_

- [x] 3. Migrate the application layer — remove Spring annotations and update HTTP type usages
  - [x] 3.1 Update `HttpResponseHelper.kt` — replace `HttpStatus.OK` with `HttpStatusCode.OK`, replace `LinkedMultiValueMap` with `mutableMapOf<String, List<String>>()`
    - Replace `headers.add("Content-Type", "application/json")` with `headers["Content-Type"] = listOf("application/json")`
    - Remove Spring imports (`org.springframework.http.HttpStatus`, `org.springframework.util.LinkedMultiValueMap`)
    - _Requirements: 9.2, 9.3_

  - [x] 3.2 Remove `@Component` and `@Profile` annotations from `AdminRequestUseCase.kt` and `ClientRequestUseCase.kt`
    - Remove Spring imports (`org.springframework.stereotype.Component`, `org.springframework.context.annotation.Profile`)
    - _Requirements: 2.2_

  - [x] 3.3 Remove `@Service` annotation from `PromptBuilderService.kt`
    - Remove Spring import (`org.springframework.stereotype.Service`)
    - _Requirements: 2.3_

  - [x] 3.4 Remove `@Component` annotation from `OpenAPIMockValidator.kt`
    - Remove Spring import (`org.springframework.stereotype.Component`)
    - _Requirements: 2.2_

  - [x] 3.5 Remove `@Configuration`, `@Profile`, `@Bean`, `@Value`, `@DependsOn`, `@PropertySource` annotations from `MockNestConfig.kt`
    - Convert to a plain Kotlin object or top-level function `createWireMockServer(...)` that takes all dependencies as parameters
    - Replace `@Value("\${MOCKNEST_WEBHOOK_QUEUE_URL:}")` with `System.getenv("MOCKNEST_WEBHOOK_QUEUE_URL") ?: ""`
    - Remove all Spring imports
    - _Requirements: 2.1, 2.5_

  - [x] 3.6 Update all application-layer files that import `org.springframework.http.HttpMethod` or `org.springframework.http.HttpStatus` to use the new domain types
    - Search for remaining Spring HTTP type imports across `software/application/src/main/kotlin/` and replace them
    - _Requirements: 9.1, 9.2, 9.4_

  - [x] 3.7 Update `software/application/build.gradle.kts` — remove Spring dependencies
    - Remove `kotlin("plugin.spring")`
    - Remove `implementation("org.springframework.boot:spring-boot-starter")`
    - Remove `implementation("org.springframework.boot:spring-boot-starter-web")`
    - Remove `implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")`
    - Remove `testImplementation("org.springframework.boot:spring-boot-starter-test")`
    - _Requirements: 1.7_

  - [x] 3.8 Update application-layer test files — replace Spring HTTP type imports with custom domain types
    - Update all 25+ test files in `software/application/src/test/kotlin/` that use `org.springframework.http.HttpMethod` or `org.springframework.http.HttpStatus`
    - _Requirements: 9.4_

  - [x] 3.9 Run `./gradlew :software:domain:test :software:application:test` and confirm all tests pass
    - _Requirements: 14.2_

- [x] 4. Checkpoint — Ensure domain and application layers compile and pass tests
  - Ensure all tests pass, ask the user if questions arise.
  - Run `./gradlew :software:domain:test :software:application:test`
  - Document any problems encountered in `docs/migration.md`

- [x] 5. Migrate shared infrastructure — create KoinBootstrap, coreModule, and migrate S3Configuration
  - [x] 5.1 Create `KoinBootstrap` object in `software/infra/aws/core/src/main/kotlin/nl/vintik/mocknest/infra/aws/core/di/KoinBootstrap.kt`
    - Implement idempotent `init(modules: List<Module>)` with double-checked locking — `startKoin` fails if called twice in the same JVM (tests, multiple handlers)
    - Use `allowOverride(false)` in `startKoin` to catch accidental duplicate bean definitions (e.g., S3Client in both coreModule and runtimeModule)
    - Add `fun getKoin(): Koin = GlobalContext.get().koin` for handler priming and CRaC hook registration
    - Write unit test verifying calling `init()` twice does not throw
    - _Requirements: 3.5_

  - [x] 5.2 Create `coreModule()` function in `software/infra/aws/core/src/main/kotlin/nl/vintik/mocknest/infra/aws/core/di/CoreModule.kt`
    - Define as `fun coreModule() = module { ... }` (function, not global val — Koin docs recommend this because `module {}` preallocates factories)
    - Include shared Jackson ObjectMapper and S3Client
    - Migrate `S3Configuration.kt` (`@Configuration @Profile("!local")` with `@Value("${AWS_REGION:eu-west-1}")`) into `coreModule` as `single { S3Client { region = System.getenv("AWS_REGION") ?: "eu-west-1" } }`
    - Delete `S3Configuration.kt` and its test after migration
    - _Requirements: 2.1_

  - [x] 5.3 Update `software/infra/aws/core/build.gradle.kts` — add Koin dependency
    - Add `implementation("io.insert-koin:koin-core")`
    - Add `testImplementation("io.insert-koin:koin-test-junit5")`
    - _Requirements: 1.2_

  - [x] 5.4 Run `./gradlew :software:infra:aws:core:test` and confirm core tests pass
    - _Requirements: 14.2_

- [x] 6. Migrate runtime infrastructure layer — replace Spring DI with Koin modules
  - [x] 6.1 Update `software/infra/aws/runtime/build.gradle.kts` — remove Spring, add Koin
    - Remove `kotlin("plugin.spring")`
    - Remove `id("io.spring.dependency-management")`
    - Remove `spring-boot-starter`, `spring-boot-starter-validation`
    - Remove `spring-cloud-function-adapter-aws`, `spring-cloud-function-kotlin`
    - Remove `testImplementation("org.springframework.boot:spring-boot-starter-test")`
    - Add `implementation("io.insert-koin:koin-core")`
    - Add `testImplementation("io.insert-koin:koin-test-junit5")`
    - _Requirements: 1.1, 1.2, 1.3, 1.4_

  - [x] 6.2 Remove Spring annotations from runtime infra source files
    - Remove `@Component`, `@Profile` from `RuntimePrimingHook.kt`, `RuntimeMappingReloadHook.kt`, `RuntimeAsyncPrimingHook.kt`, `RuntimeAsyncHandler.kt`, `AwsRuntimeHealthUseCase.kt`
    - Remove `@EventListener(ApplicationReadyEvent::class)` from priming hooks
    - Remove `@PostConstruct` from `RuntimeMappingReloadHook.register()` — CRaC registration will be done explicitly in the handler init block
    - Remove `jakarta.annotation.PostConstruct` import from `RuntimeMappingReloadHook.kt`
    - Keep `RuntimeMappingReloadHook` as a plain Kotlin class implementing `org.crac.Resource` with `afterRestore()`, `beforeCheckpoint()`, and `register()` methods
    - Replace `@Value` injections with `System.getenv()` calls (e.g., `@param:Value($"${storage.bucket.name}")` → constructor parameter with default)
    - Remove `@Configuration` and `@Bean` from `WebhookInfraConfig.kt`
    - Remove all Spring imports from these files
    - _Requirements: 2.1, 2.2, 2.5_

  - [x] 6.3 Create `runtimeModule()` function in `software/infra/aws/runtime/src/main/kotlin/nl/vintik/mocknest/infra/aws/runtime/di/RuntimeModule.kt`
    - Define as `fun runtimeModule() = module { ... }` (function, not global val) with all runtime beans: SqsClient, ObjectStorageInterface, WebhookConfig, WebhookHttpClient, SqsWebhookPublisher, DirectCallHttpServerFactory, BlobStore, RedactSensitiveHeadersFilter, S3RequestJournalStore, WireMockServer, DirectCallHttpServer, GetRuntimeHealth, HandleAdminRequest, HandleClientRequest, RuntimePrimingHook, RuntimeMappingReloadHook
    - Include `single { RuntimeMappingReloadHook(get()) }` for CRaC lifecycle support
    - S3Client comes from `coreModule` — do NOT redeclare it here
    - Use `System.getenv()` for all configuration values (MOCKNEST_S3_BUCKET_NAME, AWS_DEFAULT_REGION, MOCKNEST_WEBHOOK_QUEUE_URL, etc.)
    - _Requirements: 2.1, 2.4, 2.5, 2.6_

  - [x] 6.4 Create `asyncModule()` function in `software/infra/aws/runtime/src/main/kotlin/nl/vintik/mocknest/infra/aws/runtime/di/AsyncModule.kt`
    - Define as `fun asyncModule() = module { ... }` with: WebhookConfig, WebhookHttpClient, RuntimeAsyncHandler, RuntimeAsyncPrimingHook
    - _Requirements: 2.1, 2.6_

  - [x] 6.5 Rewrite `RuntimeLambdaHandler.kt` as a direct `RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>` with Koin
    - Implement `RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>` interface and `KoinComponent`
    - Use `KoinBootstrap.init(listOf(coreModule(), runtimeModule()))` in `companion object init` block
    - Call `KoinBootstrap.getKoin().get<RuntimePrimingHook>().onApplicationReady()` explicitly after Koin init — this runs BEFORE SnapStart snapshot, not lazily on first request
    - Register CRaC lifecycle hooks after Koin initialization: `KoinBootstrap.getKoin().get<RuntimeMappingReloadHook>().register()` — enables `afterRestore()` for mock reload after SnapStart restore
    - Inject `HandleClientRequest`, `HandleAdminRequest`, `GetRuntimeHealth` via `by inject()`
    - Move routing logic from `runtimeRouter` `Function<>` bean into `handleRequest()` method
    - Use `withHeaders(mapValues { it.value.first() })` for single-value header conversion (decision: keep first value only, matching current `toSingleValueMap()` behavior)
    - Replace `HttpStatus.NOT_FOUND` with `HttpStatusCode.NOT_FOUND`
    - Replace Spring `HttpMethod.valueOf()` with custom `HttpMethod.valueOf()`
    - Verify the final class signature matches the SAM handler FQCN: `nl.vintik.mocknest.infra.aws.runtime.function.RuntimeLambdaHandler`
    - _Requirements: 3.1, 3.4, 3.5, 6.1, 10.1_

  - [x] 6.6 Create `RuntimeAsyncLambdaHandler.kt` in `software/infra/aws/runtime/src/main/kotlin/nl/vintik/mocknest/infra/aws/runtime/runtimeasync/RuntimeAsyncLambdaHandler.kt`
    - Implement `RequestHandler<SQSEvent, Unit>` interface and `KoinComponent`
    - Use `KoinBootstrap.init(listOf(coreModule(), asyncModule()))` in `companion object init` block
    - Call `KoinBootstrap.getKoin().get<RuntimeAsyncPrimingHook>().onApplicationReady()` explicitly after Koin init
    - Register CRaC lifecycle hooks after Koin initialization if any async components implement CRaC `Resource`
    - Inject `RuntimeAsyncHandler` via `by inject()`
    - Delegate to `runtimeAsyncHandler.handle(event)` in `handleRequest()`
    - Verify the final class signature matches the SAM handler FQCN: `nl.vintik.mocknest.infra.aws.runtime.runtimeasync.RuntimeAsyncLambdaHandler`
    - _Requirements: 3.3, 3.4, 3.5, 6.1, 10.3_

  - [x] 6.7 Write property tests for runtime routing equivalence (Property 3)
    - **Property 3: Runtime routing equivalence**
    - Use `@ParameterizedTest` with `@MethodSource` generating `APIGatewayProxyRequestEvent` instances covering all routing branches: `/__admin/health`, `/__admin/*`, `/mocknest/*`, unknown paths
    - Mock use cases with MockK, verify correct delegation
    - **Validates: Requirements 3.4, 7.1, 10.1**

  - [x] 6.8 Write unit tests for RuntimeLambdaHandler routing logic
    - Test health check path, admin path, client path, and 404 path
    - Verify correct status codes and response body construction
    - Test multi-value header conversion — verify only first value is used in `withHeaders()`
    - _Requirements: 7.1, 10.1_

  - [x] 6.9 Verify CRaC `afterRestore()` behavior in unit tests
    - Update `RuntimeMappingReloadHookTest` to work without Spring context
    - Confirm `afterRestore()` calls `wireMockServer.resetToDefaultMappings()`
    - Confirm `beforeCheckpoint()` is a no-op
    - Confirm manual `register()` calls `Core.getGlobalContext().register(this)`
    - Note: AWS requires `afterRestore()` hooks to complete within 10 seconds — mock reload must remain bounded
    - _Requirements: 6.4_

  - [x] 6.10 Document CRaC behavior in `docs/migration.md`
    - Describe how Spring previously registered CRaC lifecycle via `@PostConstruct` on a Spring-managed component; Koin requires explicit registration
    - Include any issues encountered with restore behavior
    - _Requirements: 12.2_

  - [x] 6.11 Run `./gradlew :software:infra:aws:runtime:test` and confirm all runtime tests pass
    - _Requirements: 14.2_

- [x] 7. Migrate generation infrastructure layer — replace Spring DI with Koin modules
  - [x] 7.1 Update `software/infra/aws/generation/build.gradle.kts` — remove Spring, add Koin
    - Remove `kotlin("plugin.spring")`
    - Remove `id("io.spring.dependency-management")`
    - Remove `spring-boot-starter`, `spring-boot-starter-validation`
    - Remove `spring-cloud-function-adapter-aws`, `spring-cloud-function-kotlin`
    - Remove `testImplementation("org.springframework.boot:spring-boot-starter-test")`
    - Add `implementation("io.insert-koin:koin-core")`
    - Add `testImplementation("io.insert-koin:koin-test-junit5")`
    - _Requirements: 1.1, 1.2, 1.3, 1.4_

  - [x] 7.2 Remove Spring annotations from generation infra source files
    - Remove `@Configuration`, `@Bean` from `AIGenerationConfiguration.kt`, `BedrockConfiguration.kt`, `GraphQLGenerationConfig.kt`, `SoapGenerationConfig.kt`
    - Remove `@Component`, `@Profile` from `GenerationPrimingHook.kt`, `S3GenerationStorageAdapter.kt`, `ModelConfiguration.kt`, `AwsAIHealthUseCase.kt`
    - Replace all `@Value` injections with `System.getenv()` or constructor parameters
    - Remove `@Primary` annotations (Koin uses explicit wiring, no ambiguity)
    - Remove all Spring imports from these files
    - _Requirements: 2.1, 2.2, 2.5_

  - [x] 7.3 Create `generationModule()` function in `software/infra/aws/generation/src/main/kotlin/nl/vintik/mocknest/infra/aws/generation/di/GenerationModule.kt`
    - Define as `fun generationModule() = module { ... }` (function, not global val) with all generation beans: BedrockRuntimeClient, GenerationStorageInterface, InferencePrefixResolver, ModelConfiguration, OpenAPISpecificationParser, GraphQL parsers/reducers, WSDL parsers/reducers, CompositeSpecificationParser, validators, PromptBuilderService, BedrockServiceAdapter, MockGenerationFunctionalAgent, GenerateMocksFromSpecWithDescriptionUseCase, HandleAIGenerationRequest, GetAIHealth, GenerationPrimingHook
    - S3Client comes from `coreModule` — do NOT redeclare it here
    - Use `named()` qualifiers for GraphQL and WSDL specification parsers
    - Use `System.getenv()` for all configuration values
    - _Requirements: 2.1, 2.4, 2.5, 2.6_

  - [x] 7.4 Rewrite `GenerationLambdaHandler.kt` as a direct `RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>` with Koin
    - Implement `RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>` interface and `KoinComponent`
    - Use `KoinBootstrap.init(listOf(coreModule(), generationModule()))` in `companion object init` block
    - Call `KoinBootstrap.getKoin().get<GenerationPrimingHook>().onApplicationReady()` explicitly after Koin init
    - Register CRaC lifecycle hooks after Koin initialization if any generation components implement CRaC `Resource`
    - Inject `HandleAIGenerationRequest`, `GetAIHealth` via `by inject()`
    - Move routing logic from `generationRouter` `Function<>` bean into `handleRequest()` method
    - Update header conversion and status code types to custom domain types
    - Verify the final class signature matches the SAM handler FQCN: `nl.vintik.mocknest.infra.aws.generation.function.GenerationLambdaHandler`
    - _Requirements: 3.2, 3.4, 3.5, 6.1, 10.2_

  - [x] 7.5 Write property tests for generation routing equivalence (Property 4)
    - **Property 4: Generation routing equivalence**
    - Use `@ParameterizedTest` with `@MethodSource` generating `APIGatewayProxyRequestEvent` instances covering: `/ai/health`, `/ai/*`, unknown paths
    - Mock use cases with MockK, verify correct delegation
    - **Validates: Requirements 3.4, 7.2, 10.2**

  - [x] 7.6 Write property tests for async processing equivalence (Property 5)
    - **Property 5: Async processing equivalence**
    - Use `@ParameterizedTest` with `@MethodSource` generating `SQSEvent` instances with varying record counts
    - Mock `RuntimeAsyncHandler`, verify `handle()` is called with the event
    - **Validates: Requirements 3.4, 7.3, 10.3**

  - [x] 7.7 Run `./gradlew :software:infra:aws:generation:test` and confirm all generation tests pass
    - _Requirements: 14.2_

- [x] 8. Checkpoint — Ensure all infrastructure modules compile and pass tests
  - Ensure all tests pass, ask the user if questions arise.
  - Run `./gradlew :software:domain:test :software:application:test :software:infra:aws:runtime:test :software:infra:aws:generation:test`
  - Document any problems encountered in `docs/migration.md`

- [x] 9. Migrate the mocknest module — update build system and Shadow JAR
  - [x] 9.1 Update `software/infra/aws/mocknest/build.gradle.kts` — remove Spring, add Koin, simplify Shadow JAR
    - Remove `kotlin("plugin.spring")`, `id("org.springframework.boot")`, `id("io.spring.dependency-management")`
    - Remove `springBoot { mainClass }` block
    - Remove `bootJar` and `bootRun` task disabling
    - Remove `spring-cloud-function-adapter-aws`, `spring-cloud-function-kotlin`
    - Remove `testImplementation("org.springframework.boot:spring-boot-starter-test")`
    - Add `implementation("io.insert-koin:koin-core")`
    - Add `testImplementation("io.insert-koin:koin-test-junit5")`
    - In `shadowJar` task: remove `mergeServiceFiles()`, remove all 6 `append()` calls for Spring metadata, remove ONLY Spring-specific class exclusions (`org/springframework/boot/devtools/**`, `org/springframework/boot/test/**`, `org/springframework/test/**`), update manifest to remove Spring main class
    - KEEP all non-Spring exclusions: Jetty (`org/eclipse/jetty/alpn/**`, `org/eclipse/jetty/jmx/**`, etc.), `assets/swagger-ui/**`, `samples/**`, `mozilla/public-suffix-list.txt`, `ucd/**`, security files (`META-INF/*.SF`, etc.), and all `runtimeClasspath` exclusions (Tomcat, Undertow, Guava, Apache HTTP, Lettuce, Kotlin compiler)
    - _Requirements: 1.1, 1.3, 1.4, 5.1, 5.2, 5.3_

  - [x] 9.2 Remove or replace `MockNestApplication.kt` — delete `@SpringBootApplication` entry point
    - The Spring application class is no longer needed since each Lambda handler initializes Koin directly
    - Either delete the file or replace with a minimal placeholder if needed by tests
    - _Requirements: 1.1_

  - [x] 9.3 Remove `application.properties` from `software/infra/aws/mocknest/src/main/resources/`
    - Spring property files are no longer needed — all config comes from environment variables
    - _Requirements: 1.1, 2.5_

  - [x] 9.4 Update root `build.gradle.kts` — remove Spring plugins and BOMs, add Koin BOM
    - Remove `kotlin("plugin.spring")` plugin declaration
    - Remove `id("org.springframework.boot")` plugin declaration
    - Remove `id("io.spring.dependency-management")` plugin declaration
    - Remove Spring Boot and Spring Cloud BOM imports from `configure<DependencyManagementExtension>`
    - Remove the entire `configure<DependencyManagementExtension>` block (replace with explicit version management)
    - Remove `implementation("org.jetbrains.kotlin:kotlin-reflect")` from global `subprojects` dependencies if the build, tests, Koog, WireMock, Jackson, and Koin usage still pass without it — verify by running `./gradlew clean build` after removal
    - Add Koin BOM: `implementation(platform("io.insert-koin:koin-bom:4.2.1"))` or equivalent version catalog entry
    - Add explicit version management for dependencies previously managed by Spring BOM: `jackson-module-kotlin:3.1.1`, `jackson-datatype-jsr310:3.1.1`, `kotlinx-coroutines-core:1.10.2`
    - Remove `apply(plugin = "io.spring.dependency-management")` from `subprojects` block
    - _Requirements: 1.3, 1.4, 1.5_

  - [x] 9.5 Update `software/infra/generation-core/build.gradle.kts` if it has Spring dependencies — remove them
    - Check for and remove any Spring plugin or dependency references
    - _Requirements: 1.1_

  - [x] 9.6 Run `./gradlew clean build` and confirm full project compiles and all tests pass
    - _Requirements: 14.1, 14.2_

- [x] 10. Checkpoint — Full build passes after Spring removal
  - Ensure all tests pass, ask the user if questions arise.
  - Run `./gradlew clean build`
  - Document any problems encountered in `docs/migration.md`

- [x] 11. Migrate integration tests from Spring Boot Test to Koin Test
  - [x] 11.1 Migrate runtime integration tests — replace `@SpringBootTest` with Koin test utilities
    - Replace `@SpringBootTest` with `KoinTest` interface
    - Replace `@Autowired` with `val dep: Type by inject()` or `val dep = get<Type>()`
    - Replace `@TestPropertySource` / `@DynamicPropertySource` with `System.setProperty()` or `system-stubs-jupiter`
    - Add `KoinBootstrap.init(listOf(coreModule(), runtimeModule()))` in `@BeforeAll` and `stopKoin()` in `@AfterAll` (or `@BeforeEach`/`@AfterEach` for test isolation)
    - Keep LocalStack TestContainers setup unchanged
    - _Requirements: 8.1, 8.2, 8.3, 8.4_

  - [x] 11.2 Migrate generation integration tests — replace `@SpringBootTest` with Koin test utilities
    - Same pattern as 11.1 but with `coreModule(), generationModule()`
    - _Requirements: 8.1, 8.2, 8.3, 8.4_

  - [x] 11.3 Migrate async integration tests — replace Spring context tests with Koin module verification tests
    - Replace `RuntimeAsyncSpringContextTest` and `AsyncProfileIsolationPreservationTest` with Koin module verification tests using `verify()` where possible. Use isolated `koinApplication { modules(...) }` startup tests for cases that need real environment variables, AWS clients, or dynamic parameters
    - Verify that `asyncModule()` resolves all dependencies correctly
    - Verify that runtime-specific beans are NOT present in async module (isolation)
    - _Requirements: 8.5_

  - [x] 11.4 Write Koin module wiring verification tests
    - Use `verify()` on `runtimeModule()`, `generationModule()`, and `asyncModule()` to validate dependency graphs at compile/test time
    - For cases needing real environment variables or AWS clients, use isolated `koinApplication { modules(coreModule(), runtimeModule()) }` startup tests
    - Note: `checkModules()` is deprecated since Koin 4.0 — use `verify()` or the Koin compiler plugin instead
    - _Requirements: 8.5_

  - [x] 11.5 Write property tests for environment variable configuration mapping (Property 6)
    - **Property 6: Environment variable configuration mapping**
    - Use `@ParameterizedTest` with `@MethodSource` generating environment variable combinations
    - Use `system-stubs-jupiter` to set environment variables
    - Verify Koin-resolved beans have configuration values matching the environment variables
    - **Validates: Requirements 2.5**

  - [x] 11.6 Run `./gradlew clean test` and confirm all tests pass
    - _Requirements: 14.2_

- [x] 12. Update SAM template — new handler FQCNs, remove Spring environment variables, clean up env var naming
  - [x] 12.1 Update all 6 Lambda function definitions in `deployment/aws/sam/template.yaml`
    - Change `Handler` for MockNestRuntimeFunction and MockNestRuntimeFunctionIam to `nl.vintik.mocknest.infra.aws.runtime.function.RuntimeLambdaHandler`
    - Change `Handler` for MockNestGenerationFunction and MockNestGenerationFunctionIam to `nl.vintik.mocknest.infra.aws.generation.function.GenerationLambdaHandler`
    - Change `Handler` for MockNestRuntimeAsyncFunction and MockNestRuntimeAsyncFunctionIam to `nl.vintik.mocknest.infra.aws.runtime.runtimeasync.RuntimeAsyncLambdaHandler`
    - Remove `SPRING_PROFILES_ACTIVE` environment variable from all 6 functions
    - Remove `SPRING_CLOUD_FUNCTION_DEFINITION` environment variable from all 6 functions
    - Remove `MAIN_CLASS` environment variable from all 6 functions
    - Remove `MOCK_STORAGE_BUCKET` environment variable (duplicate of `MOCKNEST_S3_BUCKET_NAME` — settle on `MOCKNEST_S3_BUCKET_NAME` as the single canonical name)
    - Retain all other environment variables unchanged
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6_

  - [x] 12.2 Run `sam validate --template-file deployment/aws/sam/template.yaml --region eu-west-1` and confirm exit code 0
    - _Requirements: 4.7_

- [x] 13. Verify zero Spring imports, dependency tree, and build the Shadow JAR
  - [x] 13.1 Run `grep -r "org.springframework" --include="*.kt" software/` and confirm zero matches in source files
    - Exclude `bin/` directories (compiled output)
    - _Requirements: 9.4_

  - [x] 13.2 Run dependency tree verification for all affected modules
    - Run `./gradlew :software:infra:aws:mocknest:dependencies --configuration runtimeClasspath | grep -i spring` and confirm zero Spring dependencies
    - Run `./gradlew :software:infra:aws:runtime:dependencies --configuration runtimeClasspath | grep -i spring` and confirm zero
    - Run `./gradlew :software:infra:aws:generation:dependencies --configuration runtimeClasspath | grep -i spring` and confirm zero
    - Run `./gradlew :software:application:dependencies --configuration runtimeClasspath | grep -i spring` and confirm zero
    - Spring can remain transitively even after removing direct dependencies — this catches that
    - _Requirements: 1.1_

  - [x] 13.3 Build the Shadow JAR with `./gradlew :software:infra:aws:mocknest:shadowJar`
    - _Requirements: 5.4, 14.3_

  - [x] 13.4 Verify the Shadow JAR contains zero Spring classes
    - Run `jar tf build/dist/mocknest-serverless.jar | grep -c "org/springframework"` and confirm 0
    - Run `jar tf build/dist/mocknest-serverless.jar | grep -c "spring-cloud"` and confirm 0
    - _Requirements: 11.2, 11.3, 11.4_

  - [x] 13.5 Record post-migration JAR size in `docs/migration.md` and compare with pre-migration (83 MB)
    - Verify JAR is smaller than 83 MB (expected ~55-58 MB)
    - _Requirements: 11.1, 12.3_

- [x] 14. Final build verification and coverage check
  - [x] 14.1 Run `./gradlew clean build` and confirm all modules compile and all tests pass
    - _Requirements: 14.1, 14.2_

  - [x] 14.2 Run `./gradlew koverHtmlReport` and verify 90%+ aggregated code coverage
    - _Requirements: 14.4_

  - [x] 14.3 Run `./gradlew koverVerify` to enforce the 90% coverage threshold
    - _Requirements: 14.4_

  - [x] 14.4 Update `docs/migration.md` with final Koin module structure mapping and any remaining observations
    - Document how each Spring `@Configuration` class maps to Koin modules
    - Document any unexpected behavioral differences discovered during testing
    - _Requirements: 12.2, 12.4, 12.5_

- [x] 15. Final checkpoint — Full migration verified
  - Ensure all tests pass, ask the user if questions arise.
  - Run `./gradlew clean build`
  - Verify `sam validate` passes
  - Verify zero Spring imports in source files
  - Verify Shadow JAR size reduction
  - Confirm `docs/migration.md` is complete with all sections filled in

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation at each migration phase
- Property tests validate universal correctness properties from the design document
- The migration log (`docs/migration.md`) is updated throughout for the future article
- Post-deploy tests are NOT modified — they validate the deployed Lambda functions as-is (Requirement 13.1)
- Lambda Power Tuner comparison (Requirements 13.2–13.5) is performed after deployment, outside this task list
- `coreModule` and `KoinBootstrap` live in `:software:infra:aws:core` to avoid circular dependencies between `:runtime`/`:generation` and `:mocknest`
- `KoinBootstrap` ensures idempotent startup with `allowOverride(false)` — safe for tests where multiple handlers may share a JVM, and catches accidental duplicate bean definitions
- Shared modules defined as functions (`fun coreModule() = module { }`) not global vals — Koin docs recommend this because `module {}` preallocates factories
- `checkModules()` is deprecated since Koin 4.0 — use `verify()` or isolated `koinApplication { }` startup tests instead
- Priming is called explicitly in handler `companion object init` blocks, BEFORE SnapStart snapshot — not lazily on first request
- CRaC (`org.crac:crac`) is REQUIRED for SnapStart restore behavior — specifically for `afterRestore()` mock reload
- Spring previously registered CRaC lifecycle via `@PostConstruct` on a Spring-managed component; Koin requires explicit CRaC registration in handler init blocks
- Priming happens during handler initialization; CRaC `afterRestore()` handles post-restore state (separate concerns)
- CRaC hooks MUST be registered after Koin initialization, otherwise they will not have access to dependencies
- CRaC `afterRestore()` must complete within 10 seconds (AWS Lambda restore hook timeout) — mock reload must remain bounded and observable through logs
- `APIGatewayProxyResponseEvent.withHeaders()` accepts single-value `Map<String, String>` — use `mapValues { it.value.first() }` (matching current `toSingleValueMap()` behavior)
- Shadow JAR cleanup removes ONLY Spring-specific exclusions — all Jetty, swagger-ui, samples, and security exclusions are retained
- `MOCKNEST_S3_BUCKET_NAME` is the canonical env var name — `MOCK_STORAGE_BUCKET` is removed from SAM to avoid config drift
- `kotlin-reflect` is removed from global dependencies if the build, tests, Koog, WireMock, Jackson, and Koin still pass without it
- Dependency tree verification (`./gradlew dependencies | grep spring`) catches transitive Spring dependencies that `grep` on source files would miss
- `S3Configuration.kt` (`@Configuration @Profile("!local")`) is migrated into `coreModule` — don't forget to delete the original file
