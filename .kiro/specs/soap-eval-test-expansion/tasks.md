# Tasks: SOAP Eval Test Expansion

## Task List

- [ ] 1. Create new synthetic WSDL specification files
  - [x] 1.1 Create `banking-service-soap12.wsdl` in `software/infra/aws/generation/src/test/resources/eval/` with 5 operations (GetAccount, CreateAccount, GetTransactions, TransferFunds, GetAccountBalance), nested complex types (TransactionRecord contains MoneyAmount), enumerated TransactionStatus (pending/completed/failed/reversed) via `xsd:restriction`, multi-field request/response types (4+ fields), cross-entity relationships (accountId in GetAccount response appears in GetTransactions request), unique targetNamespace `http://example.com/banking-service`, and `soap12:address` location `http://example.com/banking-service/v1`
    - _Requirements: 1.1, 1.2, 1.3, 1.5, 1.6, 1.7, 6.1, 6.2, 6.3, 6.4, 6.5_
  - [x] 1.2 Create `inventory-warehouse-soap12.wsdl` in `software/infra/aws/generation/src/test/resources/eval/` with 6 operations (GetItem, ListItems, CreateItem, UpdateStock, CreateShipment, GetShipment), nested types (ShipmentOrder contains LineItem array), enumerated fields (ItemCategory, StockStatus) via `xsd:restriction`, multi-field InventoryItem type (5+ fields), unique targetNamespace `http://example.com/inventory-warehouse`, and `soap12:address` location `http://example.com/inventory/ws`
    - _Requirements: 1.1, 1.2, 1.4, 1.5, 1.6, 1.7, 6.1, 6.2, 6.4, 6.5_
  - [x] 1.3 Create `notification-messaging-soap12.wsdl` in `software/infra/aws/generation/src/test/resources/eval/` with 4 operations (SendNotification, GetNotificationStatus, ListNotifications, UpdatePreferences), cross-entity relationships (recipientId references a user, notificationId from SendNotification used in GetNotificationStatus), different message types (email, SMS, push), unique targetNamespace `http://example.com/notification-messaging`, and `soap12:address` location `http://example.com/notifications/v1`
    - _Requirements: 1.1, 1.2, 1.6, 1.7, 6.3, 6.4, 6.5_
  - [x] 1.4 Write unit tests for WSDL parsing and structural validation in `software/infra/aws/generation/src/test/kotlin/nl/vintik/mocknest/infra/aws/generation/ai/eval/` that verify: all 3 new WSDLs parse with `WsdlParser` without exception, `WsdlSchemaReducer` produces `CompactWsdl` with non-empty operations lists, at least one WSDL has nested complex types, at least one has 5+ operations, at least one has enumeration restrictions, and all 4 WSDLs (including calculator) have unique targetNamespaces and non-empty `soap12:address` locations
    - _Requirements: 1.3, 1.4, 1.5, 1.6, 1.7, 6.4, 6.5_
    - _Correctness Properties: 1, 5, 6_
  - [ ] 1.5 Run `./gradlew clean test` and confirm all tests pass

