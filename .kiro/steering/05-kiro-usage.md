# Kiro Usage Guidelines

## Project Structure

The project structure follows clean architecture principles as outlined in the architecture document, with clear separation between domain, application, and infrastructure layers:

```
mocknest-serverless/
│
├── build.gradle.kts     // Root build file
├── settings.gradle.kts  // Contains include statements for subprojects
│
├── .github/             // GitHub-specific configuration
│   └── workflows/       // GitHub Actions workflow definitions
│       ├── feature-aws.yml                  // Triggers AWS deployment for feature branches
│       ├── main-aws.yml                     // Triggers AWS deployment for main branch
│       └── workflow-build-deploy-aws.yml    // Reusable workflow for AWS Lambda build and deployment
│
├── docs/               // Documentation files
│   └── postman/                 // Postman collections and environments
│       ├── AWS MockNest Serverless.postman_collection.json    // Collection for AWS app
│       ├── Health Checks.postman_collection.json             // Collection for health checks
│       └── Demo Example.postman_environment.json             // Environment variables
│
├── software/            // Holds all the business logic and application code
│   ├── domain/
│   │   ├── src/
│   │   └── build.gradle.kts
│   ├── application/
│   │   ├── src/
│   │   └── build.gradle.kts
│   └── infra/            // Infrastructure specific code
│       └── aws/          // AWS-specific code, including AWS Lambda
│           ├── src/
│           └── build.gradle.kts
│
└── deployment/           // Cloud deployment configurations
    └── aws/              // AWS-specific deployment
        ├── template.yaml         // SAM template for infrastructure as code
        ├── samconfig.toml       // SAM configuration for different environments
        └── scripts/             // Deployment and build scripts
            ├── build.sh         // Build script for SAM deployment
            └── deploy.sh        // Deployment script for SAR publishing
```

## Development Workflow

### Module Development Order
Follow clean architecture principles by developing in this sequence:
1. **Domain Layer** (`:domain`) - Start with business models, entities, and domain rules
2. **Application Layer** (`:application`) - Implement use cases, WireMock orchestration, and service interfaces
3. **Infrastructure Layer** (`:infra-aws`) - Add AWS-specific adapters and implementations
4. **Deployment Configuration** (`deployment/aws/`) - Update SAM templates and deployment scripts

### Multi-module Gradle Workflow
- Treat the repository as a clean-architecture, multi-module Kotlin project: domain models live in `:domain`, application-layer WireMock orchestration in `:application`, and cloud-specific adapters in `:infra-aws`
- Keep new code aligned with that separation and register new modules in `settings.gradle.kts` if needed
- Ensure dependencies flow correctly: infra → application → domain (never the reverse)

### WireMock Integration Workflow
- When wiring new features, plug them into the existing WireMock server configuration that boots with custom stores and extensions (normalization, delete-all) so serverless deployments keep mappings and files in object storage
- Extend WireMock filters and transformers rather than bypassing the core engine
- Always validate that new mock behaviors work with the object storage persistence layer

### SAM Template Management
- Update `deployment/aws/template.yaml` when adding new Lambda functions or API Gateway endpoints
- Modify `deployment/aws/samconfig.toml` for environment-specific configurations
- Use the build and deploy scripts in `deployment/aws/scripts/` for consistent deployments
- Test SAM template changes locally before committing

### GitHub Actions Integration
- Feature branches trigger `feature-aws.yml` workflow for validation deployments
- Main branch changes trigger `main-aws.yml` for production-ready deployments
- Use `workflow-build-deploy-aws.yml` as the reusable workflow template
- Ensure new features don't break existing CI/CD pipelines

### API Documentation Maintenance
- Update Postman collections in `docs/postman/` when API endpoints change:
  - `AWS MockNest Serverless.postman_collection.json` for main API operations
  - `Health Checks.postman_collection.json` for monitoring endpoints
  - `Demo Example.postman_environment.json` for environment variables
- Keep cURL examples in README synchronized with actual API changes

## Code Generation Standards
[Guidelines for AI-assisted code generation and review]
- Generate Kotlin 2.3.0/Spring Boot 4.0 code targeting JVM 25, relying on the shared Gradle settings for dependency management and Kotlin logging; keep new tasks compatible with the existing toolchain.
- **Use Kotlin AWS SDK** (not Java SDK) for all AWS cloud infrastructure interactions - these must always be kept in the `software/infra/aws/` module to maintain clean architecture boundaries.
- **Prefer Kotlin idioms** for error handling and resource management:
  - Use `runCatching { }` instead of try-catch-finally blocks
  - Use `.use { }` for automatic resource management (closeable resources)
  - Leverage Kotlin's null safety and smart casts
  - Avoid `!!` operator
