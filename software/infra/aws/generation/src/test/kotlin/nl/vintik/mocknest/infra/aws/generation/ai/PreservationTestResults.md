# Preservation Test Results - Baseline Behavior

## Test Execution Date
Task 2 - Preservation Property Tests (BEFORE implementing fix)

## Test Status
✅ **ALL TESTS PASSED** on unfixed code

## Purpose
These tests capture the CURRENT behavior of prompt building in `BedrockServiceAdapter` BEFORE implementing the refactoring. They establish the baseline that must be preserved after moving prompt building logic to the application layer.

## Test Coverage

### 1. System Prompt Preservation
**Test**: `SystemPromptPreservation.Given agent creation When loading system prompt Then should document exact expected content`

**Status**: ✅ PASSED

**Baseline Content**:
```
You are an expert API mock generator.
You generate WireMock JSON mappings based on user instructions and specifications.
```

**Note**: This exact content must be preserved in `software/application/src/main/resources/prompts/system-prompt.txt` after refactoring.

---

### 2. Spec With Description Prompt Preservation

#### Test 2.1: Namespace without client
**Test**: `SpecWithDescriptionPromptPreservation.Given spec with namespace without client When building prompt Then should contain all required elements`

**Status**: ✅ PASSED

**Verified Elements**:
- ✅ Contains "You are an expert API mock generator"
- ✅ Contains "API Specification Summary:"
- ✅ Contains specification details (title, version, endpoints count)
- ✅ Contains "- Key endpoints:" with first 5 endpoints
- ✅ Contains "Namespace:" section
- ✅ Contains "- API Name: petstore"
- ✅ Does NOT contain "- Client:" line (when client is null)
- ✅ Contains "Enhancement Description:"
- ✅ Contains "IMPORTANT: All mock URLs must be prefixed with /petstore"
- ✅ Contains "Prefer `jsonBody` over `body`"
- ✅ Contains "Return only a JSON array"

#### Test 2.2: Namespace with client
**Test**: `SpecWithDescriptionPromptPreservation.Given spec with namespace with client When building prompt Then should include client`

**Status**: ✅ PASSED

**Verified Elements**:
- ✅ Contains "- API Name: petstore"
- ✅ Contains "- Client: acme-corp"
- ✅ Contains "/acme-corp/petstore" in URL prefix instruction

#### Test 2.3: Many endpoints (first 5 shown)
**Test**: `SpecWithDescriptionPromptPreservation.Given spec with many endpoints When building prompt Then should show first 5 endpoints only`

**Status**: ✅ PASSED

**Verified Behavior**:
- ✅ Shows "- Endpoints: 10" (total count)
- ✅ Shows first 5 endpoints in "Key endpoints:" list
- ✅ Does NOT show 6th endpoint or beyond

---

### 3. Correction Prompt Preservation

#### Test 3.1: Without specification
**Test**: `CorrectionPromptPreservation.Given invalid mocks without specification When building correction prompt Then should contain all required elements`

**Status**: ✅ PASSED

**Verified Elements**:
- ✅ Contains "You are an expert API mock generator"
- ✅ Contains "failed validation"
- ✅ Does NOT contain "API Specification Context:" (when spec is null)
- ✅ Contains "Namespace:" section
- ✅ Contains namespace details (API name, client if present)
- ✅ Contains "Mock ID:" for each invalid mock
- ✅ Contains "Current Mapping:" with actual mapping JSON
- ✅ Contains "Validation Errors:" with error list
- ✅ Contains namespace-specific URL prefix instruction
- ✅ Contains "WireMock URL matching rules"
- ✅ Contains "Return only a JSON array"

#### Test 3.2: With specification
**Test**: `CorrectionPromptPreservation.Given invalid mocks with specification When building correction prompt Then should include spec context`

**Status**: ✅ PASSED

**Verified Elements**:
- ✅ Contains "API Specification Context:" section
- ✅ Contains specification details (title, version, endpoints count)

#### Test 3.3: Multiple invalid mocks
**Test**: `CorrectionPromptPreservation.Given multiple invalid mocks When building correction prompt Then should include all with separator`

**Status**: ✅ PASSED

**Verified Behavior**:
- ✅ Contains all mock IDs
- ✅ Contains "---" separator between mocks

---

### 4. Optional Parameter Handling

#### Test 4.1: Spec prompt without client
**Test**: `OptionalParameterHandling.Given namespace without client When building spec prompt Then should omit client line`

**Status**: ✅ PASSED

**Verified Behavior**:
- ✅ Contains "- API Name: api"
- ✅ Does NOT contain "- Client:" line

#### Test 4.2: Spec prompt with client
**Test**: `OptionalParameterHandling.Given namespace with client When building spec prompt Then should include client line`

**Status**: ✅ PASSED

**Verified Behavior**:
- ✅ Contains "- API Name: api"
- ✅ Contains "- Client: client1"

#### Test 4.3: Correction prompt without client
**Test**: `OptionalParameterHandling.Given namespace without client When building correction prompt Then should omit client line`

**Status**: ✅ PASSED

**Verified Behavior**:
- ✅ Contains "- API Name: api"
- ✅ Does NOT contain "- Client:" line

#### Test 4.4: Correction prompt with client
**Test**: `OptionalParameterHandling.Given namespace with client When building correction prompt Then should include client line`

**Status**: ✅ PASSED

**Verified Behavior**:
- ✅ Contains "- API Name: api"
- ✅ Contains "- Client: client1"

#### Test 4.5: Correction prompt without specification
**Test**: `OptionalParameterHandling.Given null specification When building correction prompt Then should omit spec context`

**Status**: ✅ PASSED

**Verified Behavior**:
- ✅ Does NOT contain "API Specification Context:" section

#### Test 4.6: Correction prompt with specification
**Test**: `OptionalParameterHandling.Given specification When building correction prompt Then should include spec context`

**Status**: ✅ PASSED

**Verified Behavior**:
- ✅ Contains "API Specification Context:" section
- ✅ Contains specification details

---

## Summary

**Total Tests**: 13
**Passed**: 13 ✅
**Failed**: 0

## Preservation Requirements Validated

✅ **Requirement 3.1**: System prompt preservation verified
✅ **Requirement 3.2**: Spec prompt preservation verified  
✅ **Requirement 3.3**: Correction prompt preservation verified

## Next Steps

1. ✅ Task 1 completed - Bug condition exploration test written and documented
2. ✅ Task 2 completed - Preservation tests written and passing on unfixed code
3. ⏭️ Task 3 - Implement refactoring (move prompt building to application layer)
4. ⏭️ Task 3.6 - Re-run bug condition test (should pass after fix)
5. ⏭️ Task 3.7 - Re-run preservation tests (should still pass, confirming no regressions)

## Important Notes

- These tests establish the baseline behavior that MUST be preserved
- After refactoring, ALL these tests must still pass
- Any test failure after refactoring indicates a regression in prompt content
- The tests use structural assertions (contains checks) rather than exact string matching to be resilient to minor formatting differences while ensuring all key content is preserved
