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

## Code Standards

### Kotlin Conventions
- Follow the official [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful names for classes, functions, and variables
- Prefer immutable data structures where possible

### Project-Specific Standards
For detailed MockNest Serverless guidelines, see our steering documentation:
- **Architecture**: [System Architecture](.kiro/steering/02-architecture.md) and [AWS Services](.kiro/steering/03-aws-services.md)
- **Code Quality & Standards**: [Code Generation Standards](.kiro/steering/05-kiro-usage.md#code-generation-standards)
- **Testing Strategy**: [Testing Strategy](.kiro/steering/05-kiro-usage.md#testing-strategy)
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