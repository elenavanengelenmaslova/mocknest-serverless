# S3 Bulk Delete & Logging Level Bugfix Design

## Overview

This design addresses three related performance and observability bugs in MockNest Serverless's S3 storage layer:

1. **Sequential deletes in `ObjectStorageMappingsSource.removeAll()`** — The method iterates over all mapping keys and calls `storage.delete(key)` one at a time instead of using the existing `storage.deleteMany()` bulk API. For 100+ mappings, this exceeds the API Gateway 30-second timeout, returning HTTP 500 on the first attempt. The fix replaces the sequential `flow.collect { storage.delete(key) }` loop with a single `storage.deleteMany(flow)` call, which already batches keys into groups of 1000 and submits batches in parallel via `flatMapMerge`.

2. **INFO-level logging in `S3ObjectStorageAdapter` per-object operations** — The `get()`, `delete()`, and `save()` methods each log at INFO level per object. During bulk operations (100+ mappings), this floods CloudWatch with hundreds of log lines. The fix changes these three log statements from `logger.info` to `logger.debug`.

3. **Sequential deletes in `S3GenerationStorageAdapter.deleteGeneratedMocks()`** — The method lists mock objects and deletes them one-by-one using individual `s3Client.deleteObject()` calls. The fix collects the keys and uses S3's batch `s3Client.deleteObjects()` API, similar to how `S3ObjectStorageAdapter.deleteMany()` already works.

All three fixes are minimal and surgical: no interface changes, no new classes, no changes to business logic or data flow. Only method bodies and log levels are modified.

## Glossary

- **Bug_Condition (C)**: Three conditions that trigger the bugs — (1) `removeAll()` called on `ObjectStorageMappingsSource`, (2) any per-object `get()`/`delete()`/`save()` call on `S3ObjectStorageAdapter`, (3) `deleteGeneratedMocks()` called on `S3GenerationStorageAdapter`
- **Property (P)**: (1) `removeAll()` uses `storage.deleteMany()` for batch deletion, completing within the API Gateway timeout. (2) Per-object operations log at DEBUG level instead of INFO. (3) `deleteGeneratedMocks()` uses S3's batch `deleteObjects()` API
- **Preservation**: Existing behavior that must remain unchanged — single mapping CRUD operations, `DeleteAllMappingsAndFilesFilter` bulk delete, mapping loading at startup, `S3ObjectStorageAdapter.deleteMany()` batching, generation storage store/retrieve operations, DEBUG-level log visibility
- **`ObjectStorageMappingsSource`**: The `MappingsSource` implementation in `software/application` that manages WireMock stub mappings in S3 via `ObjectStorageInterface`
- **`S3ObjectStorageAdapter`**: The `ObjectStorageInterface` implementation in `software/infra/aws/runtime` that provides S3-backed object storage with single-object and bulk operations
- **`S3GenerationStorageAdapter`**: The `GenerationStorageInterface` implementation in `software/infra/aws/generation` that stores generated mocks, specifications, and jobs in S3
- **`deleteMany()`**: Bulk delete method on `ObjectStorageInterface` that batches keys into groups of 1000 and submits S3 `DeleteObjects` requests in parallel via `flatMapMerge`
- **`DeleteObjects`**: S3 batch API that deletes up to 1000 objects in a single request, significantly faster than individual `DeleteObject` calls

## Bug Details

### Bug Condition

The bug manifests in three scenarios:

**Scenario 1 — Sequential deletes in `removeAll()`:** When `ObjectStorageMappingsSource.removeAll()` is called (triggered by `DELETE /mappings` admin endpoint), it lists all mapping keys with `storage.listPrefix(prefix)` and then iterates with `flow.collect { key -> storage.delete(key) }`, making one S3 `DeleteObject` API call per mapping. For 100+ mappings, this takes longer than 30 seconds, exceeding the API Gateway timeout and returning HTTP 500.

**Scenario 2 — INFO-level per-object logging:** When any of `S3ObjectStorageAdapter.get()`, `delete()`, or `save()` is called, it logs at INFO level (e.g., `logger.info { "Getting object with id: $id" }`). During bulk operations that invoke these methods hundreds of times, this produces excessive CloudWatch log output.

