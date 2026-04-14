# Implementation Plan

- [x] 1. Write bug condition exploration test
  - **Property 1: Bug Condition** — Profile Cross-Contamination & Missing Post-Restore Reload
  - **CRITICAL**: This test MUST FAIL on unfixed code — failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior — it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate both bugs exist
  - **Scoped PBT Approach**: Use `@ParameterizedTest` with `@MethodSource` to test profile-based bean isolation across all three profiles (`runtime`, `generation`, `async`)
  - Write a `@SpringBootTest` with `@ActiveProfiles("runtime")` that asserts:
    - `RuntimePrimingHook` IS present in the context
    - `GenerationPrimingHook` is NOT present in the context
    - `MockNestConfig` IS present in the context
    - `RuntimeLambdaHandler` IS present in the context
    - `AdminRequestUseCase` IS present in the context
    - `ClientRequestUseCase` IS present in the context
  - Write a `@SpringBootTest` with `@ActiveProfiles("generation")` that asserts:
    - `GenerationPrimingHook` IS present in the context
    - `RuntimePrimingHook` is NOT present in the context
    - `MockNestConfig` is NOT present in the context
    - `RuntimeLambdaHandler` is NOT present in the context
  - Write a unit test that verifies `RuntimeMappingReloadHook` exists and its `afterRestore()` calls `wireMockServer.resetMappings()` — this will fail because the class does not exist yet
  - Use `@ParameterizedTest` with bean name lists to verify presence/absence across profiles for stronger property coverage
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests FAIL (this is correct — it proves the bugs exist: `runtime` profile doesn't activate the right beans because `@Profile("!async")` is used, and `RuntimeMappingReloadHook` doesn't exist)
  - Document counterexamples found to understand root cause
  - Mark task complete when tests are written, run, and failure is documented
  - _Requirements: 1.1, 1.2, 1.4, 1.5, 1.6, 1.7, 1.8, 2.1, 2.2, 2.4, 2.5, 2.6, 2.7, 2.11, 2.12, 2.13_

