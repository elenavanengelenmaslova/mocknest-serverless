# SnapStart Mapping Reload & Profile Isolation Bugfix Design

## Overview

This design addresses two related bugs in the MockNest Serverless Lambda architecture:

1. **Stale mappings after SnapStart restore** — WireMock's in-memory stub store is captured at snapshot time and never refreshed after restore. A CRaC `afterRestore` hook scoped to `@Profile("runtime")` will call `wireMockServer.resetMappings()`, which triggers `ObjectStorageMappingsSource.loadMappingsInto()` to reload all mappings from S3.

2. **Priming hook cross-contamination** — The generic `aws` profile combined with `@Profile("!async")` negation causes both `RuntimePrimingHook` and `GenerationPrimingHook` to fire on both the Runtime and Generation Lambdas. `MockNestConfig` (WireMock server wiring) also loads on the Generation Lambda where it is not needed. The fix replaces `@Profile("!async")` with positive capability-specific profiles (`@Profile("runtime")`, `@Profile("generation")`) and updates the SAM template to set `SPRING_PROFILES_ACTIVE` per Lambda.

The fix is minimal and surgical: no business logic changes, no WireMock engine changes, no S3 persistence changes. Only profile annotations, one new CRaC hook class, and SAM template environment variables are modified.

## Glossary

- **Bug_Condition (C)**: Two conditions that trigger the bugs — (1) SnapStart restore on the Runtime Lambda without mapping reload, and (2) cross-contamination of priming hooks and config beans due to the generic `aws` profile with negation-based `@Profile("!async")` exclusions
- **Property (P)**: (1) After SnapStart restore, WireMock's in-memory stub store matches S3 state. (2) Each Lambda activates only its own priming hook and configuration beans
- **Preservation**: Existing behavior that must remain unchanged — normal cold start mapping loading, priming logic, S3 persistence, async Lambda isolation, WireMock admin API, mock request matching
- **`ObjectStorageMappingsSource`**: The `MappingsSource` implementation in `software/application` that loads WireMock stub mappings from S3 via `ObjectStorageInterface`
- **`RuntimePrimingHook`**: Component in `software/infra/aws/runtime` that warms up WireMock engine during SnapStart snapshot creation
- **`GenerationPrimingHook`**: Component in `software/infra/aws/generation` that warms up Bedrock clients and AI components during SnapStart snapshot creation
- **`MockNestConfig`**: Spring `@Configuration` in `software/application` that wires `WireMockServer`, `DirectCallHttpServer`, and related beans
- **CRaC**: Coordinated Restore at Checkpoint — the Java API (`org.crac`) used by SnapStart for lifecycle hooks (`beforeCheckpoint`, `afterRestore`)
- **`resetMappings()`**: `WireMockServer` method that clears in-memory stubs and reloads from the configured `MappingsSource`

## Bug Details

### Bug Condition

The bug manifests in two scenarios:

**Scenario 1 — Stale mappings:** When SnapStart restores a Runtime Lambda snapshot, the in-memory WireMock stub store contains stale data from snapshot time. No `afterRestore` hook exists to reload mappings from S3, so mock requests return 404 / "no matching stub" until a manual `/__admin/mappings/reset` call is made.

**Scenario 2 — Cross-contamination:** When any Lambda starts with `SPRING_PROFILES_ACTIVE: aws` (inherited from SAM Globals), all beans annotated `@Profile("!async")` are activated — including `RuntimePrimingHook`, `GenerationPrimingHook`, `MockNestConfig`, `RuntimeLambdaHandler`, `AdminRequestUseCase`, and `ClientRequestUseCase`. This means the Generation Lambda loads WireMock server beans it doesn't need, and the Runtime Lambda loads Bedrock/AI beans it doesn't need.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type LambdaInvocation
  OUTPUT: boolean

  // Condition 1: SnapStart restore without mapping reload
  LET staleMappings = input.isSnapStartRestore = true
                      AND input.lambdaType = "runtime"
                      AND NOT postRestoreReloadExecuted(input)

  // Condition 2: Cross-contamination via generic "aws" profile
  LET crossContamination =
        (input.lambdaType = "runtime" AND "GenerationPrimingHook" IN activatedBeans(input))
     OR (input.lambdaType = "generation" AND "RuntimePrimingHook" IN activatedBeans(input))
     OR (input.lambdaType = "generation" AND "MockNestConfig" IN activatedBeans(input))

  RETURN staleMappings OR crossContamination
