# Design Document: README Documentation Improvements

## Overview

This design specifies the technical approach for improving the MockNest Serverless documentation structure to provide better user onboarding and comprehensive API usage guidance. The improvements create a clear path from quick deployment to detailed usage, with comprehensive cURL-based documentation that mirrors the existing Postman collection examples.

The design addresses three main documentation files:
- **README.md**: Main repository documentation with quick start and deployment guidance
- **README-SAR.md**: AWS Serverless Application Repository-specific documentation
- **docs/USAGE.md**: New comprehensive cURL-based API usage guide

The solution maintains consistency across all documentation while providing multiple entry points for different user personas (SAR users, developers building from source, CI/CD automation users).

## Architecture

### Documentation System Structure

```
mocknest-serverless/
├── README.md                    # Main entry point with quick start
├── README-SAR.md               # SAR-specific deployment guide
└── docs/
    ├── USAGE.md                # New: Comprehensive cURL usage guide
    ├── postman/
    │   ├── AWS MockNest Serverless.postman_collection.json
    │   └── Mock Nest AWS.postman_environment.json
    └── api/
        └── mocknest-openapi.yaml
```

### Cross-Reference Navigation Strategy

The documentation system uses a hub-and-spoke model:

**Hub**: README.md serves as the main entry point
- Links to README-SAR.md for SAR deployment
- Links to docs/USAGE.md for cURL examples
- Links to docs/postman/ for Postman collections

**Spokes**: Specialized documentation
- README-SAR.md links back to README.md and forward to USAGE.md
- USAGE.md references both Postman collections and OpenAPI spec

All links use relative paths from repository root to ensure portability.

### Content Organization Strategy

**README.md Structure**:
1. Quick Start (5 Minutes) - New section at top
2. Getting Started - Reorganized into subsections
3. Existing content (Features, Architecture, etc.)

**README-SAR.md Structure**:
1. Deployment instructions
2. Getting Your API Details - New section
3. Quick Start (5 Minutes) - Duplicated from README.md
4. Links to additional resources

**USAGE.md Structure**:
1. Table of Contents
2. Introduction and Prerequisites
3. Setup (environment variables)
4. Health Checks
5. Manual Mock Management (SOAP, GraphQL, REST)
6. AI-Assisted Generation
7. Administrative Operations
8. Next Steps

## Components and Interfaces

### Documentation Components

#### 1. Quick Start Section Component
**Purpose**: Provide fastest path to running MockNest (5 minutes)

**Content Elements**:
- API Gateway URL retrieval instructions
- API key retrieval instructions
- Health check cURL command
- Simple mock creation cURL command
- Mock testing cURL command
- Explanatory text for each step

**Placement**: 
- README.md: Top of document, before existing getting started
- README-SAR.md: After deployment instructions

#### 2. Getting Started Section Component
**Purpose**: Organize deployment and usage options

**Subsections**:
- Quick Start (5 Minutes) - Link to quick start section
- Deployment Options - SAR vs building from source
- After Deployment - How to get API URL and key
- Usage Options - Postman vs cURL

**Placement**: README.md only

#### 3. API Access Instructions Component
**Purpose**: Explain how to retrieve API Gateway URL and API key

**Methods Documented**:
- Method 1: Deployment Outputs (URL only) + API Gateway Console (key)
- Method 2: API Gateway Console (both URL and key) - Recommended

**Placement**:
- README-SAR.md: Dedicated "Getting Your API Details" section
- README.md: "After Deployment" subsection

#### 4. cURL Examples Component
**Purpose**: Provide comprehensive command-line API usage examples

**Example Categories**:
- Health checks (admin, AI generation)
- SOAP mock creation and testing
- GraphQL mock creation and testing
- AI-assisted mock generation
- Mock import
- Mock retrieval and management
- Administrative operations

**Placement**: docs/USAGE.md

#### 5. Cross-Reference Links Component
**Purpose**: Enable navigation between documentation files

**Link Types**:
- Forward links (README → specialized docs)
- Backward links (specialized docs → README)
- Lateral links (between specialized docs)

**Placement**: All documentation files

### Data Models

#### Example Data Model
Consistent example data used across all documentation:

```json
{
  "soap_calculator": {
    "operation": "Add",
    "intA": 5,
    "intB": 3,
    "result": 42,
    "mapping_id": "76ada7b0-55ae-4229-91c4-396a36f18123"
  },
  "graphql_pet": {
    "id": "123",
    "name": "Buddy",
    "species": "dog",
    "breed": "Golden Retriever"
  },
  "ai_generation": {
    "api_name": "petstore",
    "spec_url": "https://petstore3.swagger.io/api/v3/openapi.json",
    "pet_count": 3,
    "pets": [
      {
        "id": 1,
        "name": "Buddy",
        "status": "available",
        "tags": [{"id": 1, "name": "new"}],
        "photoUrls": ["https://cdn-fastly.petguide.com/media/2022/02/16/8235403/top-10-funniest-dog-breeds.jpg"]
      },
      {
        "id": 2,
        "name": "Max",
        "status": "available",
        "photoUrls": ["https://example.com/max.jpg"]
      },
      {
        "id": 3,
        "name": "Luna",
        "status": "available",
        "photoUrls": ["https://example.com/luna.jpg"]
      }
    ]
  }
}
```

#### Variable Naming Standards

**API URL Variables**:
- Environment variable: `MOCKNEST_URL` or `AWS_URL`
- Usage in examples: `${MOCKNEST_URL}` or `${AWS_URL}`
- Consistent across all documentation

**API Key Variables**:
- Environment variable: `API_KEY` or `api_key`
- Usage in examples: `${API_KEY}` or `${api_key}`
- Consistent across all documentation

**Deployment Output Names**:
- API Gateway URL: `MockNestApiUrl`
- API Key ID: `MockNestApiKey` (note: this is the ID, not the actual key value)
- Actual key value: Retrieved from API Gateway console

#### cURL Command Template

Standard format for all cURL examples:

```bash
# Description of what this command does
curl -X <METHOD> "${MOCKNEST_URL}/<endpoint>" \
  -H "x-api-key: ${API_KEY}" \
  -H "Content-Type: application/json" \
  -d '<request_body>'
```

**Expected Response**:
```json
{
  "response": "data"
}
```

**Key Parameters**:
- `<parameter>`: Explanation of parameter purpose

### Consistency Standards

#### Terminology Consistency

**Deployment Outputs**:
- `MockNestApiUrl` - The API Gateway URL from deployment outputs
- `MockNestApiKey` - The API key ID from deployment outputs (not the actual key)
- "API key value" - The actual key retrieved from API Gateway console

**Methods**:
- "API Gateway console" (not "AWS console" or "Gateway console")
- "deployment outputs" (not "stack outputs" or "CloudFormation outputs")
- "SAR deployment" (not "Serverless Application Repository deployment")

#### Formatting Consistency

