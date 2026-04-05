# Requirements Document: Lenient Mock Feature

## Introduction

The Lenient Mock feature extends MockNest Serverless with intelligent on-demand mock generation for exploratory testing and smoke tests. When a client calls a dedicated lenient runtime endpoint, the system first attempts standard runtime matching. Only when no mapping matches AND a saved lenient setup exists for that route, the system generates a new, highly specific mock tailored to the missed request, persists it for future use, and returns the response through standard runtime semantics.

This feature enables teams to perform exploratory testing without manually creating every possible mock scenario upfront, while maintaining the reliability and consistency of the standard WireMock runtime as the source of truth.

Supported specification formats: OpenAPI 3.x (3.0.x and 3.1.x), Swagger 2.0, WSDL 1.2 (SOAP 1.2 only — SOAP 1.1 is not supported for AI generation), and GraphQL.

**Non-goals for v1**: MCP (Model Context Protocol) and SSE (Server-Sent Events) parsers/generators are not included in v1. They will be added in a future phase.

## Glossary

- **Lenient_Setup**: A saved configuration containing a pre-compressed API specification, route prefix, namespace identity, and generation instructions that enables automatic mock generation for unmatched requests
- **Lenient_Runtime_Endpoint**: The dedicated endpoint path `/ai/lenient/endpoint/{captured-path}` that triggers lenient behavior
- **Standard_Runtime**: The existing WireMock runtime that matches requests against saved mappings
- **Generated_Mapping**: A WireMock-compatible mapping created by AI generation, narrowly tailored to a specific missed request
- **Request_Signature**: A unique identifier for a request based on method, normalized path, query parameters, and body hash
- **Setup_Identity**: The combination of namespace (MockNamespace: apiName + optional client) and route prefix that uniquely identifies a lenient setup
- **Persistence_Layer**: The S3-based storage system for mappings and lenient setup definitions
- **Lenient_Fallback_Flow**: The process of delegating to standard runtime first, then generating on miss when setup exists
- **Route_Prefix**: The base path pattern (e.g., `/api/v1/users`) that determines which requests are eligible for lenient generation. Route prefixes must be globally unique across all enabled setups.
- **MockNamespace**: The existing domain model with `apiName` (required) and optional `client` field, used as the namespace identity for lenient setups
- **API_Specification**: The formal API contract (OpenAPI 3.x, Swagger 2.0, WSDL 1.2 for SOAP 1.2, or GraphQL schema) used for mock generation
- **Generation_Instruction**: Natural language guidance for customizing generated mock behavior (required)
- **Validation_Engine**: Component that validates generated mocks against API specifications
- **Metadata**: Structured information attached to generated mappings for traceability and analysis
- **Near_Miss_Mock**: An existing mock mapping that partially matches the missed request, surfaced by the standard runtime's near-miss capability, used as secondary context to improve generation consistency
- **Near_Miss_Distance**: A similarity metric returned by the standard runtime's near-miss capability, indicating how closely an existing stub matches the missed request

## Requirements

### Requirement 1: Lenient Setup Lifecycle Management

**User Story:** As a developer, I want to create and manage lenient setups, so that I can configure automatic mock generation for specific APIs and routes.

#### Acceptance Criteria

1. THE Lenient_Setup_API SHALL provide an endpoint to create a new lenient setup with namespace (MockNamespace), route prefix, API specification content or URL (mutually exclusive), specification format, and description (required)
2. THE Lenient_Setup_API SHALL provide an endpoint to update an existing lenient setup identified by namespace and route prefix
3. THE Lenient_Setup_API SHALL provide an endpoint to retrieve a specific lenient setup by namespace and route prefix
4. THE Lenient_Setup_API SHALL provide an endpoint to list all lenient setups with optional filtering by namespace or enabled status
5. THE Lenient_Setup_API SHALL provide an endpoint to enable or disable a lenient setup without deleting it
6. THE Lenient_Setup_API SHALL provide an endpoint to delete a lenient setup by namespace and route prefix
7. WHEN a lenient setup is created or updated, THE System SHALL resolve the specification (fetch from URL if URL provided, use content if content provided), compress/reduce it using existing reducers, and store the compressed form to S3 with key pattern `lenient-setups/{namespace-key}/{route-prefix-hash}.spec.json`
8. WHEN a lenient setup is created or updated, THE Persistence_Layer SHALL store the setup metadata (without raw spec content) to S3 with key pattern `lenient-setups/{namespace-key}/{route-prefix-hash}.json`, referencing the compressed spec S3 key
9. WHEN a lenient setup is deleted, THE Persistence_Layer SHALL remove both the setup metadata and the compressed spec file from S3
10. THE Lenient_Setup_API SHALL validate that namespace and route prefix are non-empty before creating or updating a setup
11. THE Lenient_Setup_API SHALL validate that exactly one of specification content or specification URL is provided
12. WHEN a lenient setup is created with a specification URL, THE System SHALL fetch and parse the specification at creation time to validate it is accessible and parseable before storing
13. WHEN retrieving a non-existent lenient setup, THE Lenient_Setup_API SHALL return a 404 status with descriptive error message

