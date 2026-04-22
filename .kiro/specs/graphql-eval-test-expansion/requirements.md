# Requirements Document

## Introduction

The Bedrock prompt evaluation suite currently has only 2 GraphQL scenarios (`graphql-pokemon-pikachu` using `pokemon-graphql-introspection.json` and `graphql-books-two-books` using `books-graphql-introspection.json`) in the multi-protocol eval dataset. The REST expansion brought REST from 0 to 14 scenarios across 5 API specs and 6 complexity levels. The SOAP expansion brought SOAP from 1 to 14 scenarios across 4 WSDL specs and 6 complexity levels. GraphQL coverage remains minimal — two simple schemas with only queries, no mutations, no enums, no input types, and no complex nested structures do not exercise the prompt against realistic GraphQL patterns such as mutations, enum fields, deeply nested types, pagination arguments, or error responses.

This feature expands the GraphQL eval coverage by adding 2–3 new synthetic GraphQL introspection schema files with different domain characteristics and 12 new GraphQL scenarios across multiple complexity levels, bringing the total from 2 to 14 scenarios. The goal is to produce granular quality metrics for GraphQL generation that reveal which schema shapes and prompt patterns need improvement, and to improve the GraphQL prompt if eval results expose weaknesses.

## Glossary

- **Eval_Suite**: The collection of all eval scenarios executed by `BedrockPromptEvalTest`, reading from the multi-protocol eval dataset JSON file
- **Eval_Scenario**: A single test case in the eval dataset, consisting of an API spec, a user prompt (description), and semantic check criteria
- **Multi_Protocol_Dataset**: The `multi-protocol-eval-dataset.json` file containing all eval scenarios consumed by `BedrockPromptEvalTest`
- **Prompt_Complexity_Level**: A classification of user prompts by difficulty — Basic (all operations or specific queries), Filtered (subset of operations), Consistency (cross-entity data coherence), Error (GraphQL error responses), Realistic_Data (domain-specific realistic values), or Edge_Case (boundary conditions like pagination, mutations with input types)
- **Introspection_Schema**: A GraphQL introspection result JSON file in the standard `{ "data": { "__schema": { ... } } }` format, describing a GraphQL API's types, queries, mutations, enums, and input objects
- **Synthetic_Schema**: An Introspection_Schema created specifically for eval testing, not sourced from a real public service, designed to exercise specific prompt challenges
- **GraphQLSpecificationParser**: The existing parser that converts introspection JSON into an `APISpecification` via `GraphQLSchemaReducer`
- **GraphQLSchemaReducer**: The existing reducer that converts raw introspection JSON into a `CompactGraphQLSchema` with queries, mutations, types, and enums
- **GraphQLMockValidator**: The existing validator that checks generated WireMock GraphQL mappings against validation rules (POST method, urlPath, GraphQL response format with data/errors, scalar type enforcement, required field presence, enum value validation)
- **Scenario_Pass_Rate**: The percentage of scenarios where generation succeeded, all mocks are valid, and the LLM semantic judge passed
- **First_Pass_Valid_Rate**: The percentage of generated mocks that pass structural validation without a correction retry
- **CompactGraphQLSchema**: The reduced schema representation containing queries, mutations, types (object and input), and enums extracted from introspection JSON

## Requirements

### Requirement 1: Add Diverse GraphQL Introspection Schemas

**User Story:** As a prompt engineer, I want the eval suite to test GraphQL generation against multiple introspection schemas with varying domain complexity, so that I can measure prompt quality across different GraphQL schema shapes and sizes.

#### Acceptance Criteria

1. THE Eval_Suite SHALL include Introspection_Schema files for at least 4 distinct GraphQL APIs with different domain characteristics (the 2 existing schemas plus at least 2 new schemas covering domains such as e-commerce/product catalog, task management/project tracking, or social/media)
2. WHEN a new Introspection_Schema is added, THE Introspection_Schema SHALL be placed in the `software/infra/aws/generation/src/test/resources/eval/` directory
3. THE Eval_Suite SHALL include at least one Introspection_Schema with mutation operations (e.g., createProduct, updateOrder, deleteTask) in addition to query operations
4. THE Eval_Suite SHALL include at least one Introspection_Schema with ENUM types defined in the schema (e.g., OrderStatus with values PENDING, SHIPPED, DELIVERED, CANCELLED)
5. THE Eval_Suite SHALL include at least one Introspection_Schema with INPUT_OBJECT types used as mutation arguments (e.g., CreateProductInput with required fields)
6. THE Eval_Suite SHALL include at least one Introspection_Schema with nested object types where a field's return type is another object type that itself contains object-type fields (e.g., Order containing Customer containing Address)
7. THE Eval_Suite SHALL include at least one Introspection_Schema with 6 or more operations (queries plus mutations combined) to test prompt handling of larger GraphQL APIs
8. WHEN an Introspection_Schema is added, THE GraphQLSchemaReducer SHALL produce a CompactGraphQLSchema with non-empty queries or mutations lists and correctly extracted types and enums
9. WHEN an Introspection_Schema is added, THE Introspection_Schema SHALL be a valid GraphQL introspection JSON document parseable by the existing GraphQLSpecificationParser without throwing an exception

