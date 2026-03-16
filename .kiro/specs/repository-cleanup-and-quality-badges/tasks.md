# Implementation Plan: Documentation Consistency and Quality Badges

## Overview

This implementation plan ensures documentation consistency across the MockNest Serverless repository, eliminates content repetition, verifies docs/ folder accuracy, and adds Codecov integration for code coverage badges. Future planned features are preserved as-is; the focus is on consistency, not removal.

## Tasks

- [x] 1. Phase 1: Documentation Review (Identification Only - No Changes)
  - [x] 1.1 Review README.md for issues
    - List any duplicate content within README.md
    - List inconsistent terminology
    - List outdated sections
    - List contradictory statements
    - **Output: List of issues found (do not fix yet)**
    - _Requirements: 1.1, 1.5_

  - [x] 1.2 Review README-SAR.md for issues
    - List inconsistencies with main README.md
    - List duplicate content between README.md and README-SAR.md
    - List inaccurate SAR-specific content
    - List terminology mismatches
    - **Output: List of issues found (do not fix yet)**
    - _Requirements: 1.1_

  - [x] 1.3 Review docs/ folder markdown files
    - Review `docs/COST.md` - list any accuracy or consistency issues
    - Review `docs/AIDEAS_COMPETITION_ARTICLE.md` - list consistency issues
    - Review `docs/BUILDING.md` - list outdated instructions
    - Review `docs/DEVELOPMENT.md` - list accuracy issues
    - Review `docs/SAR_PUBLISHING.md` - list process accuracy issues
    - Check for any other .md files in docs/ - list issues found
    - **Output: List of issues found per file (do not fix yet)**
    - _Requirements: 6.1, 6.2, 6.3_

  - [x] 1.4 Review steering documents for issues
    - Check `.kiro/steering/00-vision.md` - list messaging inconsistencies
    - Check `.kiro/steering/01-scope-and-non-goals.md` - list accuracy issues
    - Check `.kiro/steering/02-architecture.md` - list consistency issues
    - Check `.kiro/steering/04-market-impact.md` - list positioning inconsistencies
    - Check `.kiro/steering/05-kiro-usage.md` - list workflow accuracy issues
    - **Output: List of issues found per file (do not fix yet)**
    - _Requirements: 1.3, 1.4, 4.1, 4.2, 4.3, 4.4, 7.1, 7.2, 7.3, 7.4_

  - [x] 1.5 Review API documentation for issues
    - Review `docs/api/mocknest-openapi.yaml` - list accuracy issues
    - List API examples that don't match current implementation
    - List inconsistent endpoint descriptions
    - **Output: List of issues found (do not fix yet)**
    - _Requirements: 5.1, 5.2, 5.3, 5.4_

  - [x] 1.6 Review deployment and configuration files
    - Review `deployment/aws/sam/template.yaml` - list consistency issues
    - Check `.github/workflows/*.yml` - list description mismatches
    - Check `software/application/src/main/resources/prompts/` - list accuracy issues
    - **Output: List of issues found (do not fix yet)**
    - _Requirements: 4.4, 5.5_

  - [x] 1.7 Compile comprehensive findings report
    - Consolidate all issues found in tasks 1.1-1.6
    - Categorize by severity (critical, important, minor)
    - Categorize by type (duplication, inconsistency, outdated, contradiction)
    - Create prioritized list for Phase 3
    - **Output: Comprehensive findings document for user review**
    - _Requirements: All Phase 1 requirements_

- [ ] 2. Checkpoint - Present findings to user
  - Present comprehensive findings report from task 1.7
  - Get user approval and guidance on which issues to fix
  - Clarify any unclear items before proceeding

- [ ] 3. Phase 2: Codecov Integration and Badge Setup
  - [x] 3.1 Sign up for Codecov
    - Go to https://codecov.io
    - Sign in with GitHub account
    - Authorize Codecov for the mocknest-serverless repository
    - Note the repository token (if needed for private repos)
    - _Requirements: 3.4_

  - [x] 3.2 Update GitHub Actions workflow for Codecov
    - Identify the main build workflow (likely `.github/workflows/main-aws.yml`)
    - Add Codecov upload step after test execution
    - Configure to upload Kover XML coverage report
    - Example step:
      ```yaml
      - name: Upload coverage to Codecov
        uses: codecov/codecov-action@v4
        with:
          files: ./build/reports/kover/report.xml
          fail_ci_if_error: false
      ```
    - _Requirements: 3.4_

  - [ ] 3.3 Verify Kover generates XML report
    - Check that `build.gradle.kts` or Kover configuration generates XML format
    - Ensure XML report is generated in expected location
    - Update Kover config if needed to output XML
    - _Requirements: 3.4_

  - [ ] 3.4 Test Codecov integration
    - Push changes to a test branch
    - Verify GitHub Actions runs successfully
    - Verify coverage report uploads to Codecov
    - Check Codecov dashboard shows coverage data
    - _Requirements: 3.4, 3.5_

  - [ ] 3.5 Add Codecov badge to README.md
    - Get badge markdown from Codecov dashboard
    - Add badge to README.md with other badges at top
    - Badge format: `[![codecov](https://codecov.io/gh/elenavanengelenmaslova/mocknest-serverless/branch/main/graph/badge.svg)](https://codecov.io/gh/elenavanengelenmaslova/mocknest-serverless)`
    - Position after existing badges (License, Kotlin, JVM, Build Status)
    - _Requirements: 3.4_

  - [ ] 3.6 Verify all badges are functional
    - Test MIT license badge displays correctly
    - Test Kotlin version badge displays correctly
    - Test JVM version badge displays correctly
    - Test Build Status badge displays correctly
    - Test new Codecov badge displays correctly
    - Verify all badge links work
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

  - [ ]* 3.7 Write property test for badge functionality
    - **Property 1: Badge Functionality**
    - **Validates: Requirements 3.5**

