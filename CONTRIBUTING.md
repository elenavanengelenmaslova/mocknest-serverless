# Contributing to MockNest Serverless

Thank you for your interest in contributing to MockNest Serverless! This document provides guidelines for contributing to the project.

## Getting Started

Before you begin contributing, please:

1. Review the [Contributor Covenant Code of Conduct](https://www.contributor-covenant.org/version/2/1/code_of_conduct/) that governs this project
2. Check the [existing issues](https://github.com/elenavanengelenmaslova/mocknest-serverless/issues) to see if your bug report or feature request already exists
3. Review our [Development Guide](docs/DEVELOPMENT.md) for technical setup instructions

## How to Contribute

### Reporting Bugs

Before creating a bug report, please check existing issues to avoid duplicates. When creating a bug report, include:

- A clear and descriptive title
- Steps to reproduce the issue
- Expected behavior vs actual behavior
- Your environment (OS, Java version, AWS region, etc.)
- Relevant logs or error messages

### Suggesting Features

Feature requests are welcome! Please:

- Check if the feature aligns with our [project scope](.kiro/steering/01-scope-and-non-goals.md)
- Provide a clear description of the feature and its use case
- Explain why this feature would be useful to MockNest Serverless users

### Pull Requests

1. **Fork the repository** and create your branch from `main`
2. **Follow our development setup** in [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)
3. **Make your changes** following our [coding standards](.kiro/steering/05-kiro-usage.md#code-generation-standards) and [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
4. **Add tests** for any new functionality
5. **Ensure all tests pass** and coverage remains at 90%+
6. **Update documentation** if needed following our [documentation practices](.kiro/steering/05-kiro-usage.md#documentation-practices)
7. **Submit a pull request**

#### Pull Request Guidelines

- Use a clear and descriptive title
- Reference any related issues in the description
- Include a summary of changes and motivation
- Ensure CI checks pass (build, tests, coverage)
- Keep changes focused and atomic

## Development Setup

For complete development setup instructions, see our [Development Guide](docs/DEVELOPMENT.md) and [Building Guide](docs/BUILDING.md).

## CI/CD Pipelines

MockNest Serverless uses GitHub Actions for automated testing, deployment, and publishing. Understanding these pipelines helps you know what checks your contributions will go through.

### Pipeline Overview

| Pipeline | Trigger | Purpose | What It Does |
|----------|---------|---------|--------------|
| **Feature Branch - AWS Build and Deploy** | Push to `feature/*`, `bugfix/*`, `dependabot/**` branches or PR to `main` | Validate feature branches | Runs tests, verifies coverage, optionally deploys to AWS for testing |
| **Main Branch AWS Deployment** | Push to `main` | Deploy to staging | Runs tests, verifies coverage, deploys to staging environment in AWS |
| **SAR Publish** | Release published or manual trigger | Publish to AWS SAR | Builds application, packages with SAM, publishes to AWS Serverless Application Repository |
| **OpenSSF Scorecard** | Weekly schedule, push to `main`, or manual | Security analysis | Runs OpenSSF security scorecard analysis and uploads results to GitHub Security |
| **Validate Gradle Wrapper** | Push to `main` or PR | Security check | Validates Gradle wrapper JAR hasn't been tampered with |

### Reusable Workflows

These workflows are called by the main pipelines above:

| Workflow | Purpose | Used By |
|----------|---------|---------|
| **workflow-build.yml** | Build and test all modules, verify 90% coverage, upload to Codecov | Feature and Main branch pipelines |
| **workflow-deploy-aws.yml** | Deploy to AWS using SAM | Feature and Main branch pipelines |
| **workflow-sar-publish.yml** | Package and publish to AWS Serverless Application Repository | SAR Publish pipeline |

### What Happens When You Submit a PR

1. **Gradle Wrapper Validation** - Ensures wrapper hasn't been tampered with
2. **Build & Test** - Compiles all modules and runs unit + integration tests
3. **Coverage Check** - Verifies 90% code coverage threshold is met
4. **Optional AWS Deploy** - If code changes affect `software/` or `deployment/`, deploys to test environment

### What Happens When Code Merges to Main

1. **Build & Test** - Same as PR checks
2. **Coverage Upload** - Uploads coverage reports to Codecov
3. **Deploy to Staging** - If code changes affect `software/` or `deployment/`, deploys to staging AWS environment

### SAR Publishing Process

For maintainers publishing to AWS Serverless Application Repository:

1. **Trigger**: Create a GitHub release or manually trigger the workflow
2. **Build**: Compiles application and creates unified JAR
3. **Package**: Uses SAM to package application and upload to S3
4. **Publish**: Publishes to SAR with semantic version
5. **Verify**: Check SAR console to confirm application is available

See [SAR Publishing Guide](docs/SAR_PUBLISHING.md) for detailed instructions.

### Pipeline Configuration

All pipelines use:
- **Java 25** (Temurin distribution)
- **Gradle 9.0.0** (via wrapper)
- **AWS OIDC authentication** (no long-lived credentials)
- **Kover** for code coverage reporting

### Troubleshooting Pipeline Failures

**Build Failures**:
- Check Java version compatibility
- Verify all dependencies are available
- Review build logs for compilation errors

**Test Failures**:
- Run tests locally: `./gradlew test`
- Check for environment-specific issues (Docker required for integration tests)
- Review test logs in GitHub Actions artifacts

**Coverage Failures**:
- Generate local coverage report: `./gradlew koverHtmlReport`
- Identify modules below 90% threshold
- Add tests to increase coverage

**Deployment Failures**:
- Verify AWS credentials and permissions
- Check SAM template syntax
- Review CloudFormation events in AWS Console

## Code Standards

### Kotlin Conventions
- Follow the official [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful names for classes, functions, and variables
- Prefer immutable data structures where possible

### Project-Specific Standards
For detailed MockNest Serverless guidelines, see our steering documentation:
- **Architecture**: [System Architecture](.kiro/steering/structure.md)
- **Code Quality & Standards**: [Code Generation Standards](.kiro/steering/tech.md#code-generation-standards)
- **Testing Strategy**: [Testing Strategy](.kiro/steering/tech.md#testing-strategy)
- **Development Workflow**: [Development Workflow](.kiro/steering/05-kiro-usage.md#development-workflow)
- **Project Structure**: [Project Structure](.kiro/steering/05-kiro-usage.md#project-structure)

## Commit Messages

Follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:

- Use semantic prefixes: `feat:`, `fix:`, `docs:`, `style:`, `refactor:`, `test:`, `chore:`
- Use present tense and imperative mood
- Limit first line to 72 characters
- Reference issues when applicable

Examples:
```
feat: add S3 persistence for WireMock mappings

fix: resolve coverage reporting in CI pipeline (#123)

docs: update API documentation for new endpoints

test: add integration tests for AI mock generation
```

For detailed guidelines, see the [Conventional Commits specification](https://www.conventionalcommits.org/).

## Release Process

Releases are managed by maintainers:
1. Feature branches are merged to `main` after review
2. `main` branch automatically deploys to staging
3. Releases are tagged and published to AWS SAR

## Questions?

- Check our [documentation](.kiro/steering/) for architecture and design decisions
- Open an issue for questions about contributing
- Review existing issues and discussions

## License

By contributing, you agree that your contributions will be licensed under the same license as the project.