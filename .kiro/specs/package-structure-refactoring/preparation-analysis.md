# Package Structure Refactoring - Preparation and Analysis

## Document Purpose
This document provides a comprehensive analysis of the current package structure and identifies all files that need to be moved during the refactoring from `io.mocknest` to `nl.vintik.mocknest`. It also documents potential issues and serves as a reference for the refactoring process.

## Current Package Structure

### Domain Module (`software/domain`)

#### Production Code
```
io.mocknest.domain.generation/
├── AIGenerationModels.kt
├── APISpecification.kt
├── GeneratedMock.kt
├── GenerationJob.kt
├── MockGenerationRequest.kt
├── MockNamespace.kt
├── TestAgentRequest.kt
└── TestAgentResponse.kt

io.mocknest.domain.model/
├── HttpRequest.kt
└── HttpResponse.kt
```

#### Test Code
```
io.mocknest.domain.generation/
└── MockNamespaceTest.kt
```

### Application Module (`software/application`)

#### Production Code
```
io.mocknest.application.generation/
├── agent/
│   ├── MockGenerationFunctionalAgent.kt
│   └── TestKoogAgent.kt
├── generators/
│   ├── RealisticTestDataGenerator.kt
│   └── WireMockMappingGenerator.kt
├── interfaces/
│   ├── AIModelServiceInterface.kt
│   ├── GenerationStorageInterface.kt
│   ├── MockGeneratorInterface.kt
│   └── SpecificationParserInterface.kt
├── parsers/
│   ├── CompositeSpecificationParserImpl.kt
│   └── OpenAPISpecificationParser.kt
└── usecases/
    ├── AIGenerationRequestUseCase.kt
    ├── GenerateMocksFromDescriptionUseCase.kt
    ├── GenerateMocksFromSpecUseCase.kt
    ├── GenerateMocksFromSpecWithDescriptionUseCase.kt
    └── TestAgentRequestUseCase.kt

io.mocknest.application.interfaces.storage/
└── ObjectStorageInterface.kt

io.mocknest.application.usecase/
├── AdminRequestUseCase.kt
├── ClientRequestUseCase.kt
└── HandleRequest.kt

io.mocknest.application.wiremock/
├── config/
│   └── MockNestConfig.kt
├── extensions/
│   ├── DeleteAllMappingsAndFilesFilter.kt
│   └── NormalizeMappingBodyFilter.kt
├── mappings/
│   ├── CompositeMappingsSource.kt
│   └── ObjectStorageMappingsSource.kt
└── store/
    └── adapters/
        ├── ObjectStorageBlobStore.kt
        └── ObjectStorageWireMockStores.kt
```

#### Test Code
```
io.mocknest.application.generation.generators/
└── WireMockMappingGeneratorTest.kt

io.mocknest.application.wiremock.extensions/
└── NormalizeMappingBodyFilterTest.kt

io.mocknest.application.wiremock.mappings/
└── ObjectStorageMappingsSourceTest.kt

io.mocknest.application.wiremock.store.adapters/
└── ObjectStorageBlobStoreTest.kt
```

### Infrastructure AWS Module (`software/infra/aws`)

#### Production Code
```
io.mocknest.infra.aws/
└── Application.kt

io.mocknest.infra.aws.config/
└── BedrockConfiguration.kt

io.mocknest.infra.aws.function/
└── MockNestLambdaHandler.kt

io.mocknest.infra.aws.generation/
├── AIGenerationConfiguration.kt
├── BedrockServiceAdapter.kt
├── BedrockTestKoogAgent.kt
└── S3GenerationStorageAdapter.kt

io.mocknest.infra.aws.storage/
├── S3ObjectStorageAdapter.kt
└── config/
    └── S3Configuration.kt
```

#### Test Code
```
io.mocknest.infra.aws/
├── ApplicationTests.kt
└── S3BucketPropertyResolutionTest.kt

io.mocknest.infra.aws.config/
├── AwsLocalStackTestConfiguration.kt
└── SharedLocalStackContainer.kt

io.mocknest.infra.aws.function/
├── GraphQLMockingIntegrationTest.kt
├── RestApiMockingIntegrationTest.kt
└── SoapMockingIntegrationTest.kt

io.mocknest.infra.aws.generation/
├── AIGenerationIntegrationTest.kt
└── BedrockTestKoogAgentTest.kt

io.mocknest.infra.aws.storage/
└── S3StorageIntegrationTest.kt
```

## File Classification for Target Structure

### Domain Layer → Target Mapping

**`io.mocknest.domain.model` → `nl.vintik.mocknest.domain.core`**
- HttpRequest.kt
- HttpResponse.kt

**`io.mocknest.domain.generation` → `nl.vintik.mocknest.domain.generation`** (no change in capability)
- AIGenerationModels.kt
- APISpecification.kt
- GeneratedMock.kt
- GenerationJob.kt
- MockGenerationRequest.kt
- MockNamespace.kt
- TestAgentRequest.kt
- TestAgentResponse.kt

