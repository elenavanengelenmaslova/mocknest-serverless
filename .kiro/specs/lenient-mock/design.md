# Design Document: Lenient Mock Feature

## Overview

The Lenient Mock feature adds on-demand AI mock generation to MockNest Serverless. When a request arrives at the dedicated lenient endpoint and no existing mapping matches, the system looks up a saved lenient setup for that route, generates a narrowly-scoped WireMock mapping using the stored API specification, persists it, and returns the response through the standard WireMock runtime.

The design reuses all existing generation infrastructure (specification parsers, AI generation agent, mock validator, prompt builder, S3 storage) and adds a thin new layer of domain models, application use cases, and infrastructure adapters specific to lenient mode.

## Incremental Delivery Constraint

This feature is delivered in five independently deployable slices. Each slice leaves the system in a working state. Manual checkpoints (PR, deploy, verification) occur between slices.

| Slice | Scope |
|-------|-------|
| 1 | Lenient Setup CRUD endpoints (create, read, update, delete, list, enable/disable) |
| 2 | Lenient Runtime Endpoint — path normalization, standard runtime delegation, setup lookup |
| 3 | Lenient Generation Happy Path — signature check, AI generation, validation, persist, replay |
| 4 | Near-Miss Context — query near-miss stubs, inject as secondary prompt context |
| 5 | Observability & History — CloudWatch metrics, request history persistence |

Each slice is described in detail in the Tasks section.

---

## Architecture

The lenient feature follows the same clean architecture layers as the rest of MockNest Serverless.

```
infra/aws/generation  →  application/lenient  →  domain/lenient
                      →  application/generation (reused)
```

The Generation Lambda handles all lenient admin and runtime endpoints. No new Lambda function is introduced in v1.

### Request Flow — Lenient Runtime Endpoint

```
API Gateway /ai/lenient/endpoint/{path}
        │
        ▼
GenerationLambdaHandler
        │
        ▼
HandleLenientRequest (use case)
        │
        ├─ normalize path
        ├─ delegate to Standard_Runtime (WireMock HTTP call)
        │       ├─ HIT  → return response unchanged
        │       └─ MISS → resolve lenient setup (S3 lookup, longest-prefix match)
        │               ├─ no setup → return miss result unchanged
        │               └─ setup found → LenientFallbackGenerationUseCase
        │
        ▼
LenientFallbackGenerationUseCase
        │
        ├─ compute Request_Signature (method + path + sorted query params + SHA-256 body hash)
        ├─ best-effort check: query /__admin/mappings for matching requestSignature metadata
        │       └─ found → replay through Standard_Runtime, return
        ├─ load compressed spec from S3 (specificationS3Key from setup metadata)
        ├─ query near-miss stubs (Slice 4)
        ├─ build lenient generation prompt (missed request + spec excerpt + instructions + near-miss context)
        ├─ invoke AI generation (reuses MockGenerationFunctionalAgent pattern, single-mapping mode)
        ├─ validate generated mapping (reuses CompositeMockValidator, correction budget = 1)
        ├─ persist mapping to WireMock persistent store (POST /__admin/mappings)
        ├─ replay request through Standard_Runtime
        └─ return response
        (any failure at any step → return original miss result, log failure)
```

### Request Flow — Lenient Setup API

```
API Gateway /ai/lenient/setup/...
        │
        ▼
GenerationLambdaHandler
        │
        ▼
HandleLenientSetupRequest (use case, routes by method+path)
        │
        ├─ POST   /ai/lenient/setup          → CreateLenientSetupUseCase
        ├─ PUT    /ai/lenient/setup/{id}     → UpdateLenientSetupUseCase
        ├─ GET    /ai/lenient/setup/{id}     → GetLenientSetupUseCase
        ├─ GET    /ai/lenient/setup          → ListLenientSetupsUseCase
        ├─ PATCH  /ai/lenient/setup/{id}     → EnableDisableLenientSetupUseCase
        └─ DELETE /ai/lenient/setup/{id}     → DeleteLenientSetupUseCase
```

---

## Domain Layer

Package: `nl.vintik.mocknest.domain.lenient`

### LenientSetup