- [x] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** — Async Lambda Isolation & Existing Behavior Unchanged
  - **IMPORTANT**: Follow observation-first methodology
  - Observe: `RuntimeAsyncSpringContextTest` with `@ActiveProfiles("async")` already passes — `wireMockServer`, `runtimePrimingHook`, `adminRequestUseCase`, `clientRequestUseCase`, `runtimeLambdaHandler` are all absent
  - Observe: `MockNestConfig.wireMockServer()` wires `ObjectStorageMappingsSource` as the `MappingsSource` and `server.start()` triggers `loadMappingsInto()` — this is the normal cold start path
  - Observe: `RuntimePrimingHook.prime()` exercises WireMock engine with journal suppression — method body is unchanged
  - Write preservation property tests using `@ParameterizedTest`:
    - **Async profile isolation**: Verify that `@ActiveProfiles("async")` context does NOT contain `runtimePrimingHook`, `generationPrimingHook`, `mockNestConfig`, `runtimeLambdaHandler`, `adminRequestUseCase`, `clientRequestUseCase` — parameterized over bean names
    - **MockNestConfig wiring preservation**: Verify `MockNestConfig.wireMockServer()` still creates a running `WireMockServer` with `ObjectStorageMappingsSource`, extensions registered (NormalizeMappingBodyFilter, DeleteAllMappingsAndFilesFilter, RedactSensitiveHeadersFilter, WebhookAsyncEventPublisher)
    - **RuntimePrimingHook priming logic preservation**: Verify `RuntimePrimingHook.prime()` still exercises WireMock engine (create stub, send request, remove stub) with journal suppression using MockK
  - Verify tests PASS on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (this confirms baseline behavior to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8_

- [x] 3. Fix for SnapStart mapping reload & profile isolation

  - [x] 3.1 Change `RuntimePrimingHook` profile annotation from `@Profile("!async")` to `@Profile("runtime")`
    - File: `software/infra/aws/runtime/src/main/kotlin/nl/vintik/mocknest/infra/aws/runtime/snapstart/RuntimePrimingHook.kt`
    - Single-line change: `@Profile("!async")` → `@Profile("runtime")`
    - _Bug_Condition: isBugCondition(input) where input.lambdaType = "runtime" AND "GenerationPrimingHook" IN activatedBeans(input) due to @Profile("!async")_
    - _Expected_Behavior: activatedHooks'(X) contains "RuntimePrimingHook" only when X.profile = "runtime"_
    - _Preservation: RuntimePrimingHook.prime() method body is unchanged — only annotation changes_
    - _Requirements: 2.4, 2.5, 3.8_

  - [x] 3.2 Create `RuntimeMappingReloadHook` CRaC class with `@Profile("runtime")`
    - File: `software/infra/aws/runtime/src/main/kotlin/nl/vintik/mocknest/infra/aws/runtime/snapstart/RuntimeMappingReloadHook.kt` (NEW)
    - Create a `@Component` class annotated `@Profile("runtime")` that implements `org.crac.Resource`
    - Constructor-inject `WireMockServer` as `private val wireMockServer`
    - In `@PostConstruct` (or init block), register with `org.crac.Core.getGlobalContext().register(this)`
    - `afterRestore()`: call `wireMockServer.resetMappings()` to reload all mappings from S3 via `ObjectStorageMappingsSource.loadMappingsInto()`
    - `beforeCheckpoint()`: no-op
    - Use KotlinLogging for structured logging in `afterRestore()`
    - _Bug_Condition: isBugCondition(input) where input.isSnapStartRestore = true AND NOT postRestoreReloadExecuted(input)_
    - _Expected_Behavior: afterRestoreHookExecuted(result) = true AND wireMockStubStore(result).mappings = S3MappingsSource.loadAll()_
    - _Preservation: Normal cold start mapping loading via server.start() is not modified_
    - _Requirements: 2.1, 2.2, 2.6_

  - [x] 3.3 Change `GenerationPrimingHook` profile annotation from `@Profile("!async")` to `@Profile("generation")`
    - File: `software/infra/aws/generation/src/main/kotlin/nl/vintik/mocknest/infra/aws/generation/snapstart/GenerationPrimingHook.kt`
    - Single-line change: `@Profile("!async")` → `@Profile("generation")`
    - _Bug_Condition: isBugCondition(input) where input.lambdaType = "generation" AND "RuntimePrimingHook" IN activatedBeans(input) due to @Profile("!async")_
    - _Expected_Behavior: activatedHooks'(X) contains "GenerationPrimingHook" only when X.profile = "generation"_
    - _Preservation: GenerationPrimingHook.prime() method body is unchanged — only annotation changes_
    - _Requirements: 2.5, 3.7_

  - [x] 3.4 Change `MockNestConfig` profile annotation from `@Profile("!async")` to `@Profile("runtime")`
    - File: `software/application/src/main/kotlin/nl/vintik/mocknest/application/runtime/config/MockNestConfig.kt`
    - Single-line change: `@Profile("!async")` → `@Profile("runtime")`
    - _Bug_Condition: isBugCondition(input) where input.lambdaType = "generation" AND "MockNestConfig" IN activatedBeans(input)_
    - _Expected_Behavior: "MockNestConfig" IN activatedBeans'(X) only when X.profile = "runtime"_
    - _Preservation: MockNestConfig bean wiring logic is unchanged — only annotation changes_
    - _Requirements: 2.7, 2.11_

  - [x] 3.5 Change `RuntimeLambdaHandler` profile annotation from `@Profile("!async")` to `@Profile("runtime")`
    - File: `software/infra/aws/runtime/src/main/kotlin/nl/vintik/mocknest/infra/aws/runtime/function/RuntimeLambdaHandler.kt`
    - Single-line change: `@Profile("!async")` → `@Profile("runtime")`
    - _Bug_Condition: isBugCondition(input) where RuntimeLambdaHandler loads on generation Lambda due to @Profile("!async")_
    - _Expected_Behavior: RuntimeLambdaHandler only loads when profile = "runtime"_
    - _Preservation: RuntimeLambdaHandler routing logic is unchanged — only annotation changes_
    - _Requirements: 2.4, 2.11_

  - [x] 3.6 Change `AdminRequestUseCase` profile annotation from `@Profile("!async")` to `@Profile("runtime")`
    - File: `software/application/src/main/kotlin/nl/vintik/mocknest/application/runtime/usecases/AdminRequestUseCase.kt`
    - Single-line change: `@Profile("!async")` → `@Profile("runtime")`
    - _Bug_Condition: isBugCondition(input) where AdminRequestUseCase loads on generation Lambda due to @Profile("!async")_
    - _Expected_Behavior: AdminRequestUseCase only loads when profile = "runtime"_
    - _Preservation: AdminRequestUseCase invoke logic is unchanged — only annotation changes_
    - _Requirements: 2.4, 2.11_

  - [x] 3.7 Change `ClientRequestUseCase` profile annotation from `@Profile("!async")` to `@Profile("runtime")`
    - File: `software/application/src/main/kotlin/nl/vintik/mocknest/application/runtime/usecases/ClientRequestUseCase.kt`
    - Single-line change: `@Profile("!async")` → `@Profile("runtime")`
    - _Bug_Condition: isBugCondition(input) where ClientRequestUseCase loads on generation Lambda due to @Profile("!async")_
    - _Expected_Behavior: ClientRequestUseCase only loads when profile = "runtime"_
    - _Preservation: ClientRequestUseCase invoke logic is unchanged — only annotation changes_
    - _Requirements: 2.4, 2.11_

  - [x] 3.8 Update SAM template: remove global `SPRING_PROFILES_ACTIVE` and set per-Lambda profiles
    - File: `deployment/aws/sam/template.yaml`
    - Remove `SPRING_PROFILES_ACTIVE: aws` from `Globals.Function.Environment.Variables`
    - Add `SPRING_PROFILES_ACTIVE: runtime` to `MockNestRuntimeFunction` Environment Variables
    - Add `SPRING_PROFILES_ACTIVE: runtime` to `MockNestRuntimeFunctionIam` Environment Variables
    - Add `SPRING_PROFILES_ACTIVE: generation` to `MockNestGenerationFunction` Environment Variables
    - Add `SPRING_PROFILES_ACTIVE: generation` to `MockNestGenerationFunctionIam` Environment Variables
    - Async Lambda functions (`MockNestRuntimeAsyncFunction`, `MockNestRuntimeAsyncFunctionIam`) already set `SPRING_PROFILES_ACTIVE: "core,async"` — no change needed
    - Run `sam validate --template-file deployment/aws/sam/template.yaml --region eu-west-1` and confirm exit code 0
    - _Bug_Condition: isBugCondition(input) where all Lambdas inherit SPRING_PROFILES_ACTIVE: aws from Globals_
    - _Expected_Behavior: Runtime Lambda profile = "runtime", Generation Lambda profile = "generation", Async Lambda profile = "core,async"_
    - _Preservation: Async Lambda SPRING_PROFILES_ACTIVE: "core,async" is unchanged_
    - _Requirements: 2.8, 2.9, 2.10, 3.6_

  - [x] 3.9 Update test assertions referencing `@Profile(!async)` in `RuntimeAsyncSpringContextTest`
    - File: `software/infra/aws/runtime/src/test/kotlin/nl/vintik/mocknest/infra/aws/runtime/runtimeasync/RuntimeAsyncSpringContextTest.kt`
    - Update assertion messages that reference `@Profile(!async)` to reference the new positive profile annotations (`@Profile("runtime")`, `@Profile("generation")`)
    - The `@ActiveProfiles("async")` test itself should continue to pass because beans now use positive profiles that don't match `async`
    - _Preservation: Test behavior is unchanged — only assertion message strings are updated_
    - _Requirements: 2.13_

  - [x] 3.10 Write unit tests for `RuntimeMappingReloadHook`
    - File: `software/infra/aws/runtime/src/test/kotlin/nl/vintik/mocknest/infra/aws/runtime/snapstart/RuntimeMappingReloadHookTest.kt` (NEW)
    - Follow Given-When-Then naming convention
    - Use MockK with `relaxed = true` for `WireMockServer`
    - Test `afterRestore()` calls `wireMockServer.resetMappings()` exactly once
    - Test `beforeCheckpoint()` is a no-op (does not call any methods on `wireMockServer`)
    - Test constructor registers with CRaC global context (verify `org.crac.Core.getGlobalContext().register()` is called)
    - Include `@AfterEach` with `clearAllMocks()`
    - _Requirements: 2.1, 2.2_

  - [x] 3.11 Run `./gradlew clean test` and confirm all tests pass
    - This checkpoint verifies the full build passes after all implementation changes
    - All existing tests must continue to pass (preservation)
    - New unit tests for `RuntimeMappingReloadHook` must pass

  - [x] 3.12 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** — Profile Isolation & Post-Restore Reload Verified
    - **IMPORTANT**: Re-run the SAME tests from task 1 — do NOT write new tests
    - The tests from task 1 encode the expected behavior
    - When these tests pass, it confirms:
      - `@ActiveProfiles("runtime")` context contains only runtime beans (RuntimePrimingHook, MockNestConfig, RuntimeLambdaHandler, AdminRequestUseCase, ClientRequestUseCase) and NOT generation beans
      - `@ActiveProfiles("generation")` context contains only generation beans (GenerationPrimingHook) and NOT runtime beans
      - `RuntimeMappingReloadHook.afterRestore()` calls `wireMockServer.resetMappings()`
    - Run bug condition exploration tests from task 1
    - **EXPECTED OUTCOME**: Tests PASS (confirms bugs are fixed)
    - _Requirements: 2.1, 2.2, 2.4, 2.5, 2.6, 2.7, 2.11, 2.12, 2.13_

  - [x] 3.13 Verify preservation tests still pass
    - **Property 2: Preservation** — Async Lambda Isolation & Existing Behavior Unchanged
    - **IMPORTANT**: Re-run the SAME tests from task 2 — do NOT write new tests
    - Run preservation property tests from task 2
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)
    - Confirm all preservation tests still pass after fix (no regressions)

