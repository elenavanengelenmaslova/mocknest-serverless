# Development Guide

This guide covers the development workflow and practices for MockNest Serverless contributors.

## Quick Start

1. **Setup**: See [BUILDING.md](BUILDING.md) for build prerequisites and instructions
2. **Architecture**: Review our [System Architecture](../.kiro/steering/02-architecture.md) and [AWS Services](../.kiro/steering/03-aws-services.md)
3. **Standards**: Follow our [Code Generation Standards](../.kiro/steering/05-kiro-usage.md#code-generation-standards)

## Development Workflow

For detailed development workflow including module development order, multi-module Gradle workflow, and WireMock integration, see our [Development Workflow](../.kiro/steering/05-kiro-usage.md#development-workflow) in the steering documentation.

## Code Quality Standards

All coding standards, testing requirements, and quality guidelines are documented in our steering files:

- **Code Standards**: [Code Generation Standards](../.kiro/steering/05-kiro-usage.md#code-generation-standards)
- **Testing Strategy**: [Testing Strategy](../.kiro/steering/05-kiro-usage.md#testing-strategy)
- **Code Review Process**: [Code Review Process](../.kiro/steering/05-kiro-usage.md#code-review-process)

## Key Development Practices

### Before Submitting a PR
1. Ensure all tests pass: `./gradlew test`
2. Verify coverage meets 90%: `./gradlew koverVerify`
3. Run full build: `./gradlew build`
4. Follow the commit message guidelines in [CONTRIBUTING.md](../CONTRIBUTING.md)

### Documentation Updates
When making changes that affect architecture or behavior:
- Update relevant steering documents in `.kiro/steering/`
- Follow our [Documentation Practices](../.kiro/steering/05-kiro-usage.md#documentation-practices)
- Keep documentation aligned with actual implementation

## Reference Documentation

For comprehensive development information, see our steering documents:
- [System Architecture](../.kiro/steering/02-architecture.md) - Clean architecture principles and system design
- [AWS Services](../.kiro/steering/03-aws-services.md) - AWS-specific architecture and services
- [Kiro Usage Guidelines](../.kiro/steering/05-kiro-usage.md) - Complete development workflow, standards, and practices

For build instructions, see [BUILDING.md](BUILDING.md).
For contribution guidelines, see [CONTRIBUTING.md](../CONTRIBUTING.md).