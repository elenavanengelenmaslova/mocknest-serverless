# Requirements Document

## Introduction

Restructure the MockNest Serverless README.md for a Show HN launch. The goal is to make the first screen sharper and reduce friction for Show HN readers who decide in 30 seconds whether to engage. The restructuring draws on analysis of successful Show HN posts (e.g., Floci) and applies best practices for open-source project landing pages: lead with a clear one-line promise, show differentiation early, provide a fast path to try it, and be honest about limitations.

## Glossary

- **README**: The root `README.md` file in the repository, serving as the primary landing page for GitHub visitors
- **Show_HN**: A Hacker News post format where creators present their projects to the community for feedback
- **First_Screen**: The content visible without scrolling on a typical browser viewport (~600px of rendered markdown)
- **SAR**: AWS Serverless Application Repository, a one-click deployment mechanism for AWS applications
- **Comparison_Table**: A markdown table comparing MockNest against alternative approaches on key dimensions
- **Quick_Start**: A condensed set of steps to get MockNest deployed and serving a first mock response
- **Value_Proposition**: A concise explanation of what the project does, why it matters, and how it differs from alternatives

## Requirements

### Requirement 1: Sharper Opening Tagline

**User Story:** As a Show HN reader, I want to immediately understand what MockNest does in one sentence, so that I can decide whether to read further.

#### Acceptance Criteria

1. THE README SHALL open with a one-line promise that states the concrete capability: deploying WireMock-compatible API mocking into the reader's own AWS account
2. WHEN a reader views the README, THE First_Screen SHALL NOT contain abstract or vague taglines such as "AI-powered API mocking for cloud-native testing on AWS"
3. THE README SHALL replace the current tagline with a direct statement such as "Deploy WireMock-compatible API mocking into your own AWS account"

### Requirement 2: First Screen Value Proposition

**User Story:** As a Show HN reader, I want to see what the project is, why it exists, why it is different, and how to try it within the first screen, so that I can evaluate it in under 30 seconds.

#### Acceptance Criteria

1. THE First_Screen SHALL contain four elements: what MockNest is, what problem it solves, why it is different from alternatives, and a path to try it
2. THE README SHALL include action links or buttons (SAR deploy link, demo video, Postman collection, documentation) within the first screen area
3. THE README SHALL display the AWS 10,000 AIdeas Creative Track Award line within the first screen to establish credibility
4. WHEN a reader views the first screen, THE README SHALL communicate the value proposition without requiring scrolling

### Requirement 3: Why MockNest Comparison Table

**User Story:** As a Show HN reader, I want to see how MockNest compares to alternatives at a glance, so that I can understand its differentiation without reading paragraphs of text.

#### Acceptance Criteria

1. THE README SHALL include a "Why MockNest?" comparison table positioned early in the document (before Quick Start)
2. THE Comparison_Table SHALL compare MockNest against named competitors: WireMock Cloud, Mockoon Cloud, Beeceptor, and Postman Mock Servers
3. THE Comparison_Table SHALL include columns covering: Delivery model, Customer-hosted option, Serverless capability, AI mock generation protocol support, IAM auth, and Pricing model
4. THE Comparison_Table SHALL use checkmarks (✅/❌) and concise text to show capability presence or absence for each alternative
5. THE Comparison_Table SHALL position MockNest as the only solution that is open source, serverless, customer-hosted in the reader's own AWS account, and supports IAM authentication

### Requirement 4: Correct Clone Command

**User Story:** As a developer who wants to build from source, I want the clone command to contain the actual repository URL, so that I can copy-paste it without modification.

#### Acceptance Criteria

1. THE README SHALL use the actual repository URL in the git clone command: `git clone https://github.com/elenavanengelenmaslova/mocknest-serverless.git`
2. THE README SHALL NOT contain placeholder text such as `<repository-url>` in any command example

### Requirement 5: Section Reordering for Show HN Audience

**User Story:** As a Show HN reader, I want the most compelling information presented first and detailed reference material later, so that I can progressively discover depth without being overwhelmed.

#### Acceptance Criteria

1. THE README SHALL present sections in the following order: title with one-line promise, short problem explanation, action links (SAR/demo/Postman/docs), award line, "Why MockNest?" comparison table, shortened Quick Start, Features, AI generation quality table, Architecture, Limitations and best practices, Deployment from source, Configuration reference, Cost, Security, and Troubleshooting
2. THE README SHALL move the Architecture Overview section below the Quick Start section
3. THE README SHALL move detailed SAR deployment instructions below the shortened Quick Start
4. WHEN a reader scrolls through the README, THE README SHALL present information in decreasing order of relevance to a first-time evaluator

### Requirement 6: When Not to Use This Section

**User Story:** As a Show HN reader, I want to see honest acknowledgment of limitations and non-ideal use cases, so that I can trust the project's claims and assess fit for my situation.

#### Acceptance Criteria

1. THE README SHALL include a "When not to use this" or equivalent section that describes scenarios where MockNest is not the best choice
2. THE section SHALL appear in the limitations area of the document
3. THE section SHALL cover at least: scenarios requiring sub-millisecond latency, use cases needing non-HTTP protocols (gRPC), and situations where a local-only mock server suffices

### Requirement 7: Shortened Quick Start Near Top

**User Story:** As a Show HN reader, I want a concise Quick Start that gets me to a working mock in minimal steps, so that I can try the project without reading extensive documentation first.

#### Acceptance Criteria

1. THE Quick_Start section near the top SHALL contain no more than 5 steps to go from zero to a working mock response
2. THE Quick_Start SHALL focus on the SAR deployment path as the fastest route
3. THE README SHALL move longer SAR configuration details, parameter explanations, and advanced options to a separate section lower in the document
4. WHEN a reader follows the Quick Start, THE README SHALL enable them to deploy and test a mock within 5 minutes

### Requirement 8: Automatic Updates Wording Adjustment

**User Story:** As a reader evaluating deployment options, I want accurate information about update mechanisms, so that I do not incorrectly assume my deployed application auto-updates without my action.

#### Acceptance Criteria

1. THE README SHALL NOT use wording that implies deployed MockNest instances receive automatic updates without user action
2. WHEN describing SAR deployment updates, THE README SHALL clarify that new versions are published to SAR and users choose when to update their deployment
3. IF the README mentions "Automatic updates available", THEN THE README SHALL rephrase to indicate that new versions are available via SAR for user-initiated updates

### Requirement 9: Show HN Title Suggestion

**User Story:** As the project maintainer preparing a Show HN submission, I want a suggested HN post title included in the README or documentation, so that I have a ready-to-use title optimized for the HN audience.

#### Acceptance Criteria

1. THE README or a companion document SHALL include a suggested Show HN title: "Show HN: MockNest Serverless – WireMock-compatible API mocking on AWS Lambda"
2. THE suggested title SHALL be concise (under 80 characters) and communicate the core value proposition

### Requirement 10: Problem Statement Clarity

**User Story:** As a Show HN reader, I want a brief explanation of the problem MockNest solves immediately after the tagline, so that I understand the motivation before seeing the solution.

#### Acceptance Criteria

1. THE README SHALL include a 2-3 sentence problem statement immediately after the one-line promise
2. THE problem statement SHALL explain why mocking external APIs in cloud environments is difficult (unavailability, unreliability, difficult test data setup)
3. THE problem statement SHALL NOT exceed 50 words
