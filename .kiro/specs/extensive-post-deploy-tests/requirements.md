# Requirements Document

## Introduction

This document defines the requirements for adding an extensive integration test suite to the MockNest Serverless deploy-on-demand workflow. The extensive tests cover ALL APIs defined in the MockNest OpenAPI specification (`docs/api/mocknest-openapi.yaml`) that are not already tested by the basic (standard) test suite.

The extensive test suite is controlled by an optional boolean parameter (`run-extensive-tests`) on both the deploy-on-demand workflow and the reusable integration test workflow. When enabled, the extensive tests run AFTER the standard tests complete successfully. The tests are organized into logical groups: mock management (CRUD), request verification, near-miss analysis, and file management.

## Glossary

- **Deploy_On_Demand_Workflow**: The GitHub Actions workflow (`deploy-on-demand.yml`) that allows manual deployment of MockNest Serverless to AWS
- **Integration_Test_Workflow**: The reusable GitHub Actions workflow (`workflow-integration-test.yml`) that executes post-deployment validation tests
- **Test_Script**: The bash script (`scripts/post-deploy-test.sh`) containing all integration test logic
- **Standard_Tests**: The existing test suites (setup, rest, graphql, soap, webhook) that validate health checks, AI generation, import, and webhook delivery
- **Extensive_Tests**: The new test suites that validate all remaining MockNest APIs from the OpenAPI specification not covered by Standard_Tests
- **Mock_Management_Tests**: Extensive tests covering CRUD operations on WireMock stub mappings (create, read, update, delete, find-by-metadata, remove-by-metadata, save, reset, unmatched)
- **Request_Verification_Tests**: Extensive tests covering request journal operations (list, find, count, remove, remove-by-metadata, unmatched, get-by-id, delete-by-id, clear, reset)
- **Near_Miss_Tests**: Extensive tests covering near-miss analysis endpoints (unmatched near-misses, near-misses for request, near-misses for request pattern)
- **File_Management_Tests**: Extensive tests covering file CRUD operations (list files, get file, create/update file, delete file)
- **Run_Extensive_Tests_Parameter**: A boolean workflow input parameter that controls whether Extensive_Tests execute after Standard_Tests

## Requirements

### Requirement 1: Extensive Test Parameter on Deploy-On-Demand Workflow

**User Story:** As a DevOps engineer, I want an optional parameter on the deploy-on-demand workflow to enable extensive testing, so that I can choose to run comprehensive API coverage tests when needed without slowing down routine deployments.

#### Acceptance Criteria

1. THE Deploy_On_Demand_Workflow SHALL provide a boolean input parameter named `run-extensive-tests` with a default value of `false`
2. WHEN `run-extensive-tests` is set to `true`, THE Deploy_On_Demand_Workflow SHALL pass the parameter value to the Integration_Test_Workflow
3. WHEN `run-extensive-tests` is set to `false` or not provided, THE Deploy_On_Demand_Workflow SHALL NOT trigger Extensive_Tests
4. THE Deploy_On_Demand_Workflow SHALL include a descriptive label for the `run-extensive-tests` parameter explaining its purpose

### Requirement 2: Extensive Test Parameter on Reusable Integration Test Workflow

**User Story:** As a DevOps engineer, I want the reusable integration test workflow to accept the extensive test parameter, so that any workflow calling it can control whether extensive tests run.

#### Acceptance Criteria

1. THE Integration_Test_Workflow SHALL accept a boolean input parameter named `run-extensive-tests` with a default value of `false` in the `workflow_call` trigger
2. THE Integration_Test_Workflow SHALL accept a boolean input parameter named `run-extensive-tests` with a default value of `false` in the `workflow_dispatch` trigger
3. WHEN `run-extensive-tests` is `true`, THE Integration_Test_Workflow SHALL execute Extensive_Tests after Standard_Tests complete successfully
4. WHEN `run-extensive-tests` is `false`, THE Integration_Test_Workflow SHALL skip Extensive_Tests entirely

### Requirement 3: Extensive Tests Execute After Standard Tests

**User Story:** As a DevOps engineer, I want extensive tests to run only after standard tests pass, so that I do not waste CI time on extensive tests when basic functionality is broken.

#### Acceptance Criteria

1. WHEN Standard_Tests complete successfully and `run-extensive-tests` is `true`, THE Integration_Test_Workflow SHALL execute Extensive_Tests
2. IF any Standard_Test job fails, THEN THE Integration_Test_Workflow SHALL skip Extensive_Tests regardless of the `run-extensive-tests` parameter value
3. THE Integration_Test_Workflow SHALL execute Extensive_Tests as separate parallel workflow jobs (mock-management, request-verification, near-miss, files) to reduce total execution time
4. WHEN Extensive_Tests are running, EACH extensive test job SHALL execute independently without blocking other extensive test jobs

