# Tasks: GraphQL Eval Test Expansion

## Task List

- [x] 1. Create new synthetic GraphQL introspection schema files
  - [x] 1.1 Create `ecommerce-graphql-introspection.json` in `software/infra/aws/generation/src/test/resources/eval/` with 8 operations (4 queries: products, product, orders, customer; 4 mutations: createProduct, createOrder, updateOrderStatus, deleteProduct), ENUM types (OrderStatus: PENDING, SHIPPED, DELIVERED, CANCELLED; ProductCategory: ELECTRONICS, CLOTHING, FOOD, HOME, SPORTS), INPUT_OBJECT types (CreateProductInput, CreateOrderInput with nested OrderItemInput, AddressInput), nested object types (Order → Customer → Address, Order → OrderItem → Product), multi-field types (Product with 7+ fields, Order with 7+ fields), cross-entity relationships (order.customerId matches customer.id, orderItem references product), and query arguments (category filter, status filter, ID lookups, limit parameter). The schema must be valid GraphQL introspection JSON in the standard `{ "data": { "__schema": { ... } } }` format parseable by the existing `GraphQLSpecificationParser`
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 6.1, 6.2, 6.3, 6.4, 6.5_
  - [x] 1.2 Create `taskmanagement-graphql-introspection.json` in `software/infra/aws/generation/src/test/resources/eval/` with 7 operations (4 queries: projects, project, tasks, users; 3 mutations: createTask, updateTaskStatus, assignTask), ENUM types (TaskStatus: TODO, IN_PROGRESS, IN_REVIEW, DONE; TaskPriority: LOW, MEDIUM, HIGH, CRITICAL), INPUT_OBJECT type (CreateTaskInput), nested object types (Task → User, Project → Task[], Task → Project), multi-field types (Task with 8+ fields), cross-entity relationships (task.assigneeId matches user.id, task.projectId matches project.id), and query arguments (projectId, status, assigneeId filters). The schema must be valid GraphQL introspection JSON in the standard `{ "data": { "__schema": { ... } } }` format parseable by the existing `GraphQLSpecificationParser`
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 6.1, 6.2, 6.3, 6.4, 6.5_
  - [x] 1.3 Write unit tests for introspection schema parsing and structural validation in `software/infra/aws/generation/src/test/kotlin/nl/vintik/mocknest/infra/aws/generation/ai/eval/GraphqlIntrospectionSpecValidationTest.kt` that verify: all 4 introspection schemas (pokemon, books, ecommerce, taskmanagement) parse with `GraphQLSpecificationParser` and `GraphQLSchemaReducer` without exception, `CompactGraphQLSchema` has non-empty queries or mutations lists and correctly extracted types and enums, at least one schema has mutation operations, at least one has ENUM types, at least one has INPUT_OBJECT types, at least one has nested object types, at least one has 6+ operations, and at least one has multi-field types (5+ fields per type). Follow the same test structure as `SoapWsdlSpecValidationTest` using `@ParameterizedTest` with `@ValueSource` for introspection schema file names
    - _Requirements: 1.8, 1.9, 1.3, 1.4, 1.5, 1.6, 1.7, 6.1, 6.2, 6.3, 6.4, 6.5_
    - _Correctness Properties: 1_
  - [x] 1.4 Run `./gradlew clean test` and confirm all tests pass

