# Bugfix Requirements Document

## Introduction

This document covers two related SnapStart bugs in the MockNest Serverless Lambda architecture:

**Bug 1 — Stale mappings after SnapStart restore:** After enabling SnapStart on the Runtime Lambda, WireMock mock mappings are not available on cold start (restore from snapshot). The mappings are loaded once during Spring context initialization (`server.start()` → `ObjectStorageMappingsSource.loadMappingsInto()`), but SnapStart takes its snapshot *after* this initialization. When the snapshot is restored for a new invocation, the in-memory WireMock state is from snapshot time — which may be empty or stale relative to what is currently in S3. The current `RuntimePrimingHook` only exercises the WireMock engine during snapshot creation but has no `afterRestore` hook to reload mappings from S3 after each restore. Calling `/__admin/mappings/reset` manually triggers a reload and fixes the issue, confirming the mappings exist in S3 but are simply not loaded into memory post-restore.

**Bug 2 — Priming hook cross-contamination caused by the generic `aws` profile:** All three Lambda functions share the same JAR (`mocknest-serverless.jar`) and `MockNestApplication` main class. The SAM template `Globals` section sets `SPRING_PROFILES_ACTIVE: aws` for all functions, and only the Async Lambda overrides this with `SPRING_PROFILES_ACTIVE: "core,async"`. The `aws` profile is a generic cloud-provider label that does not differentiate between Lambda capabilities. Both `RuntimePrimingHook` (`@Profile("!async")`) and `GenerationPrimingHook` (`@Profile("!async")`) use the same negation-based profile exclusion, so both hooks fire on both the Runtime and Generation Lambdas. Similarly, `MockNestConfig` (which wires the WireMock server, `DirectCallHttpServer`, and related beans) uses `@Profile("!async")`, meaning it also loads on the Generation Lambda where WireMock is not needed.

The fix replaces the generic `aws` profile with three capability-specific Spring profiles that map 1:1 to the three Lambda functions:
- **`runtime`** — for the Runtime Lambda (serves mock responses, WireMock engine)
- **`generation`** — for the Generation Lambda (AI mock generation via Bedrock)
- **`async`** — for the Async Lambda (webhook dispatch via SQS) — already exists and works correctly

This makes `@Profile` annotations self-documenting and eliminates the need for negation-based exclusions like `@Profile("!async")`.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN SnapStart restores a Lambda snapshot and a mock request arrives THEN the system returns no match (404 or WireMock "no matching stub" response) because in-memory mappings from snapshot time are stale or empty

1.2 WHEN SnapStart restores a Lambda snapshot THEN the system does not execute any post-restore logic to reload mappings from S3 into WireMock's in-memory stub store

1.3 WHEN a user manually calls `/__admin/mappings/reset` after a SnapStart restore THEN the system correctly loads all mappings from S3, confirming the mappings exist in storage but were not loaded automatically

1.4 WHEN the Runtime Lambda starts with `SPRING_PROFILES_ACTIVE: aws` (inherited from SAM Globals) THEN both `RuntimePrimingHook` and `GenerationPrimingHook` are activated because both use `@Profile("!async")` and the generic `aws` profile does not differentiate between Lambda capabilities

1.5 WHEN the Generation Lambda starts with `SPRING_PROFILES_ACTIVE: aws` (inherited from SAM Globals) THEN both `GenerationPrimingHook` and `RuntimePrimingHook` are activated because both use `@Profile("!async")` and the generic `aws` profile does not differentiate between Lambda capabilities

1.6 WHEN `RuntimePrimingHook` fires on the Generation Lambda THEN the system attempts to exercise WireMock engine components (stub creation, request matching, journal suppression) that are irrelevant to the Generation Lambda's purpose, wasting snapshot creation time and potentially causing errors if WireMock beans are not available

1.7 WHEN `GenerationPrimingHook` fires on the Runtime Lambda THEN the system attempts to warm up Bedrock clients, AI model configuration, specification parsers, and prompt builders that are irrelevant to the Runtime Lambda's purpose, wasting snapshot creation time and increasing snapshot size