### Requirement 4: Test Script Extensive Suite Support

**User Story:** As a developer, I want the test script to support new extensive test suite arguments, so that each extensive test group can be invoked independently.

#### Acceptance Criteria

1. THE Test_Script SHALL accept `mock-management` as a test suite argument to run Mock_Management_Tests
2. THE Test_Script SHALL accept `request-verification` as a test suite argument to run Request_Verification_Tests
3. THE Test_Script SHALL accept `near-miss` as a test suite argument to run Near_Miss_Tests
4. THE Test_Script SHALL accept `files` as a test suite argument to run File_Management_Tests
5. THE Test_Script SHALL accept `extensive` as a test suite argument to run all four extensive test groups sequentially
6. THE Test_Script SHALL document the new test suite options in its usage help text

### Requirement 5: Mock Management CRUD Tests

**User Story:** As a developer, I want integration tests that validate all mock management CRUD operations, so that I can verify the full WireMock admin API for mappings works correctly after deployment.

#### Acceptance Criteria

1. WHEN running Mock_Management_Tests, THE Test_Script SHALL create a new mapping via `POST /__admin/mappings` and verify a 201 response with a mapping ID
2. WHEN a mapping is created, THE Test_Script SHALL retrieve the mapping via `GET /__admin/mappings/{mappingId}` and verify a 200 response with matching mapping data
3. WHEN a mapping exists, THE Test_Script SHALL update the mapping via `PUT /__admin/mappings/{mappingId}` and verify a 200 response
4. WHEN a mapping is updated, THE Test_Script SHALL retrieve the mapping again and verify the updated data is returned
5. WHEN a mapping exists, THE Test_Script SHALL list all mappings via `GET /__admin/mappings` and verify the response contains the created mapping
6. WHEN a mapping exists, THE Test_Script SHALL delete the mapping via `DELETE /__admin/mappings/{mappingId}` and verify a 200 response
7. IF a mapping is requested with a non-existent ID, THEN THE Test_Script SHALL verify a 404 response from `GET /__admin/mappings/{mappingId}`

### Requirement 6: Mock Management Save and Reset Tests

**User Story:** As a developer, I want integration tests for persist and reset operations, so that I can verify mappings can be saved to S3 and the server state can be fully reset.

#### Acceptance Criteria

1. WHEN running Mock_Management_Tests, THE Test_Script SHALL create a mapping and then call `POST /__admin/mappings/save` and verify a 200 response
2. WHEN running Mock_Management_Tests, THE Test_Script SHALL create mappings and then call `POST /__admin/reset` and verify a 200 response
3. WHEN reset is called, THE Test_Script SHALL verify that `GET /__admin/mappings` returns zero mappings

### Requirement 7: Mock Management Metadata Tests

**User Story:** As a developer, I want integration tests for metadata-based find and remove operations, so that I can verify mappings can be queried and removed by metadata.

#### Acceptance Criteria

1. WHEN running Mock_Management_Tests, THE Test_Script SHALL create a mapping with metadata and then call `POST /__admin/mappings/find-by-metadata` with a matching metadata pattern and verify the mapping is returned
2. WHEN running Mock_Management_Tests, THE Test_Script SHALL create a mapping with metadata and then call `POST /__admin/mappings/remove-by-metadata` with a matching metadata pattern and verify a 200 response
3. WHEN metadata removal is complete, THE Test_Script SHALL verify the mapping is no longer returned by `GET /__admin/mappings`

### Requirement 8: Mock Management Unmatched Mappings Tests

**User Story:** As a developer, I want integration tests for unmatched mapping operations, so that I can verify the system correctly identifies and removes mappings that have not matched any requests.

#### Acceptance Criteria

1. WHEN running Mock_Management_Tests, THE Test_Script SHALL call `GET /__admin/mappings/unmatched` and verify a 200 response with a mappings array
2. WHEN unmatched mappings exist, THE Test_Script SHALL call `DELETE /__admin/mappings/unmatched` and verify a 200 response

### Requirement 9: Request Verification Tests

**User Story:** As a developer, I want integration tests that validate all request journal operations, so that I can verify request recording, querying, and cleanup work correctly after deployment.

#### Acceptance Criteria