END FUNCTION
```

### Examples

- **Stale mappings**: Runtime Lambda restores from SnapStart snapshot → user sends `GET /mocknest/api/orders` → WireMock returns "no matching stub" (404) because in-memory stubs are from snapshot time, even though the mapping exists in S3
- **Cross-contamination (Runtime)**: Runtime Lambda starts with `aws` profile → `GenerationPrimingHook` fires → attempts to warm up `BedrockRuntimeClient`, `ModelConfiguration`, `OpenAPISpecificationParser`, `PromptBuilderService` — wasting snapshot creation time and increasing snapshot size
- **Cross-contamination (Generation)**: Generation Lambda starts with `aws` profile → `RuntimePrimingHook` fires → attempts to inject `WireMockServer` and `DirectCallHttpServer` which may fail or waste resources → `MockNestConfig` loads and wires WireMock server beans unnecessarily
- **Manual workaround works**: After SnapStart restore, calling `POST /__admin/mappings/reset` triggers `ObjectStorageMappingsSource.loadMappingsInto()` and all mappings become available — confirming the data is in S3 but not loaded into memory

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- Normal (non-SnapStart) cold start continues to load mappings from S3 during `server.start()` via `ObjectStorageMappingsSource.loadMappingsInto()` — this path is not modified
- `RuntimePrimingHook.prime()` logic (health check warm-up, S3 client initialization, WireMock engine exercise with journal suppression) remains identical — only the `@Profile` annotation changes
- `GenerationPrimingHook.prime()` logic (Bedrock client warm-up, specification parser exercise, prompt builder exercise, mock validator exercise, SOAP/WSDL and GraphQL component exercise) remains identical — only the `@Profile` annotation changes
- WireMock admin API persistence to S3 via `ObjectStorageMappingsSource.save()` / `remove()` / `removeAll()` is not modified
- Mock request matching via `DirectCallHttpServer.stubRequest()` is not modified
- Async Lambda profile configuration (`SPRING_PROFILES_ACTIVE: "core,async"`) is not modified
- `RuntimeAsyncPrimingHook` (`@Profile("async")`) is not modified
- `S3Configuration` (`@Profile("!local")`) is not modified — this profile exclusion is orthogonal to capability profiles
- `BedrockConfiguration` (no profile annotation) is not modified — it remains available to any profile that needs it

**Scope:**
All inputs that do NOT involve SnapStart restore on the Runtime Lambda or profile-based bean activation are completely unaffected by this fix. This includes:
- Non-SnapStart Lambda invocations (regular cold starts and warm invocations)
- Mock request matching and response serving
- WireMock admin API operations (save, remove, reset, list mappings)
- S3 persistence operations
- Async Lambda webhook dispatch

## Hypothesized Root Cause

Based on the bug description and code analysis, the root causes are:

1. **Missing CRaC `afterRestore` hook**: `RuntimePrimingHook` implements `ApplicationReadyEvent` listener for snapshot-time priming but has no CRaC `Resource` implementation with `afterRestore()` to reload mappings after SnapStart restore. The WireMock server's in-memory stub store is captured in the snapshot and never refreshed.

2. **Generic `aws` profile with negation-based exclusions**: The SAM template `Globals` section sets `SPRING_PROFILES_ACTIVE: aws` for all Lambdas. All runtime-specific beans use `@Profile("!async")` — a negation that only excludes the async Lambda. Since the `aws` profile is not `async`, all `@Profile("!async")` beans activate on every Lambda that inherits the global profile (Runtime and Generation). The profile system lacks positive capability identification.

3. **Seven classes affected by `@Profile("!async")`**: The following classes all use `@Profile("!async")` and will cross-contaminate:
   - `RuntimePrimingHook` (infra/aws/runtime)
   - `GenerationPrimingHook` (infra/aws/generation)
   - `MockNestConfig` (application/runtime)
   - `RuntimeLambdaHandler` (infra/aws/runtime)
   - `AdminRequestUseCase` (application/runtime)
   - `ClientRequestUseCase` (application/runtime)

4. **SAM template only overrides for Async Lambda**: Only the Async Lambda explicitly sets `SPRING_PROFILES_ACTIVE: "core,async"`. The Runtime and Generation Lambdas inherit the global `aws` value, making them indistinguishable to Spring's profile system.

## Correctness Properties

Property 1: Bug Condition — Mappings Reloaded After SnapStart Restore

_For any_ Runtime Lambda invocation where SnapStart restore has occurred, the fixed system SHALL execute a CRaC `afterRestore` hook that calls `wireMockServer.resetMappings()`, causing `ObjectStorageMappingsSource.loadMappingsInto()` to reload all mappings from S3 into WireMock's in-memory stub store, so that subsequent mock requests return correct responses.

**Validates: Requirements 2.1, 2.2**

Property 2: Bug Condition — Profile-Based Bean Isolation

_For any_ Lambda invocation, the fixed system SHALL activate only the priming hook and configuration beans that correspond to the Lambda's capability-specific profile: `@Profile("runtime")` beans on the Runtime Lambda, `@Profile("generation")` beans on the Generation Lambda, and `@Profile("async")` beans on the Async Lambda — eliminating cross-contamination.

**Validates: Requirements 2.4, 2.5, 2.6, 2.7, 2.11, 2.12, 2.13**

Property 3: Preservation — Unchanged Behavior for Non-Bug Inputs

_For any_ input where the bug condition does NOT hold (non-SnapStart invocations, mock request matching, S3 persistence, admin API operations, async Lambda dispatch), the fixed system SHALL produce exactly the same behavior as the original system, preserving all existing functionality including normal cold start mapping loading, priming logic, journal suppression, and WireMock engine behavior.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8**

## Fix Implementation

### Changes Required

Assuming our root cause analysis is correct:

**File**: `software/infra/aws/runtime/src/main/kotlin/nl/vintik/mocknest/infra/aws/runtime/snapstart/RuntimePrimingHook.kt`

**Change 1 — Profile annotation**: Change `@Profile("!async")` to `@Profile("runtime")`.

**File**: `software/infra/aws/runtime/src/main/kotlin/nl/vintik/mocknest/infra/aws/runtime/snapstart/RuntimeMappingReloadHook.kt` (NEW)

**Change 2 — New CRaC afterRestore hook**: Create a new `@Component` class annotated `@Profile("runtime")` that implements `org.crac.Resource`. In `afterRestore()`, call `wireMockServer.resetMappings()` to reload all mappings from S3. Register itself with `org.crac.Core.getGlobalContext().register(this)` in the constructor or `@PostConstruct`. The `beforeCheckpoint()` method is a no-op.

**File**: `software/infra/aws/generation/src/main/kotlin/nl/vintik/mocknest/infra/aws/generation/snapstart/GenerationPrimingHook.kt`

**Change 3 — Profile annotation**: Change `@Profile("!async")` to `@Profile("generation")`.

**File**: `software/application/src/main/kotlin/nl/vintik/mocknest/application/runtime/config/MockNestConfig.kt`

**Change 4 — Profile annotation**: Change `@Profile("!async")` to `@Profile("runtime")`.

**File**: `software/infra/aws/runtime/src/main/kotlin/nl/vintik/mocknest/infra/aws/runtime/function/RuntimeLambdaHandler.kt`

**Change 5 — Profile annotation**: Change `@Profile("!async")` to `@Profile("runtime")`.

**File**: `software/application/src/main/kotlin/nl/vintik/mocknest/application/runtime/usecases/AdminRequestUseCase.kt`

**Change 6 — Profile annotation**: Change `@Profile("!async")` to `@Profile("runtime")`.

**File**: `software/application/src/main/kotlin/nl/vintik/mocknest/application/runtime/usecases/ClientRequestUseCase.kt`

**Change 7 — Profile annotation**: Change `@Profile("!async")` to `@Profile("runtime")`.

**File**: `deployment/aws/sam/template.yaml`

**Change 8 — SAM template profiles**: 
- Remove `SPRING_PROFILES_ACTIVE: aws` from `Globals.Function.Environment.Variables`
- Add `SPRING_PROFILES_ACTIVE: runtime` to both `MockNestRuntimeFunction` and `MockNestRuntimeFunctionIam` environment variables
- Add `SPRING_PROFILES_ACTIVE: generation` to both `MockNestGenerationFunction` and `MockNestGenerationFunctionIam` environment variables
- Async Lambda functions already set `SPRING_PROFILES_ACTIVE: "core,async"` — no change needed

**File**: `software/infra/aws/runtime/src/test/kotlin/nl/vintik/mocknest/infra/aws/runtime/runtimeasync/RuntimeAsyncSpringContextTest.kt`

**Change 9 — Update test assertions**: Update assertion messages that reference `@Profile(!async)` to reference the new positive profile annotations. The `@ActiveProfiles("async")` test itself should continue to pass because the beans now use positive profiles (`runtime`, `generation`) that don't match `async`.

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, surface counterexamples that demonstrate the bug on unfixed code, then verify the fix works correctly and preserves existing behavior.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate the bug BEFORE implementing the fix. Confirm or refute the root cause analysis. If we refute, we will need to re-hypothesize.

**Test Plan**: Write Spring integration tests that boot the application context with specific profiles and assert bean presence/absence. Run these tests on the UNFIXED code to observe failures and understand the root cause.

**Test Cases**:
1. **Runtime Profile Cross-Contamination Test**: Boot with `@ActiveProfiles("runtime")` — on unfixed code, `GenerationPrimingHook` will be present because `@Profile("!async")` matches any non-async profile (will fail on unfixed code because the `runtime` profile doesn't exist yet and beans won't load at all)
2. **Generation Profile Cross-Contamination Test**: Boot with `@ActiveProfiles("generation")` — on unfixed code, `RuntimePrimingHook` and `MockNestConfig` will be present because `@Profile("!async")` matches (will fail on unfixed code for the same reason)
3. **Stale Mappings Test**: Unit test that creates a `RuntimeMappingReloadHook` and verifies `afterRestore()` calls `wireMockServer.resetMappings()` (will fail on unfixed code because the class doesn't exist)

**Expected Counterexamples**:
- On unfixed code with `aws` profile: both `RuntimePrimingHook` and `GenerationPrimingHook` are present in the same context
- On unfixed code: no CRaC `afterRestore` hook exists, so `wireMockServer.resetMappings()` is never called after restore

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds, the fixed function produces the expected behavior.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  result := fixedSystem(input)
  IF input.isSnapStartRestore AND input.lambdaType = "runtime" THEN
    ASSERT afterRestoreHookExecuted(result)
    ASSERT wireMockStubStore(result).mappings = S3MappingsSource.loadAll()
  END IF
  IF input.lambdaType = "runtime" THEN
    ASSERT "RuntimePrimingHook" IN activatedBeans(result)
    ASSERT "GenerationPrimingHook" NOT IN activatedBeans(result)
  END IF
  IF input.lambdaType = "generation" THEN
    ASSERT "GenerationPrimingHook" IN activatedBeans(result)
    ASSERT "RuntimePrimingHook" NOT IN activatedBeans(result)
    ASSERT "MockNestConfig" NOT IN activatedBeans(result)
  END IF
END FOR
```

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed function produces the same result as the original function.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT originalSystem(input) = fixedSystem(input)
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because:
- It generates many test cases automatically across the input domain
- It catches edge cases that manual unit tests might miss
- It provides strong guarantees that behavior is unchanged for all non-buggy inputs