1.8 WHEN `MockNestConfig` loads on the Generation Lambda (because it uses `@Profile("!async")` and the `aws` profile does not include `async`) THEN the system unnecessarily wires WireMock server, `DirectCallHttpServer`, and related beans on a Lambda that only needs Bedrock/AI components

### Expected Behavior (Correct)

2.1 WHEN SnapStart restores a Lambda snapshot and a mock request arrives THEN the system SHALL return the correct mock response because mappings have been automatically reloaded from S3 after restore

2.2 WHEN SnapStart restores a Lambda snapshot THEN the system SHALL execute a post-restore hook (CRaC `afterRestore` or equivalent) that reloads all mappings from S3 into WireMock's in-memory stub store

2.3 WHEN a user calls `/__admin/mappings/reset` after a SnapStart restore THEN the system SHALL continue to reload mappings from S3 as before (manual reset remains functional)

2.4 WHEN the Runtime Lambda starts with `SPRING_PROFILES_ACTIVE: runtime` THEN only `RuntimePrimingHook` (annotated `@Profile("runtime")`) SHALL be activated; `GenerationPrimingHook` SHALL NOT be activated on the Runtime Lambda

2.5 WHEN the Generation Lambda starts with `SPRING_PROFILES_ACTIVE: generation` THEN only `GenerationPrimingHook` (annotated `@Profile("generation")`) SHALL be activated; `RuntimePrimingHook` SHALL NOT be activated on the Generation Lambda

2.6 WHEN the post-restore mapping reload executes THEN it SHALL only run on the Runtime Lambda (scoped via `@Profile("runtime")`), not on the Generation Lambda or Async Lambda

2.7 WHEN `MockNestConfig` is evaluated on the Generation Lambda THEN it SHALL NOT load because it uses `@Profile("runtime")`, and the Generation Lambda's active profile is `generation`

2.8 WHEN the SAM template deploys the Runtime Lambda THEN its environment SHALL set `SPRING_PROFILES_ACTIVE: runtime` instead of inheriting the global `aws` profile

2.9 WHEN the SAM template deploys the Generation Lambda THEN its environment SHALL set `SPRING_PROFILES_ACTIVE: generation` instead of inheriting the global `aws` profile

2.10 WHEN the SAM template `Globals` section is evaluated THEN `SPRING_PROFILES_ACTIVE` SHALL either be removed from Globals or set to a base value that does not activate capability-specific beans, since each Lambda now declares its own profile

2.11 WHEN a Spring integration test runs with `@ActiveProfiles("runtime")` THEN the application context SHALL contain `RuntimePrimingHook`, `MockNestConfig`, `WireMockServer`, `DirectCallHttpServer`, and the post-restore mapping reload hook, AND SHALL NOT contain `GenerationPrimingHook` or generation-specific beans (Bedrock clients, AI model configuration, specification parsers, prompt builders)

2.12 WHEN a Spring integration test runs with `@ActiveProfiles("generation")` THEN the application context SHALL contain `GenerationPrimingHook` and generation-specific beans (Bedrock clients, AI model configuration, specification parsers, prompt builders), AND SHALL NOT contain `RuntimePrimingHook`, `MockNestConfig`, `WireMockServer`, `DirectCallHttpServer`, or the post-restore mapping reload hook

2.13 WHEN a Spring integration test runs with `@ActiveProfiles("core", "async")` THEN the application context SHALL NOT contain `RuntimePrimingHook`, `GenerationPrimingHook`, `MockNestConfig`, or the post-restore mapping reload hook — confirming the async Lambda remains isolated from both runtime and generation components

### Unchanged Behavior (Regression Prevention)

3.1 WHEN the Lambda performs a normal (non-SnapStart) cold start THEN the system SHALL CONTINUE TO load mappings from S3 during `server.start()` via `ObjectStorageMappingsSource.loadMappingsInto()` as it does today

3.2 WHEN the SnapStart snapshot is being created THEN the system SHALL CONTINUE TO execute the existing priming logic (health check warm-up, S3 client initialization, WireMock engine exercise) without attempting to load production mappings at snapshot time

3.3 WHEN the WireMock engine is exercised during SnapStart priming THEN the system SHALL CONTINUE TO suppress S3 journal writes and clean up the temporary test mapping afterward