- [ ] 4. Checkpoint - Verify Codecov integration
  - Ensure Codecov is uploading coverage successfully
  - Ensure badge displays current coverage
  - Ask user if questions arise

- [ ] 5. Phase 3: Fix Documentation Issues (Based on Phase 1 Findings)
  - [ ] 5.1 Fix README.md issues
    - Address issues identified in task 1.1
    - Remove duplicate content
    - Fix inconsistent terminology
    - Update outdated sections
    - Resolve contradictions
    - _Requirements: 1.1, 1.5_

  - [ ] 5.2 Fix README-SAR.md issues
    - Address issues identified in task 1.2
    - Align with main README.md
    - Remove duplicate content
    - Update terminology for consistency
    - _Requirements: 1.1_

  - [ ] 5.3 Fix docs/ folder issues
    - Address issues identified in task 1.3
    - Update `docs/COST.md` as needed
    - Update `docs/AIDEAS_COMPETITION_ARTICLE.md` as needed
    - Update `docs/BUILDING.md` as needed
    - Update `docs/DEVELOPMENT.md` as needed
    - Update `docs/SAR_PUBLISHING.md` as needed
    - Fix any other docs/ .md files as needed
    - _Requirements: 6.1, 6.2, 6.3_

  - [ ] 5.4 Fix steering document issues
    - Address issues identified in task 1.4
    - Update `.kiro/steering/00-vision.md` as needed
    - Update `.kiro/steering/01-scope-and-non-goals.md` as needed
    - Update `.kiro/steering/02-architecture.md` as needed
    - Update `.kiro/steering/04-market-impact.md` as needed
    - Update `.kiro/steering/05-kiro-usage.md` as needed
    - _Requirements: 1.3, 1.4, 4.1, 4.2, 4.3, 4.4, 7.1, 7.2, 7.3, 7.4_

  - [ ] 5.5 Fix API documentation issues
    - Address issues identified in task 1.5
    - Update `docs/api/mocknest-openapi.yaml` as needed
    - Fix API examples to match current implementation
    - Update endpoint descriptions for consistency
    - _Requirements: 5.1, 5.2, 5.3, 5.4_

  - [ ] 5.6 Fix deployment and configuration file issues
    - Address issues identified in task 1.6
    - Update `deployment/aws/sam/template.yaml` as needed
    - Update `.github/workflows/*.yml` descriptions as needed
    - Update `software/application/src/main/resources/prompts/` as needed
    - _Requirements: 4.4, 5.5_

  - [ ]* 5.7 Write property test for documentation consistency
    - **Property 2: Documentation Consistency**
    - **Validates: Requirements 1.4, 4.4, 6.2, 6.3**

  - [ ]* 5.8 Write property test for API specification accuracy
    - **Property 3: API Specification Accuracy**
    - **Validates: Requirements 5.1, 5.2, 5.3**

- [ ] 6. Final Validation
  - [ ] 6.1 Perform comprehensive consistency check
    - Scan all documentation for cross-file consistency
    - Verify terminology is consistent across all files
    - Check that no duplicate content remains
    - Validate all badges are functional
    - Verify all Phase 1 issues have been addressed
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

  - [ ]* 6.2 Write property test for content preservation
    - **Property 4: Content Preservation**
    - **Validates: Requirements 2.5, 7.2, 7.3, 7.4, 7.5**

  - [ ]* 6.3 Create validation script for ongoing consistency
    - Write Kotlin script to check for documentation consistency
    - Implement badge functionality checker
    - Create automated consistency validation
    - _Requirements: All requirements for ongoing maintenance_

- [ ] 7. Final checkpoint - Ensure all tests pass and documentation is consistent
  - Ensure all tests pass
  - Verify Codecov integration is working
  - Verify all documentation issues are resolved
  - Ask user if questions arise

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation at each phase
- Property tests validate universal correctness properties across all repository files
- Future planned features are preserved as-is - no need to mark or remove them
- Focus is on consistency, eliminating repetition, and ensuring accuracy
- Codecov is free for open source projects
- Phase 1 is identification only - no changes made until Phase 3
- Phase 2 implements Codecov integration independently
- Phase 3 fixes documentation based on user-approved Phase 1 findings

## Implementation Strategy

### Phase 1 Focus (Identification Only)
Comprehensive review of all documentation to identify inconsistencies, repetition, and outdated content. **No changes made** - only identification and reporting. User reviews findings before any changes.

### Phase 2 Focus (Codecov Integration)
Set up Codecov integration, configure GitHub Actions to upload coverage reports, and add coverage badge to README. This is independent of documentation fixes and can proceed in parallel.

### Phase 3 Focus (Documentation Fixes)
Fix all user-approved issues from Phase 1. Update documentation for consistency, remove duplication, and ensure accuracy based on the prioritized findings report.

### Validation Approach
Each phase includes validation checkpoints to ensure changes meet requirements before proceeding. Property tests provide automated verification of universal correctness properties.
