# Requirements Document

## Introduction

This document specifies requirements for improving the MockNest Serverless README documentation structure to provide better user onboarding and comprehensive API usage guidance. The improvements focus on creating a clear path from quick deployment to detailed usage, with comprehensive cURL-based documentation that mirrors the existing Postman collection examples.

## Glossary

- **README.md**: The main repository documentation file providing project overview, deployment instructions, and usage examples
- **README-SAR.md**: The AWS Serverless Application Repository-specific documentation for users deploying via SAR
- **Postman_Collection**: The JSON file containing all API endpoint examples with request/response samples located at `docs/postman/AWS MockNest Serverless.postman_collection.json`
- **USAGE.md**: A new comprehensive cURL-based API usage guide to be created at `docs/USAGE.md`
- **Quick_Start_Section**: A new "Getting Started in 5 Minutes" section providing the fastest path to running MockNest
- **SAR_Deployment_Outputs**: The outputs displayed on the AWS Serverless Application Repository deployment page after deployment completes, containing MockNestApiUrl and MockNestApiKey ID
- **API_Gateway_Console**: The AWS API Gateway console where users can retrieve the actual API key value using the API key ID
- **Documentation_System**: The collection of README files, usage guides, and cross-references that help users understand and use MockNest Serverless

## Requirements

### Requirement 1: Quick Start Section in README.md

**User Story:** As a new user, I want to get MockNest running in 5 minutes with basic examples, so that I can quickly evaluate whether it meets my needs.

#### Acceptance Criteria

1. THE Documentation_System SHALL include a "Getting Started in 5 Minutes" section at the top of README.md above the current getting started content
2. THE Quick_Start_Section SHALL include step-by-step instructions for obtaining the API Gateway URL from either deployment outputs or API_Gateway_Console
3. THE Quick_Start_Section SHALL include step-by-step instructions for obtaining the API key value from API_Gateway_Console
4. THE Quick_Start_Section SHALL recommend using API Gateway console as the simplest method to get both URL and key in one place
5. THE Quick_Start_Section SHALL include a cURL command for health check verification
6. THE Quick_Start_Section SHALL include a cURL command for creating a simple mock with inline JSON body
7. THE Quick_Start_Section SHALL include a cURL command for testing the created mock
8. THE Quick_Start_Section SHALL include explanatory text describing what each command does

### Requirement 2: Restructured Getting Started Section in README.md

**User Story:** As a user, I want clear, organized getting started information, so that I can choose the right deployment option and understand next steps.

#### Acceptance Criteria

1. THE README.md SHALL reorganize the getting started section into four distinct subsections
2. THE README.md SHALL include a "Quick Start (5 Minutes)" subsection linking to the new quick start section
3. THE README.md SHALL include a "Deployment Options" subsection explaining SAR deployment versus building from source
4. THE README.md SHALL include an "After Deployment" subsection explaining how to retrieve API Gateway URL and API key from API_Gateway_Console (recommended) or from deployment outputs (URL only)
5. THE README.md SHALL include a "Usage Options" subsection explaining Postman collections versus cURL commands
6. THE README.md SHALL link to the Postman_Collection in the docs/postman/ directory
7. THE README.md SHALL link to the new USAGE.md documentation for comprehensive cURL examples

### Requirement 3: Enhanced README-SAR.md with API Access Instructions

**User Story:** As a SAR user, I want clear instructions for getting my API URL and key, so that I can start using MockNest immediately after deployment.

#### Acceptance Criteria

1. THE README-SAR.md SHALL include a "Getting Your API Details" section explaining multiple methods for obtaining API access credentials
2. THE README-SAR.md SHALL explain Method 1 (Deployment Outputs): MockNestApiUrl is available directly in the SAR deployment outputs
3. THE README-SAR.md SHALL explain Method 2 (API Gateway Console): Both URL and API key can be obtained from API_Gateway_Console:
   - Go to API Gateway console
   - Find your API (MockNest Serverless)
   - Get the Invoke URL from the Stages section
   - Get the API key value from the API Keys section