3.4 WHEN a mock request arrives on a non-SnapStart Lambda invocation THEN the system SHALL CONTINUE TO match and serve mock responses using the mappings loaded at startup

3.5 WHEN mappings are saved, removed, or modified via the WireMock admin API THEN the system SHALL CONTINUE TO persist those changes to S3 via `ObjectStorageMappingsSource`

3.6 WHEN the Async Lambda starts with `SPRING_PROFILES_ACTIVE: "core,async"` THEN the system SHALL CONTINUE TO exclude both `RuntimePrimingHook` and `GenerationPrimingHook` and `MockNestConfig` — the Async Lambda's profile configuration is unchanged

3.7 WHEN the Generation Lambda starts with `SPRING_PROFILES_ACTIVE: generation` THEN `GenerationPrimingHook` SHALL CONTINUE TO warm up Bedrock clients, AI model configuration, specification parsers, prompt builders, mock validators, SOAP/WSDL components, and GraphQL components as it does today

3.8 WHEN the Runtime Lambda starts with `SPRING_PROFILES_ACTIVE: runtime` THEN `RuntimePrimingHook` SHALL CONTINUE TO warm up health check, S3 client, and WireMock engine as it does today

---

## Bug Condition

### Bug Condition Function

```pascal
FUNCTION isBugCondition(X)
  INPUT: X of type LambdaInvocation
  OUTPUT: boolean
  
  // Bug condition 1: SnapStart restore without mapping reload
  LET isSnapStartRestore = X.isSnapStartRestore = true
  
  // Bug condition 2: Priming hook cross-contamination due to generic "aws" profile
  //   - Runtime Lambda activating GenerationPrimingHook (both use @Profile("!async"), both get "aws" profile)
  //   - Generation Lambda activating RuntimePrimingHook (same reason)
  //   - Generation Lambda loading MockNestConfig (uses @Profile("!async"), "aws" profile doesn't exclude it)
  LET isCrossContamination = (X.lambdaType = "runtime" AND X.activatedHooks CONTAINS "GenerationPrimingHook")
                          OR (X.lambdaType = "generation" AND X.activatedHooks CONTAINS "RuntimePrimingHook")
                          OR (X.lambdaType = "generation" AND X.activatedBeans CONTAINS "MockNestConfig")
  
  RETURN isSnapStartRestore OR isCrossContamination
END FUNCTION
```

### Property Specification — Fix Checking

