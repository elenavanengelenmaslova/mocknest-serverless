# Implementation Tasks: README Show HN Restructure

## Task 1: Rewrite the Opening Section

- [x] 1.1 Replace the current italic tagline `*AI-powered API mocking for cloud-native testing on AWS*` with a blockquote one-line promise: `> Deploy WireMock-compatible API mocking into your own AWS account.`
- [x] 1.2 Replace the current introductory paragraph with a concise problem statement (≤50 words) explaining why mocking external APIs in cloud environments is difficult
- [x] 1.3 Add action links row immediately after the problem statement: `[**Deploy via SAR**](SAR-link) | [**Demo Video**](youtube-link) | [**Postman Collection**](postman-link) | [**API Docs**](openapi-link)`
- [x] 1.4 Move the 🏆 Award line to appear directly after the action links (before the logo)
- [x] 1.5 Verify the first 30 lines after badges contain: tagline, problem statement, action links, and award line

## Task 2: Add the "Why MockNest?" Comparison Table

- [x] 2.1 Add a `## Why MockNest?` section after the logo and before Quick Start
- [x] 2.2 Create the comparison table with columns: Solution, Delivery, Customer-hosted, Serverless, AI mock generation, IAM auth, Pricing
- [x] 2.3 Populate rows for: MockNest Serverless, WireMock Cloud, Mockoon Cloud, Beeceptor, Postman Mock Servers (using data from the winning article)
- [x] 2.4 Verify table renders correctly in GitHub markdown preview

## Task 3: Shorten and Reposition Quick Start

- [x] 3.1 Condense the Quick Start to exactly 5 steps: (1) Deploy from SAR, (2) Get API URL/key from CloudFormation, (3) Verify health, (4) Create a mock, (5) Test the mock
- [ ] 3.2 Remove detailed SAR parameter explanations, region selection guidance, and IAM mode details from the Quick Start section
- [ ] 3.3 Add a link at the end of Quick Start pointing to the detailed deployment section lower in the document
- [ ] 3.4 Position the Quick Start section immediately after the comparison table

## Task 4: Reorganize Remaining Sections

- [ ] 4.1 Move the Features section (current features, AI generation flow, Koog agent strategy, generation quality table) to appear after Quick Start
- [ ] 4.2 Move Architecture Overview to appear after Features (below its current position)
- [ ] 4.3 Consolidate "Known Limitations and Best Practices" section to appear after Architecture
- [ ] 4.4 Move "Deployment for Developers" (build from source) section below Limitations
- [ ] 4.5 Move Configuration Reference below Deployment for Developers
- [ ] 4.6 Move Cost Information below Configuration Reference
- [ ] 4.7 Ensure Security and Troubleshooting remain at the bottom
- [ ] 4.8 Remove the duplicate "Quick Start for SAR Users" section (content now consolidated into the shortened Quick Start and a lower "Deployment Options" section)
- [ ] 4.9 Remove the "Deployment Options" subsection that currently appears mid-document and consolidate its content into the "Deployment for Developers" section lower down

## Task 5: Add "When Not to Use This" Section

- [ ] 5.1 Add a `### When Not to Use MockNest` subsection within the Limitations section
- [ ] 5.2 Include at least these scenarios: sub-millisecond latency requirements, gRPC or non-HTTP protocols, local-only development without AWS account, very large request or response payloads over 6 MB
- [ ] 5.3 Frame each scenario positively (e.g., "Use standard WireMock if you only need local mocking")

## Task 6: Fix Clone Command and Auto-Update Wording

- [x] 6.1 Replace `git clone <repository-url>` with `git clone https://github.com/elenavanengelenmaslova/mocknest-serverless.git`
- [ ] 6.2 Search the entire README for any remaining placeholder text in code blocks (`<repository-url>`, `<your-...>`) and replace with actual values
- [x] 6.3 Replace "Automatic updates available" wording with "New versions published to SAR — update your deployment when ready" (or similar phrasing that clarifies user-initiated updates)

## Task 7: Add Show HN Title Suggestion

- [ ] 7.1 Add a brief section or HTML comment at the bottom of the README with the suggested Show HN title: "Show HN: MockNest Serverless – WireMock-compatible API mocking on AWS Lambda"
- [ ] 7.2 Verify the title is under 80 characters

## Task 8: Final Review and Validation

- [ ] 8.1 Verify section order matches the design specification: title → tagline → problem → links → award → logo → comparison table → Quick Start → Features → Architecture → Limitations → Deployment from source → Configuration → Cost → Security → Troubleshooting
- [ ] 8.2 Verify no placeholder text remains in any code block
- [ ] 8.3 Verify the comparison table has 5 solution rows and 6 dimension columns
- [ ] 8.4 Verify Quick Start has exactly 5 numbered steps
- [ ] 8.5 Verify "When Not to Use MockNest" section exists with at least 3 scenarios
- [ ] 8.6 Verify all `git clone` commands use the actual repository URL
- [ ] 8.7 Verify no wording implies automatic updates without user action
- [ ] 8.8 Verify the README renders correctly on GitHub (check markdown formatting, table alignment, link validity)