### Requirement 2: Dedicated Lenient Runtime Endpoint

**User Story:** As a tester, I want to call a dedicated lenient endpoint, so that my requests automatically generate mocks when no match exists, without affecting normal MockNest traffic.

#### Acceptance Criteria

1. THE Lenient_Runtime_Endpoint SHALL accept requests at path `/ai/lenient/endpoint/{captured-path}` where captured-path represents the actual API path being mocked
2. THE Lenient_Runtime_Endpoint SHALL support all HTTP methods (GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS)
3. WHEN a request arrives at the Lenient_Runtime_Endpoint, THE System SHALL normalize the captured path by removing the `/ai/lenient/endpoint` prefix
4. WHEN the normalized path is determined, THE System SHALL delegate the request to Standard_Runtime first
5. IF Standard_Runtime returns a matched mapping, THEN THE System SHALL return that response unchanged without lenient processing
6. IF Standard_Runtime returns no match, THEN THE System SHALL resolve whether a lenient setup exists for the normalized path
7. WHEN resolving lenient setup existence, THE System SHALL match the normalized path against all enabled lenient setups globally by route prefix (longest prefix match)
8. IF no enabled lenient setup exists for the normalized path, THEN THE System SHALL return the original Standard_Runtime miss result unchanged
9. IF an enabled lenient setup exists for the normalized path, THEN THE System SHALL proceed to lenient fallback generation
10. THE Lenient_Runtime_Endpoint SHALL emit structured logs for each request indicating: lenient endpoint invoked, standard runtime match result, lenient setup found or not found, generation triggered or not triggered

### Requirement 3: Lenient Fallback Generation Flow

**User Story:** As a tester, I want unmatched requests to automatically generate specific mocks, so that I can explore API behavior without manual mock creation.

#### Acceptance Criteria

1. WHEN lenient fallback is triggered, THE System SHALL compute a Request_Signature from the missed request using method, normalized path, query parameters, and body hash
2. WHEN Request_Signature is computed, THE System SHALL perform a best-effort check for whether a mapping with matching Request_Signature already exists
3. IF a mapping with matching Request_Signature exists, THEN THE System SHALL replay the request through Standard_Runtime and return the matched response
4. IF no mapping with matching Request_Signature exists, THEN THE System SHALL proceed with generation
5. WHEN a Generated_Mapping is created, THE System SHALL include exact method matching, normalized path matching, query parameter matching where present, and body matchers where appropriate
6. WHEN a Generated_Mapping is created, THE System SHALL attach Metadata including source=LENIENT_FALLBACK, setup namespace, setup route prefix, Request_Signature, creation timestamp, and near-miss fields
7. THE Validation_Engine SHALL validate the Generated_Mapping against the API specification (validation is always enabled and cannot be disabled for lenient generation)
8. IF validation fails and cannot be auto-corrected within the correction budget, THEN THE System SHALL log the validation failure and return the original Standard_Runtime miss result unchanged
9. WHEN Generated_Mapping passes validation, THE System SHALL persist the mapping to Persistence_Layer using existing persistent mock storage
10. WHEN Generated_Mapping is persisted, THE System SHALL replay the request through Standard_Runtime to ensure response comes from standard runtime semantics
11. THE System SHALL return the response from Standard_Runtime replay
12. IF generation fails at any step, THEN THE System SHALL log the failure with context and return the original Standard_Runtime miss result unchanged
13. IF persistence fails, THEN THE System SHALL log the failure with context and return the original Standard_Runtime miss result unchanged