```kotlin
data class LenientSetup(
    val namespace: MockNamespace,
    val routePrefix: String,
    val specificationS3Key: String,
    val format: SpecificationFormat,
    val description: String,
    val generationInstructions: String,
    val enabled: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
)
```

### LenientSetupId

```kotlin
data class LenientSetupId(
    val namespace: MockNamespace,
    val routePrefix: String
)
```

### RequestSignature

```kotlin
data class RequestSignature(val value: String) // SHA-256 hex of method+normalizedPath+sortedQueryParams+bodyHash
```

### LenientGenerationRequest

```kotlin
data class LenientGenerationRequest(
    val missedRequest: HttpRequest,
    val normalizedPath: String,
    val setup: LenientSetup,
    val requestSignature: RequestSignature
)
```

### NamespaceKey

A value type wrapping the canonical S3-safe string derived from `MockNamespace`. The derivation algorithm is defined in the application layer and must be stable and collision-safe across different `apiName`/`client` combinations.

---

## Application Layer

Package: `nl.vintik.mocknest.application.lenient`

### New Interfaces

**LenientSetupStorageInterface**
```kotlin
interface LenientSetupStorageInterface {
    suspend fun save(setup: LenientSetup, compressedSpec: String): LenientSetup
    suspend fun get(id: LenientSetupId): LenientSetup?
    suspend fun list(namespace: MockNamespace? = null, enabledOnly: Boolean = false): List<LenientSetup>
    suspend fun delete(id: LenientSetupId): Boolean
    suspend fun resolveForPath(normalizedPath: String): LenientSetup?  // longest-prefix match across enabled setups
}
```

**LenientHistoryStorageInterface**
```kotlin
interface LenientHistoryStorageInterface {
    suspend fun recordUnmatched(event: LenientUnmatchedEvent)
    suspend fun recordGenerated(event: LenientGeneratedEvent)
}
```

**StandardRuntimeClientInterface**
```kotlin
interface StandardRuntimeClientInterface {
    suspend fun dispatch(request: HttpRequest): HttpResponse
    suspend fun getMappings(): List<WireMockMappingMetadata>
    suspend fun getNearMisses(request: HttpRequest): List<NearMissResult>
}
```

### NamespaceKeyDerivation

A pure function in the application layer that derives a canonical, S3-safe namespace key from `MockNamespace`. The algorithm must be:
- Deterministic: same input always produces same output
- Collision-safe: different `apiName`/`client` combinations never produce the same key
- S3-safe: output contains only characters valid in S3 key path segments

Implementation note: a suitable approach is to hash the canonical string representation of the namespace fields, or to use a URL-safe encoding of the concatenated fields with a separator that cannot appear in either field. The exact algorithm is an implementation decision.

### Use Cases

**CreateLenientSetupUseCase**
- Validates input (non-empty namespace/routePrefix, exactly one of content/URL, supported format, no reserved path overlap)
- Rejects if Setup_Identity already exists
- Rejects if any enabled setup already uses the same route prefix
- Resolves spec (fetch URL or use content), compresses using existing reducers
- Derives namespace key, computes route-prefix hash
- Persists via `LenientSetupStorageInterface`

**UpdateLenientSetupUseCase**
- Identifies setup by namespace + routePrefix
- If route prefix changes: enforces same uniqueness rules as create
- If setup becomes enabled: enforces same enabled-route-prefix uniqueness rules as enable
- Re-compresses spec if spec content/URL changed

**EnableDisableLenientSetupUseCase**
- On enable: rejects if another enabled setup already uses the same route prefix
- On disable: no conflict check needed

**DeleteLenientSetupUseCase**
- Removes setup metadata and compressed spec from S3

**GetLenientSetupUseCase / ListLenientSetupsUseCase**
- Read-only, no conflict checks

**HandleLenientRequest** (runtime use case)
- Normalizes path
- Dispatches to Standard_Runtime via `StandardRuntimeClientInterface`
- On hit: returns response unchanged
- On miss: calls `LenientSetupStorageInterface.resolveForPath`
- On no setup: returns miss result unchanged
- On setup found: delegates to `LenientFallbackGenerationUseCase`

