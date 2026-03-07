# Bug Condition Exploration Test Results

## Test Execution Summary

**Test File**: `PromptBuildingArchitectureViolationTest.kt`  
**Execution Date**: Task 1 - Bug Condition Exploration  
**Expected Outcome**: Test FAILS (confirms architectural violation exists)  
**Actual Outcome**: ✅ Test FAILED as expected (5 out of 5 tests failed)

## Counterexamples Found

The bug condition exploration test successfully identified the following architectural violations in the unfixed code:

### 1. Prompt Building Methods in Infrastructure Layer

**Location**: `BedrockServiceAdapter.kt` (infrastructure layer)

**Violations Found**:
- ✅ Method `buildSpecWithDescriptionPrompt()` exists in infrastructure layer
- ✅ Method `buildCorrectionPrompt()` exists in infrastructure layer

**Evidence**:
```kotlin
internal fun buildSpecWithDescriptionPrompt(
    specification: APISpecification, 
    description: String, 
    namespace: MockNamespace
): String { ... }

internal fun buildCorrectionPrompt(
    invalidMocks: List<Pair<GeneratedMock, List<String>>>,
    namespace: MockNamespace,
    specification: APISpecification?
): String { ... }
```

**Expected Behavior**: These methods should NOT exist in infrastructure layer. Prompt building logic should be in application layer (`PromptBuilderService`).

---

### 2. Hardcoded Prompt Text in Infrastructure Layer

**Location**: `BedrockServiceAdapter.kt` (infrastructure layer)

**Violations Found**:
- ✅ Hardcoded system prompt text in `createAgent()` method
- ✅ Hardcoded prompt instructions in `buildSpecWithDescriptionPrompt()`
- ✅ Hardcoded correction prompt text in `buildCorrectionPrompt()`

**Evidence**:

**System Prompt (in createAgent method)**:
```kotlin
systemPrompt = """
    You are an expert API mock generator.
    You generate WireMock JSON mappings based on user instructions and specifications.
""".trimIndent()
```

**Spec With Description Prompt**:
```kotlin
return """
    You are an expert API mock generator. Generate WireMock JSON mappings based on this API specification and enhancement description:
    ...
""".trimIndent()
```

**Correction Prompt**:
```kotlin
return """
    You are an expert API mock generator. The following WireMock mappings failed validation against the specification.
    ...
""".trimIndent()
```

**Expected Behavior**: All prompt text should be externalized to resource files in application module:
- `software/application/src/main/resources/prompts/system-prompt.txt`
- `software/application/src/main/resources/prompts/spec-with-description.txt`
- `software/application/src/main/resources/prompts/correction.txt`

---

### 3. Missing PromptBuilderService in Application Layer

**Expected Location**: `software/application/src/main/kotlin/nl/vintik/mocknest/application/generation/services/PromptBuilderService.kt`

**Violation**: ✅ File does not exist

**Expected Behavior**: A `PromptBuilderService` should exist in the application layer with methods:
- `loadSystemPrompt(): String`
- `buildSpecWithDescriptionPrompt(specification: APISpecification, description: String, namespace: MockNamespace): String`
- `buildCorrectionPrompt(invalidMocks: List<Pair<GeneratedMock, List<String>>>, namespace: MockNamespace, specification: APISpecification?): String`

---

### 4. Missing Prompt Template Files

**Expected Locations**:
- `software/application/src/main/resources/prompts/system-prompt.txt`
- `software/application/src/main/resources/prompts/spec-with-description.txt`
- `software/application/src/main/resources/prompts/correction.txt`

**Violations**: ✅ All three files do not exist

**Expected Behavior**: Prompt text should be stored in external resource files with parameter placeholders (e.g., `{{PARAM_NAME}}`) for dynamic value injection.

---

### 5. createAgent Method Does Not Use PromptBuilderService

**Location**: `BedrockServiceAdapter.createAgent()` method

**Violation**: ✅ Method contains hardcoded system prompt instead of loading from `PromptBuilderService`

**Current Implementation**:
```kotlin
override fun createAgent(): AIAgent<String, String> {
    val model = modelConfiguration.getModel()
    logger.info { "Initializing AI agent: model=${model.id}" }
    return AIAgent(
        promptExecutor = executor,
        llmModel = model,
        systemPrompt = """
            You are an expert API mock generator.
            You generate WireMock JSON mappings based on user instructions and specifications.
        """.trimIndent(),
        temperature = TEMPERATURE,
        toolRegistry = ToolRegistry.EMPTY
    )
}
```

**Expected Behavior**: Should inject `PromptBuilderService` and load system prompt:
```kotlin
systemPrompt = promptBuilder.loadSystemPrompt()
```

---

## Conclusion

The bug condition exploration test successfully confirmed the architectural violation exists in the unfixed code. All 5 test cases failed as expected, proving that:

1. ✅ Prompt building logic exists in infrastructure layer (should be in application layer)
2. ✅ Prompt text is hardcoded in infrastructure code (should be externalized to resource files)
3. ✅ PromptBuilderService does not exist in application layer (should exist)
4. ✅ Prompt template files do not exist in application resources (should exist)
5. ✅ BedrockServiceAdapter does not use PromptBuilderService (should use it)

**Next Steps**: Proceed to Task 2 (Write preservation property tests) to capture the current prompt content before implementing the fix.

**Note**: This test encodes the expected behavior. When it passes after implementation, it will confirm the architectural violation is fixed.
