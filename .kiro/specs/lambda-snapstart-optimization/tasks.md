# Implementation Plan: Lambda SnapStart Optimization

## Overview

This implementation plan enables AWS Lambda SnapStart for both MockNest Serverless Lambda functions (runtime and generation) to reduce cold start times. The approach includes SAM template configuration, priming hook implementation for resource warmup during snapshot creation, comprehensive testing, and performance validation using AWS Lambda Power Tuner.

SnapStart creates pre-initialized snapshots of Lambda execution environments, reducing cold start latency from several seconds to sub-second response times. The implementation uses Spring Boot lifecycle events to execute priming logic during snapshot creation, warming up health checks, S3 clients, and Bedrock clients before the first invocation.

## Tasks

- [x] 1. Update SAM template with SnapStart configuration
  - Add AutoPublishAlias and SnapStart configuration to both Lambda functions
  - Add CloudFormation outputs for version and alias verification
  - Ensure API Gateway integration automatically uses published alias
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.8_

- [ ]* 1.1 Write integration tests for SAM template SnapStart configuration
  - Verify SnapStart is enabled for both functions after deployment
  - Verify AutoPublishAlias creates "live" alias pointing to published version
  - Verify API Gateway integration invokes alias instead of $LATEST
  - _Requirements: 2.6, 2.7, 2.9_

- [-] 2. Implement Runtime function priming hook
  - [x] 2.1 Create RuntimePrimingHook component with Spring ApplicationReadyEvent listener
    - Implement SnapStart environment detection using AWS_LAMBDA_INITIALIZATION_TYPE
    - Add comprehensive logging for priming execution
    - _Requirements: 3.1, 3.2, 3.6_
  
  - [x] 2.2 Implement health check warmup in priming hook
    - Invoke health check use case during priming
    - Wrap in runCatching for graceful degradation
    - _Requirements: 3.3, 3.7_
  
  - [x] 2.3 Implement S3 client initialization in priming hook
    - Execute S3 listBuckets operation to warm up client connections
    - Wrap in runCatching for graceful degradation
    - _Requirements: 3.4, 3.7_
  
  - [x] 2.4 Write unit tests for RuntimePrimingHook
    - Test successful priming execution with all components
    - Test graceful degradation when health check fails
    - Test graceful degradation when S3 client initialization fails
    - Test SnapStart environment detection logic
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.7_
  
  - [ ]* 2.5 Write property test for priming initialization timeout
    - **Property 1: Priming Initialization Timeout**
    - **Validates: Requirements 3.5**
    - Test that priming completes within 10 second Lambda initialization timeout
    - Use Kotest property testing with 100 iterations
    - _Requirements: 3.5_
  
  - [ ]* 2.6 Write property test for priming resilience
    - **Property 2: Priming Resilience to Non-Critical Failures**
    - **Validates: Requirements 3.7**
    - Test that non-critical resource failures don't prevent snapshot creation
    - Use Kotest property testing with 100 iterations
    - _Requirements: 3.7_

- [x] 3. Implement Generation function priming hook
  - [x] 3.1 Create GenerationPrimingHook component with Spring ApplicationReadyEvent listener
    - Implement SnapStart environment detection using AWS_LAMBDA_INITIALIZATION_TYPE
    - Add comprehensive logging for priming execution
    - _Requirements: 4.1, 4.2, 4.7_
  
  - [x] 3.2 Implement health check warmup in priming hook
    - Invoke AI health check use case during priming
    - Wrap in runCatching for graceful degradation
    - _Requirements: 4.3, 4.8_
  
  - [x] 3.3 Implement S3 client initialization in priming hook
    - Execute S3 listBuckets operation to warm up client connections
    - Wrap in runCatching for graceful degradation
    - _Requirements: 4.4, 4.8_
  
  - [x] 3.4 Write unit tests for GenerationPrimingHook
    - Test successful priming execution with all components
    - Test graceful degradation when health check fails
    - Test graceful degradation when S3 client initialization fails
    - Test SnapStart environment detection logic
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.8_
  
  - [ ]* 3.5 Write property test for priming initialization timeout
    - **Property 1: Priming Initialization Timeout**
    - **Validates: Requirements 4.6**
    - Test that priming completes within 10 second Lambda initialization timeout
    - Use Kotest property testing with 100 iterations
    - _Requirements: 4.6_
  
  - [ ]* 3.6 Write property test for priming resilience
    - **Property 2: Priming Resilience to Non-Critical Failures**
    - **Validates: Requirements 4.8**
    - Test that non-critical resource failures don't prevent snapshot creation
    - Use Kotest property testing with 100 iterations
    - _Requirements: 4.8_

