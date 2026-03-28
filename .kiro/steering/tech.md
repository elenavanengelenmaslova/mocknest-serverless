# Development Practices

This document contains development workflows, coding standards, testing practices, and guidelines for building and maintaining MockNest Serverless.

# Development Workflow

## Incremental Feature Development Strategy

MockNest Serverless follows a strict incremental development approach where each major feature must be fully completed, tested, and deployed before moving to the next:

**Current Phase: AI Mock Generation from Specifications (Priority 1)**
- Implement mock generation from API specifications (OpenAPI/REST, SOAP/WSDL, GraphQL, MCP, SSE)
- Support optional generation instructions
- Complete comprehensive testing of implementation
- Deploy and validate in AWS environment
- Achieve 90%+ test coverage
- **Take small steps and get full feature deployed and tested in AWS before moving to next priority**

**Future Phase: AI Mock Evolution (Priority 2)**
- Only begin after Priority 1 is complete and deployed
- Implement mock updates for changed API specifications
- Preserve custom configurations while applying spec updates
- Complete testing and deployment before moving to next phase

**Future Phase: AI Lenient Mock Mode (Priority 3)**
- Only begin after Priority 2 is complete and deployed
- Implement configuration endpoint for spec and instructions
- Implement auto-generation for unmatched requests
- Complete testing and deployment before moving to next phase

**Future Phase: AI Traffic Analysis (Priority 4)**
- Only begin after Priority 3 is complete and deployed
- Implement traffic analysis and coverage reporting
- Identify gaps, unused mocks, and missing scenarios
- Complete testing and deployment

## Module Development Order

Follow clean architecture principles by developing in this sequence:
1. **Domain Layer** (`:software:domain`) - Start with business models, entities, and domain rules
2. **Application Layer** (`:software:application`) - Implement use cases, WireMock orchestration, and service interfaces
3. **Infrastructure Layer** (`:software:infra:aws:*`) - Add AWS-specific adapters and implementations (submodules: core, runtime, generation, mocknest)
4. **Deployment Configuration** (`deployment/aws/sam/`) - Update SAM templates and deployment scripts

## Multi-module Gradle Workflow

- Treat the repository as a clean-architecture, multi-module Kotlin project: domain models live in `:software:domain`, application-layer WireMock orchestration in `:software:application`, and cloud-specific adapters in `:software:infra:aws:*`
- Keep new code aligned with that separation and register new modules in `settings.gradle.kts` if needed
- Ensure dependencies flow correctly: infra → application → domain (never the reverse)

## WireMock Integration Workflow

- When wiring new features, plug them into the existing WireMock server configuration that boots with custom stores and extensions (normalization, delete-all) so serverless deployments keep mappings and files in object storage
- Extend WireMock filters and transformers rather than bypassing the core engine
- Always validate that new mock behaviors work with the object storage persistence layer

## SAM Template Management

- Update `deployment/aws/sam/template.yaml` when adding new Lambda functions or API Gateway endpoints
- Modify `deployment/aws/sam/samconfig.toml` for environment-specific configurations
- Use the build and deploy scripts in `deployment/aws/sam/` for consistent deployments
- Test SAM template changes locally before committing

## GitHub Actions Integration

- Feature branches trigger `feature-aws.yml` workflow for build and test validation (no deployment)
- Main branch changes trigger `main-aws.yml` for production-ready build and deployment
- Use `workflow-build.yml` and `workflow-deploy-aws.yml` as reusable workflow templates
- Ensure new features don't break existing CI/CD pipelines

## API Documentation Maintenance

- Update Postman collections in `docs/postman/` when API endpoints change:
  - `AWS MockNest Serverless.postman_collection.json` for main API operations
  - `Health Checks.postman_collection.json` for monitoring endpoints
  - `Demo Example.postman_environment.json` for environment variables
- Keep cURL examples in README synchronized with actual API changes


# Code Generation Standards

## General Kotlin Development

Guidelines for AI-assisted code generation and review:

- Generate Kotlin 2.3.0/Spring Boot 4.0 code targeting JVM 25, using Gradle 9.0.0, relying on the shared Gradle settings for dependency management and Kotlin logging; keep new tasks compatible with the existing toolchain
- **Use Kotlin AWS SDK** (not Java SDK) for all AWS cloud infrastructure interactions - these must always be kept in the `software/infra/aws/` module to maintain clean architecture boundaries
- **Use proper imports** instead of fully qualified class names in code:
 ```kotlin
  // Good: Use proper imports
  import org.springframework.beans.factory.annotation.Autowired
  
  @Autowired
  private lateinit var lambdaHandler: MockNestLambdaHandler
  
  // Bad: Fully qualified class names
  @org.springframework.beans.factory.annotation.Autowired
  private lateinit var lambdaHandler: MockNestLambdaHandler
  ```

## Kotlin Idioms

**Prefer Kotlin idioms** for error handling and resource management:
- Use `runCatching { }` instead of try-catch-finally blocks:
  ```kotlin
  runCatching { input.toInt() }
      .onFailure { e -> logger.error(e){"Failed parsing integer"}} // Log error
      .getOrThrow()
  ```
- Use `.use { }` for automatic resource management (closeable resources)
- Leverage Kotlin's null safety and smart casts
- Avoid `!!` operator
- Prefer built-in functions `checkNotNull`, `check`, `error` instead of throwing `IllegalStateException`
- Prefer `require` and `requireNotNull` instead of throwing `IllegalArgumentException`. These functions are to be used only for detecting bugs in the code, and not for user input validation or test assertions
- Prefer latest language features, such as `enum.entries` over `enum.values()` for looping through enumerations
- When escaping the `$` sign, use multi-dollar string interpolation: instead of `"\${file}"`, use `$$"${file}"`
- When annotating a constructor or method parameter with `@Qualifier` in Spring Boot, use the `param:` use-site target: `@param:Qualifier("serviceName")`

## Logging Standards

- **Use kotlin-logging (KotlinLogging)** for all logging throughout the application:
  ```kotlin
  private val logger = KotlinLogging.logger {}
  ```
- Place logger instances as a private top-level member of the kt file. Private visibility avoids leaking loggers across modules

- **Use structured logging** with consistent message formatting:
  ```kotlin
  // Good: Structured logging with context
  logger.info { "Saving object with id: $id" }
  logger.warn(exception) { "Failed to retrieve object: id=$id, bucket=$bucketName" }
  
  // Bad: String concatenation and no context
  logger.info("Saving object with id: " + id)
  logger.warn("Failed: " + exception.message)
  ```

- **Include relevant context** in log messages to aid debugging:
  - Object IDs, keys, file names
  - Operation parameters (bucket names, prefixes, etc.)
  - Counts and metrics where relevant

- **Use appropriate log levels consistently**:
  - `ERROR` - System errors that prevent operation completion
  - `WARN` - Recoverable errors, fallback scenarios, expected failures
  - `INFO` - Normal operation flow, significant state changes
  - `DEBUG` - Detailed execution information, expected exceptions in retry scenarios

- **Always pass exceptions to the logger** when logging failures:
  ```kotlin
  // Good: Exception passed to logger for full stack trace
  logger.error(exception) { "Operation failed: context info" }
  
  // Bad: Only exception message logged
  logger.error { "Operation failed: ${exception.message}" }
  ```

- Avoid logging sensitive data such as API URLs, bucket names, api key information, including in pipelines

## Exception Handling Standards

- **Never swallow exceptions silently** - Always log exceptions at minimum WARNING level before handling them
- **Prefer `runCatching` as a scope function** when it improves readability:
  ```kotlin
  // Good: Using runCatching as scope function
  s3Client.runCatching {
      createBucket(CreateBucketRequest {
          bucket = "test-bucket"
      })
  }.onFailure { exception ->
      logger.warn(exception) { "Bucket creation failed, may already exist: test-bucket" }
  }
  
  // Also good: Traditional runCatching when multiple operations or complex logic
  runCatching {
      val result = complexOperation()
      processResult(result)
      return result
  }.onFailure { exception ->
      logger.error(exception) { "Complex operation failed" }
      throw exception
  }
  ```