1. WHEN running Request_Verification_Tests, THE Test_Script SHALL first create a mapping and invoke it to generate a journal entry
2. WHEN a request is recorded, THE Test_Script SHALL list requests via `GET /__admin/requests` and verify a 200 response containing the recorded request
3. WHEN a request is recorded, THE Test_Script SHALL find the request via `POST /__admin/requests/find` with matching criteria and verify a 200 response
4. WHEN a request is recorded, THE Test_Script SHALL count requests via `POST /__admin/requests/count` with matching criteria and verify the count is at least 1
5. WHEN a request is recorded, THE Test_Script SHALL retrieve the request by ID via `GET /__admin/requests/{requestId}` and verify a 200 response
6. WHEN a request exists, THE Test_Script SHALL call `GET /__admin/requests/unmatched` and verify a 200 response
7. WHEN a request exists, THE Test_Script SHALL remove requests via `POST /__admin/requests/remove` with matching criteria and verify a 200 response
8. WHEN cleanup is needed, THE Test_Script SHALL clear all requests via `DELETE /__admin/requests` and verify a 200 response
9. WHEN cleanup is needed, THE Test_Script SHALL reset the request log via `POST /__admin/requests/reset` and verify a 200 response
10. WHEN a request exists, THE Test_Script SHALL delete the request by ID via `DELETE /__admin/requests/{requestId}` and verify a 200 response

### Requirement 10: Request Verification Metadata Tests

**User Story:** As a developer, I want integration tests for metadata-based request removal, so that I can verify requests can be removed by metadata criteria.

#### Acceptance Criteria

1. WHEN running Request_Verification_Tests, THE Test_Script SHALL call `POST /__admin/requests/remove-by-metadata` with metadata criteria and verify a 200 response

### Requirement 11: Near-Miss Analysis Tests

**User Story:** As a developer, I want integration tests for near-miss analysis endpoints, so that I can verify the system correctly identifies near-misses for debugging unmatched requests.

#### Acceptance Criteria

1. WHEN running Near_Miss_Tests, THE Test_Script SHALL first create a mapping and send a request that does not exactly match it to generate near-miss data
2. WHEN near-miss data exists, THE Test_Script SHALL call `GET /__admin/requests/unmatched/near-misses` and verify a 200 response
3. WHEN running Near_Miss_Tests, THE Test_Script SHALL call `POST /__admin/near-misses/request` with a logged request body and verify a 200 response
4. WHEN running Near_Miss_Tests, THE Test_Script SHALL call `POST /__admin/near-misses/request-pattern` with a request pattern and verify a 200 response

### Requirement 12: File Management Tests

**User Story:** As a developer, I want integration tests that validate all file CRUD operations, so that I can verify response body file management in S3 works correctly after deployment.

#### Acceptance Criteria

1. WHEN running File_Management_Tests, THE Test_Script SHALL create a file via `PUT /__admin/files/{fileId}` with a test file name and content and verify a 200 response
2. WHEN a file is created, THE Test_Script SHALL retrieve the file via `GET /__admin/files/{fileId}` and verify a 200 response with the correct content
3. WHEN files exist, THE Test_Script SHALL list all files via `GET /__admin/files` and verify a 200 response containing the created file name
4. WHEN a file exists, THE Test_Script SHALL update the file via `PUT /__admin/files/{fileId}` with new content and verify a 200 response
5. WHEN a file is updated, THE Test_Script SHALL retrieve the file again and verify the updated content is returned
6. WHEN a file exists, THE Test_Script SHALL delete the file via `DELETE /__admin/files/{fileId}` and verify a 200 response
7. IF a file is requested with a non-existent ID, THEN THE Test_Script SHALL verify a 404 response from `GET /__admin/files/{fileId}`

### Requirement 13: Extensive Test Cleanup

**User Story:** As a developer, I want extensive tests to clean up after themselves, so that they do not leave stale data that could affect subsequent test runs.

#### Acceptance Criteria

1. WHEN Mock_Management_Tests complete, THE Test_Script SHALL delete all mappings created during the test
2. WHEN Request_Verification_Tests complete, THE Test_Script SHALL clear the request journal
3. WHEN File_Management_Tests complete, THE Test_Script SHALL delete all files created during the test
4. WHEN Near_Miss_Tests complete, THE Test_Script SHALL clean up any mappings and requests created during the test

### Requirement 14: Extensive Test Error Handling

**User Story:** As a developer, I want clear error reporting for extensive tests, so that I can quickly identify which API endpoint failed and why.

#### Acceptance Criteria

1. WHEN any extensive test HTTP request fails, THE Test_Script SHALL report the endpoint path, HTTP method, expected status code, actual status code, and response body
2. WHEN any extensive test fails, THE Test_Script SHALL stop execution of that test group and exit with a non-zero exit code
3. THE Test_Script SHALL prefix all extensive test output messages with the test group name for easy identification in logs

### Requirement 15: IAM Auth Mode Compatibility

**User Story:** As a DevOps engineer, I want extensive tests to work in both API_KEY and IAM authentication modes, so that the full API surface is validated regardless of the deployment's auth configuration.

#### Acceptance Criteria

1. WHEN `auth-mode` is `API_KEY`, THE Test_Script SHALL use the `x-api-key` header for all extensive test requests
2. WHEN `auth-mode` is `IAM`, THE Test_Script SHALL use AWS SigV4 signing for all extensive test requests
3. THE Test_Script SHALL reuse the existing authentication mechanism (CURL_OPTS) for all extensive test requests