### Requirement 4: Exactness and Specificity of Generated Mappings

**User Story:** As a developer, I want generated mocks to be specific to the actual request, so that I avoid overly broad wildcards that match unintended requests.

#### Acceptance Criteria

1. THE Generated_Mapping SHALL match the exact HTTP method of the missed request
2. THE Generated_Mapping SHALL match the exact normalized path or use path parameters only where the API specification defines them
3. WHEN query parameters are present in the missed request, THE Generated_Mapping SHALL include query parameter matchers for those specific parameters
4. WHEN a request body is present, THE Generated_Mapping SHALL include body matchers appropriate to the content type (JSON path, XML path, or exact match)
5. THE Generated_Mapping SHALL NOT use wildcard path matching (e.g., `/**`) in version 1
6. THE Generated_Mapping SHALL NOT use catch-all query parameter matching in version 1
7. WHEN the API specification defines path parameters (e.g., `/users/{userId}`), THE Generated_Mapping SHALL use path parameter matching for those segments only
8. THE Generated_Mapping SHALL include protocol-specific details such as Content-Type headers and Accept headers where relevant

### Requirement 5: Fail-Safe Behavior

**User Story:** As a tester, I want the system to gracefully handle failures, so that lenient mode never breaks my test flow with partial or unstable responses.

#### Acceptance Criteria

1. IF no lenient setup exists for a missed request, THEN THE System SHALL return the original Standard_Runtime miss result unchanged
2. IF generation fails for any reason, THEN THE System SHALL return the original Standard_Runtime miss result unchanged
3. IF validation fails and cannot be corrected, THEN THE System SHALL return the original Standard_Runtime miss result unchanged
4. IF persistence fails, THEN THE System SHALL return the original Standard_Runtime miss result unchanged
5. THE System SHALL NOT return a generated mock response unless the mapping is successfully persisted and replayed through Standard_Runtime
6. WHEN any failure occurs in lenient fallback flow, THE System SHALL emit structured logs with failure reason, Request_Signature, and setup identity
7. THE System SHALL NOT cache or serve unpersisted generated mocks

### Requirement 6: Metadata and Traceability

**User Story:** As a developer, I want generated mappings to include metadata, so that I can trace which mocks were auto-generated and analyze lenient usage patterns.

#### Acceptance Criteria

1. THE Generated_Mapping SHALL include metadata field `source` with value `LENIENT_FALLBACK`
2. THE Generated_Mapping SHALL include metadata field `setupNamespace` with the namespace from the lenient setup
3. THE Generated_Mapping SHALL include metadata field `setupRoutePrefix` with the route prefix from the lenient setup
4. THE Generated_Mapping SHALL include metadata field `requestSignature` with the computed Request_Signature value
5. THE Generated_Mapping SHALL include metadata field `createdAt` with ISO 8601 timestamp of generation
6. THE Generated_Mapping SHALL include metadata field `specificationFormat` indicating the API specification type
7. THE Generated_Mapping SHALL include metadata field `nearMissStubId` with the WireMock stub ID of the top near-miss used as context (null if none)
8. THE Generated_Mapping SHALL include metadata field `nearMissDistance` with the near-miss distance score (null if none)
9. THE System SHALL store `requestSignature` metadata on generated mappings so that duplicate mappings can be identified and cleaned up later if needed

### Requirement 7: S3-Based Persistence for Lenient Setups

**User Story:** As a developer, I want lenient setups stored in S3 with pre-compressed specifications, so that generation is fast and cold starts are not impacted.

#### Acceptance Criteria