### Application Layer → Target Mapping

**`io.mocknest.application.interfaces.storage` → `nl.vintik.mocknest.application.core.interfaces.storage`**
- ObjectStorageInterface.kt

**`io.mocknest.application.usecase` → `nl.vintik.mocknest.application.runtime.usecases`**
- AdminRequestUseCase.kt
- ClientRequestUseCase.kt
- HandleRequest.kt

**`io.mocknest.application.wiremock` → `nl.vintik.mocknest.application.runtime`**
- config/MockNestConfig.kt → runtime.config/
- extensions/DeleteAllMappingsAndFilesFilter.kt → runtime.extensions/
- extensions/NormalizeMappingBodyFilter.kt → runtime.extensions/
- mappings/CompositeMappingsSource.kt → runtime.mappings/
- mappings/ObjectStorageMappingsSource.kt → runtime.mappings/
- store/adapters/ObjectStorageBlobStore.kt → runtime.store.adapters/
- store/adapters/ObjectStorageWireMockStores.kt → runtime.store.adapters/

**`io.mocknest.application.generation` → `nl.vintik.mocknest.application.generation`** (no change in capability)
- All files remain in generation capability with same subdirectory structure

### Infrastructure Layer → Target Mapping

**`io.mocknest.infra.aws.config` → `nl.vintik.mocknest.infra.aws.runtime.config`**
- BedrockConfiguration.kt

**`io.mocknest.infra.aws.function` → `nl.vintik.mocknest.infra.aws.runtime.function`**
- MockNestLambdaHandler.kt

**`io.mocknest.infra.aws.storage.config` → `nl.vintik.mocknest.infra.aws.core.storage.config`**
- S3Configuration.kt

**`io.mocknest.infra.aws.storage` → `nl.vintik.mocknest.infra.aws.runtime.storage`**
- S3ObjectStorageAdapter.kt

**`io.mocknest.infra.aws.generation` → Split into two locations:**
- AIGenerationConfiguration.kt → `nl.vintik.mocknest.infra.aws.core.ai/`
- BedrockServiceAdapter.kt → `nl.vintik.mocknest.infra.aws.generation.ai/`
- BedrockTestKoogAgent.kt → `nl.vintik.mocknest.infra.aws.generation.ai/`
- S3GenerationStorageAdapter.kt → `nl.vintik.mocknest.infra.aws.generation.storage/`

**`io.mocknest.infra.aws.Application` → `nl.vintik.mocknest.infra.aws.runtime`**
- Application.kt

## Potential Issues Analysis

### 1. Reflection Usage

**Finding:** Limited reflection usage found, primarily in test code and framework annotations.

**Instances:**
- `::class` references in Spring Boot test annotations (`@SpringBootTest`, `@Import`, `@ContextConfiguration`)
- `::class.java` for resource loading in tests
- Jackson `readValue` with `::class.java` for deserialization

**Risk Level:** LOW
- Spring Boot annotations will be automatically updated by IDE refactoring
- Resource loading uses relative paths, not package names
- Jackson deserialization uses runtime class references, not string-based class names

**Action Required:** None - IDE refactoring will handle all `::class` references automatically

### 2. Serialization

**Finding:** Kotlinx Serialization used for domain models, Jackson used for infrastructure.

**Instances:**
- `@Serializable` annotation on `TestAgentRequest` and `TestAgentResponse`
- Jackson `ObjectMapper` used in infrastructure layer for JSON serialization

**Risk Level:** NONE
- Kotlinx Serialization doesn't include package names in serialized output
- Jackson configured to use JSON without type information
- No `@JsonTypeInfo` or `@JsonTypeName` annotations that would embed package names

**Action Required:** None - serialization is package-agnostic

### 3. String-Based Package References

**Finding:** Package names referenced in configuration files and build scripts.

**Instances:**

**Spring Boot Component Scanning:**
```kotlin
@SpringBootApplication(scanBasePackages = [
    "io.mocknest"
])
```
Location: `software/infra/aws/src/main/kotlin/io/mocknest/infra/aws/Application.kt`

**Gradle Kover Exclusions:**
```kotlin
classes(
    // interfaces
    "io.mocknest.*.interfaces.*",
    // entry points
    ...
)
```
Location: `build.gradle.kts`

**SAM Template Handler Reference:**
```yaml
MAIN_CLASS: "io.mocknest.infra.aws.Application"
```
Location: `deployment/aws/sam/template.yaml`

**Risk Level:** MEDIUM
- These require manual updates as they are string literals
- IDE refactoring will NOT automatically update these

