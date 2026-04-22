# Changelog

All notable changes to MockNest Serverless will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).


## [0.6.0] - 2026-04-22
### Documentation
- Added generation quality evaluation metrics with a 42-scenario evaluation suite and protocol-by-protocol results reporting validity rates, costs, and latency.
### Bug Fixes
- Improved OpenAPI response validation to correctly handle 2xx status codes and parameter lookups.
- Enhanced SOAP namespace validation with special handling for fault elements and improved error messaging.
- Added WSDL service address tracking and metadata support.
### Tests
- Added comprehensive validation test suites for GraphQL, SOAP, and WSDL specifications.
- Implemented phase-based cost tracking separating generation and evaluation costs.

## [0.5.4] - 2026-04-18
- Improve prompts after eval tests
- Improve performance on delete mappings
- Improve documentation

## [0.5.3] - 2026-04-14
- Fix after restore and priming bug
- Upgrade libraries

## [0.5.2] - 2026-04-13
- Improved parameters and documentation thereof

## [0.5.1] - 2026-04-12
- Improved the documentation on new SAM parameters
- Improved post-SAR deployment integration test workflow

## [0.5.0] - 2026-04-12
- **Webhook and Callback Support**: Trigger outbound HTTP calls from mocks to simulate chained or event-driven service interactions via SQS, support for AWS IAM SigV4 on webhooks

## [0.4.2] - 2026-04-06
- **IAM authentication for inbound API**: API Gateway endpoints now support `AuthMode=IAM` (AWS SigV4 request signing) in addition to the existing `AuthMode=API_KEY`

## [0.4.1] - 2026-04-03
- **Improved Documentation**: small documentation fix for SOAP support

## [0.4.0] - 2026-04-03
- **SOAP 1.2 AI Mock Generation**: AI-powered mock generation now supports SOAP APIs, in addition to existing OpenAPI/Swagger and GraphQL support
- **Postman collection and Usage**: Added new examples to Postman collection and usage documentation.

## [0.3.0] - 2026-03-29

### Added

- **GraphQL AI Mock Generation**: AI-powered mock generation now supports GraphQL APIs via schema introspection, in addition to existing OpenAPI/Swagger support
- **SSRF-Safe URL Resolution**: Added SafeUrlResolver for secure URL handling in specification parsers
- **TRACE Method Support**: OpenAPI specification parser now supports the TRACE HTTP method
- **Configurable Log Retention**: New `LogRetentionDays` SAM parameter to configure CloudWatch log retention (default: 7 days)
- **Code of Conduct**: Added Contributor Covenant Code of Conduct

### Changed

- **Lambda Timeout**: Reduced default from 120s to 30s to align with API Gateway synchronous timeout (~29s)
- **Generation Function Timeout**: Removed 300s override; generation function now uses the global `LambdaTimeout` (30s default)
- **BedrockGenerationMaxRetries**: Constrained to 0-2 range (was unconstrained, documented as 0-5) due to API Gateway timeout limitation
- **Documentation**: Updated README, README-SAR, COST, TROUBLESHOOTING, and OpenAPI docs for consistency with new timeout and retry defaults
- **API Gateway Timeout Limitation**: Documented ~29s synchronous timeout as a known limitation for AI generation

## [0.2.7] - 2026-03-25
- Improve ai generation prompt
- Improve sar docs
## [0.2.6] - 2026-03-25
- Enable snapstart with priming
- Add performance docs
- Improve readmes and usage
## [0.2.1] - 2026-03-13
- Documentation improvements and demo video link
  
## [0.2.0] - 2026-03-10

### Added

**Core Runtime Features:**
- Serverless WireMock runtime on AWS Lambda with full WireMock API support
- Persistent mock storage in Amazon S3 across Lambda cold starts and deployments
- Support for REST, SOAP, and GraphQL-over-HTTP API mocking
- Comprehensive request matching (URL, headers, body, query parameters)
- Response templating and transformation capabilities
- JSON and XML body matching with JSONPath and XPath support
- Stateful behavior and scenarios for complex interaction flows
- Request verification and admin API for debugging and testing
- File serving for response bodies with automatic S3 integration
- Callback and webhook simulation capabilities

**AI-Powered Mock Generation:**
- AI-assisted mock generation from OpenAPI/Swagger specifications using Amazon Bedrock
- `/ai/generation/from-spec` endpoint for generating WireMock mappings with instruction text support
- Support for both inline specification content and specification URLs
- Intelligent inference prefix selection with automatic region detection
- Support for Amazon Nova Pro model with fallback strategies
- Namespace organization for multi-team and multi-API scenarios
- Configurable generation options (validation, examples, error cases, realistic data)