**LenientFallbackGenerationUseCase**
- Computes `RequestSignature`
- Best-effort existing mapping check via `StandardRuntimeClientInterface.getMappings()`
- Loads compressed spec from S3 via `LenientSetupStorageInterface`
- Optionally queries near-miss stubs (Slice 4)
- Builds lenient generation prompt (extends `PromptBuilderService`)
- Invokes AI generation (reuses `MockGenerationFunctionalAgent` in single-mapping mode)
- Validates with correction budget = 1 (reuses `CompositeMockValidator`)
- Persists via WireMock admin API (POST `/__admin/mappings` with `persistent: true`)
- Replays request through Standard_Runtime
- Returns response; on any failure returns original miss result

### Prompt Extension

`PromptBuilderService` gains a new method:

```kotlin
fun buildLenientFallbackPrompt(
    missedRequest: HttpRequest,
    specExcerpt: String,
    instructions: String,
    format: SpecificationFormat,
    nearMissContext: List<NearMissResult> = emptyList()
): String
```

The prompt instructs the AI to produce exactly one mapping, use exact method/path matching, avoid wildcards, and use near-miss stubs only as secondary style context.

---

## Infrastructure Layer

Package: `nl.vintik.mocknest.infra.aws.generation.lenient`

### LenientSetupS3Storage (implements LenientSetupStorageInterface)

- Derives namespace key using `NamespaceKeyDerivation`
- Computes route-prefix hash (SHA-256 hex of normalized route prefix)
- S3 key patterns:
  - Metadata: `lenient-setups/{namespace-key}/{route-prefix-hash}.json`
  - Compressed spec: `lenient-setups/{namespace-key}/{route-prefix-hash}.spec.json`
- `resolveForPath`: lists all enabled setup metadata files, performs longest-prefix match in memory
- Server-side encryption enabled on all writes
- Lazy loading: no preload at cold start

### LenientHistoryS3Storage (implements LenientHistoryStorageInterface)

- S3 key patterns:
  - Unmatched: `lenient-history/unmatched/{date}/{namespace-key}/{timestamp}-{request-id}.json`
  - Generated: `lenient-history/generated/{date}/{namespace-key}/{timestamp}-{request-id}.json`
- Best-effort write with 2-second timeout; failure is logged and swallowed

### WireMockRuntimeClient (implements StandardRuntimeClientInterface)

- Makes HTTP calls to the WireMock server running in the same Lambda process (localhost)
- `dispatch`: forwards the normalized request to WireMock and returns the response
- `getMappings`: calls `GET /__admin/mappings`, filters by `metadata.requestSignature`
- `getNearMisses`: calls `GET /__admin/near-misses` with the missed request

### GenerationLambdaHandler extension

The existing `GenerationLambdaHandler` gains two new routing branches:

```kotlin
path.startsWith("/ai/lenient/setup") -> handleLenientSetupRequest(...)
path.startsWith("/ai/lenient/endpoint") -> handleLenientRequest(...)
```

### SAM Template additions

- New API Gateway routes on the existing `MockNestGenerationFunction`:
  - `ANY /ai/lenient/setup/{proxy+}` — lenient setup management
  - `ANY /ai/lenient/endpoint/{proxy+}` — lenient runtime endpoint
- New environment variable: `LENIENT_NEAR_MISS_THRESHOLD` (default `0.5`)
- New environment variable: `LENIENT_HISTORY_ENABLED` (default `true`)

---

## Namespace Key Derivation

The canonical namespace key is derived in the application layer and used by the infrastructure layer for all S3 key construction. The algorithm must satisfy:

1. Deterministic for the same `MockNamespace`
2. Collision-safe: `MockNamespace("foo", "bar")` ≠ `MockNamespace("foo-bar", null)`
3. S3-safe: only lowercase alphanumeric characters and hyphens in path segments

A suitable implementation uses URL-safe Base64 encoding of the UTF-8 bytes of a canonical string that includes a separator character that cannot appear in `apiName` or `client` values, or alternatively a SHA-256 hash of the canonical representation. The exact choice is made during implementation.

---

## Request Signature Computation

