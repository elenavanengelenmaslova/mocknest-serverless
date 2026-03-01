# AI Specification Code Refactoring Bugfix Design

## Overview

The current implementation violates clean architecture principles by placing prompt building logic in the infrastructure layer. The `BedrockServiceAdapter` class contains prompt building methods and hardcoded prompt text that should reside in the application layer. This design document outlines a refactoring approach that moves prompt building logic to the application layer while maintaining all existing functionality.

**Scope of this refactor**: Move prompt building logic and prompt text out of infrastructure layer into application layer. Response parsing and mock creation logic remain in their current locations (out of scope for this refactor).

The fix involves:
- Moving prompt building logic to application layer service with externalized templates
- Externalizing all prompt text (including system prompt) to resource files with parameter injection
- Simplifying `BedrockServiceAdapter` to only contain AWS Bedrock SDK integration code
- Updating use cases to build prompts before invoking AI model service

## Glossary

- **Bug_Condition (C)**: The condition where prompt building logic exists in the infrastructure layer - specifically `buildSpecWithDescriptionPrompt`, `buildCorrectionPrompt`, and hardcoded system prompt in `BedrockServiceAdapter`
- **Property (P)**: The desired state where infrastructure layer contains only AWS SDK integration code, with prompt building logic in application layer and prompt text in resource files
- **Preservation**: All existing mock generation functionality must continue to work identically, including prompt content and structure
- **BedrockServiceAdapter**: The infrastructure class in `software/infra/aws/src/main/kotlin/nl/vintik/mocknest/infra/aws/generation/ai/BedrockServiceAdapter.kt` that currently contains prompt building logic
- **Clean Architecture**: Architectural pattern where dependencies flow inward (infra → application → domain) and business logic is separated from infrastructure concerns
- **Prompt Template**: Text template stored in resource files with parameter placeholders ({{PARAM_NAME}}) for dynamic value injection

## Bug Details

### Fault Condition

The bug manifests when examining the codebase architecture. The `BedrockServiceAdapter` class in the infrastructure layer contains prompt building logic that violates clean architecture principles. This creates tight coupling between prompt construction and AWS Bedrock implementation, making it impossible to support alternative AI providers without duplicating prompt building logic.

**Formal Specification:**
```
FUNCTION isBugCondition(codeElement)
  INPUT: codeElement of type ClassOrMethod
  OUTPUT: boolean
  
  RETURN (codeElement.layer == INFRASTRUCTURE)
         AND (codeElement.contains(PROMPT_BUILDING_LOGIC)
              OR codeElement.contains(HARDCODED_PROMPT_TEXT))
         AND codeElement.className == "BedrockServiceAdapter"
END FUNCTION
```

### Examples

- **BedrockServiceAdapter.buildSpecWithDescriptionPrompt()** - Prompt building logic with hardcoded text strings in infrastructure layer (should be in application layer with externalized templates)
- **BedrockServiceAdapter.buildCorrectionPrompt()** - Prompt building logic with hardcoded text strings in infrastructure layer (should be in application layer with externalized templates)
- **BedrockServiceAdapter.createAgent() system prompt** - Hardcoded system prompt text in infrastructure layer (should be externalized to resource file)

## Expected Behavior

### Layer Ownership

**Infrastructure Layer (BedrockServiceAdapter)**:
- AWS Bedrock SDK integration (BedrockRuntimeClient calls)
- Koog framework integration (BedrockLLMClient, SingleLLMPromptExecutor)
- Low-level model invocation (agent.run(prompt))
- Agent creation with system prompt loaded from resource file
- NO prompt building logic
- NO hardcoded prompt text

**Application Layer (PromptBuilderService)**:
- Prompt building logic for all prompt types
- Template loading from resource files
- Parameter injection into templates
- Prompt validation and formatting

**Application Module Resources (software/application/src/main/resources/prompts/)**:
- system-prompt.txt - System prompt for agent creation
- spec-with-description.txt - Template for spec + description generation
- correction.txt - Template for mock correction

### Preservation Requirements

**Unchanged Behaviors:**
- Mock generation from API specifications with descriptions must continue to work exactly as before
- Mock correction based on validation errors must continue to work exactly as before
- Prompt content and structure must remain identical after externalization to resource files
- Response parsing logic remains unchanged (stays in BedrockServiceAdapter)
- Mock creation logic remains unchanged (stays in BedrockServiceAdapter)
- Error handling with logging and empty list returns remains unchanged

**Scope:**
All code that does NOT involve prompt building should be completely unaffected by this fix. This includes:
- Response parsing logic (parseModelResponse)
- Mock creation logic (createGeneratedMock)
- Domain models (GeneratedMock, MockNamespace, APISpecification, etc.)
- Test code that validates mock generation behavior
- WireMock integration and storage logic

## Hypothesized Root Cause

Based on the bug description and code analysis, the root causes are:

1. **Incremental Feature Development**: The AI mock generation feature was likely developed incrementally, with initial implementation placing all logic in the infrastructure adapter for speed, without subsequent refactoring to proper layers

2. **Lack of Separation Guidance**: Without explicit application layer services for prompt building, developers naturally placed this logic in the infrastructure adapter

3. **Hardcoded Prompts**: Prompt text was embedded directly in code rather than externalized to resource files, making it harder to maintain and test independently

## Correctness Properties

Property 1: Fault Condition - Clean Architecture Compliance for Prompts

_For any_ prompt building code in the infrastructure layer (BedrockServiceAdapter), the refactored system SHALL move that logic to application layer with externalized templates.

**Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5, 2.6**

Property 2: Preservation - Functional Equivalence

_For any_ mock generation or correction request, the refactored system SHALL produce exactly the same prompt content as the original system.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6**

## Fix Implementation

### Changes Required

**File**: `software/application/src/main/kotlin/nl/vintik/mocknest/application/generation/services/PromptBuilderService.kt` (NEW)

**Function**: New application layer service for prompt building

**Specific Changes**:
1. **Create Prompt Builder Service**: Implement prompt building logic
   - Add `loadSystemPrompt(): String` method - loads from `prompts/system-prompt.txt`
   - Add `buildSpecWithDescriptionPrompt(specification: APISpecification, description: String, namespace: MockNamespace): String` method - loads template from `prompts/spec-with-description.txt` and injects parameters
   - Add `buildCorrectionPrompt(invalidMocks: List<Pair<GeneratedMock, List<String>>>, namespace: MockNamespace, specification: APISpecification?): String` method - loads template from `prompts/correction.txt` and injects parameters
   - Implement template loading from classpath resources
   - Implement parameter injection using string replacement with {{PARAM_NAME}} placeholders
   - Handle optional parameters (e.g., namespace.client) by conditionally including/excluding template sections

**File**: `software/infra/aws/src/main/kotlin/nl/vintik/mocknest/infra/aws/generation/ai/BedrockServiceAdapter.kt`

**Function**: Infrastructure adapter implementation

**Specific Changes**:
1. **Remove Prompt Building Logic**: Delete prompt building methods
   - Remove `buildSpecWithDescriptionPrompt()` method
   - Remove `buildCorrectionPrompt()` method

2. **Update Agent Creation**: Load system prompt from resource file
   - Inject `PromptBuilderService` dependency
   - In `createAgent()`, replace hardcoded system prompt with `promptBuilder.loadSystemPrompt()`

3. **Keep Unchanged**: Response parsing and mock creation remain in BedrockServiceAdapter
   - Keep `parseModelResponse()` method (unchanged)
   - Keep `createGeneratedMock()` method (unchanged)
   - Keep `generateMockFromSpecWithDescription()` method but update to use injected prompts
   - Keep `correctMocks()` method but update to use injected prompts

**File**: `software/application/src/main/kotlin/nl/vintik/mocknest/application/generation/agent/MockGenerationFunctionalAgent.kt`

**Function**: Existing use case that orchestrates mock generation

**Specific Changes**:
1. **Update to Build Prompts**: Inject PromptBuilderService and build prompts before calling AI service
   - Inject `PromptBuilderService` dependency
   - Before calling `aiModelService.generateMockFromSpecWithDescription()`, build prompt using `promptBuilder.buildSpecWithDescriptionPrompt()`
   - Before calling `aiModelService.correctMocks()`, build prompt using `promptBuilder.buildCorrectionPrompt()`
   - Pass built prompts to AI model service methods

**File**: `software/application/src/main/resources/prompts/system-prompt.txt` (NEW)

**Function**: Externalized system prompt for agent creation

**Specific Changes**:
1. **Create Template File**: Extract system prompt text from BedrockServiceAdapter.createAgent()
   - Content: "You are an expert API mock generator.\nYou generate WireMock JSON mappings based on user instructions and specifications."
   - No placeholders needed (static text)

**File**: `software/application/src/main/resources/prompts/spec-with-description.txt` (NEW)

**Function**: Externalized prompt template for spec + description generation

**Specific Changes**:
1. **Create Template File**: Extract prompt text from buildSpecWithDescriptionPrompt()
   - Use placeholders: {{SPEC_TITLE}}, {{SPEC_VERSION}}, {{ENDPOINT_COUNT}}, {{KEY_ENDPOINTS}}, {{API_NAME}}, {{CLIENT_SECTION}}, {{DESCRIPTION}}, {{NAMESPACE}}
   - {{CLIENT_SECTION}} is optional - if namespace.client is null, this entire line is omitted
   - Maintain exact same text content as current implementation

**File**: `software/application/src/main/resources/prompts/correction.txt` (NEW)

**Function**: Externalized prompt template for mock correction