**Markdown Code Blocks**:
- Bash commands: ` ```bash `
- JSON: ` ```json `
- XML: ` ```xml `

**Section Headings**:
- Level 1 (#): Document title only
- Level 2 (##): Major sections
- Level 3 (###): Subsections
- Level 4 (####): Sub-subsections (use sparingly)

**Links**:
- Relative paths from repository root
- Format: `[Link Text](path/to/file.md)`
- Internal section links: `[Link Text](#section-anchor)`

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property Reflection

After analyzing all acceptance criteria, the following properties were identified as testable. Many criteria relate to documentation content quality and structure, which are not amenable to automated testing. The properties below focus on verifiable aspects like file existence, link presence, data consistency, and format compliance.

**Redundancy Analysis**:
- Properties 3.1 and 5.1 both test for link existence but cover different link types (cross-references vs specific files)
- Properties 6.1 and 6.2 test variable naming consistency separately for URL and API key, which is appropriate as they are independent concerns
- Properties 7.1-7.4 test example data alignment for different API types, which cannot be combined as each validates different data structures
- Property 7.5 subsumes individual ID checks across examples by validating all IDs match

No redundant properties were identified that should be removed or consolidated.

### Property 1: Quick Start Section Duplication

*For any* content in the "Getting Started in 5 Minutes" section of README.md, the same content should appear in README-SAR.md to ensure consistent quick start experience across deployment methods.

**Validates: Requirements 3.6**

### Property 2: Relative Path Links

*For all* cross-reference links in the documentation system (README.md, README-SAR.md, USAGE.md), links should use relative paths (not starting with http:// or https://) to ensure portability.

**Validates: Requirements 5.8**

### Property 3: Variable Name Consistency for API URL

*For all* cURL examples across README.md, README-SAR.md, and USAGE.md, the API URL variable name should be consistent (either `MOCKNEST_URL` or `AWS_URL` throughout).

**Validates: Requirements 6.1**

### Property 4: Variable Name Consistency for API Key

*For all* cURL examples across README.md, README-SAR.md, and USAGE.md, the API key variable name should be consistent (either `API_KEY` or `api_key` throughout).

**Validates: Requirements 6.2**

### Property 5: Deployment Output Terminology Consistency

*For all* references to deployment outputs across the documentation system, the terminology should be consistent: `MockNestApiUrl` for the URL, `MockNestApiKey` for the key ID, and "API key value" for the actual key from API Gateway console.

**Validates: Requirements 6.5**

### Property 6: Identical Endpoint Examples

*For any* API endpoint that appears in multiple documentation files, the example request and response should be identical across all occurrences to avoid confusion.

**Validates: Requirements 6.7**

### Property 7: Postman Collection Response Alignment

*For all* examples in USAGE.md that correspond to Postman collection requests, the expected response bodies should match the saved responses in the Postman collection.

**Validates: Requirements 7.6**

### Property 8: Code Block Formatting

*For all* cURL commands and JSON/XML responses in USAGE.md, proper markdown code blocks should be used with appropriate language tags (```bash, ```json, ```xml).

**Validates: Requirements 4.16**

### Property 9: Environment Variable Usage

*For all* examples in the documentation system, API URLs and keys should be referenced using environment variable syntax (${VARIABLE_NAME}) rather than hardcoded values.

**Validates: Requirements 10.3**

### Property 10: Example Data Consistency

*For all* occurrences of the same example scenario (SOAP calculator, GraphQL pet, AI generation) across documentation files, the example data should be identical.

**Validates: Requirements 10.7**

## Error Handling

### Documentation Validation Errors

**Missing Required Sections**:
- Error: Required section not found in documentation file
- Handling: Validation script reports missing sections with file location
- Prevention: Use section checklist during documentation updates

**Broken Links**:
- Error: Link points to non-existent file or section
- Handling: Link checker reports broken links with source and target
- Prevention: Validate all links after documentation changes

**Inconsistent Examples**:
- Error: Same example has different data in different files
- Handling: Consistency checker reports mismatches with file locations
- Prevention: Use shared example data source

**Variable Name Inconsistency**:
- Error: Different variable names used for same purpose
- Handling: Variable checker reports inconsistencies
- Prevention: Define variable naming standards upfront

### Maintenance Errors

**Postman Collection Drift**:
- Error: USAGE.md examples don't match Postman collection
- Handling: Comparison script reports differences
- Prevention: Update both simultaneously, add sync check to CI

**Example Data Drift**:
- Error: Example data in documentation doesn't match actual API behavior
- Handling: Manual review and testing
- Prevention: Regular documentation review cycle

## Testing Strategy

### Dual Testing Approach

This feature requires both automated validation and manual review:

**Automated Validation** (Unit Tests):
- File existence checks (USAGE.md, updated README files)
- Link validation (all cross-references resolve)
- Variable name consistency checks
- Code block format validation
- Example data consistency checks
- Relative path validation

**Manual Review** (Human Validation):
- Content quality and clarity
- Instruction completeness and accuracy
- Example explanations adequacy
- Section organization effectiveness
- User experience flow

### Unit Testing Focus

Unit tests should verify:

**File Structure**:
- USAGE.md exists at docs/USAGE.md
- README.md contains new sections
- README-SAR.md contains new sections

**Link Integrity**:
- All markdown links resolve to existing files or sections
- All links use relative paths
- Cross-references are bidirectional where expected

**Content Presence**:
- Required sections exist (Table of Contents, Prerequisites, Setup, etc.)
- Required cURL examples present
- Required explanatory text present

**Format Compliance**:
- Code blocks use correct language tags
- Variable syntax is consistent
- JSON formatting is valid

**Data Consistency**:
- SOAP calculator example (5+3=42) matches across files
- GraphQL pet example (Buddy) matches across files
- AI generation example (3 pets) matches across files
- Pet data (Buddy, Max, Luna) matches across files
- Mapping IDs match Postman collection

### Property-Based Testing Configuration

Property tests should use a markdown parsing library to validate documentation structure and content. Each test should run a minimum of 100 iterations with different documentation variations.

**Property Test Tags**:
- Feature: readme-documentation-improvements, Property 1: Quick Start Section Duplication
- Feature: readme-documentation-improvements, Property 2: Relative Path Links
- Feature: readme-documentation-improvements, Property 3: Variable Name Consistency for API URL
- Feature: readme-documentation-improvements, Property 4: Variable Name Consistency for API Key
- Feature: readme-documentation-improvements, Property 5: Deployment Output Terminology Consistency
- Feature: readme-documentation-improvements, Property 6: Identical Endpoint Examples
- Feature: readme-documentation-improvements, Property 7: Postman Collection Response Alignment
- Feature: readme-documentation-improvements, Property 8: Code Block Formatting
- Feature: readme-documentation-improvements, Property 9: Environment Variable Usage
- Feature: readme-documentation-improvements, Property 10: Example Data Consistency

### Testing Tools

**Recommended Tools**:
- Markdown parser: CommonMark or similar
- Link checker: markdown-link-check or custom script
- JSON validator: Built-in language JSON parser
- Diff tool: For comparing examples across files

### Validation Script Structure

```bash
#!/bin/bash
# Documentation validation script

# Check file existence
test -f docs/USAGE.md || echo "ERROR: USAGE.md not found"

# Validate links
markdown-link-check README.md README-SAR.md docs/USAGE.md

# Check variable consistency
grep -r '\${MOCKNEST_URL}' . | wc -l  # Should match expected count
grep -r '\${AWS_URL}' . | wc -l       # Should be 0 if MOCKNEST_URL is standard

# Validate example data consistency
# Extract SOAP example from each file and compare
# Extract GraphQL example from each file and compare
# Extract AI generation example from each file and compare

# Check code block formatting
grep -A 1 '```' docs/USAGE.md | grep -E '(bash|json|xml)' | wc -l

# Report results
echo "Validation complete"
```

### Manual Testing Checklist

- [ ] Quick start instructions are clear and complete
- [ ] API Gateway console navigation instructions are accurate
- [ ] cURL commands execute successfully
- [ ] Example responses match actual API behavior
- [ ] Cross-reference links navigate correctly
- [ ] Section organization flows logically
- [ ] Terminology is consistent throughout
- [ ] Code examples are properly formatted
- [ ] Explanatory text is helpful and accurate

## Implementation Notes

### Content Creation Order

1. **Define Example Data**: Establish canonical example data matching Postman collection
2. **Create USAGE.md**: Build comprehensive cURL guide with all examples
3. **Update README.md**: Add quick start section and reorganize getting started
4. **Update README-SAR.md**: Add API access instructions and quick start
5. **Add Cross-References**: Insert all navigation links
6. **Validate Consistency**: Run validation scripts
7. **Manual Review**: Test all instructions and examples

### Example Data Source

Create a reference document (not published) containing canonical example data:

**docs/internal/example-data-reference.md**:
- SOAP calculator example with exact values
- GraphQL pet example with exact values
- AI generation example with exact request and response
- Pet data with exact IDs, names, and attributes
- Mapping IDs from Postman collection

Use this as single source of truth when writing examples.

### Variable Naming Decision

**Recommendation**: Use `MOCKNEST_URL` and `API_KEY` consistently across all documentation.

**Rationale**:
- `MOCKNEST_URL` is more descriptive than `AWS_URL`
- `API_KEY` is simpler than `api_key` and follows common convention
- Consistency is more important than the specific choice

### Link Path Strategy

All links should be relative from repository root:

**From README.md**:
- To README-SAR.md: `[SAR Guide](README-SAR.md)`
- To USAGE.md: `[Usage Guide](docs/USAGE.md)`
- To Postman: `[Postman Collection](docs/postman/AWS MockNest Serverless.postman_collection.json)`

**From README-SAR.md**:
- To README.md: `[Main README](README.md)`
- To USAGE.md: `[Usage Guide](docs/USAGE.md)`

**From docs/USAGE.md**:
- To README.md: `[Main README](../README.md)`
- To Postman: `[Postman Collection](postman/AWS MockNest Serverless.postman_collection.json)`
- To OpenAPI: `[OpenAPI Spec](api/mocknest-openapi.yaml)`

### Section Anchor Strategy

For internal document links, use GitHub-style anchors:

- Convert heading to lowercase
- Replace spaces with hyphens
- Remove special characters

Example: `## Getting Started in 5 Minutes` → `#getting-started-in-5-minutes`

### Maintenance Workflow

**When Postman Collection Changes**:
1. Update example data reference document
2. Update USAGE.md examples to match
3. Update README.md examples if affected
4. Update README-SAR.md examples if affected
5. Run validation scripts
6. Manual testing of changed examples

**When API Changes**:
1. Update OpenAPI specification
2. Update Postman collection
3. Follow Postman collection change workflow above

### Documentation Review Cycle

**Frequency**: Quarterly or with major releases

**Review Checklist**:
- Verify all examples still work
- Check for broken links
- Validate consistency across files
- Update version/date information
- Review for clarity and completeness
- Test quick start instructions end-to-end

## Version Information

**Design Version**: 1.0
**Last Updated**: 2024
**Corresponds to**: MockNest Serverless v0.2.0+

## References

- Requirements Document: `.kiro/specs/readme-documentation-improvements/requirements.md`
- Postman Collection: `docs/postman/AWS MockNest Serverless.postman_collection.json`
- OpenAPI Specification: `docs/api/mocknest-openapi.yaml`
- Current README.md: `README.md`
- Current README-SAR.md: `README-SAR.md`