**Scenario 3 — Sequential deletes in `deleteGeneratedMocks()`:** When `S3GenerationStorageAdapter.deleteGeneratedMocks()` is called, it lists mock objects under the job prefix and deletes them one-by-one with individual `s3Client.deleteObject()` calls in a `forEach` loop, instead of using S3's batch `deleteObjects()` API.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type StorageOperation
  OUTPUT: boolean

  // Condition 1: removeAll() uses sequential deletes
  LET sequentialRemoveAll = input.operation = "removeAll"
                            AND input.target = "ObjectStorageMappingsSource"

  // Condition 2: Per-object operations log at INFO level
  LET infoLevelLogging = input.operation IN {"get", "delete", "save"}
                         AND input.target = "S3ObjectStorageAdapter"

  // Condition 3: deleteGeneratedMocks() uses sequential deletes
  LET sequentialGenerationDelete = input.operation = "deleteGeneratedMocks"
                                   AND input.target = "S3GenerationStorageAdapter"

  RETURN sequentialRemoveAll OR infoLevelLogging OR sequentialGenerationDelete
END FUNCTION
```

### Examples

- **Sequential removeAll()**: User calls `DELETE /__admin/mappings` with 100 stubs → `removeAll()` makes 100 individual S3 `DeleteObject` calls → exceeds 30-second API Gateway timeout → HTTP 500 returned → user retries → HTTP 200 (most objects already deleted by timed-out first attempt)
- **INFO-level logging**: User calls `DELETE /__admin/mappings` with 100 stubs → `removeAll()` calls `storage.delete(key)` 100 times → each `delete()` logs `INFO: Deleting object with id: mappings/xxx.json` → 100 INFO log lines flood CloudWatch
- **Sequential generation delete**: User calls `deleteGeneratedMocks("job-123")` with 50 mock objects → `deleteGeneratedMocks()` makes 50 individual `deleteObject()` calls → unnecessarily slow compared to a single batch `deleteObjects()` call
- **Edge case — empty mappings**: User calls `DELETE /__admin/mappings` with 0 stubs → `removeAll()` lists no keys → no deletes needed → completes instantly (works correctly in both old and new code)

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- `ObjectStorageMappingsSource.save()` and `remove()` for individual mapping operations continue to use single-object `storage.save()` and `storage.delete()` calls
- `ObjectStorageMappingsSource.loadMappingsInto()` continues to load all mappings from S3 using the existing concurrent streaming approach with `flatMapMerge(concurrency)`
- `DeleteAllMappingsAndFilesFilter` continues to call `storage.deleteMany(storage.listPrefix(FILES_PREFIX))` for `__files/` cleanup — this code is not modified
- `S3ObjectStorageAdapter.deleteMany()` continues to batch keys into groups of 1000 and submit batches in parallel via `flatMapMerge` — this method is not modified
- `S3ObjectStorageAdapter.get()`, `delete()`, and `save()` continue to perform their operations with the same success/failure semantics — only the log level changes
- `S3GenerationStorageAdapter` store, retrieve, list, and update operations for mocks, specifications, and jobs continue to work correctly — only `deleteGeneratedMocks()` is modified
- When log level is set to DEBUG, per-object operation messages for `get()`, `delete()`, and `save()` in `S3ObjectStorageAdapter` continue to appear in logs

**Scope:**
All inputs that do NOT involve `removeAll()`, per-object S3 logging, or `deleteGeneratedMocks()` are completely unaffected by this fix. This includes:
- Individual mapping CRUD via WireMock admin API
- Mock request matching and response serving
- Mapping loading at startup
- Bulk file deletion via `DeleteAllMappingsAndFilesFilter`
- Generation storage operations other than `deleteGeneratedMocks()`

## Hypothesized Root Cause

Based on the bug description and code analysis, the root causes are:

1. **`removeAll()` not using existing bulk API**: `ObjectStorageMappingsSource.removeAll()` was implemented with a simple `flow.collect { key -> storage.delete(key) }` pattern, likely for simplicity or because `deleteMany()` didn't exist when `removeAll()` was first written. The `deleteMany()` method on `ObjectStorageInterface` already exists and is already used by `ObjectStorageBlobStore.clear()` and `DeleteAllMappingsAndFilesFilter`, but `removeAll()` was never updated to use it.

2. **Overly verbose logging level**: The `S3ObjectStorageAdapter` per-object operations (`get()`, `delete()`, `save()`) were written with INFO-level logging, which is appropriate for debugging individual operations but becomes excessive during bulk operations. The logging was likely set to INFO during initial development and never adjusted for production bulk workloads.

3. **`deleteGeneratedMocks()` not using S3 batch API**: `S3GenerationStorageAdapter.deleteGeneratedMocks()` uses `S3Client` directly (not `ObjectStorageInterface`) and was implemented with a simple `objects.forEach { obj -> deleteObject(...) }` loop. Unlike `S3ObjectStorageAdapter`, which has a dedicated `deleteMany()` method using S3's batch `DeleteObjects` API, the generation adapter never implemented batch deletion.

## Correctness Properties

Property 1: Bug Condition — Batch Delete in removeAll()

_For any_ call to `ObjectStorageMappingsSource.removeAll()`, the fixed method SHALL call `storage.deleteMany()` with the flow of keys from `storage.listPrefix(prefix)`, delegating to the existing batch deletion implementation that uses S3's `DeleteObjects` API with batches of 1000 and parallel submission.

**Validates: Requirements 2.1, 2.2**

Property 2: Bug Condition — DEBUG Logging for Per-Object Operations

_For any_ call to `S3ObjectStorageAdapter.get()`, `delete()`, or `save()`, the fixed methods SHALL log the per-object operation message at DEBUG level instead of INFO level, keeping per-object log messages out of production logs during bulk operations while remaining visible when DEBUG logging is enabled.

**Validates: Requirements 2.3, 3.8**

Property 3: Bug Condition — Batch Delete in deleteGeneratedMocks()

_For any_ call to `S3GenerationStorageAdapter.deleteGeneratedMocks()`, the fixed method SHALL use S3's batch `deleteObjects()` API to delete all mock objects and the results file in a single batch request, instead of iterating with individual `deleteObject()` calls.

**Validates: Requirements 2.4**

Property 4: Preservation — Unchanged Behavior for Non-Bug Inputs

_For any_ input where the bug condition does NOT hold (individual mapping CRUD, mapping loading, file deletion via `DeleteAllMappingsAndFilesFilter`, `S3ObjectStorageAdapter.deleteMany()`, generation storage store/retrieve/list operations), the fixed system SHALL produce exactly the same behavior as the original system, preserving all existing functionality.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7**

## Fix Implementation

### Changes Required

Assuming our root cause analysis is correct:

**File**: `software/application/src/main/kotlin/nl/vintik/mocknest/application/runtime/mappings/ObjectStorageMappingsSource.kt`

**Function**: `removeAll()`

**Specific Changes**:
1. **Replace sequential delete loop with `deleteMany()` call**: Replace the `flow.collect { key -> storage.runCatching { delete(key) } ... }` block with `storage.deleteMany(flow)`. The `deleteMany()` method already handles batching (1000 keys per batch), parallel submission (`flatMapMerge`), and error handling internally.
2. **Simplify error handling**: The outer `runCatching` for `listPrefix()` remains. The inner per-key error handling is no longer needed since `deleteMany()` handles failures internally.

**File**: `software/infra/aws/runtime/src/main/kotlin/nl/vintik/mocknest/infra/aws/runtime/storage/S3ObjectStorageAdapter.kt`

**Function**: `get()`, `delete()`, `save()`

**Specific Changes**:
3. **Change log level in `get()`**: Change `logger.info { "Getting object with id: $id" }` to `logger.debug { "Getting object with id: $id" }`
4. **Change log level in `delete()`**: Change `logger.info { "Deleting object with id: $id" }` to `logger.debug { "Deleting object with id: $id" }`
5. **Change log level in `save()`**: Change `logger.info { "Saving object with id: $id" }` to `logger.debug { "Saving object with id: $id" }`

**File**: `software/infra/aws/generation/src/main/kotlin/nl/vintik/mocknest/infra/aws/generation/storage/S3GenerationStorageAdapter.kt`

**Function**: `deleteGeneratedMocks()`

**Specific Changes**:
6. **Replace sequential `deleteObject()` loop with batch `deleteObjects()` call**: Collect all object keys (mock objects + results file) into a list, then call `s3Client.deleteObjects()` with a `Delete` request containing all keys as `ObjectIdentifier` entries. This mirrors the pattern used in `S3ObjectStorageAdapter.deleteMany()`.
7. **Handle empty object list**: If no mock objects are found, skip the batch delete call and only delete the results file (or return early if neither exists).

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, surface counterexamples that demonstrate the bug on unfixed code, then verify the fix works correctly and preserves existing behavior.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate the bug BEFORE implementing the fix. Confirm or refute the root cause analysis. If we refute, we will need to re-hypothesize.

**Test Plan**: Write unit tests using MockK that verify the interaction patterns of the unfixed code. Run these tests on the UNFIXED code to observe the sequential delete pattern and INFO-level logging.

**Test Cases**:
1. **Sequential removeAll() Test**: Mock `ObjectStorageInterface`, call `removeAll()` with 5 keys, verify that `storage.delete()` is called 5 times individually and `storage.deleteMany()` is NOT called (will pass on unfixed code, confirming the bug)
2. **INFO Logging Test**: Mock `S3Client`, call `get()` / `delete()` / `save()`, capture log output and verify it contains INFO-level per-object messages (will pass on unfixed code, confirming the bug)
3. **Sequential deleteGeneratedMocks() Test**: Mock `S3Client`, call `deleteGeneratedMocks()` with 3 mock objects, verify that `s3Client.deleteObject()` is called 3+ times individually and `s3Client.deleteObjects()` is NOT called (will pass on unfixed code, confirming the bug)

**Expected Counterexamples**:
- `removeAll()` calls `storage.delete(key)` N times instead of `storage.deleteMany(flow)` once
- `get()`, `delete()`, `save()` log at INFO level instead of DEBUG
- `deleteGeneratedMocks()` calls `s3Client.deleteObject()` N times instead of `s3Client.deleteObjects()` once

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds, the fixed function produces the expected behavior.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  IF input.operation = "removeAll" THEN
    result := removeAll_fixed(input)
    ASSERT storage.deleteMany() was called with keys flow
    ASSERT storage.delete() was NOT called individually
  END IF
  IF input.operation IN {"get", "delete", "save"} AND input.target = "S3ObjectStorageAdapter" THEN
    result := operation_fixed(input)
    ASSERT logLevel(result) = DEBUG
  END IF
  IF input.operation = "deleteGeneratedMocks" THEN
    result := deleteGeneratedMocks_fixed(input)
    ASSERT s3Client.deleteObjects() was called with batch request
    ASSERT s3Client.deleteObject() was NOT called individually for mocks
  END IF
END FOR
```

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed function produces the same result as the original function.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT originalFunction(input) = fixedFunction(input)
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because:
- It generates many test cases automatically across the input domain
- It catches edge cases that manual unit tests might miss
- It provides strong guarantees that behavior is unchanged for all non-buggy inputs