**Specific Changes**:
1. **Create Template File**: Extract prompt text from buildCorrectionPrompt()
   - Use placeholders: {{SPEC_CONTEXT}}, {{API_NAME}}, {{CLIENT_SECTION}}, {{MOCKS_WITH_ERRORS}}, {{NAMESPACE}}
   - {{SPEC_CONTEXT}} is optional - if specification is null, this section is omitted
   - {{CLIENT_SECTION}} is optional - if namespace.client is null, this line is omitted
   - Maintain exact same text content as current implementation

### Placeholder Handling Rules

**Required Placeholders** (always present):
- {{SPEC_TITLE}}, {{SPEC_VERSION}}, {{ENDPOINT_COUNT}}, {{KEY_ENDPOINTS}}, {{API_NAME}}, {{DESCRIPTION}}, {{NAMESPACE}}, {{MOCKS_WITH_ERRORS}}

**Optional Placeholders** (conditionally included):
- {{CLIENT_SECTION}} - If namespace.client is null, omit the entire line "- Client: {{CLIENT}}"
- {{SPEC_CONTEXT}} - If specification is null, omit the entire "API Specification Context:" section

**Implementation Approach**:
- Use conditional template sections marked with special syntax (e.g., `{{#if CLIENT}}...{{/if}}`)
- OR use string replacement with empty string for optional sections
- Ensure prompt output is byte-for-byte identical to current implementation

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, verify that the refactored code maintains identical behavior to the original implementation (preservation checking), then verify that the architectural violation is fixed (fix checking).

### Exploratory Fault Condition Checking

**Goal**: Verify that the architectural violation exists in the current code BEFORE implementing the fix.

**Test Plan**: Write architectural tests that analyze BedrockServiceAdapter and assert that prompt building logic exists in infrastructure layer. Run these tests on the UNFIXED code to observe failures.

**Test Cases**:
1. **Infrastructure Layer Purity Test**: Assert BedrockServiceAdapter contains only AWS SDK calls (will fail on unfixed code)
2. **Prompt Building Location Test**: Assert prompt building logic exists in application layer (will fail on unfixed code)
3. **Prompt Text Location Test**: Assert prompt text exists in resource files (will fail on unfixed code)

**Expected Counterexamples**:
- BedrockServiceAdapter contains methods like buildSpecWithDescriptionPrompt, buildCorrectionPrompt
- BedrockServiceAdapter contains hardcoded prompt text strings

### Fix Checking

**Goal**: Verify that prompt building logic is moved to application layer with externalized templates.

**Pseudocode:**
```
FOR ALL promptBuildingMethod IN BedrockServiceAdapter DO
  ASSERT promptBuildingMethod NOT EXISTS
END FOR

ASSERT PromptBuilderService EXISTS IN APPLICATION_LAYER
ASSERT system-prompt.txt EXISTS IN RESOURCES
ASSERT spec-with-description.txt EXISTS IN RESOURCES
ASSERT correction.txt EXISTS IN RESOURCES
```

### Preservation Checking

**Goal**: Verify that prompt content remains identical after externalization.

**Pseudocode:**
```
FOR ALL (specification, description, namespace) IN testCases DO
  originalPrompt := originalSystem.buildSpecWithDescriptionPrompt(specification, description, namespace)
  refactoredPrompt := refactoredSystem.buildSpecWithDescriptionPrompt(specification, description, namespace)
  ASSERT originalPrompt == refactoredPrompt
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because:
- It generates many test cases automatically across different specifications, descriptions, and namespaces
- It catches edge cases that manual unit tests might miss (null values, special characters, empty strings)
- It provides strong guarantees that prompt content is unchanged for all inputs

**Test Plan**: Capture behavior of UNFIXED code first by recording prompt outputs for various inputs, then write property-based tests that verify the refactored code produces identical outputs.

**Test Cases**:
1. **System Prompt Preservation**: Verify system prompt is identical before and after externalization
2. **Spec Prompt Preservation**: Verify buildSpecWithDescriptionPrompt produces identical prompts for all input combinations
3. **Correction Prompt Preservation**: Verify buildCorrectionPrompt produces identical prompts for all input combinations
4. **Optional Parameter Handling**: Verify optional parameters (namespace.client, specification) are handled correctly

### Unit Tests

- Test PromptBuilderService with various specifications, descriptions, and namespaces
- Test template loading from resource files
- Test parameter injection with required and optional parameters
- Test BedrockServiceAdapter contains only AWS SDK integration code
- Test MockGenerationFunctionalAgent builds prompts before calling AI service

### Property-Based Tests

- Generate random API specifications and verify prompt building produces valid prompts
- Generate random namespaces (with and without client) and verify optional parameter handling
- Test that all prompt parameters are correctly injected across many scenarios

### Integration Tests

- Test full mock generation flow with refactored architecture
- Test full mock correction flow with refactored architecture
- Test that externalized prompts load correctly from resource files