### Requirement 2: Add GraphQL Scenarios to Multi-Protocol Dataset

**User Story:** As a prompt engineer, I want GraphQL eval scenarios in the multi-protocol dataset covering the new introspection schemas, so that GraphQL prompt quality is measured with the same granularity as REST and SOAP.

#### Acceptance Criteria

1. THE Multi_Protocol_Dataset SHALL contain at least 12 new GraphQL Eval_Scenario entries covering both existing and new Introspection_Schema files (in addition to the existing 2 GraphQL scenarios), bringing the total to at least 14 GraphQL scenarios
2. WHEN a GraphQL Eval_Scenario is added to the Multi_Protocol_Dataset, THE Eval_Scenario SHALL use protocol "GraphQL", format "GRAPHQL", and reference a valid specFile path that exists on the classpath
3. THE Multi_Protocol_Dataset SHALL contain at least one GraphQL Eval_Scenario for each new Introspection_Schema file
4. THE existing `graphql-pokemon-pikachu` and `graphql-books-two-books` GraphQL Eval_Scenario entries SHALL remain unchanged in the Multi_Protocol_Dataset
5. WHEN the Eval_Suite runs, THE Summary_Table SHALL show the GraphQL row with updated aggregate metrics reflecting the expanded scenario count

### Requirement 3: Cover Multiple GraphQL Prompt Complexity Levels

**User Story:** As a prompt engineer, I want GraphQL eval scenarios at different prompt complexity levels, so that I can identify which types of GraphQL prompts the model handles well and which need improvement.

#### Acceptance Criteria

1. THE Multi_Protocol_Dataset SHALL contain at least 3 GraphQL Eval_Scenario entries at the Basic Prompt_Complexity_Level (e.g., "Generate mocks for all queries" or "Generate a mock for a specific query with given data")
2. THE Multi_Protocol_Dataset SHALL contain at least 1 GraphQL Eval_Scenario at the Filtered Prompt_Complexity_Level (e.g., "Generate mocks only for product-related queries, not order queries")
3. THE Multi_Protocol_Dataset SHALL contain at least 1 GraphQL Eval_Scenario at the Error Prompt_Complexity_Level (e.g., "Generate GraphQL error responses for invalid queries")
4. THE Multi_Protocol_Dataset SHALL contain at least 1 GraphQL Eval_Scenario at the Realistic_Data Prompt_Complexity_Level (e.g., "Generate mocks with realistic product names, prices in EUR, and European customer names")
5. THE Multi_Protocol_Dataset SHALL contain at least 1 GraphQL Eval_Scenario at the Consistency Prompt_Complexity_Level (e.g., "Generate mocks where the order's customerId matches the customer query response id")
6. THE Multi_Protocol_Dataset SHALL contain at least 1 GraphQL Eval_Scenario at the Edge_Case Prompt_Complexity_Level (e.g., "Generate mocks for mutation operations with input types" or "Generate mocks with pagination arguments")

### Requirement 4: Semantic Check Quality for GraphQL Scenarios

**User Story:** As a prompt engineer, I want each GraphQL eval scenario to have a precise semantic check, so that the LLM judge can meaningfully evaluate whether the generated GraphQL mocks match the prompt intent.

#### Acceptance Criteria

1. WHEN a GraphQL Eval_Scenario is added, THE semanticCheck field SHALL specify concrete, verifiable criteria (e.g., exact operation names, expected response field names, expected data values, expected enum values)
2. THE semanticCheck field SHALL avoid vague criteria such as "looks correct", "reasonable output", or "seems right"
3. WHEN the Eval_Scenario uses the Error Prompt_Complexity_Level, THE semanticCheck SHALL verify that GraphQL error elements are present with non-empty message fields in the errors array
4. WHEN the Eval_Scenario uses the Consistency Prompt_Complexity_Level, THE semanticCheck SHALL verify cross-entity data consistency (e.g., "the customerId in the order response matches the id in the customer query response")
5. WHEN the Eval_Scenario uses the Realistic_Data Prompt_Complexity_Level, THE semanticCheck SHALL verify that response data contains domain-appropriate realistic values (e.g., realistic product names, non-placeholder customer names, realistic monetary amounts)
6. THE semanticCheck field for new GraphQL scenarios SHALL include a CONTEXT preamble explaining that the output is a JSON array of WireMock stub mappings with GraphQL-specific structure (POST method, urlPath to /namespace/graphql, bodyPatterns with matchesJsonPath on operationName, jsonBody with data wrapper)

