# Requirements Document

## Introduction

The Bedrock prompt evaluation suite currently has only 1 SOAP scenario (calculator-soap12.wsdl with all 3 operations) in the multi-protocol eval dataset. The REST expansion brought REST from 0 to 14 scenarios across 5 API specs and 6 complexity levels, achieving an 86% scenario pass rate. SOAP coverage remains minimal — a single calculator spec with simple arithmetic operations does not exercise the prompt against realistic enterprise WSDL structures such as nested complex types, enumerated status fields, cross-entity references, or fault handling.

This feature expands the SOAP eval coverage by adding 2–3 new synthetic WSDL 1.2 specification files with different domain characteristics and 8–12 new SOAP scenarios across multiple complexity levels. The goal is to produce granular quality metrics for SOAP generation that reveal which WSDL shapes and prompt patterns need improvement, and to improve the SOAP prompt if eval results expose weaknesses.

## Glossary

- **Eval_Suite**: The collection of all eval scenarios executed by `BedrockPromptEvalTest`, reading from the multi-protocol eval dataset JSON file
- **Eval_Scenario**: A single test case in the eval dataset, consisting of an API spec, a user prompt (description), and semantic check criteria
- **Multi_Protocol_Dataset**: The `multi-protocol-eval-dataset.json` file containing all eval scenarios consumed by `BedrockPromptEvalTest`
- **Prompt_Complexity_Level**: A classification of user prompts by difficulty — Basic (all operations), Filtered (subset of operations), Consistency (cross-entity data coherence), Error (SOAP fault generation), Realistic_Data (domain-specific realistic values), or Edge_Case (boundary conditions)
- **WSDL_Spec**: A WSDL 1.2 specification file in XML format describing a SOAP service's operations, messages, types, and bindings
- **Synthetic_WSDL**: A WSDL_Spec created specifically for eval testing, not sourced from a real public service, designed to exercise specific prompt challenges
- **SOAP_Fault**: A SOAP 1.2 fault envelope returned for error scenarios, using the `soapenv:Fault` element with Code, Reason, and optional Detail
- **WsdlSpecificationParser**: The existing parser that converts WSDL XML into an `APISpecification` via `WsdlParser` and `WsdlSchemaReducer`
- **SoapMockValidator**: The existing validator that checks generated WireMock SOAP mappings against 7 validation rules (POST method, URL path, SOAPAction, XML well-formedness, SOAP envelope, target namespace, Content-Type)
- **Scenario_Pass_Rate**: The percentage of scenarios where generation succeeded, all mocks are valid, and the LLM semantic judge passed
- **First_Pass_Valid_Rate**: The percentage of generated mocks that pass structural validation without a correction retry
- **Summary_Table**: The formatted output table produced after all scenarios complete, grouped by protocol and showing aggregate metrics
- **Scenario_Detail_Table**: A per-scenario breakdown table showing individual API × prompt results

## Requirements

### Requirement 1: Add Diverse SOAP WSDL Specifications

**User Story:** As a prompt engineer, I want the eval suite to test SOAP generation against multiple WSDL specifications with varying domain complexity, so that I can measure prompt quality across different WSDL shapes and sizes.

#### Acceptance Criteria

1. THE Eval_Suite SHALL include WSDL_Spec files for at least 3 distinct SOAP services with different domain characteristics (e.g., banking/financial, inventory/warehouse, notification/messaging)
2. WHEN a new WSDL_Spec is added, THE WSDL_Spec SHALL be placed in the `software/infra/aws/generation/src/test/resources/eval/` directory
3. THE Eval_Suite SHALL include at least one WSDL_Spec with nested complex types where an operation's request or response references a complex type that itself contains fields of another complex type (e.g., an Invoice containing LineItem objects)
4. THE Eval_Suite SHALL include at least one WSDL_Spec with 5 or more operations to test prompt handling of larger SOAP services
5. THE Eval_Suite SHALL include at least one WSDL_Spec with enumerated type fields (e.g., status values like pending, completed, failed) defined via `xsd:restriction` or equivalent XSD constructs
6. WHEN a WSDL_Spec is added, THE WSDL_Spec SHALL be a valid WSDL 1.2 document parseable by the existing WsdlSpecificationParser without throwing an exception
7. WHEN a WSDL_Spec is added, THE WsdlSchemaReducer SHALL produce a CompactWsdl with a non-empty operations list and correctly resolved XSD types