- [x] 2. Add SOAP scenarios to the multi-protocol eval dataset
  - [x] 2.1 Add 10 new SOAP scenario entries to `multi-protocol-eval-dataset.json` covering all 3 new WSDL specs across 6 complexity levels (Basic ×3, Filtered ×1, Error ×1, Realistic_Data ×2, Consistency ×2, Edge_Case ×1). Each scenario must have protocol "SOAP", format "WSDL", a valid specFile path, and a precise semanticCheck with CONTEXT preamble and concrete verifiable criteria. Update the dataset name and description to reflect the expanded SOAP scope. The existing calculator scenario must remain unchanged
    - Scenarios: `soap-banking-basic-all`, `soap-inventory-basic-all`, `soap-notification-basic-all`, `soap-banking-filtered-account`, `soap-banking-error-faults`, `soap-banking-realistic-data`, `soap-banking-consistency`, `soap-inventory-edge-xpath`, `soap-inventory-realistic-data`, `soap-notification-consistency`
    - _Requirements: 2.1, 2.2, 2.3, 2.5, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 7.1, 7.2_
  - [x] 2.2 Write unit tests for dataset structural validation in `software/infra/aws/generation/src/test/kotlin/nl/vintik/mocknest/infra/aws/generation/ai/eval/` that verify: at least 8 new SOAP scenarios exist (beyond calculator), each new WSDL is referenced by at least one scenario, all SOAP scenarios have protocol "SOAP" / format "WSDL" / valid specFile paths, complexity level distribution meets requirements (3+ Basic, 1+ Filtered, 1+ Error, 1+ Realistic, 1+ Consistency, 1+ Edge Case), all new SOAP semanticCheck fields contain a CONTEXT preamble and concrete verifiable criteria without vague phrases, and the existing calculator scenario is unchanged
    - _Requirements: 2.1, 2.2, 2.3, 2.5, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 4.1, 4.2, 4.6, 7.1, 7.2_
    - _Correctness Properties: 2, 3, 4_
  - [x] 2.3 Run `./gradlew clean test` and confirm all tests pass

- [ ] 3. Run the SOAP eval to establish baseline and improve prompt if needed
  - [x] 3.1 Run the SOAP eval scenarios with `BEDROCK_EVAL_ENABLED=true BEDROCK_EVAL_FILTER=soap ./gradlew :software:infra:aws:generation:bedrockEval` and record the baseline summary table (first-pass valid rate, after-retry valid rate, scenario pass rate). If the first-pass valid rate is below 70%, review the SOAP prompt at `software/application/src/main/resources/prompts/soap/spec-with-description.txt` and update it to address identified failure patterns. Re-run the eval to confirm improvement. Ensure the existing calculator scenario continues to pass after any prompt change
    - _Requirements: 5.1, 5.2, 5.3, 7.6_
  - [ ] 3.2 Run `./gradlew clean test` and confirm all tests pass

- [ ] 4. Update documentation
  - [ ] 4.1 Update `docs/PROMPT_EVAL.md` to reflect the expanded SOAP coverage: update the protocol breakdown table to show 11 SOAP scenarios (1 existing + 10 new) across 4 WSDL specs, update the total scenario count from 17 to 27, add all 3 new WSDL files to the API specification files table with their domain, operation count, and key characteristics, add a SOAP scenario complexity levels section (matching the REST complexity levels section), and update the cost estimate section to include the additional SOAP scenarios in the per-suite cost calculation
    - _Requirements: 8.1, 8.2, 8.3, 8.4_
  - [ ] 4.2 Run `./gradlew clean test` and confirm all tests pass

- [ ] 5. Verify test coverage and quality
  - [ ] 5.1 Run `./gradlew koverHtmlReport` and verify 80%+ coverage for new code (enforced threshold; aim for 90%+ as a goal)
  - [ ] 5.2 Run `./gradlew koverVerify` to enforce coverage threshold
  - [ ] 5.3 Review test quality: Given-When-Then naming, proper assertions, edge case coverage

## Notes

- All changes are test-only — no production code is modified unless the SOAP prompt needs improvement (task 3.1)
- The existing calculator SOAP scenario and all REST/GraphQL scenarios remain unchanged
- SOAP eval scenarios (task 3.1) require `BEDROCK_EVAL_ENABLED=true` and incur Bedrock API costs (~$0.004–$0.007 per scenario) — ask the user for confirmation before running
- Each top-level task ends with a `./gradlew clean test` checkpoint to catch integration issues early
- Property tests use `@ParameterizedTest` with `@ValueSource` for WSDL file names and `@MethodSource` for scenario-based tests
- The `BEDROCK_EVAL_FILTER=soap` filter can be used to run only SOAP scenarios during iterative prompt tuning