**Deployment and Infrastructure:**
- AWS Serverless Application Repository (SAR) publication for one-click deployment
- AWS SAM template with comprehensive parameter configuration
- Automatic region detection using AWS_REGION environment variable
- Intelligent Bedrock inference profile selection (AUTO, GLOBAL_ONLY, GEO_ONLY modes)
- Least-privilege IAM permissions scoped to deployment region
- API Gateway integration with API key authentication
- CloudFormation stack outputs for easy integration

**Health Monitoring:**
- Separate health check endpoints for runtime and AI features
- Runtime health monitoring with S3 connectivity verification
- AI health monitoring with model configuration and invocation status
- Comprehensive health response data including region, storage, and AI status

**Documentation and User Experience:**
- Comprehensive user guide with getting started instructions
- Complete OpenAPI 3.1.1 specification for all endpoints with Swagger Editor compatibility
- SAR-specific README for AWS Console users
- Cost transparency documentation with Free Tier guidance
- Security documentation with IAM permissions and best practices
- Troubleshooting guide with common issues and solutions
- Multi-persona documentation (SAR users, developers, contributors)

### Changed

**Configuration Simplification:**
- Removed misleading AppRegion parameter from SAM template
- Eliminated MOCKNEST_APP_REGION environment variable
- Simplified Bedrock model selection with automatic inference prefix resolution
- Updated default model to AmazonNovaPro with official support indication

**Security Hardening:**
- Removed bedrock:ListFoundationModels IAM permission
- Scoped Bedrock permissions to deployment region only
- Implemented resource-specific IAM policies with documented rationale
- Enhanced API Gateway security with API key enforcement

**Storage Architecture:**
- Removed local filesystem and classpath mock loading support
- Simplified to S3-only storage for consistent serverless behavior
- Eliminated CompositeMappingsSource complexity
- Implemented health check as standard controller endpoint

### Fixed

**Region Configuration:**
- Fixed region parameter confusion by using AWS-provided AWS_REGION
- Resolved Bedrock client region configuration issues
- Corrected S3 client region alignment with deployment region

**AI Model Configuration:**
- Implemented robust model name mapping with reflection-based validation
- Added graceful fallback to AmazonNovaPro for invalid model names
- Fixed inference prefix retry logic with proper error detection
- Added prefix caching for improved performance

### Removed

**Deprecated Configuration:**
- Removed AppRegion parameter from SAM template
- Removed MOCKNEST_APP_REGION environment variable references
- Removed aws.region property from application.properties
- Removed bedrock:ListFoundationModels IAM permission

**Simplified Storage:**
- Removed CompositeMappingsSource and filesystem fallback logic
- Removed classpath resource loading for mappings
- Removed local filesystem paths from configuration

### Security

**IAM Permission Tightening:**
- Scoped all Bedrock permissions to deployment region
- Removed unnecessary foundation model listing permissions
- Implemented least-privilege access controls
- Added comprehensive resource scoping documentation

**Data Protection:**
- Enforced S3 server-side encryption for mock storage
- Implemented HTTPS-only API communication
- Added API key rotation capabilities
- Enhanced audit trail through CloudTrail integration

---

## Version History

### Versioning Strategy

MockNest Serverless follows [Semantic Versioning](https://semver.org/):

- **MAJOR** version for incompatible API changes
- **MINOR** version for backwards-compatible functionality additions  
- **PATCH** version for backwards-compatible bug fixes

### Release Process

1. **Development**: Features developed in feature branches
2. **Testing**: Comprehensive testing in multiple AWS regions (us-east-1, eu-west-1)
3. **Private SAR**: Testing via private SAR deployment before public release
4. **Public Release**: Publication to public AWS Serverless Application Repository
5. **Documentation**: Release notes and migration guides for breaking changes

**Support Policy:**

- **Current Version (0.x)**: Pre-release versions with active development and bug fixes
- **Beta/RC Versions**: Community support only, not recommended for production use
- **Version 1.0+**: Will include full support policy once contract testing is established

### Compatibility

**AWS Services:**
- Requires AWS Lambda, API Gateway, and S3 in deployment region
- Optional Amazon Bedrock for AI features (region availability varies)
- Compatible with all AWS regions supporting core services

**WireMock Compatibility:**
- Based on WireMock 3.x with serverless adaptations
- Maintains API compatibility with standard WireMock admin endpoints
- Custom extensions for S3 persistence and AI integration

**Breaking Changes:**
- Major version releases may include breaking changes to configuration or API
- Migration guides provided for all breaking changes
- Deprecation notices given at least one minor version before removal