### Requirement 2: Add SOAP Scenarios to Multi-Protocol Dataset

**User Story:** As a prompt engineer, I want SOAP eval scenarios in the multi-protocol dataset covering the new WSDL specifications, so that SOAP prompt quality is measured with the same granularity as REST and GraphQL.

#### Acceptance Criteria

1. THE Multi_Protocol_Dataset SHALL contain at least 8 new SOAP Eval_Scenario entries covering the added WSDL_Spec files (in addition to the existing calculator scenario)
2. WHEN a SOAP Eval_Scenario is added to the Multi_Protocol_Dataset, THE Eval_Scenario SHALL use protocol "SOAP", format "WSDL", and reference a valid specFile path that exists on the classpath
3. THE Multi_Protocol_Dataset SHALL contain at least one SOAP Eval_Scenario for each new WSDL_Spec file
4. WHEN the Eval_Suite runs, THE Summary_Table SHALL show the SOAP row with updated aggregate metrics reflecting the expanded scenario count
5. THE existing calculator SOAP Eval_Scenario SHALL remain unchanged in the Multi_Protocol_Dataset

### Requirement 3: Cover Multiple SOAP Prompt Complexity Levels

**User Story:** As a prompt engineer, I want SOAP eval scenarios at different prompt complexity levels, so that I can identify which types of SOAP prompts the model handles well and which need improvement.

#### Acceptance Criteria

1. THE Multi_Protocol_Dataset SHALL contain at least 3 SOAP Eval_Scenario entries at the Basic Prompt_Complexity_Level (e.g., "Generate mocks for all operations")
2. THE Multi_Protocol_Dataset SHALL contain at least 1 SOAP Eval_Scenario at the Filtered Prompt_Complexity_Level (e.g., "Generate mocks only for account-related operations")
3. THE Multi_Protocol_Dataset SHALL contain at least 1 SOAP Eval_Scenario at the Error Prompt_Complexity_Level (e.g., "Generate SOAP fault responses for invalid input scenarios")
4. THE Multi_Protocol_Dataset SHALL contain at least 1 SOAP Eval_Scenario at the Realistic_Data Prompt_Complexity_Level (e.g., "Generate mocks with realistic financial amounts and account numbers")
5. THE Multi_Protocol_Dataset SHALL contain at least 1 SOAP Eval_Scenario at the Consistency Prompt_Complexity_Level (e.g., "Generate mocks where GetOrder response references the same customer from GetCustomer")
6. THE Multi_Protocol_Dataset SHALL contain at least 1 SOAP Eval_Scenario at the Edge_Case Prompt_Complexity_Level (e.g., "Generate mocks with XPath body matchers to disambiguate operations sharing the same endpoint")

### Requirement 4: Semantic Check Quality for SOAP Scenarios

**User Story:** As a prompt engineer, I want each SOAP eval scenario to have a precise semantic check, so that the LLM judge can meaningfully evaluate whether the generated SOAP mocks match the prompt intent.

#### Acceptance Criteria

1. WHEN a SOAP Eval_Scenario is added, THE semanticCheck field SHALL specify concrete, verifiable criteria (e.g., exact operation names, expected SOAP action URLs, expected response element names, expected XML namespace)
2. THE semanticCheck field SHALL avoid vague criteria such as "looks correct", "reasonable output", or "seems right"
3. WHEN the Eval_Scenario uses the Error Prompt_Complexity_Level, THE semanticCheck SHALL verify that SOAP fault elements are present with appropriate fault codes (e.g., soapenv:Sender or soapenv:Receiver) and non-empty reason text
4. WHEN the Eval_Scenario uses the Consistency Prompt_Complexity_Level, THE semanticCheck SHALL verify cross-entity data consistency (e.g., "the customerId in the GetOrder response matches the id in the GetCustomer response")
5. WHEN the Eval_Scenario uses the Realistic_Data Prompt_Complexity_Level, THE semanticCheck SHALL verify that response data contains domain-appropriate realistic values (e.g., realistic monetary amounts, non-placeholder names)
6. THE semanticCheck field SHALL include a CONTEXT preamble explaining that the output is a JSON array of WireMock stub mappings with SOAP-specific structure (POST method, Content-Type action matching, XML response bodies)