1. THE Persistence_Layer SHALL store lenient setup metadata in S3 under prefix `lenient-setups/`
2. THE System SHALL derive a canonical, S3-safe namespace key from MockNamespace in a way that is stable and collision-safe for different apiName/client combinations; this canonical key SHALL be used in all S3 key patterns for lenient setup storage and request history storage
3. THE Persistence_Layer SHALL use S3 key pattern `lenient-setups/{namespace-key}/{route-prefix-hash}.json` for setup metadata and `lenient-setups/{namespace-key}/{route-prefix-hash}.spec.json` for the pre-compressed specification
4. WHEN a lenient setup is created or updated, THE System SHALL compress/reduce the specification using existing reducers and store the result to the `.spec.json` key
5. WHEN a lenient setup is created or updated, THE Persistence_Layer SHALL write setup metadata as JSON to S3 with server-side encryption enabled, referencing the compressed spec S3 key
6. WHEN a lenient setup is retrieved, THE Persistence_Layer SHALL read setup metadata from S3 and parse JSON into setup object
7. WHEN listing lenient setups, THE Persistence_Layer SHALL use S3 list operations with prefix filtering on metadata files only
8. THE System SHALL NOT preload lenient setups or compressed specs at Lambda cold start
9. THE System SHALL NOT rely on retained in-memory state between invocations
10. WHEN resolving a lenient setup for a missed request, THE System SHALL load setup metadata lazily from S3 on demand
11. WHEN generation is triggered, THE System SHALL load the pre-compressed spec lazily from S3 by reading the `specificationS3Key` from the setup metadata

### Requirement 8: Request History Persistence

**User Story:** As a developer, I want request history persisted, so that I can analyze coverage gaps and lenient usage patterns over time.

#### Acceptance Criteria

1. THE System SHALL persist unmatched request events to S3 under prefix `lenient-history/unmatched/` with key pattern `{date}/{namespace-key}/{timestamp}-{request-id}.json`
2. THE System SHALL persist lenient fallback generation events to S3 under prefix `lenient-history/generated/` with key pattern `{date}/{namespace-key}/{timestamp}-{request-id}.json`
3. THE persisted event SHALL include timestamp, namespace, route prefix, Request_Signature, HTTP method, normalized path, query parameters, request headers, and generation outcome (success or failure reason)
4. THE System SHALL NOT persist matched requests that were served by Standard_Runtime without lenient processing
5. THE System SHALL NOT persist request bodies containing sensitive data (determined by Content-Type and configurable exclusion patterns)
6. THE System SHALL write history events synchronously on the request path with a best-effort timeout of 2 seconds; if the write fails or times out, THE System SHALL log the failure and continue without failing the request
7. THE System SHALL provide a configuration option to enable or disable request history persistence (default: enabled)
8. THE System SHALL document that persisted history is intended for offline analysis and coverage gap detection, not for hot-path runtime matching

### Requirement 9: Logging and Metrics

**User Story:** As a developer, I want comprehensive logging and metrics, so that I can monitor lenient behavior, troubleshoot issues, and analyze usage patterns.

#### Acceptance Criteria

1. THE System SHALL emit structured log when Lenient_Runtime_Endpoint receives a request, including normalized path and Request_Signature
2. THE System SHALL emit structured log when Standard_Runtime returns a match, indicating lenient processing was skipped
3. THE System SHALL emit structured log when Standard_Runtime returns no match, indicating lenient setup resolution will proceed
4. THE System SHALL emit structured log when lenient setup is found or not found, including namespace and route prefix
5. THE System SHALL emit structured log when lenient fallback generation starts, including Request_Signature and setup identity
6. THE System SHALL emit structured log when generation succeeds, including mapping ID and generation duration
7. THE System SHALL emit structured log when generation fails, including failure reason and Request_Signature
8. THE System SHALL emit structured log when validation fails, including validation errors and Request_Signature
9. THE System SHALL emit structured log when persistence fails, including failure reason and Request_Signature
10. THE System SHALL emit CloudWatch metric `LenientSetupFound` with dimensions: namespace, routePrefix
11. THE System SHALL emit CloudWatch metric `LenientSetupNotFound` with dimension: endpoint=ai_lenient
12. THE System SHALL emit CloudWatch metric `LenientGenerationSuccess` with dimensions: namespace, routePrefix
13. THE System SHALL emit CloudWatch metric `LenientGenerationFailure` with dimensions: namespace, routePrefix, failureType (enum: GENERATION_FAILURE, VALIDATION_FAILURE, PERSISTENCE_FAILURE)
14. THE System SHALL emit CloudWatch metric `LenientValidationFailure` with dimensions: namespace, routePrefix
15. THE System SHALL emit CloudWatch metric `LenientPersistenceFailure` with dimensions: namespace, routePrefix
16. THE System SHALL emit CloudWatch metric `LenientMappingsBySource` with dimension: source=LENIENT_FALLBACK
17. THE System SHALL emit CloudWatch metric `LenientNearMissContextUsed` with dimensions: namespace, routePrefix
18. THE System SHALL emit CloudWatch metric `LenientNearMissDistance` with dimensions: namespace, routePrefix
19. THE System SHALL emit CloudWatch metric `LenientGenerationDuration` with dimensions: namespace, routePrefix
20. THE System SHALL record raw normalized path and free-text failure details in structured logs only, not as CloudWatch metric dimensions

