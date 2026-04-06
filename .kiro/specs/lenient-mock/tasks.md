# Implementation Tasks: Lenient Mock Feature

## Incremental Delivery

The feature is delivered in five slices. Each slice is independently deployable and leaves the system in a working state. Manual checkpoints appear between slices — these are markers for the human workflow, not tasks Kiro performs.

---

## Slice 1 — Lenient Setup CRUD

### Implementation

- [ ] 1. Add domain models for lenient setup
  - [ ] 1.1 Create `LenientSetup` data class in `nl.vintik.mocknest.domain.lenient`
  - [ ] 1.2 Create `LenientSetupId`, `RequestSignature`, `NamespaceKey`, `LenientGenerationRequest` domain types
  - [ ] 1.3 Create `LenientUnmatchedEvent` and `LenientGeneratedEvent` domain types for history

- [ ] 2. Add application-layer interfaces and namespace key derivation
  - [ ] 2.1 Create `LenientSetupStorageInterface` in `nl.vintik.mocknest.application.lenient`
  - [ ] 2.2 Create `LenientHistoryStorageInterface`
  - [ ] 2.3 Create `StandardRuntimeClientInterface`
  - [ ] 2.4 Implement `NamespaceKeyDerivation` — stable, collision-safe, S3-safe derivation from `MockNamespace`

- [ ] 3. Implement lenient setup use cases
  - [ ] 3.1 Implement `CreateLenientSetupUseCase` (validate, reject duplicate Setup_Identity, reject duplicate enabled route prefix, compress spec, persist)
  - [ ] 3.2 Implement `UpdateLenientSetupUseCase` (enforce uniqueness rules on route prefix change or enable transition)
  - [ ] 3.3 Implement `EnableDisableLenientSetupUseCase` (reject enable if route prefix conflict)
  - [ ] 3.4 Implement `DeleteLenientSetupUseCase`
  - [ ] 3.5 Implement `GetLenientSetupUseCase` and `ListLenientSetupsUseCase`
  - [ ] 3.6 Implement `HandleLenientSetupRequest` routing use case (dispatches by method + path)

- [ ] 4. Implement S3 storage adapter
  - [ ] 4.1 Implement `LenientSetupS3Storage` in `nl.vintik.mocknest.infra.aws.generation.lenient`
  - [ ] 4.2 Implement namespace key derivation integration and route-prefix hash computation
  - [ ] 4.3 Implement `resolveForPath` (list enabled setups, longest-prefix match in memory)
  - [ ] 4.4 Wire `LenientSetupS3Storage` into Spring configuration

- [ ] 5. Extend `GenerationLambdaHandler` with setup routing
  - [ ] 5.1 Add routing branch for `path.startsWith("/ai/lenient/setup")` → `HandleLenientSetupRequest`

- [ ] 6. Add SAM template routes for setup endpoints
  - [ ] 6.1 Add `ANY /ai/lenient/setup/{proxy+}` event to `MockNestGenerationFunction`

### Tests

- [ ] 7. Unit tests for domain models and namespace key derivation
  - [ ] 7.1 Unit tests for `NamespaceKeyDerivation` — determinism, collision-safety, S3-safety properties using `@ParameterizedTest` with diverse namespace combinations
  - [ ] 7.2 Unit tests for `RequestSignature` computation — determinism and sensitivity to each input field

- [ ] 8. Unit tests for setup use cases
  - [ ] 8.1 Unit tests for `CreateLenientSetupUseCase` — success, duplicate Setup_Identity rejection, duplicate route prefix rejection, reserved path rejection, URL/content mutual exclusivity
  - [ ] 8.2 Unit tests for `UpdateLenientSetupUseCase` — route prefix change uniqueness, enable transition uniqueness
  - [ ] 8.3 Unit tests for `EnableDisableLenientSetupUseCase` — enable conflict rejection
  - [ ] 8.4 Unit tests for `DeleteLenientSetupUseCase` and read use cases

