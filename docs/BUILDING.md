# Building MockNest Serverless

This guide covers building, testing, and packaging MockNest Serverless.

## Prerequisites

- **Java 25** (recommended via [SDKMAN](https://sdkman.io/))
- **Gradle 9.4.1** (included via wrapper)
- **Kotlin 2.3.0** (configured in build files)

### Installing Java 25 with SDKMAN

```bash
# Install SDKMAN
curl -s "https://get.sdkman.io" | bash

# Install and set Java 25 as default
sdk install java 25.0.1-open
sdk default java 25.0.1-open

# Verify installation
java -version
```

## Building

### Full Build
Build all modules and run tests:
```bash
./gradlew build
```

### Clean Build
Clean and rebuild everything:
```bash
./gradlew clean build
```

### Module-Specific Builds
```bash
# Build only the domain module
./gradlew :software:domain:build

# Build only the application module
./gradlew :software:application:build

# Build only specific AWS infrastructure modules
./gradlew :software:infra:aws:runtime:build
./gradlew :software:infra:aws:generation:build
./gradlew :software:infra:aws:mocknest:build
```

## Testing

### Running Tests
Make sure docker (or similar) is running, e.g. `colima start`.

```bash
# Run all tests
./gradlew test

# Run tests for specific module
./gradlew :software:application:test
```

### Coverage Reports

#### Generate Coverage Reports
```bash
# Generate HTML coverage reports
./gradlew koverHtmlReport

# Generate XML coverage reports (for CI/Codecov)
./gradlew koverXmlReport
```

#### View Coverage Reports
```bash
# Root project coverage
open build/reports/kover/html/index.html

# Individual module coverage
open software/application/build/reports/kover/html/index.html
open software/domain/build/reports/kover/html/index.html
open software/infra/aws/runtime/build/reports/kover/html/index.html
```

#### Coverage Verification
```bash
# Verify 80% coverage threshold (fails build if under 80%)
./gradlew koverVerify
```

### Complete Test Workflow
```bash
# Run tests, generate reports, and verify coverage
./gradlew test koverHtmlReport koverXmlReport koverVerify
```

## Packaging for AWS

### SAM Build
```bash
cd deployment/aws/sam
sam build
```

### Local Testing
```bash
# Start local API Gateway
sam local start-api

# Invoke function locally
sam local invoke MockNestRuntimeFunction --event events/test-event.json
```

## CI/CD Pipeline

### Feature Branches
- Build and test all modules
- Verify 80% code coverage
- No deployment

### Main Branch
- Build and test all modules
- Verify 80% code coverage
- Upload coverage to Codecov
- Deploy to AWS

## Troubleshooting

### Common Issues

**Java Version Mismatch**
```bash
# Check current Java version
java -version

# Switch to Java 25 with SDKMAN
sdk use java 25.0.1-open
```

**Gradle Daemon Issues**
```bash
# Stop all Gradle daemons
./gradlew --stop

# Run with fresh daemon
./gradlew build
```

**Coverage Failures**
```bash
# Check which modules are under 80%
./gradlew koverVerify

# Generate detailed coverage report
./gradlew koverHtmlReport
```

### Build Performance

**Enable Configuration Cache**
```bash
# Add to gradle.properties
org.gradle.configuration-cache=true
```

**Parallel Builds**
```bash
# Add to gradle.properties
org.gradle.parallel=true
```

## Module Dependencies

The project follows clean architecture with strict dependency rules:

```
software/infra/aws → software/application → software/domain
```

- **Domain**: Minimal dependencies (spring-web for HTTP types, wiremock-standalone, kotlinx-serialization)
- **Application**: Depends on domain, defines interfaces
- **Infrastructure**: Depends on application and domain, implements interfaces, cloud-specific dependencies

Never create reverse dependencies (e.g., domain depending on application).