- **Choose the appropriate pattern** based on context:
  - **Scope function style** (`object.runCatching { method() }`) for single method calls
  - **Traditional style** (`runCatching { /* multiple operations */ }`) for complex blocks
- **Always include proper logging** with context information (see Logging Standards above)
- **Use appropriate log levels** (see Logging Standards above)

## Serialization

**Prefer Kotlinx Serialization** over Jackson mapper where possible for both production code and tests:
- Use `@Serializable` data classes for JSON handling
- Use `Json.encodeToString()` and `Json.decodeFromString()` for serialization
- Only fall back to Jackson when integrating with libraries that require it (like WireMock)
- When Jackson is required, reuse the shared `mapper` if available instead of creating new instances
- If a shared mapper is not available, create one with `jacksonObjectMapper()` and not `ObjectMapper()`, unless a specific WireMock-provided mapper is required

## Persistence

- Reuse the ObjectStorage-backed abstractions for persistence so FILES and MAPPINGS stay in object storage (and never fall back to local disk)
- Extend the WireMock filters instead of bypassing them when manipulating mappings


# Unit Testing Standards

Comprehensive guidelines for unit test creation and maintenance.

## Test Structure and Naming

- **Use Given-When-Then naming convention** for test methods:
  ```kotlin
  @Test
  fun `Given valid mapping request When processing normalization Then should externalize body to file`()
  ```
- Test method names should clearly describe the scenario being tested
- Use backticks for readable test names with spaces and special characters

## MockK Configuration and Best Practices

- **Declare mocks with relaxed behavior** at the property level rather than in setup methods:
  ```kotlin
  private val mockStorage: ObjectStorageInterface = mockk(relaxed = true)
  private val mockService: SomeService = mockk(relaxed = true)
  ```

- **Reset all mocks in teardown** to ensure test isolation:
  ```kotlin
  @AfterEach
  fun tearDown() {
      clearAllMocks()
  }
  ```

- Use `relaxed = true` by default to avoid unnecessary stubbing of irrelevant method calls
- Only use strict mocks when you need to verify that specific methods are NOT called
- Prefer `coEvery` and `coVerify` for suspend functions

## Test Organization

- Group related test methods using nested classes when appropriate. Nested test class names must follow standard Kotlin class naming conventions (PascalCase) with no spaces:
  ```kotlin
  @Nested
  inner class MappingNormalization {
      // Related tests here
  }
  ```
- Use `@BeforeEach` for common test setup that applies to all tests in a class
- Keep test data creation close to the test that uses it
- Extract common test data builders into companion objects or separate test utilities

## Verification Patterns

- Use `coVerify` to assert that expected interactions occurred:
  ```kotlin
  coVerify { mockStorage.save("expected-key", "expected-content") }
  ```

- Use `coVerify(exactly = 0)` to assert that methods were NOT called:
  ```kotlin
  coVerify(exactly = 0) { mockStorage.save(any(), any()) }
  ```

- Prefer specific argument matching over `any()` when the exact values matter for the test

## Coroutine Testing

- **Use JUnit 6 suspend test support** - Annotate test functions with `suspend` and avoid `runBlocking` in unit tests:
  ```kotlin
  @Test
  suspend fun `Given valid request When processing async operation Then should complete successfully`() {
      // Test suspend functions directly without runBlocking
      val result = suspendingService.processRequest(request)
      assertEquals(expectedResult, result)
  }
  ```

- Avoid using `suspend` in unit test functions where it is unnecessary:
  ```kotlin
  // Avoid this if nothing inside is suspending
  @Test
  suspend fun `Given constants When calculating Then should be correct`() {
      assertEquals(4, 2 + 2)
  }
  ```

- **Use kotlinx-coroutines-test library** for advanced coroutine testing scenarios:
  ```kotlin
  @Test
  fun `Given delayed operation When using test dispatcher Then should control time`() = runTest {
      // Use runTest for controlling virtual time and testing delays
      val result = async { delayedOperation() }
      advanceUntilIdle()
      assertEquals(expectedResult, result.await())
  }
  ```