**Test Plan**: Observe behavior on UNFIXED code first for non-bug operations (individual mapping save/remove, mapping loading, file deletion), then write property-based tests capturing that behavior.

**Test Cases**:
1. **Individual Mapping Save Preservation**: Verify `ObjectStorageMappingsSource.save(mapping)` still calls `storage.save(key, json)` for a single mapping — this method is not modified
2. **Individual Mapping Remove Preservation**: Verify `ObjectStorageMappingsSource.remove(mapping)` still calls `storage.delete(key)` for a single mapping — this method is not modified
3. **Mapping Loading Preservation**: Verify `ObjectStorageMappingsSource.loadMappingsInto()` still uses `flatMapMerge(concurrency)` to load mappings concurrently — this method is not modified
4. **deleteMany() Preservation**: Verify `S3ObjectStorageAdapter.deleteMany()` still batches at 1000 and uses `flatMapMerge` — this method is not modified
5. **Generation Store/Retrieve Preservation**: Verify `S3GenerationStorageAdapter.storeGeneratedMocks()`, `getGeneratedMocks()`, and other non-delete operations continue to work correctly

### Unit Tests

- Test `ObjectStorageMappingsSource.removeAll()` calls `storage.deleteMany()` with the keys flow using MockK `coVerify`
- Test `ObjectStorageMappingsSource.removeAll()` does NOT call `storage.delete()` individually using MockK `coVerify(exactly = 0)`
- Test `ObjectStorageMappingsSource.removeAll()` handles `listPrefix()` failure gracefully
- Test `S3ObjectStorageAdapter.get()`, `delete()`, `save()` log at DEBUG level (verify via log capture or code inspection)
- Test `S3GenerationStorageAdapter.deleteGeneratedMocks()` calls `s3Client.deleteObjects()` with batch request
- Test `S3GenerationStorageAdapter.deleteGeneratedMocks()` does NOT call `s3Client.deleteObject()` for mock objects individually
- Test `S3GenerationStorageAdapter.deleteGeneratedMocks()` handles empty mock list (no objects to delete)
- Test `S3GenerationStorageAdapter.deleteGeneratedMocks()` includes results file in the batch delete

### Property-Based Tests

- Generate random sets of mapping keys (varying sizes: 0, 1, 10, 100, 1000+) and verify `removeAll()` always uses `deleteMany()` regardless of key count
- Generate random S3 object configurations for `deleteGeneratedMocks()` and verify batch `deleteObjects()` is always used
- Use `@ParameterizedTest` with varying mapping counts to verify preservation of individual `save()` and `remove()` operations

### Integration Tests

- LocalStack integration test: create 50+ mappings in S3, call `removeAll()`, verify all objects are deleted from the bucket
- LocalStack integration test: create mock objects in S3, call `deleteGeneratedMocks()`, verify all objects are deleted from the bucket
- LocalStack integration test: verify `deleteMany()` batching behavior with 1000+ keys against real S3 API