### Requirement 10: Security and Abuse Prevention

**User Story:** As a system administrator, I want lenient endpoints protected, so that I prevent accidental high-cost AI usage and unauthorized access.

#### Acceptance Criteria

1. THE Lenient_Runtime_Endpoint SHALL require the same API key authentication as existing MockNest admin and runtime endpoints
2. THE Lenient_Setup_API SHALL require the same API key authentication as existing MockNest admin endpoints
3. THE System SHALL validate and sanitize API specification URLs before fetching to prevent SSRF attacks
4. THE System SHALL validate and sanitize generation instructions to prevent prompt injection attacks
5. THE System SHALL validate that route prefix in lenient setup does not overlap with reserved MockNest admin paths (`/__admin`, `/ai/generation`, `/ai/lenient/endpoint`)
6. THE System SHALL document that rate limiting is provided by the existing API Gateway usage plan (`BurstLimit: 1`, `RateLimit: 100 req/s`) and no additional application-level rate limiting is required
7. THE System SHALL implement a maximum generation timeout of 25 seconds to stay within the API Gateway 29-second limit
8. IF generation timeout is exceeded, THEN THE System SHALL cancel the generation, log the timeout, and return the original Standard_Runtime miss result unchanged

### Requirement 11: Non-Goals for Version 1

**User Story:** As a developer, I want to understand what is explicitly not included in version 1, so that I have clear expectations and can plan for future enhancements.

#### Acceptance Criteria

1. THE System SHALL NOT enable lenient behavior on normal `/mocknest/...` traffic in version 1
2. THE System SHALL NOT replace the Standard_Runtime matching engine in version 1
3. THE System SHALL NOT replace in-memory request journal behavior in version 1
4. THE System SHALL NOT implement asynchronous background generation workflow in version 1
5. THE System SHALL NOT automatically broaden or update existing mappings during lenient fallback in version 1
6. THE System SHALL NOT silently mutate existing mappings during lenient fallback in version 1
7. THE System SHALL NOT provide global lenient mode that affects all endpoints in version 1
8. THE System SHALL NOT support MCP (Model Context Protocol) or SSE (Server-Sent Events) specifications in version 1; these are planned for a future phase
9. THE System SHALL NOT implement per-namespace application-level rate limiting in version 1; rate limiting is handled entirely by the existing API Gateway usage plan
10. THE System SHALL NOT guarantee deduplication of simultaneous identical misses in version 1
11. THE System SHALL NOT guarantee which duplicate mapping is selected first if multiple generated mappings exist for the same Request_Signature in version 1
12. THE System SHALL NOT implement locking or distributed concurrency control in version 1

### Requirement 12: API Specification Parser Integration

**User Story:** As a developer, I want lenient generation to reuse existing specification parsers, so that I maintain consistency with Priority 1 generation capabilities.

#### Acceptance Criteria

1. THE System SHALL reuse the existing specification parsing capability for parsing API specifications in lenient setups
2. THE System SHALL support OpenAPI 3.x (3.0.x and 3.1.x) and Swagger 2.0 specifications for lenient generation
3. THE System SHALL support WSDL 1.2 specifications for lenient generation (SOAP 1.2 only — SOAP 1.1 is not supported for AI generation)
4. THE System SHALL support GraphQL schemas for lenient generation
5. WHEN a lenient setup is created with an API specification, THE System SHALL validate that the specification format is supported
6. WHEN a lenient setup is created with an API specification URL, THE System SHALL fetch and parse the specification at creation time to validate it is accessible and parseable
7. THE System SHALL NOT cache parsed API specifications in memory between invocations; the pre-compressed spec stored in S3 is the durable form

