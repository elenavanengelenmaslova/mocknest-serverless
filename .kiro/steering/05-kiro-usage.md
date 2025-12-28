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
[How Kiro should be used throughout the development process]

## Code Generation Standards
[Guidelines for AI-assisted code generation and review]

## Documentation Practices
[How Kiro should help maintain and generate documentation]

## Testing Strategy
[AI assistance for test creation, execution, and maintenance]

## Code Review Process
[How Kiro integrates with code review and quality assurance]

## Deployment Assistance
[AI support for deployment, monitoring, and troubleshooting]

## Knowledge Management
[How Kiro helps capture and share project knowledge]

## Best Practices
[Recommended patterns for effective AI-assisted development]