- [x] 4. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ]* 5. Write functional correctness integration tests
  - [ ]* 5.1 Write property test for Runtime function correctness with SnapStart
    - **Property 3: Runtime Function Correctness with SnapStart**
    - **Validates: Requirements 8.1, 8.2, 8.6, 8.7**
    - Test that Runtime function returns same responses with SnapStart enabled
    - Test mock serving functionality works correctly
    - Test WireMock admin API works correctly
    - Test S3 persistence works correctly
    - Test API Gateway integration works correctly
    - Use Kotest property testing with 100 iterations
    - _Requirements: 8.1, 8.2, 8.6, 8.7_
  
  - [ ]* 5.2 Write property test for Generation function correctness with SnapStart
    - **Property 4: Generation Function Correctness with SnapStart**
    - **Validates: Requirements 8.3, 8.4, 8.6, 8.7**
    - Test that Generation function returns same responses with SnapStart enabled
    - Test mock generation from specifications works correctly
    - Test Bedrock interaction works correctly
    - Test S3 persistence works correctly
    - Test API Gateway integration works correctly
    - Use Kotest property testing with 100 iterations
    - _Requirements: 8.3, 8.4, 8.6, 8.7_
  
  - [ ]* 5.3 Run existing MockNest integration test suite against SnapStart-enabled functions
    - Execute all existing integration tests to verify no regressions
    - Verify all mock serving functionality works correctly
    - Verify all admin API operations work correctly
    - Verify all generation functionality works correctly
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_

- [ ] 6. Deploy and verify SnapStart configuration
  - [ ] 6.1 Deploy SAM template with SnapStart configuration
    - Run build.sh to build Lambda deployment package
    - Run deploy.sh to deploy updated SAM template
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_
  
  - [ ] 6.2 Verify SnapStart configuration in AWS
    - Use AWS CLI to verify SnapStart is enabled for both functions
    - Verify AutoPublishAlias created "live" alias for both functions
    - Verify API Gateway integration points to alias instead of $LATEST
    - _Requirements: 2.6, 2.7, 2.9_
  
  - [ ] 6.3 Verify priming hook execution in CloudWatch Logs
    - Check CloudWatch Logs for "SnapStart detected" messages
    - Verify priming hook executed successfully for both functions
    - Verify all priming steps logged correctly
    - _Requirements: 3.6, 4.7_

- [x] 7. Create PERFORMANCE.md documentation
  - [x] 7.1 Document Lambda Power Tuner setup process
    - Provide step-by-step instructions for deploying Lambda Power Tuner from SAR
    - Document IAM permissions required for tuner execution
    - Document test payload preparation for both functions
    - _Requirements: 1.1, 6.1, 7.3, 7.4_
  
  - [x] 7.2 Execute Lambda Power Tuner and document results (manual testing required)
    - Execute Lambda Power Tuner for Runtime function with SnapStart enabled
    - Execute Lambda Power Tuner for Generation function with SnapStart enabled
    - Test memory configurations from 512MB to 3072MB with 10 invocations per config
    - Include Lambda Power Tuner visualization diagrams in documentation
    - _Requirements: 1.2, 1.3, 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8_
  
  - [x] 7.3 Document memory configuration recommendations
    - Document recommended memory setting (best cost/performance balance)
    - Document cheapest memory configuration option
    - Document most expensive/fastest memory configuration option
    - Document cold start times and warm execution times for each option
    - Document cost per invocation for each option
    - _Requirements: 6.3, 6.4, 6.5, 6.6, 6.8_
  
  - [x] 7.4 Update SAM template with recommended memory configuration
    - Update MemorySize for Runtime function to recommended setting from Power Tuner
    - Update MemorySize for Generation function to recommended setting from Power Tuner
    - Document rationale for chosen defaults in comments
    - _Requirements: 6.8_
  
  - [x] 7.5 Document SnapStart configuration details
    - Document SAM template SnapStart configuration
    - Document priming hook approach for both functions
    - Document SnapStart environment detection mechanism
    - _Requirements: 6.9_
  
  - [x] 7.6 Document testing automation guidance
    - Clarify which tests can be automated in CI/CD
    - Clarify which tests require manual AWS console/CLI work
    - Document Lambda Power Tuner execution steps
    - Document cold start triggering procedure
    - Document SnapStart verification steps
    - _Requirements: 6.10, 7.1, 7.2, 7.6, 7.7, 7.8_

- [ ] 8. Final checkpoint - Ensure all tests pass and documentation is complete
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties
- Unit tests validate specific examples and edge cases
- Manual performance testing (task 7.2) requires AWS Lambda Power Tuner execution
- Cold start measurement requires forcing Lambda to scale to zero (15-30 minutes inactivity)
- Performance documentation focuses on SnapStart-enabled results only (no baseline comparison)
- Future performance testing with mocks in memory will be a separate story
