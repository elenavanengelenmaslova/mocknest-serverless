# Task Reorganization: Dual Input Mode Property Test

## Summary

Task 2.6 "Write property test for dual input mode support" has been moved from Phase 2 to Phase 3 (now Task 3.6) to align with the logical dependency on URL-based introspection infrastructure.

## Rationale

The original task placement created a dependency issue:

- **Task 2.6** (Phase 2): Write property test for dual input mode support
  - Required testing BOTH pre-fetched schema AND URL-based introspection
  - But URL-based introspection doesn't exist in Phase 2

- **Task 3.2** (Phase 3): Implement GraphQLIntrospectionClient
- **Task 3.5** (Phase 3): Wire GraphQLIntrospectionClient into GraphQLSpecificationParser

**Problem**: You cannot fully test "dual input mode support" until both input modes are implemented.

## Changes Made

### 1. Task List Reorganization

**Phase 2 (Application Layer):**
- Removed old Task 2.6 (dual input mode property test)
- Renumbered subsequent tasks:
  - 2.7 → 2.6 (GraphQLMockValidator implementation)
  - 2.8 → 2.7 (GraphQLMockValidator unit tests)
  - 2.9 → 2.8 (Mock validation property test)
  - 2.10 → 2.9 (Reorganize REST prompts)
  - 2.11 → 2.10 (GraphQL generation prompt)
  - 2.12 → 2.11 (GraphQL correction prompt)
  - 2.13 → 2.12 (Update PromptBuilderService)
  - 2.14 → 2.13 (PromptBuilderService tests)

**Phase 3 (Infrastructure Layer):**
- Added new Task 3.6 after Task 3.5:
  - **3.6 Write property test for dual input mode support**
  - Completes URL-based introspection testing
  - Pre-fetched schema tests already implemented
  - Depends on Task 3.5 (wiring introspection client)
- Renumbered subsequent tasks:
  - Old 3.6 → 3.7 (Integration tests for parser)
  - Old 3.7 → 3.8 (Spring configuration)

### 2. Test File Updates

**File**: `software/application/src/test/kotlin/nl/vintik/mocknest/application/generation/parsers/GraphQLSpecificationParserDualInputPropertyTest.kt`

**Status Updated**:
```kotlin
/**
 * STATUS: PARTIALLY COMPLETE
 * - ✅ Pre-fetched schema mode tests are implemented and passing
 * - ⏳ URL-based introspection tests are prepared but commented out
 * - TODO: Complete URL-based tests in Phase 3 after Task 3.5
 */
```

**URL-based tests**:
- Remain commented out with clear TODO instructions
- Will be activated in Phase 3, Task 3.6
- Include step-by-step instructions for completion

### 3. Task Status

- Task 2.6 (old): Marked as completed (pre-fetched schema tests done)
- Task 3.6 (new): Marked as not started (URL-based tests pending)

## Implementation Status

### ✅ Completed (Phase 2)
- Pre-fetched schema mode property tests
- 8 diverse test cases covering different schema complexities
- Validation and metadata extraction tests
- All tests passing

### ⏳ Pending (Phase 3, Task 3.6)
- URL-based introspection property tests
- Mock introspection client integration
- Verification that both input modes work correctly
- Depends on:
  - Task 3.2: GraphQLIntrospectionClient implementation
  - Task 3.5: Wiring client into parser

## Next Steps

When implementing Task 3.6 in Phase 3:

1. Complete Task 3.2 (Implement GraphQLIntrospectionClient)
2. Complete Task 3.5 (Wire client into parser)
3. Open `GraphQLSpecificationParserDualInputPropertyTest.kt`
4. Uncomment URL-based test methods
5. Update parser constructor calls to include introspection client
6. Verify all tests pass
7. Mark Task 3.6 as complete

## Benefits of Reorganization

1. **Logical dependency order**: Tests come after implementation
2. **Clearer task progression**: Each phase is self-contained
3. **Better testability**: Can verify each mode independently
4. **Reduced confusion**: No "partially complete" tasks in Phase 2
5. **Accurate tracking**: Task status reflects actual completion state