- **Prefer `suspend` over `runBlocking`**, and for complex coroutine scenarios that need time control use `runTest`
- **Test both success and failure paths** for suspend functions, including exception handling
- **Use `TestDispatcher`** when you need to control coroutine execution and timing in tests

## TestContainers Integration Testing

- **Use proper TestContainers lifecycle management** with `@BeforeAll`/`@AfterAll` for expensive setup operations:
  ```kotlin
  @Testcontainers
  class IntegrationTest {
      companion object {
          @Container
          @JvmStatic
          private val localStackContainer = LocalStackContainer(
              DockerImageName.parse("localstack/localstack:4.12.0")
          ).withServices(
              LocalStackContainer.Service.S3
          ).waitingFor(
              Wait.forLogMessage(".*Ready.*", 1)
          )

          @BeforeAll
          @JvmStatic
          fun setupClass() {
              // One-time expensive setup here
              // Configure clients, create resources
          }

          @AfterAll
          @JvmStatic
          fun tearDownClass() {
              // Clean up resources
          }
      }

      @BeforeEach
      fun setup() {
          // Clear test data before each test
      }

      @AfterEach
      fun tearDown() {
          // Clean up test data after each test
      }
  }
  ```

- **Use TestContainers built-in waiting strategies** instead of manual retry loops:
  - `Wait.forLogMessage(".*Ready.*", 1)` for LocalStack readiness
  - `Wait.forHttp("/health")` for HTTP health checks
  - `Wait.forListeningPort()` for port availability

- **Share expensive resources** like containers and clients across tests using `@BeforeAll`/`@AfterAll`
- **Clean test data** (not containers) between tests using `@BeforeEach`/`@AfterEach`

- **Use LocalStack TestContainers** for infrastructure layer integration tests to validate AWS service interactions:
  - LocalStack container for S3, Lambda, API Gateway, and Bedrock testing
  - Test actual Kotlin AWS SDK calls against containerized AWS services
  - Validate object storage persistence, mapping externalization, and file handling
  - Test AI-assisted mock generation using LocalStack's Bedrock emulation
  - Keep integration tests in the `software/infra/aws/` module alongside the infrastructure code

## Test Data Management

- **Use resource files for test data** - Store test data in `src/test/resources` folder and read them in tests:
  ```kotlin
  private fun loadTestData(filename: String): String {
      return this::class.java.getResource("/test-data/$filename")?.readText()
          ?: throw IllegalArgumentException("Test data file not found: $filename")
  }
  
  @Test
  suspend fun `Given large mapping JSON When processing Then should normalize correctly`() {
      val mappingJson = loadTestData("large-mapping.json")
      val result = filter.normalizeMappingToBodyFile(mappingJson)
      // assertions...
  }
  ```

- **Prefer external files for JSON data larger than 3 lines** - Keep test code clean and readable by externalizing complex test data

- **Use parameterized tests for multiple scenarios** - Test different data combinations efficiently:
  ```kotlin
  @ParameterizedTest
  @ValueSource(strings = ["mapping-with-body.json", "mapping-with-base64.json", "mapping-without-body.json"])
  suspend fun `Given different mapping types When processing Then should handle correctly`(filename: String) {
      val mappingJson = loadTestData(filename)
      val result = filter.normalizeMappingToBodyFile(mappingJson)
      // scenario-specific assertions based on filename
  }
  ```

- **Organize test data files** in logical subdirectories under `src/test/resources/`:
  - `test-data/mappings/` - WireMock mapping JSON files
  - `test-data/requests/` - HTTP request examples
  - `test-data/responses/` - Expected response payloads

- **Use meaningful file names** that clearly indicate the test scenario they represent


# Testing Strategy

AI assistance for test creation, execution, and maintenance:

- Add JUnit 6/Kotlin test coverage alongside new features, following the unit testing standards defined in the Code Generation Standards section above
- **Use Kover for code coverage** - Apply `org.jetbrains.kotlinx.kover` plugin for Kotlin-optimized coverage reporting with better support for inline functions and coroutines
- **Target 80% minimum aggregated code coverage** (enforced) across the entire project, aiming for 90%+ - run `./gradlew koverHtmlReport` to generate coverage reports and `./gradlew koverVerify` to enforce the 80% threshold
- **Emphasize integration tests over unit tests** - Focus on comprehensive end-to-end testing that validates actual system behavior rather than artificial per-module coverage targets
- **Aggregated coverage enforcement** - The 80% coverage minimum is enforced at the project level (aggregated across all modules) rather than per-module, allowing flexibility in test strategy while ensuring overall system quality

