# Implementation Plan: README Documentation Improvements

## Overview

This plan implements comprehensive documentation improvements for MockNest Serverless, creating a clear path from quick deployment to detailed usage. The implementation follows the content creation order specified in the design document, starting with canonical example data, then building the comprehensive USAGE.md guide, updating README files, adding cross-references, and finally validating consistency.

## Tasks

- [x] 1. Define canonical example data reference
  - Create internal reference document with exact example data matching Postman collection
  - Document SOAP calculator example (5+3=42, mapping ID: 76ada7b0-55ae-4229-91c4-396a36f18123)
  - Document GraphQL pet example (Buddy, Golden Retriever, id: 123)
  - Document AI generation example (3 pets: Buddy with tag "new", Max, Luna)
  - Document variable naming standards (MOCKNEST_URL, API_KEY)
  - **IMPORTANT**: Only include examples that exist in Postman collection - this is the source of truth
  - _Requirements: 6.1, 6.2, 7.1, 7.2, 7.3, 7.4, 7.5_

- [x] 2. Create comprehensive cURL usage guide (docs/USAGE.md)
  - [x] 2.1 Create USAGE.md file structure with table of contents
    - Add document title and introduction
    - Create table of contents with links to all major sections
    - Add prerequisites section explaining API URL and key requirements
    - _Requirements: 9.1, 9.2, 9.3, 9.4_

  - [x] 2.2 Add setup and environment variable section
    - Document how to set MOCKNEST_URL environment variable
    - Document how to set API_KEY environment variable
    - Include examples for bash/zsh shell configuration
    - _Requirements: 9.5, 10.3_

  - [x] 2.3 Add health check examples
    - Add Admin Health check cURL example with description and expected response
    - Add AI Generation Health check cURL example with description and expected response
    - Include parameter explanations for each example
    - _Requirements: 4.2, 4.3, 4.15_

  - [x] 2.4 Add SOAP mock management examples
    - Add SOAP mapping creation example (calculator 5+3=42)
    - Add SOAP mock testing example
    - Include XML request/response formatting
    - Include parameter explanations
    - _Requirements: 4.4, 4.5, 4.15, 7.1_

  - [x] 2.5 Add GraphQL mock management examples
    - Add GraphQL mapping creation example (Buddy pet)
    - Add GraphQL mock testing example
    - Include JSON request/response formatting
    - Include parameter explanations
    - _Requirements: 4.6, 4.7, 4.15, 7.2_

  - [x] 2.6 Add AI-assisted mock generation examples
    - Add AI generation from OpenAPI spec example (petstore with 3 pets)
    - Add import generated mappings example
    - Add calling generated pet API by status example
    - Add calling generated pet API by tags example
    - Include all parameter explanations
    - **IMPORTANT**: Only include endpoints that exist in Postman collection
    - _Requirements: 4.8, 4.9, 4.10, 4.11, 4.15, 7.3, 7.4_

  - [x] 2.7 Add administrative operations examples
    - Add get all mappings example
    - Add get file content example
    - Add delete all mappings example
    - Include parameter explanations
    - _Requirements: 4.12, 4.13, 4.14, 4.15_

  - [x] 2.8 Add next steps and references section
    - Link to OpenAPI specification
    - Link to Postman collection
    - Link to main README
    - Add note about keeping in sync with Postman collection
    - Add version/last updated information
    - _Requirements: 9.7, 10.1, 10.2, 10.4_

  - [x] 2.9 Ensure proper markdown formatting throughout USAGE.md
    - Use ```bash for cURL commands
    - Use ```json for JSON responses
    - Use ```xml for XML responses
    - Verify consistent section heading levels
    - _Requirements: 4.16, 6.3, 6.6_

- [x] 3. Update README.md with quick start and restructured getting started
  - [x] 3.1 Add "Getting Started in 5 Minutes" section at top
    - Add section before existing getting started content
    - Include step-by-step API Gateway URL retrieval instructions
    - Include step-by-step API key retrieval instructions
    - Recommend API Gateway console as simplest method
    - Add health check cURL command
    - Add simple mock creation cURL command
    - Add mock testing cURL command
    - Include explanatory text for each step
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8_

  - [x] 3.2 Restructure existing getting started section
    - Create "Quick Start (5 Minutes)" subsection linking to new section
    - Create "Deployment Options" subsection (SAR vs building from source)
    - Create "After Deployment" subsection with API access instructions
    - Create "Usage Options" subsection (Postman vs cURL)
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

  - [x] 3.3 Add cross-reference links in README.md
    - Link to Postman collection in docs/postman/
    - Link to USAGE.md for comprehensive cURL examples
    - Link to README-SAR.md for SAR-specific instructions
    - Use relative paths from repository root
    - _Requirements: 2.6, 2.7, 5.1, 5.2, 5.3, 5.8_

- [ ] 4. Update README-SAR.md with API access instructions and quick start
  - [ ] 4.1 Add "Getting Your API Details" section
    - Explain Method 1: Deployment Outputs (URL) + API Gateway Console (key)
    - Explain Method 2: API Gateway Console (both URL and key) - Recommended
    - Provide numbered steps for API Gateway console method
    - Explain MockNestApiKey ID vs actual key value distinction
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 8.1, 8.2, 8.3, 8.4_

  - [ ] 4.2 Duplicate "Getting Started in 5 Minutes" section from README.md
    - Copy exact content from README.md quick start section
    - Place after "Getting Your API Details" section
    - Ensure identical cURL examples and explanations
    - _Requirements: 3.6_

  - [ ] 4.3 Add cross-reference links in README-SAR.md
    - Link to USAGE.md for detailed API usage
    - Link to main README.md for full documentation
    - Use relative paths from repository root
    - _Requirements: 3.7, 5.4, 5.5, 5.8_

- [ ] 5. Validate documentation consistency
  - [ ] 5.1 Remove outdated API examples and verify against Postman collection
    - Remove `/ai/generation/from-description` example from README.md (not in Postman collection)
    - Remove `/ai/generation/jobs/{jobId}/mocks` example from README.md (not in Postman collection)
    - Remove any other examples not present in Postman collection
    - Verify all remaining endpoints exist in Postman collection (source of truth)
    - Cross-check with OpenAPI specification (docs/api/mocknest-openapi.yaml) as secondary validation
    - Ensure only documented endpoints: `/ai/generation/from-spec`, `/ai/generation/health`, `/__admin/*`, and mock endpoints
    - _Requirements: 10.4, 7.6_

  - [ ] 5.2 Verify variable naming consistency
    - Check all cURL examples use MOCKNEST_URL consistently
    - Check all cURL examples use API_KEY consistently
    - Verify no instances of AWS_URL or api_key
    - _Requirements: 6.1, 6.2_

  - [ ] 5.3 Verify example data consistency
    - Confirm SOAP calculator example matches across all files (5+3=42)
    - Confirm GraphQL pet example matches across all files (Buddy)
    - Confirm AI generation example matches across all files (3 pets)
    - Confirm mapping ID 76ada7b0-55ae-4229-91c4-396a36f18123 is consistent
    - _Requirements: 6.7, 7.1, 7.2, 7.3, 7.4, 7.5, 10.7_

  - [ ] 5.4 Verify terminology consistency
    - Check "MockNestApiUrl" used for deployment output URL
    - Check "MockNestApiKey" used for deployment output key ID
    - Check "API key value" used for actual key from console
    - Check "API Gateway console" terminology consistent
    - _Requirements: 6.5_

  - [ ] 5.5 Verify link integrity
    - Test all relative path links resolve correctly
    - Verify all cross-references are bidirectional where expected
    - Check internal section anchors work correctly
    - _Requirements: 5.8_

  - [ ] 5.6 Verify quick start section duplication
    - Confirm "Getting Started in 5 Minutes" content identical in README.md and README-SAR.md
    - Check all cURL commands match exactly
    - Verify explanatory text is identical
    - _Requirements: 3.6_

- [ ] 6. Final review and manual testing
  - Review all documentation for clarity and completeness
  - Verify cURL commands are properly formatted
  - Check JSON/XML formatting and indentation
  - Ensure section organization flows logically
  - Verify all requirements are addressed

## Notes

- This is a documentation-only feature with no code implementation
- All tasks involve creating or modifying markdown files
- **Postman collection is the source of truth** - all examples must match what's in the collection
- Example data must match the Postman collection exactly
- Variable naming (MOCKNEST_URL, API_KEY) must be consistent throughout
- Quick start section must be identical in README.md and README-SAR.md
- All links should use relative paths for portability
- Remove any outdated examples that don't exist in Postman collection
- Validation tasks ensure consistency across all documentation files