- [ ] 9. Property-based tests for setup persistence round-trip
  - [ ] 9.1 `@ParameterizedTest` with diverse `LenientSetup` objects verifying `retrieve(persist(setup)) == setup` (Requirement 14)

- [ ] 10. Integration tests for `LenientSetupS3Storage`
  - [ ] 10.1 LocalStack TestContainers integration tests for save, get, list, delete, resolveForPath

---

> **MANUAL CHECKPOINT — Slice 1**
> - PR / review
> - Deploy to AWS
> - Manual verification: create, retrieve, update, enable/disable, delete a lenient setup via API

---

## Slice 2 — Lenient Runtime Endpoint (Basic Behavior)

### Implementation

- [ ] 11. Implement `HandleLenientRequest` use case (path normalization + standard runtime delegation + setup lookup)
  - [ ] 11.1 Normalize captured path (strip `/ai/lenient/endpoint` prefix)
  - [ ] 11.2 Dispatch to Standard_Runtime via `StandardRuntimeClientInterface`
  - [ ] 11.3 On hit: return response unchanged
  - [ ] 11.4 On miss: call `LenientSetupStorageInterface.resolveForPath`
  - [ ] 11.5 On no setup: return miss result unchanged with structured log
  - [ ] 11.6 On setup found: stub out generation (return miss result for now — generation added in Slice 3)

- [ ] 12. Implement `WireMockRuntimeClient` (implements `StandardRuntimeClientInterface`)
  - [ ] 12.1 Implement `dispatch` — forward normalized request to WireMock localhost
  - [ ] 12.2 Implement `getMappings` — call `GET /__admin/mappings`, filter by `metadata.requestSignature`
  - [ ] 12.3 Wire into Spring configuration

- [ ] 13. Extend `GenerationLambdaHandler` with lenient runtime routing
  - [ ] 13.1 Add routing branch for `path.startsWith("/ai/lenient/endpoint")` → `HandleLenientRequest`

- [ ] 14. Add SAM template route for lenient runtime endpoint
  - [ ] 14.1 Add `ANY /ai/lenient/endpoint/{proxy+}` event to `MockNestGenerationFunction`

### Tests

- [ ] 15. Unit tests for `HandleLenientRequest`
  - [ ] 15.1 Standard runtime hit → response returned unchanged, no setup lookup
  - [ ] 15.2 Standard runtime miss, no setup → miss result returned unchanged
  - [ ] 15.3 Standard runtime miss, setup found → generation stub invoked (mock the generation use case)
  - [ ] 15.4 Path normalization correctness using `@ParameterizedTest` with diverse path inputs

- [ ] 16. Unit tests for `WireMockRuntimeClient`
  - [ ] 16.1 `dispatch` forwards request and returns response
  - [ ] 16.2 `getMappings` filters by requestSignature metadata correctly

- [ ] 17. Integration tests for lenient runtime endpoint routing
  - [ ] 17.1 LocalStack TestContainers test: request to `/ai/lenient/endpoint/{path}` with existing mapping → hit returned
  - [ ] 17.2 Request with no matching setup → miss result returned

---

> **MANUAL CHECKPOINT — Slice 2**
> - PR / review
> - Deploy to AWS
> - Manual verification: requests to `/ai/lenient/endpoint/...` route correctly; hits return mock responses; misses with no setup return 404/miss result

---

## Slice 3 — Lenient Generation Happy Path

### Implementation

- [ ] 18. Extend `PromptBuilderService` with lenient fallback prompt
  - [ ] 18.1 Add `buildLenientFallbackPrompt` method (missed request + spec excerpt + instructions + placeholder for near-miss context)
  - [ ] 18.2 Add prompt template files for REST, SOAP, GraphQL lenient fallback