- [x] 4. Write Spring integration tests for profile-based bean wiring
  - [x] 4.1 Write `@SpringBootTest` with `@ActiveProfiles("runtime")` integration test
    - File: `software/infra/aws/runtime/src/test/kotlin/nl/vintik/mocknest/infra/aws/runtime/snapstart/RuntimeProfileSpringContextTest.kt` (NEW)
    - Boot full Spring context with `@ActiveProfiles("runtime")`
    - Use `@ParameterizedTest` with `@MethodSource` for bean presence/absence checks:
      - Beans that MUST be present: `runtimePrimingHook`, `mockNestConfig`, `wireMockServer`, `directCallHttpServer`, `runtimeLambdaHandler`, `adminRequestUseCase`, `clientRequestUseCase`, `runtimeMappingReloadHook`
      - Beans that MUST be absent: `generationPrimingHook`
    - Provide `TestConfiguration` with mock beans for S3, SQS dependencies (similar to `RuntimeAsyncSpringContextTest` pattern)
    - Follow Given-When-Then naming convention
    - _Requirements: 2.4, 2.11_

  - [x] 4.2 Write `@SpringBootTest` with `@ActiveProfiles("generation")` integration test
    - File: `software/infra/aws/generation/src/test/kotlin/nl/vintik/mocknest/infra/aws/generation/snapstart/GenerationProfileSpringContextTest.kt` (NEW)
    - Boot full Spring context with `@ActiveProfiles("generation")`
    - Use `@ParameterizedTest` with `@MethodSource` for bean presence/absence checks:
      - Beans that MUST be present: `generationPrimingHook`
      - Beans that MUST be absent: `runtimePrimingHook`, `mockNestConfig`, `wireMockServer`, `directCallHttpServer`, `runtimeLambdaHandler`, `runtimeMappingReloadHook`
    - Provide `TestConfiguration` with mock beans for Bedrock, S3 dependencies
    - Follow Given-When-Then naming convention
    - _Requirements: 2.5, 2.7, 2.12_

  - [x] 4.3 Update existing `RuntimeAsyncSpringContextTest` assertions for `@ActiveProfiles("async")`
    - File: `software/infra/aws/runtime/src/test/kotlin/nl/vintik/mocknest/infra/aws/runtime/runtimeasync/RuntimeAsyncSpringContextTest.kt`
    - Add assertion that `generationPrimingHook` is absent (was not previously tested)
    - Add assertion that `runtimeMappingReloadHook` is absent (new bean, must not load on async)
    - Update assertion messages from `@Profile(!async)` to reference new positive profiles
    - Existing assertions for `wireMockServer`, `runtimePrimingHook`, `adminRequestUseCase`, `clientRequestUseCase`, `runtimeLambdaHandler` absence remain unchanged
    - _Requirements: 2.13_

  - [x] 4.4 Run `./gradlew clean test` and confirm all tests pass
    - This checkpoint verifies all Spring integration tests pass with the new profile configuration

- [x] 5. Verify test coverage and quality
  - [x] 5.1 Run `./gradlew koverHtmlReport` and verify 80%+ coverage for new code (enforced threshold; aim for 90%+ as a goal)
  - [x] 5.2 Run `./gradlew koverVerify` to enforce coverage threshold
  - [x] 5.3 Review test quality: Given-When-Then naming, proper assertions, edge case coverage