### Requirement 13: AI Generation Service Integration

**User Story:** As a developer, I want lenient generation to reuse existing AI generation services, so that I maintain consistency and avoid duplicating AI integration logic.

#### Acceptance Criteria

1. THE System SHALL reuse the existing AI generation service for generating mocks in lenient fallback
2. THE System SHALL use the same prompt building logic as Priority 1 generation for consistency
3. THE System SHALL pass the missed request details (method, path, query params, body) to the AI generation service as context
4. THE System SHALL pass the lenient setup's generation instructions to the AI generation service as additional guidance
5. THE System SHALL pass the relevant portion of the API specification (matching the missed request path) to the AI generation service
6. THE System SHALL request that the AI generation service produce a single, specific mapping for the missed request
7. THE System SHALL include in the generation prompt explicit instructions to avoid wildcard matching
8. THE System SHALL validate that the generated mapping matches the missed request before persisting

### Requirement 14: Round-Trip Property for Lenient Setup Persistence

**User Story:** As a developer, I want lenient setup persistence to be reliable, so that I can trust that saved setups are retrieved exactly as stored.

#### Acceptance Criteria

1. FOR ALL stored LenientSetup objects, THE System SHALL satisfy the property: retrieve(persist(setup)) equals setup
2. THE System SHALL preserve all fields of the stored LenientSetup model during persist and retrieve: namespace, routePrefix, specificationS3Key, format, description, enabled, createdAt, updatedAt
3. THE System SHALL preserve field types during persist and retrieve operations (strings remain strings, booleans remain booleans, objects remain objects)
4. THE System SHALL preserve empty optional fields (null) during persist and retrieve operations
5. THE System SHALL preserve special characters in text fields during persist and retrieve operations

### Requirement 15: AWS Lambda Synchronous Execution Constraints

**User Story:** As a developer, I want lenient generation to complete within AWS Lambda synchronous limits, so that API Gateway integration remains stable.

#### Acceptance Criteria

1. THE System SHALL complete lenient fallback generation within 29 seconds to stay within API Gateway timeout
2. THE System SHALL implement generation timeout of 25 seconds to allow buffer for persistence and replay
3. IF generation exceeds 25 seconds, THEN THE System SHALL cancel generation and return the original Standard_Runtime miss result unchanged
4. THE System SHALL log generation duration for all lenient fallback operations
5. THE System SHALL emit CloudWatch metric `LenientGenerationDuration` with dimensions namespace and routePrefix
6. THE System SHALL document that lenient generation is designed for synchronous Lambda execution, not asynchronous background processing

### Requirement 16: Cloud-Agnostic Domain and Application Layers

**User Story:** As a developer, I want domain and application logic to be cloud-agnostic, so that the architecture remains portable and testable.

#### Acceptance Criteria

1. THE domain layer SHALL NOT contain AWS-specific imports or dependencies
2. THE application layer SHALL NOT contain AWS-specific imports or dependencies
3. THE application layer SHALL define interfaces for lenient setup storage and request history storage
4. THE application layer SHALL define an interface for near-miss retrieval from the mock runtime
5. THE infrastructure layer SHALL provide AWS-specific implementations of storage interfaces
6. THE infrastructure layer SHALL contain all cloud-specific and runtime-integration code
7. THE System SHALL organize code following clean architecture: infra → application → domain

### Requirement 17: Lenient Setup Identity and Global Route Prefix Uniqueness

**User Story:** As a developer, I want lenient setup identity to be explicit and unambiguous, so that I understand exactly how setups are matched to requests.

#### Acceptance Criteria