- **Property-based testing with @ParameterizedTest** - Use JUnit 6's `@ParameterizedTest` to validate universal properties across multiple examples:
  - Create comprehensive test data files covering edge cases (simple, complex, large, nested, etc.)
  - Use `@ValueSource`, `@MethodSource`, or `@CsvSource` to provide multiple test cases
  - Test properties that should hold for ALL valid inputs (e.g., "all operations extracted", "size reduced by 40%+")
  - Prefer deterministic examples over random generation for easier debugging
  - Aim for 10-20 diverse examples per property test to catch edge cases
  - Example: `@ParameterizedTest @ValueSource(strings = ["simple.json", "complex.json", "large.json"])`

- **Integration testing with LocalStack TestContainers** - Use LocalStack TestContainers for infrastructure layer integration tests to validate AWS service interactions:
  - LocalStack container for S3, Lambda, API Gateway, and Bedrock testing
  - Test actual Kotlin AWS SDK calls against containerized AWS services
  - Validate object storage persistence, mapping externalization, and file handling
  - Test AI-assisted mock generation using LocalStack's Bedrock emulation
  - Keep integration tests in the `software/infra/aws/` module alongside the infrastructure code
  - Use proper TestContainers lifecycle management with `@BeforeAll`/`@AfterAll` for container setup
  - Use TestContainers built-in waiting strategies like `Wait.forLogMessage(".*Ready.*", 1)`
  - Share expensive resources like containers and clients across tests, only clean test data between tests

- Prefer focused integration tests that validate mapping normalization, content-type defaults, and file externalization behaviors instead of broad unit test suites
- Follow the MockK configuration and Given-When-Then naming conventions as specified in the unit testing standards

# Documentation Practices

Documentation in this project is designed to stay accurate, intentional, and easy to maintain as the system evolves. The goal is to avoid duplication, reduce drift between code and documentation, and clearly communicate architectural intent.

## Source of Truth

- **Steering documents** (located under `.kiro/steering/`) are the authoritative source for architectural decisions, system design, and long-term direction
- These documents should be updated deliberately when architectural decisions change
- They are not meant to be auto-generated or frequently rewritten

## Role of Kiro

- Kiro may **propose documentation updates** when code changes affect architecture or behavior
- Documentation updates should always be reviewed and applied intentionally
- Kiro should **not regenerate entire documents** when only small sections require changes

## Documentation Structure

- **README.md**
  - Entry point for the repository
  - Describes the purpose of the project, how to get started, and where to find deeper documentation
  - Links to relevant steering documents and technical references

- **`docs/` directory**
  - Contains supporting documentation such as:
    - Architecture diagrams
    - API specifications
    - Postman collections
    - Operational guides and examples

- **Steering documents (`.kiro/steering/`)**
  - Capture architectural decisions, constraints, and rationale
  - Define long-term direction and non-functional requirements
  - Serve as reference material for contributors and reviewers

## Documentation Maintenance Guidelines

- Avoid duplicating the same information across multiple files
- When functionality or architecture changes:
  - Update the relevant steering document first
  - Reflect essential changes in the README if user-facing
- Keep documentation concise and focused on intent rather than implementation detail

## Code–Documentation Alignment

- Ensure documentation matches the current behavior of the system
- Update docs alongside code changes that affect APIs, configuration, or deployment
- Prefer linking to canonical sources rather than copying large sections of content

## Recommended Practices

- Treat documentation as part of the codebase, reviewed and versioned together
- Favor clarity over completeness—documents should explain *why* things exist, not just *what* exists
- Use diagrams and examples where they add clarity, but avoid redundancy

This approach keeps documentation accurate, maintainable, and aligned with the evolution of the system without creating unnecessary overhead.

# Code Review Process

How Kiro integrates with code review and quality assurance:

- Verify contributions respect the object-storage-first design (FILE and MAPPING stores, delete-all behavior) and keep WireMock extensions wired through the central configuration
- Ensure reviewers check for unintended bypasses of the normalization filter (e.g., mappings that write inline bodies without being marked persistent) and for adherence to the mapping ID/file naming conventions

# Deployment Assistance

AI support for deployment, monitoring, and troubleshooting:

- For AWS, keep S3 object storage interactions aligned with the existing bucket/key patterns and batching semantics
- Preserve the documented deployment touchpoints (function/API keys, URLs, and Postman collections) so operators can continue following the published setup instructions


# Spec Development Workflow

## Kiro-Assisted Spec Creation

Kiro is used to systematically develop feature specifications through a structured workflow:

1. **Requirements Generation**: Kiro generates initial requirements documents based on user ideas and steering document context
2. **Design Creation**: After requirements approval, Kiro creates technical design documents with architecture, components, and correctness properties
3. **Task Planning**: Following design approval, Kiro generates implementation task lists with specific, actionable steps

## Mandatory Testing Requirements for Task Generation

**CRITICAL: All generated task lists MUST include comprehensive testing tasks**

When generating implementation tasks (tasks.md), the following testing requirements are MANDATORY:

### Required Test Tasks for Every Feature

1. **Unit Tests (MANDATORY)**
   - Every new class, function, or component MUST have corresponding unit tests
   - Unit tests MUST follow Given-When-Then naming convention
   - Unit tests MUST use MockK for mocking dependencies
   - Unit tests MUST achieve minimum 90% code coverage for new code
   - Task example: "Write unit tests for [ComponentName] covering success and failure paths"

2. **Property-Based Tests (MANDATORY)**
   - Features with data transformation, parsing, or validation logic MUST include property-based tests
   - Use JUnit 6's `@ParameterizedTest` with multiple test cases (10-20 diverse examples)
   - Create test data files in `src/test/resources/test-data/` for complex scenarios
   - Test universal properties that should hold for ALL valid inputs
   - Task example: "Write property-based tests for [FeatureName] using @ParameterizedTest with diverse test data files"

3. **Integration Tests (MANDATORY for infrastructure layer)**
   - Infrastructure layer code (AWS adapters, storage implementations) MUST have LocalStack TestContainers integration tests
   - Integration tests MUST validate actual AWS SDK interactions
   - Integration tests MUST use proper TestContainers lifecycle management
   - Task example: "Write LocalStack integration tests for [AWS Component] validating S3/Bedrock interactions"

### Task Generation Rules

- **Every implementation task MUST be followed by a corresponding test task**
- Test tasks are NOT optional - they are required for task completion
- Test tasks should be explicit and specific about what needs to be tested
- Test tasks should reference the testing standards in this document

### Example Task Structure

```markdown
- [ ] 1. Implement GraphQL schema parser
  - [ ] 1.1 Create GraphQLSchemaParser class in application layer
  - [ ] 1.2 Implement schema parsing logic
  - [ ] 1.3 Write unit tests for GraphQLSchemaParser covering all parsing scenarios
  - [ ] 1.4 Write property-based tests using @ParameterizedTest with 10+ diverse GraphQL schema files
  - [ ] 1.5 Write LocalStack integration tests validating end-to-end schema parsing with S3 storage
```

### Coverage Verification Task

Every task list MUST include a final verification task:
```markdown
- [ ] N. Verify test coverage and quality
  - [ ] N.1 Run `./gradlew koverHtmlReport` and verify 90%+ coverage for new code
  - [ ] N.2 Run `./gradlew koverVerify` to enforce coverage threshold
  - [ ] N.3 Review test quality: Given-When-Then naming, proper assertions, edge case coverage
```

## Incremental Feature Completion Strategy

**Critical Rule: Complete one feature fully before starting the next**

**Current Priority: AI Mock Generation from Specifications (Priority 1)**
- Focus exclusively on completing comprehensive testing of existing implementation
- Ensure support for all target protocols (OpenAPI/REST, SOAP/WSDL, GraphQL, MCP, SSE)
- Fix GitHub Actions build issues
- Implement SAM template for AWS deployment
- Deploy and validate in AWS environment
- Achieve target test coverage (90%+)
- **Do not begin Priority 2 development until Priority 1 is complete and deployed**

