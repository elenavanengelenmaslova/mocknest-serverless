# Design Document: README Show HN Restructure

## Overview

This design describes the structural transformation of the MockNest Serverless `README.md` to optimize it for a Show HN launch. The restructuring prioritizes first-screen impact, progressive disclosure of detail, and honest positioning — following patterns observed in successful Show HN posts.

## Approach

The README restructure is a content reorganization and rewrite task (no code changes). The approach is:

1. **Rewrite the opening** — Replace the abstract tagline with a concrete one-line promise and a brief problem statement
2. **Restructure for progressive disclosure** — Lead with differentiation and quick-start, push reference material lower
3. **Add new sections** — Comparison table, "When not to use this", Show HN title suggestion
4. **Fix existing issues** — Correct the clone URL, adjust auto-update wording
5. **Preserve all existing content** — No information is deleted, only reorganized and condensed where appropriate

## Target README Structure

The final README will follow this section order:

```
1. Title + badges
2. One-line promise (tagline)
3. Problem statement (2-3 sentences, ≤50 words)
4. Action links: [Deploy via SAR] | [Demo Video] | [Postman Collection] | [Docs]
5. Award line (🏆 Creative Track Award)
6. Logo
7. "Why MockNest?" comparison table
8. Quick Start (shortened, 5 steps max, SAR-focused)
9. Features (current features, AI generation flow, generation quality table)
10. Architecture Overview (diagram + brief description)
11. Known Limitations and Best Practices
    - "When not to use this" subsection
    - Performance considerations
    - SOAP/WSDL support notes
    - AI generation timeout
12. Deployment from Source (for developers/contributors)
13. Configuration Reference (full parameter tables)
14. Cost Information
15. Security
16. Troubleshooting
17. Show HN title suggestion (comment or section at bottom)
```

## Key Content Changes

### Opening Section (Lines 1-20 of rendered output)

**Before:**
```markdown
*AI-powered API mocking for cloud-native testing on AWS*

MockNest Serverless is a serverless WireMock-compatible runtime for AWS...
```

**After:**
```markdown
> Deploy WireMock-compatible API mocking into your own AWS account.

Your integration tests need stable external APIs. Those APIs are often unavailable, unreliable, or impossible to configure with test data in non-production environments. MockNest gives you a persistent, serverless mock server running in your own AWS account.

[**Deploy via SAR**](link) | [**Demo Video**](link) | [**Postman Collection**](link) | [**API Docs**](link)

🏆 Creative Track Award — AWS 10,000 AIdeas Competition
```

### Comparison Table

Based on the maintainer's winning article comparison:

| Solution | Delivery | Customer-hosted | Serverless | AI mock generation | IAM auth | Pricing |
|---|---|---|---|---|---|---|
| **MockNest Serverless** | Own AWS account | N/A (already customer-hosted) | ✅ | REST / GraphQL / SOAP | ✅ | Open source |
| WireMock Cloud | Hosted SaaS | Kubernetes + Postgres | ❌ | REST / GraphQL | ❌ | Free tier + paid |
| Mockoon Cloud | Hosted SaaS | CLI / Docker (self-assembly) | ❌ | HTTP / JSON templates | ❌ | Paid + trial |
| Beeceptor | Hosted SaaS | Docker / VMs / Kubernetes | ❌ | REST / GraphQL / SOAP / gRPC | ❌ | Free tier + paid |
| Postman Mock Servers | Hosted SaaS | Local desktop only | ❌ | HTTP example-based | ❌ | Free tier + paid |

### Quick Start (Shortened)

Condensed to 5 steps:
1. Deploy from SAR (link + 1-sentence instruction)
2. Get your API URL and key from CloudFormation outputs
3. Verify health (`curl` one-liner)
4. Create a mock (`curl` POST)
5. Test the mock (`curl` GET)

Advanced SAR parameters, region selection details, and IAM mode instructions move to a "Deployment Options" section lower in the document.

### "When Not to Use This" Section

New subsection within Limitations:
- Sub-millisecond latency requirements (Lambda cold starts add latency)
- gRPC or non-HTTP protocols (not supported)
- Local-only development with no AWS account (use standard WireMock instead)
- Very large request or response payloads over 6 MB (Lambda payload size limit)

### Auto-Update Wording Fix

**Before:** "Automatic updates available"

**After:** "New versions published to SAR — update your deployment when ready"

### Clone Command Fix

**Before:** `git clone <repository-url>`

**After:** `git clone https://github.com/elenavanengelenmaslova/mocknest-serverless.git`

### Show HN Title

Included as a comment at the bottom of the README or in a companion file:

> **Suggested Show HN title:** Show HN: MockNest Serverless – WireMock-compatible API mocking on AWS Lambda

## Correctness Properties

Since this is a documentation restructure (not code), correctness properties focus on structural validation:

1. **Section order property**: The README headings appear in the specified order (title → problem → links → award → comparison → quick start → features → architecture → limitations → deployment → config → cost → security → troubleshooting)

2. **No placeholder property**: The README contains no placeholder text (`<repository-url>`, `<your-...>`, etc.) in code blocks that should have real values

3. **Quick Start brevity property**: The Quick Start section contains exactly 5 numbered steps

4. **Comparison table completeness property**: The comparison table contains rows for all 5 solutions and columns for all 6 dimensions

5. **First-screen density property**: The first 30 lines of content (after badges) contain: the tagline, problem statement, action links, and award line

6. **Honesty section existence property**: A "When not to use this" section exists and mentions at least 3 scenarios

7. **Clone URL correctness property**: All `git clone` commands use `https://github.com/elenavanengelenmaslova/mocknest-serverless.git`

8. **No auto-update implication property**: The README does not contain the phrase "Automatic updates available" or similar wording implying updates happen without user action

## Dependencies

- No code changes required
- No new dependencies
- The README-SAR.md file remains unchanged (it serves SAR-specific users)
- Existing documentation in `docs/` remains unchanged

## Risks and Mitigations

| Risk | Mitigation |
|------|-----------|
| Comparison table becomes outdated as competitors change | Add a note with last-verified date; keep table factual and verifiable |
| Shortened Quick Start loses important context | Link to detailed SAR guide for users who need more |
| "When not to use this" discourages potential users | Frame positively — "Use standard WireMock if..." rather than "Don't use MockNest if..." |
| First-screen content too dense | Test rendering on GitHub to verify readability |