**Test Plan**: Observe behavior on UNFIXED code first for non-bug inputs (async Lambda isolation, normal cold start mapping loading, WireMock admin API), then write tests capturing that behavior.

**Test Cases**:
1. **Async Lambda Isolation Preservation**: The existing `RuntimeAsyncSpringContextTest` with `@ActiveProfiles("async")` must continue to pass — verifying that async Lambda remains isolated from both runtime and generation components
2. **Normal Cold Start Mapping Loading Preservation**: Verify that `MockNestConfig.wireMockServer()` still wires `ObjectStorageMappingsSource` as the `MappingsSource` and that `server.start()` triggers `loadMappingsInto()`
3. **Priming Logic Preservation**: Verify that `RuntimePrimingHook.prime()` and `GenerationPrimingHook.prime()` method bodies are unchanged — only `@Profile` annotations change
4. **WireMock Admin API Preservation**: Verify that `ObjectStorageMappingsSource.save()`, `remove()`, `removeAll()` continue to work

### Unit Tests

- Test `RuntimeMappingReloadHook.afterRestore()` calls `wireMockServer.resetMappings()` using MockK
- Test `RuntimeMappingReloadHook.beforeCheckpoint()` is a no-op
- Test `RuntimeMappingReloadHook` registers itself with CRaC global context

### Property-Based Tests

- `@SpringBootTest` with `@ActiveProfiles("runtime")` — assert runtime-specific beans present, generation-specific beans absent, post-restore hook present
- `@SpringBootTest` with `@ActiveProfiles("generation")` — assert generation-specific beans present, runtime-specific beans absent, MockNestConfig absent
- `@SpringBootTest` with `@ActiveProfiles("async")` — assert both runtime and generation beans absent (existing test, updated assertions)
- Use `@ParameterizedTest` with bean name lists to verify presence/absence across profiles

### Integration Tests

- Full Spring context boot with `runtime` profile verifying WireMock server starts and serves mock responses
- Full Spring context boot with `generation` profile verifying Bedrock client is available and WireMock is not loaded
- SAM template validation (`sam validate`) confirming the template is syntactically correct after profile changes