```
requestSignature = SHA-256(
    method.uppercase()
    + "|" + normalizedPath
    + "|" + sortedQueryParams   // "key1=val1&key2=val2" sorted by key
    + "|" + SHA-256(requestBody ?: "")
)
```

Encoded as lowercase hex string.

---

## Validation and Correction Budget

Lenient generation reuses `CompositeMockValidator` with a fixed correction budget of 1 (1 initial attempt + 1 correction = 2 total). This matches the existing `BedrockGenerationMaxRetries` pattern. Validation cannot be disabled for lenient generation.

---

## Fail-Safe Contract

Every step in `LenientFallbackGenerationUseCase` is wrapped in error handling. Any exception at any step causes the use case to:
1. Log the failure with context (Request_Signature, setup identity, step name)
2. Return the original Standard_Runtime miss result unchanged

The caller (test tool) always gets a valid HTTP response.

---

## Generated Mapping Metadata

All generated mappings include the following WireMock metadata fields:

| Field | Value |
|-------|-------|
| `source` | `LENIENT_FALLBACK` |
| `setupNamespace` | namespace display name |
| `setupRoutePrefix` | route prefix from setup |
| `requestSignature` | computed Request_Signature hex |
| `createdAt` | ISO 8601 timestamp |
| `specificationFormat` | e.g. `OPENAPI_3`, `GRAPHQL` |
| `nearMissStubId` | stub ID of top near-miss (null if none) |
| `nearMissDistance` | distance score of top near-miss (null if none) |

---

## CloudWatch Metrics

| Metric | Dimensions |
|--------|-----------|
| `LenientSetupFound` | namespace, routePrefix |
| `LenientSetupNotFound` | endpoint=ai_lenient |
| `LenientGenerationSuccess` | namespace, routePrefix |
| `LenientGenerationFailure` | namespace, routePrefix, failureType |
| `LenientValidationFailure` | namespace, routePrefix |
| `LenientPersistenceFailure` | namespace, routePrefix |
| `LenientMappingsBySource` | source=LENIENT_FALLBACK |
| `LenientNearMissContextUsed` | namespace, routePrefix |
| `LenientNearMissDistance` | namespace, routePrefix |
| `LenientGenerationDuration` | namespace, routePrefix |

`failureType` enum: `GENERATION_FAILURE`, `VALIDATION_FAILURE`, `PERSISTENCE_FAILURE`

Raw paths and free-text failure details are recorded in structured logs only, never as CloudWatch dimensions.

---

## Security

- All lenient endpoints inherit the existing API Gateway API key authentication
- Specification URLs are validated through the existing `SafeUrlResolver` (SSRF prevention)
- Generation instructions are sanitized before inclusion in AI prompts (prompt injection prevention)
- Route prefix is validated against reserved paths (`/__admin`, `/ai/generation`, `/ai/lenient/endpoint`)

---

## Correctness Properties (for Property-Based Testing)

1. **Round-trip persistence**: `retrieve(persist(setup)) == setup` for all valid `LenientSetup` objects
2. **Fail-safe**: for any failure in the generation flow, the response is identical to the Standard_Runtime miss result
3. **Signature stability**: `computeSignature(request) == computeSignature(request)` for the same request (determinism)
4. **Namespace key collision-safety**: for any two distinct `MockNamespace` values, their derived namespace keys are distinct
5. **Route prefix uniqueness**: after any create/enable/update operation, no two enabled setups share the same route prefix
6. **No wildcard generation**: no generated mapping contains `/**` path patterns or catch-all query matchers
7. **Metadata completeness**: every generated mapping contains all required metadata fields with non-null values (except nearMiss fields which may be null)

---

## Module Placement

| Component | Module |
|-----------|--------|
| Domain models (`LenientSetup`, `RequestSignature`, etc.) | `:software:domain` |
| Application interfaces and use cases | `:software:application` |
| `PromptBuilderService` extension | `:software:application` |
| S3 storage implementations | `:software:infra:aws:generation` |
| `WireMockRuntimeClient` | `:software:infra:aws:generation` |
| Lambda handler routing extension | `:software:infra:aws:generation` |
| SAM template additions | `deployment/aws/sam/template.yaml` |

No new Gradle modules are required. All lenient code lives in existing modules under new `lenient` sub-packages.