- [ ] 19. Implement `LenientFallbackGenerationUseCase`
  - [ ] 19.1 Compute `RequestSignature` (method + normalizedPath + sorted query params + SHA-256 body hash)
  - [ ] 19.2 Best-effort existing mapping check via `StandardRuntimeClientInterface.getMappings()`
  - [ ] 19.3 Load compressed spec from S3 via `LenientSetupStorageInterface`
  - [ ] 19.4 Build lenient generation prompt via `PromptBuilderService.buildLenientFallbackPrompt`
  - [ ] 19.5 Invoke AI generation (reuse `MockGenerationFunctionalAgent` in single-mapping mode, correction budget = 1)
  - [ ] 19.6 Attach required metadata fields to generated mapping (`source`, `setupNamespace`, `setupRoutePrefix`, `requestSignature`, `createdAt`, `specificationFormat`)
  - [ ] 19.7 Persist mapping via WireMock admin API (`POST /__admin/mappings` with `persistent: true`)
  - [ ] 19.8 Replay request through Standard_Runtime
  - [ ] 19.9 Return response; on any failure return original miss result with structured log

- [ ] 20. Wire `LenientFallbackGenerationUseCase` into `HandleLenientRequest` (replace stub from Slice 2)

- [ ] 21. Add generation timeout enforcement (25-second budget)
  - [ ] 21.1 Wrap generation call with coroutine timeout; on timeout log and return miss result

### Tests

- [ ] 22. Unit tests for `LenientFallbackGenerationUseCase`
  - [ ] 22.1 Happy path: signature computed, no existing mapping, generation succeeds, mapping persisted, replay returns response
  - [ ] 22.2 Existing mapping found by signature → replay without generation
  - [ ] 22.3 Generation failure → miss result returned, failure logged
  - [ ] 22.4 Validation failure exhausts correction budget → miss result returned
  - [ ] 22.5 Persistence failure → miss result returned, failure logged
  - [ ] 22.6 Generation timeout → miss result returned, timeout logged

- [ ] 23. Property-based tests for generation correctness
  - [ ] 23.1 `@ParameterizedTest` with diverse missed requests: generated mapping always contains all required metadata fields
  - [ ] 23.2 Generated mapping never contains `/**` wildcard path patterns
  - [ ] 23.3 Fail-safe property: for any simulated failure, response equals original miss result

- [ ] 24. Unit tests for `RequestSignature` computation
  - [ ] 24.1 Determinism: same request always produces same signature
  - [ ] 24.2 Sensitivity: different method, path, query params, or body each produce different signature

- [ ] 25. Integration tests for generation happy path
  - [ ] 25.1 LocalStack TestContainers: end-to-end lenient request triggers generation, mapping persisted to S3, replay returns generated response

---

> **MANUAL CHECKPOINT — Slice 3**
> - PR / review
> - Deploy to AWS
> - Manual verification: unmatched request to lenient endpoint with a configured setup triggers AI generation; generated mock is persisted; second identical request is served from the persisted mock without re-generating

---

## Slice 4 — Near-Miss Context

### Implementation

- [ ] 26. Implement near-miss retrieval in `WireMockRuntimeClient`
  - [ ] 26.1 Implement `getNearMisses` — call `GET /__admin/near-misses`, return ranked results

- [ ] 27. Integrate near-miss context into `LenientFallbackGenerationUseCase`
  - [ ] 27.1 Query near-miss stubs after signature check
  - [ ] 27.2 Filter: remove results with distance score above configurable threshold (default 0.5, env var `LENIENT_NEAR_MISS_THRESHOLD`)
  - [ ] 27.3 Filter: remove results with different HTTP method than missed request
  - [ ] 27.4 Take top 1–3 results from ranked list
  - [ ] 27.5 Pass near-miss stubs to `buildLenientFallbackPrompt` as secondary context
  - [ ] 27.6 Attach `nearMissStubId` and `nearMissDistance` metadata fields to generated mapping

- [ ] 28. Extend `PromptBuilderService.buildLenientFallbackPrompt` to include near-miss context section

- [ ] 29. Add `LENIENT_NEAR_MISS_THRESHOLD` environment variable to SAM template

### Tests