**Action Required:**
1. Update `scanBasePackages` in Application.kt: `"io.mocknest"` → `"nl.vintik.mocknest"`
2. Update Kover exclusions in build.gradle.kts: `"io.mocknest.*.interfaces.*"` → `"nl.vintik.mocknest.*.interfaces.*"`
3. Update MAIN_CLASS in SAM template: `"io.mocknest.infra.aws.Application"` → `"nl.vintik.mocknest.infra.aws.runtime.Application"`

### 4. Resource Files

**Finding:** Test resource files use relative paths, not package-based paths.

**Instances:**
- Test data files in `src/test/resources/test-data/`
- Application properties in `src/test/resources/application-test.properties`

**Risk Level:** NONE
- Resource loading uses relative paths like `/test-data/mappings/valid-mapping-1.json`
- No package-based resource paths found

**Action Required:** None - resource files are package-agnostic

### 5. External Dependencies

**Finding:** No external dependencies reference internal package names.

**Risk Level:** NONE
- WireMock, Spring Boot, AWS SDK, and other dependencies don't reference our package structure
- All integrations use interfaces and dependency injection

**Action Required:** None

## Build Configuration Files to Update

### 1. Root build.gradle.kts
- Update `group = "com.mocknest"` → `group = "nl.vintik.mocknest"`
- Update Kover exclusion patterns

### 2. SAM Template (deployment/aws/sam/template.yaml)
- Update `MAIN_CLASS` environment variable

### 3. Spring Boot Application.kt
- Update `scanBasePackages` annotation parameter

## Test Files Summary

**Total Test Files:** 14

**Domain Module:** 1 test file
- MockNamespaceTest.kt

**Application Module:** 4 test files
- WireMockMappingGeneratorTest.kt
- NormalizeMappingBodyFilterTest.kt
- ObjectStorageMappingsSourceTest.kt
- ObjectStorageBlobStoreTest.kt

**Infrastructure Module:** 9 test files
- ApplicationTests.kt
- S3BucketPropertyResolutionTest.kt
- AwsLocalStackTestConfiguration.kt (test utility)
- SharedLocalStackContainer.kt (test utility)
- GraphQLMockingIntegrationTest.kt
- RestApiMockingIntegrationTest.kt
- SoapMockingIntegrationTest.kt
- AIGenerationIntegrationTest.kt
- BedrockTestKoogAgentTest.kt
- S3StorageIntegrationTest.kt

## Backup Strategy

### Git-Based Backup
1. Create feature branch: `git checkout -b refactor/package-structure`
2. Commit current state: `git commit -m "Checkpoint: Before package refactoring"`
3. Each major phase will be committed separately for easy rollback

### Verification Points
- After base package rename: compile + test
- After domain layer reorganization: compile + test
- After application layer reorganization: compile + test
- After infrastructure layer reorganization: compile + test
- After build configuration updates: compile + test
- After documentation updates: final review

## Refactoring Tool Strategy

### IDE Refactoring (Primary Method)
- Use IntelliJ IDEA's "Rename Package" for base package rename
- Use IntelliJ IDEA's "Move Package" for capability reorganization
- Enable "Search in comments and strings" option
- Review preview before applying changes

### Manual Updates (Required)
- Spring Boot `scanBasePackages` annotation
- Gradle Kover exclusion patterns
- SAM template `MAIN_CLASS` environment variable

## Risk Assessment Summary

| Risk Category | Level | Mitigation |
|--------------|-------|------------|
| Reflection Usage | LOW | IDE refactoring handles automatically |
| Serialization | NONE | Package-agnostic serialization |
| String References | MEDIUM | Manual updates documented |
| Resource Files | NONE | Relative paths used |
| External Dependencies | NONE | No package references |

**Overall Risk:** LOW - Well-defined refactoring with clear mitigation strategies

## Estimated File Counts

**Production Files to Move:** 42 files
- Domain: 10 files
- Application: 20 files
- Infrastructure: 12 files

**Test Files to Move:** 14 files
- Domain: 1 file
- Application: 4 files
- Infrastructure: 9 files

**Configuration Files to Update:** 3 files
- build.gradle.kts
- Application.kt
- template.yaml

**Total Files Affected:** 59 files

## Next Steps

1. ✅ Create feature branch
2. ✅ Commit current state as backup
3. → Proceed to Task 2: Base Package Rename
4. → Proceed to Task 3: Domain Layer Reorganization
5. → Proceed to Task 4: Application Layer Reorganization
6. → Proceed to Task 5: Infrastructure Layer Reorganization
7. → Proceed to Task 6: Build Configuration Updates
8. → Proceed to Task 7: Documentation Updates
9. → Proceed to Task 8: SAM Template Updates
10. → Proceed to Task 9: Final Validation

## Conclusion

The preparation and analysis phase has identified:
- Clear mapping of all 42 production files and 14 test files
- 3 configuration files requiring manual updates
- Low overall risk with well-defined mitigation strategies
- No blocking issues for proceeding with refactoring

The refactoring can proceed safely using IDE-based automated refactoring with manual updates for string-based package references in configuration files.