1. THE Setup_Identity SHALL be the combination of MockNamespace (apiName + optional client) and route prefix
2. THE System SHALL enforce that route prefix is globally unique across ALL enabled lenient setups, regardless of namespace
3. WHEN creating a lenient setup, THE System SHALL reject creation if a setup with the same Setup_Identity already exists
4. WHEN creating a lenient setup, THE System SHALL reject creation if any existing enabled setup already uses the same route prefix
5. WHEN enabling a previously disabled lenient setup, THE System SHALL reject the enable operation if any other enabled setup already uses the same route prefix
6. WHEN updating a lenient setup, THE System SHALL require both namespace and route prefix to identify the setup to update
7. WHEN updating a lenient setup such that its route prefix changes, THE System SHALL enforce the same route-prefix uniqueness rules as for create
8. WHEN updating a lenient setup such that it becomes enabled, THE System SHALL enforce the same enabled-route-prefix uniqueness rules as for enable
9. WHEN resolving lenient setup for a request, THE System SHALL use longest prefix matching on route prefix across all enabled setups globally (no namespace context required at resolution time)
10. IF multiple enabled setups match a request path, THEN THE System SHALL select the setup with the longest matching route prefix
11. THE System SHALL normalize route prefixes by removing trailing slashes before comparison
12. THE System SHALL document that Setup_Identity is namespace + route prefix, and that route prefix must be globally unique across all enabled setups
13. WHEN create, update, or enable operations violate setup identity or route-prefix uniqueness rules, THE Lenient_Setup_API SHALL reject the operation with a conflict response

### Requirement 18: Validation and Correction Budget

**User Story:** As a system administrator, I want validation to be mandatory for lenient generation with a bounded correction budget, so that automatically generated mocks are always verified and the Lambda timeout is respected.

#### Acceptance Criteria

1. THE System SHALL always enable validation for lenient mock generation; validation cannot be disabled
2. THE System SHALL NOT provide a configuration option to disable validation for lenient setups
3. THE Validation_Engine SHALL reuse the existing mock validation capability for validating generated mappings
4. THE Validation_Engine SHALL check that the response status code is valid for the endpoint
5. THE Validation_Engine SHALL check that the response body schema matches the API specification
6. THE Validation_Engine SHALL check that required response headers are present
7. THE correction budget SHALL be: 1 initial generation attempt + maximum 1 correction attempt (2 attempts total), consistent with the existing generation retry pattern and Lambda timeout constraints
8. IF the generated mapping fails validation and the correction budget is not exhausted, THEN THE System SHALL attempt one correction
9. IF the generated mapping fails validation and the correction budget is exhausted (after 1 correction attempt), THEN THE System SHALL log the validation errors and return the original Standard_Runtime miss result unchanged
10. WHEN a generated mapping fails validation, THE System SHALL NOT persist the mapping
11. THE System SHALL emit structured logs with validation error details when validation fails
12. THE System SHALL document that validation is mandatory because lenient mocks are generated automatically without human review

### Requirement 19: Near-Miss Context for Lenient Generation

**User Story:** As a developer, I want lenient generation to use existing similar mocks as secondary context, so that generated mocks are consistent with the response conventions already established in the mock suite.

#### Acceptance Criteria

1. WHEN lenient fallback generation is triggered, THE System SHALL query the standard runtime for near-miss stubs on the unmatched request
2. THE System SHALL use the standard runtime's native near-miss ranking (already ordered by the runtime's own distance scoring) rather than implementing a custom similarity algorithm
3. THE System SHALL take the top 1–3 results from the runtime's ranked near-miss list
4. THE System SHALL filter out near-miss results where the distance score exceeds a configurable threshold (default 0.5)
5. THE System SHALL filter out near-miss results with a different HTTP method than the missed request
6. IF the runtime returns no near-misses or all results exceed the threshold, THEN THE System SHALL proceed with generation using only the primary inputs (missed request + spec + instruction)
7. THE System SHALL pass near-miss stub mappings to the AI generation prompt as secondary context only, not as primary input
8. THE System SHALL use near-miss context to improve consistency of response shape, field naming, headers, and sample values
9. THE System SHALL NOT copy or broaden near-miss stubs during lenient generation
10. THE System SHALL NOT mutate or overwrite existing near-miss stubs during lenient generation
11. WHEN a mapping is generated using near-miss context, THE System SHALL store `nearMissStubId` (stub ID of top near-miss) and `nearMissDistance` (distance score) in the generated mapping metadata
12. THE System SHALL emit structured logs listing which near-miss stubs were used as context, including their stub IDs and distance scores