- [ ] 30. Unit tests for near-miss integration
  - [ ] 30.1 Near-miss results above threshold are filtered out
  - [ ] 30.2 Near-miss results with different HTTP method are filtered out
  - [ ] 30.3 Top 1–3 results are passed to prompt; remainder discarded
  - [ ] 30.4 No near-misses available → generation proceeds with primary inputs only
  - [ ] 30.5 `nearMissStubId` and `nearMissDistance` metadata fields populated when near-miss used; null when not used

- [ ] 31. Property-based tests for near-miss filtering
  - [ ] 31.1 `@ParameterizedTest`: for any set of near-miss results, filtered set never contains results above threshold or with wrong method

---

> **MANUAL CHECKPOINT — Slice 4**
> - PR / review
> - Deploy to AWS
> - Manual verification: when similar mocks exist, generated mock reflects their response conventions; near-miss metadata fields populated on generated mapping

---

## Slice 5 — Observability and History

### Implementation

- [ ] 32. Implement `LenientHistoryS3Storage` (implements `LenientHistoryStorageInterface`)
  - [ ] 32.1 Implement `recordUnmatched` — write to `lenient-history/unmatched/{date}/{namespace-key}/{timestamp}-{request-id}.json`
  - [ ] 32.2 Implement `recordGenerated` — write to `lenient-history/generated/{date}/{namespace-key}/{timestamp}-{request-id}.json`
  - [ ] 32.3 Best-effort write with 2-second timeout; log failure and continue
  - [ ] 32.4 Respect `LENIENT_HISTORY_ENABLED` environment variable (default `true`)
  - [ ] 32.5 Wire into Spring configuration

- [ ] 33. Integrate history recording into `HandleLenientRequest` and `LenientFallbackGenerationUseCase`
  - [ ] 33.1 Record unmatched event when standard runtime misses and no setup found
  - [ ] 33.2 Record generated event after generation attempt (success or failure)

- [ ] 34. Implement CloudWatch metrics emission
  - [ ] 34.1 Emit `LenientSetupFound` / `LenientSetupNotFound` from `HandleLenientRequest`
  - [ ] 34.2 Emit `LenientGenerationSuccess` / `LenientGenerationFailure` (with `failureType`) from `LenientFallbackGenerationUseCase`
  - [ ] 34.3 Emit `LenientValidationFailure` / `LenientPersistenceFailure` from respective failure paths
  - [ ] 34.4 Emit `LenientMappingsBySource` on successful persist
  - [ ] 34.5 Emit `LenientNearMissContextUsed` / `LenientNearMissDistance` when near-miss context used
  - [ ] 34.6 Emit `LenientGenerationDuration` on every generation attempt

- [ ] 35. Add `LENIENT_HISTORY_ENABLED` environment variable to SAM template

### Tests

- [ ] 36. Unit tests for `LenientHistoryS3Storage`
  - [ ] 36.1 Unmatched event written to correct S3 key pattern
  - [ ] 36.2 Generated event written to correct S3 key pattern
  - [ ] 36.3 Write failure is logged and swallowed — does not propagate to caller
  - [ ] 36.4 History disabled via env var → no S3 writes

- [ ] 37. Unit tests for CloudWatch metrics
  - [ ] 37.1 Each metric emitted with correct dimensions on the corresponding code path
  - [ ] 37.2 `LenientGenerationFailure` emitted with correct `failureType` for each failure scenario

- [ ] 38. Integration tests for history persistence
  - [ ] 38.1 LocalStack TestContainers: unmatched event and generated event written to correct S3 prefixes

- [ ] 39. Coverage verification
  - [ ] 39.1 Run `./gradlew koverHtmlReport` and verify 80%+ coverage for new lenient code (aim for 90%+)
  - [ ] 39.2 Run `./gradlew koverVerify` to enforce coverage threshold
  - [ ] 39.3 Review test quality: Given-When-Then naming, proper assertions, edge case coverage

---

> **MANUAL CHECKPOINT — Slice 5**
> - PR / review
> - Deploy to AWS
> - Manual verification: CloudWatch metrics appear in AWS console after lenient requests; history events written to S3 under correct prefixes; coverage threshold passes