**Future AI Priorities (2-4)**
- Only proceed to design and implementation phases after Priority 1 is fully deployed
- Each AI priority must be completed and deployed before moving to the next:
  - Priority 2: Mock Evolution for Updated Specifications
  - Priority 3: Lenient Mock Mode (Auto-Generation on Demand)
  - Priority 4: Traffic Analysis
- Maintain the same rigorous testing and deployment standards

## Iterative Review Process

Each phase requires explicit user approval before proceeding:
- **Requirements Review**: User reviews and approves requirements before design phase
- **Design Review**: User reviews and approves design before task creation
- **Task Review**: User reviews and approves implementation plan before execution
- **Deployment Review**: User validates deployment before moving to next feature

## Steering Document Alignment

When corrections or clarifications are needed during any phase of development:
- **Context Updates**: Steering documents are updated to provide better context for future work
- **Consistency Maintenance**: Changes to steering documents ensure all future specs, designs, tasks, and implementation align with project direction
- **Knowledge Capture**: Lessons learned during requirements, design, task planning, and implementation are captured in steering documents
- **Continuous Refinement**: Steering documents evolve based on insights gained during actual development work

This approach ensures that Kiro has the right context and that all project artifacts remain aligned with project goals and architectural decisions throughout the development lifecycle.

# Knowledge Management

## How Kiro Helps Capture and Share Project Knowledge

Kiro is used as a supporting tool to help capture, maintain, and evolve project knowledge in a consistent and traceable way. It does not replace documentation or architectural decisions, but assists in keeping them accurate and aligned with the codebase over time.

Kiro contributes to knowledge management in the following ways:

- **Documentation alignment**  
  Kiro helps keep architectural descriptions, design decisions, and usage documentation in sync with the actual implementation. When changes are made to code or structure, Kiro can assist in updating related documentation to reflect those changes accurately.

- **Clarifying design intent**  
  Kiro is used to explain *why* certain architectural decisions exist (e.g., storage separation, clean architecture boundaries, serverless constraints), making those decisions easier to understand for new contributors.

- **Consistent terminology and structure**  
  Kiro helps maintain consistent naming, concepts, and structure across documentation, diagrams, and code comments, reducing ambiguity and cognitive load.

- **Supporting onboarding and knowledge transfer**  
  New contributors can use Kiro-assisted documentation to quickly understand:
  - The purpose of each module
  - How components interact
  - Where to make changes safely
  - How architectural constraints are enforced

- **Keeping documentation close to the code**  
  Architectural explanations, usage guides, and operational notes live alongside the codebase (e.g. in `docs/` and README files). Kiro assists in keeping these documents accurate as the system evolves.

## What Kiro Does *Not* Do

- Kiro does **not** replace architectural decision-making
- Kiro does **not** automatically generate or modify production code
- Kiro does **not** act as a source of truth independent of the repository

Instead, Kiro acts as a **documentation and reasoning assistant** that helps ensure the project's design intent remains understandable, consistent, and well-communicated over time.

# Best Practices

Recommended patterns for effective AI-assisted development.

## Current Development Focus

**Current Priority: AI Mock Generation from Specifications (Priority 1)**
- Focus exclusively on completing comprehensive testing of mock generation implementation
- Ensure support for all target protocols (OpenAPI/REST, SOAP/WSDL, GraphQL, MCP, SSE)
- Fix any remaining issues in GitHub Actions build pipeline
- Deploy and validate in AWS environment
- Achieve target test coverage (90%+)
- **Do not begin Priority 2 development until Priority 1 is complete and deployed**

## Technical Implementation Guidelines

- Keep persistent mapping bodies externalized to files via the normalization filter, and always set sensible default content types when bodies are moved into storage
- Use bounded-concurrency flows and batch operations for storage access (as already done in the storage adapters) to minimize cold-start and latency impact in serverless environments
- **Focus on Priority 1 (Mock Generation from Specifications) until fully complete and deployed**