- [x] 2. Add GraphQL scenarios to the multi-protocol eval dataset
  - [x] 2.1 Add 12 new GraphQL scenario entries to `multi-protocol-eval-dataset.json` covering all 4 introspection schemas (including existing pokemon and books) across 6 complexity levels (Basic ×4, Filtered ×1, Error ×1, Realistic_Data ×2, Consistency ×2, Edge_Case ×2). Each scenario must have protocol "GraphQL", format "GRAPHQL", a valid specFile path, and a precise semanticCheck with CONTEXT preamble and concrete verifiable criteria. Update the dataset name and description to reflect the expanded GraphQL scope. The existing `graphql-pokemon-pikachu` and `graphql-books-two-books` scenarios must remain unchanged
    - Scenarios: `graphql-ecommerce-basic-queries`, `graphql-ecommerce-basic-mutations`, `graphql-taskmanagement-basic-all`, `graphql-pokemon-basic-list`, `graphql-ecommerce-filtered-orders`, `graphql-ecommerce-error-notfound`, `graphql-ecommerce-realistic-data`, `graphql-taskmanagement-realistic-data`, `graphql-ecommerce-consistency`, `graphql-taskmanagement-consistency`, `graphql-ecommerce-edge-mutation-input`, `graphql-taskmanagement-edge-enum-filter`
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 7.1, 7.2_
  - [x] 2.2 Write unit tests for dataset structural validation in `software/infra/aws/generation/src/test/kotlin/nl/vintik/mocknest/infra/aws/generation/ai/eval/GraphqlEvalDatasetValidationTest.kt` that verify: at least 12 new GraphQL scenarios exist (beyond the existing pokemon and books), each new introspection schema is referenced by at least one scenario, all GraphQL scenarios have protocol "GraphQL" / format "GRAPHQL" / valid specFile paths, complexity level distribution meets requirements (3+ Basic, 1+ Filtered, 1+ Error, 1+ Realistic, 1+ Consistency, 1+ Edge Case), all new GraphQL semanticCheck fields contain a CONTEXT preamble and concrete verifiable criteria without vague phrases, and the existing `graphql-pokemon-pikachu` and `graphql-books-two-books` scenarios are unchanged. Follow the same test structure as `SoapEvalDatasetValidationTest` using `@ParameterizedTest` with `@MethodSource` for scenario-based tests
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 4.1, 4.2, 4.6, 7.1, 7.2_
    - _Correctness Properties: 2, 3, 4_
  - [x] 2.3 Run `./gradlew clean test` and confirm all tests pass

- [x] 3. Run the GraphQL eval to establish baseline and improve prompt if needed
  - [x] 3.1 Run the GraphQL eval scenarios with `BEDROCK_EVAL_ENABLED=true BEDROCK_EVAL_FILTER=graphql ./gradlew :software:infra:aws:generation:bedrockEval` and record the baseline summary table (first-pass valid rate, after-retry valid rate, scenario pass rate). If the first-pass valid rate is below 70%, review the GraphQL prompt at `software/application/src/main/resources/prompts/graphql/spec-with-description.txt` and update it to address identified failure patterns. Re-run the eval to confirm improvement. Ensure the existing `graphql-pokemon-pikachu` and `graphql-books-two-books` scenarios continue to pass after any prompt change
    - _Requirements: 5.1, 5.2, 5.3, 7.5, 7.6_
  - [x] 3.2 Run `./gradlew clean test` and confirm all tests pass

- [x] 4. Update documentation
  - [x] 4.1 Update `docs/PROMPT_EVAL.md` to reflect the expanded GraphQL coverage: update the protocol breakdown table to show 14 GraphQL scenarios (2 existing + 12 new) across 4 introspection schemas, update the total scenario count to include the new GraphQL scenarios, add both new introspection schema files (ecommerce-graphql-introspection.json, taskmanagement-graphql-introspection.json) to the API specification files table with their domain, query/mutation count, and key characteristics, add a GraphQL scenario complexity levels section (matching the REST and SOAP complexity levels sections), and update the cost estimate section to include the additional GraphQL scenarios in the per-suite cost calculation
    - _Requirements: 8.1, 8.2, 8.3, 8.4_
  - [x] 4.2 Run `./gradlew clean test` and confirm all tests pass

- [x] 5. Verify test coverage and quality
  - [x] 5.1 Run `./gradlew koverHtmlReport` and verify 80%+ coverage for new code (enforced threshold; aim for 90%+ as a goal)
  - [x] 5.2 Run `./gradlew koverVerify` to enforce coverage threshold
  - [x] 5.3 Review test quality: Given-When-Then naming, proper assertions, edge case coverage

## Notes

- All changes are test-only — no production code is modified unless the GraphQL prompt needs improvement (task 3.1)
- The existing `graphql-pokemon-pikachu` and `graphql-books-two-books` GraphQL scenarios and all REST/SOAP scenarios remain unchanged
- GraphQL eval scenarios (task 3.1) require `BEDROCK_EVAL_ENABLED=true` and incur Bedrock API costs (~$0.004–$0.007 per scenario) — ask the user for confirmation before running
- Each top-level task ends with a `./gradlew clean test` checkpoint to catch integration issues early
- Property tests use `@ParameterizedTest` with `@ValueSource` for introspection schema file names and `@MethodSource` for scenario-based tests
- The `BEDROCK_EVAL_FILTER=graphql` filter can be used to run only GraphQL scenarios during iterative prompt tuning
- New introspection schemas must be valid GraphQL introspection JSON in the standard `{ "data": { "__schema": { ... } } }` format, parseable by the existing `GraphQLSpecificationParser` and `GraphQLSchemaReducer`