### Requirement 5: SOAP Prompt Improvement Based on Eval Results

**User Story:** As a prompt engineer, I want the SOAP generation prompt to be improved if eval results reveal weaknesses, so that SOAP generation quality improves alongside expanded test coverage.

#### Acceptance Criteria

1. WHEN the expanded SOAP Eval_Suite reveals a First_Pass_Valid_Rate below 70% for SOAP scenarios, THE SOAP generation prompt at `software/application/src/main/resources/prompts/soap/spec-with-description.txt` SHALL be reviewed and updated to address the identified failure patterns
2. IF the SOAP prompt is modified, THEN THE existing calculator SOAP Eval_Scenario SHALL continue to pass after the prompt change
3. IF the SOAP prompt is modified, THEN THE prompt change SHALL be validated by running the SOAP eval scenarios before and after the change to measure improvement

### Requirement 6: WSDL Specification Design for Prompt Stress Testing

**User Story:** As a prompt engineer, I want the synthetic WSDL specifications to exercise specific SOAP generation challenges, so that the eval suite catches prompt weaknesses that a simple calculator WSDL would miss.

#### Acceptance Criteria

1. THE Eval_Suite SHALL include at least one WSDL_Spec where multiple operations share the same service endpoint path, requiring the prompt to generate distinct WireMock stubs differentiated by SOAPAction or XPath body matchers
2. THE Eval_Suite SHALL include at least one WSDL_Spec with operations that have multi-field request and response types (at least 4 fields per type) to test the prompt's ability to generate realistic field values
3. THE Eval_Suite SHALL include at least one WSDL_Spec with cross-entity relationships where one operation's response contains an identifier that should appear in another operation's request or response (e.g., a customerId returned by CreateCustomer that appears in GetOrder)
4. WHEN a Synthetic_WSDL is designed, THE WSDL_Spec SHALL use a unique targetNamespace per service to avoid namespace collisions in the eval dataset
5. WHEN a Synthetic_WSDL is designed, THE WSDL_Spec SHALL include a `soap12:address` element with a realistic service path (e.g., `/banking-service/v1` or `/inventory/ws`) so that the prompt can extract the correct URL path

### Requirement 7: Backward Compatibility with Existing Eval Infrastructure

**User Story:** As a developer, I want the expanded SOAP eval suite to work with the existing test infrastructure, so that no existing tests or CI pipelines break.

#### Acceptance Criteria

1. THE BedrockPromptEvalTest SHALL continue to load SOAP scenarios from the Multi_Protocol_Dataset using the existing dataset JSON schema (input, metadata with protocol, specFile, format, namespace, description, semanticCheck)
2. THE existing GraphQL and REST Eval_Scenario entries in the Multi_Protocol_Dataset SHALL remain unchanged and continue to pass
3. THE eval tests SHALL remain gated behind the `BEDROCK_EVAL_ENABLED=true` environment variable and the `bedrock-eval` JUnit tag
4. THE eval tests SHALL remain excluded from the normal `./gradlew test` run
5. IF the `BEDROCK_EVAL_FILTER` environment variable is set to "soap", THEN THE BedrockPromptEvalTest SHALL run only SOAP Eval_Scenario entries (including both existing and new scenarios)
6. THE SoapMockValidator SHALL validate generated mocks from the new WSDL_Spec files using the same 7 validation rules applied to the existing calculator scenario

### Requirement 8: Documentation Updates

**User Story:** As a project maintainer, I want the eval documentation updated to reflect the expanded SOAP coverage, so that contributors understand the full scope of the eval suite.

#### Acceptance Criteria

1. THE docs/PROMPT_EVAL.md documentation SHALL update the protocol breakdown table to reflect the expanded SOAP scenario count and WSDL specification list
2. THE docs/PROMPT_EVAL.md documentation SHALL update the cost estimate section to include the additional SOAP scenarios in the per-suite cost calculation
3. THE docs/PROMPT_EVAL.md documentation SHALL list all new WSDL_Spec files in the API specification files table with their domain, operation count, and key characteristics
4. THE docs/PROMPT_EVAL.md documentation SHALL update the total scenario count to include the new SOAP scenarios