- **Testing with MockK** - Use MockK library for all mocking in unit tests, taking advantage of Kotlin-specific features like extension functions and coroutines support.
- Reuse the ObjectStorage-backed abstractions for persistence so FILES and MAPPINGS stay in object storage (and never fall back to local disk); extend the WireMock filters instead of bypassing them when manipulating mappings.

## Documentation Practices
[How Kiro should help maintain and generate documentation]
- **Steering documents contain core project documentation** - Vision, architecture, scope, market analysis, and usage guidelines are maintained in `.kiro/steering/` files
- **Link steering documentation from README** - The main README should reference and link to relevant steering documents for comprehensive project information
- **Use docs/ folder for supplementary documentation** - Detailed diagrams, API specifications, deployment guides, and other documentation that extends beyond steering scope
- **Maintain Postman collections** - Keep API collections in `docs/postman/` updated when endpoints change:
  - `AWS MockNest Serverless.postman_collection.json` for main API operations
  - `Health Checks.postman_collection.json` for monitoring endpoints
  - `Demo Example.postman_environment.json` for environment variables
- Update the README’s cURL quickstart and links to the WireMock Admin API whenever admin endpoints change, and keep examples for AWS API Gateway deployments current.
- Reflect any changes to mapping normalization or persistence rules in the docs so external users understand why bodies become `bodyFileName` entries stored in object storage.

## Testing Strategy
[AI assistance for test creation, execution, and maintenance]
- Add JUnit 5/Kotlin test coverage alongside new features, following the existing pattern of exercising WireMock filters with in-memory storage doubles.
- **Use Kover for code coverage** - Apply `org.jetbrains.kotlinx.kover` plugin for Kotlin-optimized coverage reporting with better support for inline functions and coroutines.
- **Target 90% code coverage** across all modules - run `./gradlew koverHtmlReport` to generate coverage reports and ensure new code maintains high coverage standards.
- **All modules should achieve 90%+ coverage** - Domain, Application, and Infrastructure layers should all maintain consistent high-quality test coverage.
- **Integration testing with LocalStack TestContainers** - Use LocalStack TestContainers for infrastructure layer integration tests to validate AWS service interactions:
  - LocalStack container for S3, Lambda, and API Gateway testing
  - Test actual Kotlin AWS SDK calls against containerized AWS services
  - Validate object storage persistence, mapping externalization, and file handling
  - Keep integration tests in the `software/infra/aws/` module alongside the infrastructure code
- Prefer focused tests that validate mapping normalization, content-type defaults, and file externalization behaviors instead of broad integration suites.
- Use MockK for all mocking to leverage Kotlin-specific features and maintain consistency.

## Code Review Process
[How Kiro integrates with code review and quality assurance]
- Verify contributions respect the object-storage-first design (FILE and MAPPING stores, delete-all behavior) and keep WireMock extensions wired through the central configuration.
- Ensure reviewers check for unintended bypasses of the normalization filter (e.g., mappings that write inline bodies without being marked persistent) and for adherence to the mapping ID/file naming conventions.

## Deployment Assistance
[AI support for deployment, monitoring, and troubleshooting]
- For AWS, keep S3 object storage interactions aligned with the existing bucket/key patterns and batching semantics;
- Preserve the documented deployment touchpoints (function/API keys, URLs, and Postman collections) so operators can continue following the published setup instructions.

## Knowledge Management
[How Kiro helps capture and share project knowledge]
- Capture updates to API surfaces or storage behaviors near the WireMock Admin API reference and default mappings so future changes stay discoverable alongside the running mock assets.
- When adding new cloud integrations, document the storage layout and lifecycle expectations in the same locations used by the current S3/Blob adapters.

## Best Practices
[Recommended patterns for effective AI-assisted development]
- Keep persistent mapping bodies externalized to files via the normalization filter, and always set sensible default content types when bodies are moved into storage.
- Use bounded-concurrency flows and batch operations for storage access (as already done in the storage adapters) to minimize cold-start and latency impact in serverless environments.