```pascal
// Property 1: Fix Checking — Mappings reloaded after SnapStart restore
FOR ALL X WHERE X.isSnapStartRestore = true AND X.lambdaType = "runtime" DO
  result ← invokeRuntime'(X)
  ASSERT wireMockStubStore(result).mappings = S3MappingsSource.loadAll()
  ASSERT matchingRequest(result) ≠ "no matching stub"
END FOR

// Property 2: Fix Checking — Priming hook isolation via capability-specific profiles
FOR ALL X WHERE X.lambdaType = "runtime" AND X.profile = "runtime" DO
  hooks ← activatedHooks'(X)
  ASSERT "RuntimePrimingHook" ∈ hooks
  ASSERT "GenerationPrimingHook" ∉ hooks
END FOR

FOR ALL X WHERE X.lambdaType = "generation" AND X.profile = "generation" DO
  hooks ← activatedHooks'(X)
  ASSERT "GenerationPrimingHook" ∈ hooks
  ASSERT "RuntimePrimingHook" ∉ hooks
END FOR

// Property 3: Fix Checking — MockNestConfig scoped to runtime profile only
FOR ALL X WHERE X.lambdaType = "generation" AND X.profile = "generation" DO
  beans ← activatedBeans'(X)
  ASSERT "MockNestConfig" ∉ beans
END FOR

FOR ALL X WHERE X.lambdaType = "runtime" AND X.profile = "runtime" DO
  beans ← activatedBeans'(X)
  ASSERT "MockNestConfig" ∈ beans
END FOR

// Property 4: Fix Checking — Post-restore reload scoped to runtime only
FOR ALL X WHERE X.isSnapStartRestore = true AND X.lambdaType = "generation" DO
  ASSERT postRestoreMappingReload(X) = false
END FOR

// Property 5: Fix Checking — Spring integration tests verify profile-based bean wiring
// These @SpringBootTest tests catch profile misconfiguration at test time rather than in production.

FOR ALL TestContext WHERE TestContext.activeProfiles = {"runtime"} DO
  beans ← applicationContext'(TestContext)
  ASSERT "RuntimePrimingHook" ∈ beans
  ASSERT "MockNestConfig" ∈ beans
  ASSERT "WireMockServer" ∈ beans
  ASSERT "DirectCallHttpServer" ∈ beans
  ASSERT "postRestoreMappingReloadHook" ∈ beans
  ASSERT "GenerationPrimingHook" ∉ beans
  ASSERT "BedrockRuntimeClient" ∉ beans
  ASSERT "ModelConfiguration" ∉ beans
  ASSERT "OpenAPISpecificationParser" ∉ beans
  ASSERT "PromptBuilderService" ∉ beans
END FOR

FOR ALL TestContext WHERE TestContext.activeProfiles = {"generation"} DO
  beans ← applicationContext'(TestContext)
  ASSERT "GenerationPrimingHook" ∈ beans
  ASSERT "BedrockRuntimeClient" ∈ beans
  ASSERT "ModelConfiguration" ∈ beans
  ASSERT "OpenAPISpecificationParser" ∈ beans
  ASSERT "PromptBuilderService" ∈ beans
  ASSERT "RuntimePrimingHook" ∉ beans
  ASSERT "MockNestConfig" ∉ beans
  ASSERT "WireMockServer" ∉ beans
  ASSERT "DirectCallHttpServer" ∉ beans
  ASSERT "postRestoreMappingReloadHook" ∉ beans
END FOR

FOR ALL TestContext WHERE TestContext.activeProfiles = {"core", "async"} DO
  beans ← applicationContext'(TestContext)
  ASSERT "RuntimePrimingHook" ∉ beans
  ASSERT "GenerationPrimingHook" ∉ beans
  ASSERT "MockNestConfig" ∉ beans
  ASSERT "postRestoreMappingReloadHook" ∉ beans
END FOR

// Property 6: Fix Checking — SAM template profile assignment
FOR ALL Lambda WHERE Lambda.name = "Runtime" DO
  ASSERT Lambda.environment.SPRING_PROFILES_ACTIVE = "runtime"
END FOR

FOR ALL Lambda WHERE Lambda.name = "Generation" DO
  ASSERT Lambda.environment.SPRING_PROFILES_ACTIVE = "generation"
END FOR

FOR ALL Lambda WHERE Lambda.name = "Async" DO
  ASSERT Lambda.environment.SPRING_PROFILES_ACTIVE = "core,async"
END FOR
```

### Property Specification — Preservation Checking

```pascal
// Property: Preservation Checking — Non-buggy behavior unchanged
FOR ALL X WHERE NOT isBugCondition(X) DO
  ASSERT invoke(X) = invoke'(X)
END FOR

// Specifically: Async Lambda isolation preserved (profile unchanged at "core,async")
FOR ALL X WHERE X.lambdaType = "async" DO
  hooks ← activatedHooks'(X)
  ASSERT "RuntimePrimingHook" ∉ hooks
  ASSERT "GenerationPrimingHook" ∉ hooks
  beans ← activatedBeans'(X)
  ASSERT "MockNestConfig" ∉ beans
END FOR
```

**Key Definitions:**
- **F** (`invoke`): The original functions using `SPRING_PROFILES_ACTIVE: aws` with `@Profile("!async")` exclusions, without post-restore mapping reload
- **F'** (`invoke'`): The fixed functions using capability-specific profiles (`runtime`, `generation`, `async`) with positive `@Profile` annotations, and CRaC `afterRestore` hook for mapping reload
- **`activatedHooks(X)`**: The set of priming hooks that fire during Lambda initialization for invocation X under the old `aws` profile scheme
- **`activatedHooks'(X)`**: The set of priming hooks that fire after the fix with capability-specific profiles (`@Profile("runtime")`, `@Profile("generation")`)
- **`activatedBeans(X)`**: The set of Spring configuration beans loaded under the old `aws` profile scheme
- **`activatedBeans'(X)`**: The set of Spring configuration beans loaded after the fix with capability-specific profiles