### Requirement 5: GraphQL Prompt Improvement Based on Eval Results

**User Story:** As a prompt engineer, I want the GraphQL generation prompt to be improved if eval results reveal weaknesses, so that GraphQL generation quality improves alongside expanded test coverage.

#### Acceptance Criteria

1. WHEN the expanded GraphQL Eval_Suite reveals a First_Pass_Valid_Rate below 70% for GraphQL scenarios, THE GraphQL generation prompt at `software/application/src/main/resources/prompts/graphql/spec-with-description.txt` SHALL be reviewed and updated to address the identified failure patterns
2. IF the GraphQL prompt is modified, THEN THE existing `graphql-pokemon-pikachu` and `graphql-books-two-books` Eval_Scenario entries SHALL continue to pass after the prompt change
3. IF the GraphQL prompt is modified, THEN THE prompt change SHALL be validated by running the GraphQL eval scenarios before and after the change to measure improvement

### Requirement 6: Introspection Schema Design for Prompt Stress Testing

**User Story:** As a prompt engineer, I want the synthetic introspection schemas to exercise specific GraphQL generation challenges, so that the eval suite catches prompt weaknesses that simple query-only schemas would miss.

#### Acceptance Criteria

1. THE Eval_Suite SHALL include at least one Introspection_Schema with mutation operations that accept INPUT_OBJECT arguments, requiring the prompt to generate WireMock stubs with bodyPatterns matching mutation operationNames and producing responses with created/updated entity data
2. THE Eval_Suite SHALL include at least one Introspection_Schema with ENUM types used as field return types, requiring the prompt to generate response data with valid enum values from the schema
3. THE Eval_Suite SHALL include at least one Introspection_Schema with query operations that accept arguments (e.g., filters, pagination parameters like limit/offset, or ID lookups), requiring the prompt to generate stubs with appropriate operationName matching
4. THE Eval_Suite SHALL include at least one Introspection_Schema with cross-entity relationships where one query's response contains an identifier that should appear in another query's response (e.g., a customerId returned by a customer query that appears in an order query response)
5. THE Eval_Suite SHALL include at least one Introspection_Schema with multi-field object types (at least 5 fields per type) to test the prompt's ability to generate realistic field values and include all required fields

### Requirement 7: Backward Compatibility with Existing Eval Infrastructure

**User Story:** As a developer, I want the expanded GraphQL eval suite to work with the existing test infrastructure, so that no existing tests or CI pipelines break.

#### Acceptance Criteria

1. THE BedrockPromptEvalTest SHALL continue to load GraphQL scenarios from the Multi_Protocol_Dataset using the existing dataset JSON schema (input, metadata with protocol, specFile, format, namespace, description, semanticCheck)
2. THE existing SOAP and REST Eval_Scenario entries in the Multi_Protocol_Dataset SHALL remain unchanged and continue to pass
3. THE eval tests SHALL remain gated behind the `BEDROCK_EVAL_ENABLED=true` environment variable and the `bedrock-eval` JUnit tag
4. THE eval tests SHALL remain excluded from the normal `./gradlew test` run
5. IF the `BEDROCK_EVAL_FILTER` environment variable is set to "graphql", THEN THE BedrockPromptEvalTest SHALL run only GraphQL Eval_Scenario entries (including both existing and new scenarios)
6. THE GraphQLMockValidator SHALL validate generated mocks from the new Introspection_Schema files using the same validation rules applied to the existing Pokemon and Books scenarios (POST method, urlPath, GraphQL response format, scalar type enforcement, required fields, enum values)

### Requirement 8: Documentation Updates

**User Story:** As a project maintainer, I want the eval documentation updated to reflect the expanded GraphQL coverage, so that contributors understand the full scope of the eval suite.

#### Acceptance Criteria

1. THE docs/PROMPT_EVAL.md documentation SHALL update the protocol breakdown table to reflect the expanded GraphQL scenario count and introspection schema list
2. THE docs/PROMPT_EVAL.md documentation SHALL update the cost estimate section to include the additional GraphQL scenarios in the per-suite cost calculation
3. THE docs/PROMPT_EVAL.md documentation SHALL list all new Introspection_Schema files in the API specification files table with their domain, query/mutation count, and key characteristics
4. THE docs/PROMPT_EVAL.md documentation SHALL update the total scenario count to include the new GraphQL scenarios
