# Bugfix Requirements Document

## Introduction

MockNest Serverless deploys two separate AWS Lambda functions (runtime and generation) that both use Spring Cloud Function. After splitting from a monolithic jar to separate Lambda deployment packages, both functions fail during initialization with `NoSuchBeanDefinitionException` for the `FunctionCatalog` bean. This prevents both Lambda functions from starting successfully.

The root cause is that both Lambda functions share the same `software/infra/aws` module, which contains a single Spring Boot application context. When packaged into separate jars, each Lambda lacks proper Spring context isolation and cannot initialize its own `FunctionCatalog`.

This bugfix splits the monolithic `software/infra/aws` module into three separate modules to provide proper Spring context isolation for each Lambda function while sharing common infrastructure code.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN the runtime Lambda function initializes THEN the system throws `NoSuchBeanDefinitionException: No qualifying bean of type 'org.springframework.cloud.function.context.FunctionCatalog' available` at `FunctionInvoker.start()`

1.2 WHEN the generation Lambda function initializes THEN the system throws `NoSuchBeanDefinitionException: No qualifying bean of type 'org.springframework.cloud.function.context.FunctionCatalog' available` at `FunctionInvoker.start()`

1.3 WHEN both Lambda jars are built from the shared `software/infra/aws` module THEN the system creates deployment packages without proper Spring Boot application context separation

1.4 WHEN Spring Cloud Function adapter attempts to locate the FunctionCatalog bean THEN the system fails because the Spring context is not properly initialized for each Lambda's specific function beans

### Expected Behavior (Correct)

2.1 WHEN the runtime Lambda function initializes THEN the system SHALL successfully create a Spring context with its own FunctionCatalog containing runtime-specific function beans

2.2 WHEN the generation Lambda function initializes THEN the system SHALL successfully create a Spring context with its own FunctionCatalog containing generation-specific function beans

2.3 WHEN both Lambda jars are built from separate modules (`infra/aws/runtime` and `infra/aws/generation`) THEN the system SHALL create deployment packages with isolated Spring Boot application contexts

2.4 WHEN each Lambda module defines its own Spring Boot application class THEN the system SHALL properly initialize Spring Cloud Function with the correct FunctionCatalog for that Lambda's specific functions

2.5 WHEN shared infrastructure code is needed by both Lambdas THEN the system SHALL provide it through a common `infra/aws/core` module that both Lambda modules depend on

### Unchanged Behavior (Regression Prevention)

3.1 WHEN the runtime Lambda processes mock requests THEN the system SHALL CONTINUE TO serve mocked endpoints using WireMock with S3-backed storage

3.2 WHEN the generation Lambda processes AI generation requests THEN the system SHALL CONTINUE TO generate mocks using Bedrock and store them in S3

3.3 WHEN either Lambda accesses S3 storage THEN the system SHALL CONTINUE TO use the existing ObjectStorage implementations

3.4 WHEN WireMock configuration is needed THEN the system SHALL CONTINUE TO use the existing WireMock setup with custom stores and extensions

3.5 WHEN clean architecture boundaries are enforced THEN the system SHALL CONTINUE TO maintain the dependency flow: infrastructure → application → domain

3.6 WHEN Gradle builds the project THEN the system SHALL CONTINUE TO produce deployable Lambda jars with all required dependencies

3.7 WHEN the SAM template deploys the Lambdas THEN the system SHALL CONTINUE TO configure API Gateway, Lambda functions, and S3 buckets correctly