4. THE README-SAR.md SHALL note that the deployment output shows MockNestApiKey ID (not the actual key value), which requires visiting API Gateway console to reveal
5. THE README-SAR.md SHALL recommend using the API Gateway console as the simplest method to get both values in one place
6. THE README-SAR.md SHALL include the same "Getting Started in 5 Minutes" quick start section with cURL examples as README.md
7. THE README-SAR.md SHALL link to USAGE.md for comprehensive cURL documentation

### Requirement 4: Comprehensive cURL Usage Documentation

**User Story:** As a developer, I want comprehensive cURL-based API usage examples, so that I can integrate MockNest into my command-line workflows and CI/CD pipelines without using Postman.

#### Acceptance Criteria

1. THE Documentation_System SHALL include a new file at docs/USAGE.md containing comprehensive cURL-based usage examples
2. THE USAGE.md SHALL include a cURL example for Admin Health check with description, command, and expected response
3. THE USAGE.md SHALL include a cURL example for AI Generation Health check with description, command, and expected response
4. THE USAGE.md SHALL include a cURL example for creating a SOAP mapping (calculator example) with description, command, and expected response
5. THE USAGE.md SHALL include a cURL example for calling the SOAP mock with description, command, and expected response
6. THE USAGE.md SHALL include a cURL example for creating a GraphQL mapping with description, command, and expected response
7. THE USAGE.md SHALL include a cURL example for calling the GraphQL mock with description, command, and expected response
8. THE USAGE.md SHALL include a cURL example for AI-generating mocks from an OpenAPI specification with description, command, and expected response
9. THE USAGE.md SHALL include a cURL example for importing generated mappings with description, command, and expected response
10. THE USAGE.md SHALL include a cURL example for calling generated pet API mocks by status with description, command, and expected response
11. THE USAGE.md SHALL include a cURL example for calling generated pet API mocks by tags with description, command, and expected response
12. THE USAGE.md SHALL include a cURL example for getting all mappings with description, command, and expected response
13. THE USAGE.md SHALL include a cURL example for getting file content with description, command, and expected response
14. THE USAGE.md SHALL include a cURL example for deleting all mappings with description, command, and expected response
15. FOR ALL cURL examples in USAGE.md, THE Documentation_System SHALL include an explanation of key parameters and their purpose
16. THE USAGE.md SHALL use proper formatting with code blocks for cURL commands and JSON responses

### Requirement 5: Cross-Reference Links

**User Story:** As a user, I want clear navigation between documentation files, so that I can easily find related information without searching.

#### Acceptance Criteria

1. THE README.md SHALL include a link to the Postman_Collection files in docs/postman/
2. THE README.md SHALL include a link to docs/USAGE.md for comprehensive cURL examples
3. THE README.md SHALL include a link to README-SAR.md for SAR-specific deployment instructions
4. THE README-SAR.md SHALL include a link to docs/USAGE.md for detailed API usage
5. THE README-SAR.md SHALL include a link to the main README.md for full documentation
6. THE USAGE.md SHALL include a reference to the Postman_Collection as an alternative usage option
7. THE USAGE.md SHALL include a reference to the OpenAPI specification for complete API reference
8. FOR ALL cross-reference links, THE Documentation_System SHALL use relative paths from the repository root

### Requirement 6: Documentation Consistency

**User Story:** As a user, I want consistent terminology and examples across all documentation, so that I can easily understand and follow instructions without confusion.

#### Acceptance Criteria

1. THE Documentation_System SHALL use consistent variable names for API URL (MOCKNEST_URL or AWS_URL) across all examples
2. THE Documentation_System SHALL use consistent variable names for API key (API_KEY or api_key) across all examples
3. THE Documentation_System SHALL use consistent formatting for cURL commands across README.md, README-SAR.md, and USAGE.md
4. THE Documentation_System SHALL use consistent JSON formatting and indentation across all examples
5. THE Documentation_System SHALL use consistent terminology when referring to deployment outputs (MockNestApiUrl for the URL, MockNestApiKey for the key ID, and "API key value" for the actual key retrieved from API Gateway console)
6. THE Documentation_System SHALL use consistent section heading styles across all documentation files
7. WHEN referring to the same API endpoint, THE Documentation_System SHALL use identical example requests and responses across different documentation files

### Requirement 7: Example Data Alignment

**User Story:** As a user, I want documentation examples that match the Postman collection, so that I can verify my setup is working correctly by comparing results.

#### Acceptance Criteria

1. THE USAGE.md SHALL use the same SOAP calculator example (5+3=42) as the Postman_Collection
2. THE USAGE.md SHALL use the same GraphQL pet example (Buddy, Golden Retriever) as the Postman_Collection
3. THE USAGE.md SHALL use the same AI generation example (petstore API with 3 pets) as the Postman_Collection
4. THE USAGE.md SHALL use the same pet data (Buddy with new tag, Max, Luna) as the Postman_Collection
5. THE USAGE.md SHALL use the same mapping IDs and file names as shown in the Postman_Collection responses
6. FOR ALL examples in USAGE.md, THE Documentation_System SHALL include expected response bodies that match the Postman_Collection saved responses
7. WHEN the Postman_Collection is updated, THE Documentation_System SHALL maintain alignment between cURL examples and Postman examples

### Requirement 8: API Access Instructions

**User Story:** As a user, I want clear, step-by-step instructions for getting my API URL and key, so that I can quickly start using MockNest.

#### Acceptance Criteria

1. THE README-SAR.md SHALL present two methods for obtaining API credentials: deployment outputs + API Gateway console, or API Gateway console only
2. THE README-SAR.md SHALL recommend the API Gateway console method as the simplest approach to get both URL and key in one place
3. THE README-SAR.md SHALL provide numbered steps for the API Gateway console method:
   - Navigate to API Gateway console
   - Find the MockNest Serverless API
   - Copy the Invoke URL from the Stages section (append the stage name if needed)
   - Navigate to API Keys section
   - Find the API key and click "Show" to reveal the value
4. THE README-SAR.md SHALL explain the deployment outputs method as an alternative for getting the URL
5. THE README.md SHALL include similar instructions in the "After Deployment" subsection for SAM CLI users
6. THE README.md SHALL explain that SAM CLI users can use either deployment outputs or API Gateway console
7. THE Documentation_System SHALL include visual descriptions or screenshots to help users navigate the API Gateway console

### Requirement 9: Usage Documentation Structure

**User Story:** As a developer, I want well-organized usage documentation, so that I can quickly find the specific API operation I need to use.

#### Acceptance Criteria

1. THE USAGE.md SHALL organize examples into logical sections: Setup, Health Checks, Manual Mock Management, AI-Assisted Generation, and Administrative Operations
2. THE USAGE.md SHALL include a table of contents at the beginning linking to each major section
3. THE USAGE.md SHALL include an introduction explaining the purpose of the document and prerequisites
4. THE USAGE.md SHALL include a "Prerequisites" section explaining that users need the API URL and API key value, both obtainable from API_Gateway_Console
5. THE USAGE.md SHALL include a "Setup" section showing how to set environment variables for the API URL and key
6. FOR ALL example sections, THE USAGE.md SHALL follow a consistent format: description, cURL command, expected response, parameter explanation
7. THE USAGE.md SHALL include a "Next Steps" section at the end linking to additional resources (OpenAPI spec, Postman collection, main README)

### Requirement 10: Documentation Maintenance

**User Story:** As a maintainer, I want documentation that is easy to keep synchronized with code changes, so that documentation remains accurate as the API evolves.

#### Acceptance Criteria

1. THE USAGE.md SHALL include a note at the top indicating it should be kept in sync with the Postman_Collection
2. THE USAGE.md SHALL include version information or last updated date
3. THE Documentation_System SHALL use environment variables (${MOCKNEST_URL}, ${API_KEY}) in examples to avoid hardcoding specific values
4. THE Documentation_System SHALL reference the OpenAPI specification (docs/api/mocknest-openapi.yaml) as the authoritative API contract
5. WHEN API endpoints change, THE Documentation_System SHALL require updates to README.md, README-SAR.md, USAGE.md, and Postman_Collection
6. THE Documentation_System SHALL include comments in USAGE.md indicating which Postman collection request each example corresponds to
7. THE Documentation_System SHALL use consistent example data that can be easily updated in one place if needed
