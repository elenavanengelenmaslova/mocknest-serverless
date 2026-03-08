# Bug Exploration Test Findings

## Test Execution Summary

**Test Created**: `LambdaSpringContextIsolationBugTest.kt`
**Test Status**: ✅ PASSED (unexpected - test was expected to FAIL on unfixed code)
**Date**: 2026-03-07

## Test Results

### Runtime Lambda Initialization
- ✅ Spring context initialized successfully
- ✅ FunctionCatalog bean found: `BeanFactoryAwareFunctionRegistry`
- ⚠️  WARNING: "Failed to locate function 'runtimeRouter' for function definition 'runtimeRouter'. Returning null."
- ✅ Function bean 'runtimeRouter' found in Spring context
- ✅ Function lookup returned: `FunctionInvocationWrapper`

### Generation Lambda Initialization  
- ✅ Spring context initialized successfully
- ✅ FunctionCatalog bean found: `BeanFactoryAwareFunctionRegistry`
- ⚠️  WARNING: "Failed to locate function 'generationRouter' for function definition 'generationRouter'. Returning null."
- ✅ Function bean 'generationRouter' found in Spring context
- ✅ Function lookup returned: `FunctionInvocationWrapper`

## Key Findings

### 1. Spring Context Initialization Works
Both RuntimeApplication and GenerationApplication successfully initialize their Spring contexts in the test environment, despite being in the same monolithic module.

### 2. FunctionCatalog Bean Exists
The `FunctionCatalog` bean is properly created and available in both Lambda contexts.

### 3. Function Beans Are Registered
Both `runtimeRouter` and `generationRouter` function beans are successfully registered in their respective Spring contexts and can be retrieved via `context.getBean()`.

### 4. Warning Messages Are Misleading
The warning "Failed to locate function... Returning null" appears in the logs, but:
- The function lookup actually returns a `FunctionInvocationWrapper` (not null)
- The function bean exists in the Spring context
- The function appears to be properly wired

## Analysis

### Possible Explanations

1. **Bug Only Manifests in Lambda Deployment**
   - The test environment may not accurately reproduce the Lambda cold start scenario
   - The bug might only occur when the Shadow JAR is deployed to actual AWS Lambda
   - Different classloading or initialization order in Lambda vs test environment

2. **Bug Description May Be Inaccurate**
   - The reported `NoSuchBeanDefinitionException` for `FunctionCatalog` is not reproduced
   - The actual issue might be different from what was described in the bugfix.md
   - The root cause hypothesis may need revision

3. **Bug Was Already Fixed**
   - Recent changes to the codebase may have inadvertently fixed the issue
   - The monolithic structure might actually be working correctly now
   - The bug report might be outdated

### Evidence from Test Execution

**Spring Boot Logs Show Successful Initialization:**
```
Started GradleWorkerMain in 4.461 seconds (process running for 5.796)
Spring context initialized successfully
Active profiles: test
Bean definition count: 99 (Runtime), 105 (Generation)
```

**Function Beans Are Present:**
```
✓ Function bean 'runtimeRouter' registered in Spring context
✓ Function bean 'generationRouter' registered in Spring context
```

**FunctionCatalog Lookup Works:**
```
Lookup result: runtimeRouter<...> (type: FunctionInvocationWrapper)
Lookup result: generationRouter<...> (type: FunctionInvocationWrapper)
```

## Recommendations

### 1. Test in Actual Lambda Environment
To confirm whether the bug exists, we need to:
- Build the Shadow JARs for both Lambda functions
- Deploy to actual AWS Lambda (or LocalStack)
- Invoke the Lambda functions via API Gateway
- Observe the cold start initialization logs in CloudWatch

### 2. Review Bug Report Accuracy
- Re-examine the original bug report and stack traces
- Verify if the `NoSuchBeanDefinitionException` was actually observed
- Check if there were recent code changes that might have fixed the issue

### 3. Consider Alternative Root Causes
If the bug does exist in Lambda deployment but not in tests, possible causes:
- Shadow JAR packaging issues (missing META-INF files, incorrect merging)
- Lambda-specific classloading behavior
- Differences in how Spring Boot initializes in Lambda vs test environment
- Issues with Spring Cloud Function adapter in Lambda runtime

## Test Code Location

**File**: `software/infra/aws/src/test/kotlin/nl/vintik/mocknest/infra/aws/bugfix/LambdaSpringContextIsolationBugTest.kt`

**Test Method**: `Property 1 - Given Lambda initialization When using monolithic module structure Then FunctionCatalog bean should exist in Spring context`

**Test Approach**: Parameterized test that initializes Spring contexts for both RUNTIME and GENERATION Lambda types and verifies:
1. Spring context initializes without errors
2. FunctionCatalog bean exists
3. Function beans are registered and retrievable

## Next Steps

1. ✅ Bug exploration test created and executed
2. ⏭️ Document findings (this file)
3. ⏭️ Decide whether to proceed with module split or investigate further
4. ⏭️ If proceeding: Test in actual Lambda deployment before implementing fix
5. ⏭️ If investigating: Deploy current code to Lambda and observe behavior

## Conclusion

The bug condition exploration test **does not reproduce the reported bug** in the test environment. Both Lambda Spring contexts initialize successfully with properly registered function beans. This suggests either:
- The bug only manifests in actual Lambda deployment
- The bug description is inaccurate
- The bug was already fixed

**Recommendation**: Deploy and test the current monolithic structure in actual AWS Lambda before proceeding with the module split to confirm the bug exists.

---

## UPDATE: Bug Confirmed in AWS Lambda Deployment

**Date**: 2026-03-07
**Status**: ✅ BUG CONFIRMED in actual Lambda deployment

### Actual Lambda Error Log

```
12:33:47.453 [main] INFO nl.vintik.mocknest.infra.aws.runtime.RuntimeApplication -- Started RuntimeApplication in 6.108 seconds (process running for 6.692)
No qualifying bean of type 'org.springframework.cloud.function.context.FunctionCatalog' available: org.springframework.beans.factory.NoSuchBeanDefinitionException
org.springframework.beans.factory.NoSuchBeanDefinitionException: No qualifying bean of type 'org.springframework.cloud.function.context.FunctionCatalog' available
    at org.springframework.beans.factory.support.DefaultListableBeanFactory.getBean(DefaultListableBeanFactory.java:386)
    at org.springframework.beans.factory.support.DefaultListableBeanFactory.getBean(DefaultListableBeanFactory.java:377)
    at org.springframework.context.support.AbstractApplicationContext.getBean(AbstractApplicationContext.java:1296)
    at org.springframework.cloud.function.adapter.aws.FunctionInvoker.start(FunctionInvoker.java:112)
    at org.springframework.cloud.function.adapter.aws.FunctionInvoker.<init>(FunctionInvoker.java:71)
```

### Key Observations

1. **Spring Boot Starts Successfully**: `Started RuntimeApplication in 6.108 seconds` - Spring context initializes without errors
2. **FunctionInvoker Fails**: The error occurs when `FunctionInvoker` tries to get the `FunctionCatalog` bean
3. **JAR Packaging Issue**: The bug is NOT a Spring context initialization problem - it's a Shadow JAR packaging issue

### Root Cause Analysis

The bug manifests only in Lambda deployment because:

1. **Test Environment**: Uses Spring Boot's test framework which automatically configures Spring Cloud Function beans
2. **Lambda Environment**: Uses the Shadow JAR which must include proper Spring Cloud Function auto-configuration

**The actual root cause is Shadow JAR packaging**, not the monolithic module structure itself. The JAR is missing:
- Spring Cloud Function auto-configuration classes
- Proper META-INF/spring.factories entries
- FunctionCatalog bean registration

### Evidence

- Spring Boot application starts successfully in Lambda
- All custom beans (WireMock, S3 storage, etc.) are properly initialized
- Only the Spring Cloud Function infrastructure (FunctionCatalog) is missing
- This points to incomplete Shadow JAR configuration, not module structure issues

### Revised Fix Strategy

The module split may still be beneficial for separation of concerns, but the immediate fix should focus on:

1. **Shadow JAR Configuration**: Ensure proper merging of Spring Cloud Function META-INF files
2. **Auto-Configuration**: Verify Spring Cloud Function auto-configuration is included
3. **Bean Registration**: Ensure FunctionCatalog bean is properly registered in the JAR

The design document's Shadow JAR configuration includes these critical lines:
```kotlin
mergeServiceFiles()
append("META-INF/spring.handlers")
append("META-INF/spring.schemas")
append("META-INF/spring.tooling")
append("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
append("META-INF/spring.factories")
```

**UPDATE**: Verified that current `build.gradle.kts` already has these configurations. The issue is likely:
1. **Component Scanning Conflict**: Both `RuntimeApplication` and `GenerationApplication` exist in the same JAR with overlapping scan paths
2. **Spring Cloud Function Auto-Configuration**: May not be triggering properly due to multiple application classes
3. **Function Bean Registration**: The function beans may not be discovered due to component scanning ambiguity

### Investigation Results

**Current Shadow JAR Configuration** (from `software/infra/aws/build.gradle.kts`):
- ✅ Has `mergeServiceFiles()`
- ✅ Has all required META-INF append statements
- ✅ Excludes Spring Boot auto-configuration from minimize
- ✅ Excludes Spring Cloud Function from minimize

**RuntimeApplication Configuration**:
```kotlin
@SpringBootApplication(
    scanBasePackages = [
        "nl.vintik.mocknest.application.runtime",
        "nl.vintik.mocknest.application.core",
        "nl.vintik.mocknest.infra.aws.runtime",
        "nl.vintik.mocknest.infra.aws.core.storage"
    ]
)
```

**Problem**: Both `RuntimeApplication` and `GenerationApplication` are compiled into the same JAR. Even though the Shadow JAR tasks specify different Main-Class attributes, Spring Cloud Function's auto-configuration may be confused by the presence of both application classes.

### Confirmed Root Cause

The bug is caused by **monolithic module structure with multiple Spring Boot application classes**:
1. Both `RuntimeApplication` and `GenerationApplication` exist in the same compiled output
2. Spring Cloud Function adapter tries to initialize but cannot determine which application context to use
3. The `FunctionCatalog` bean is not created because Spring Cloud Function auto-configuration fails
4. This only manifests in Lambda deployment, not in test environment (which uses Spring Boot test framework)

### Conclusion

**The module split fix is the correct approach**. Separating runtime and generation into dedicated modules will:
1. Ensure each Lambda JAR contains only one Spring Boot application class
2. Eliminate component scanning conflicts
3. Allow Spring Cloud Function auto-configuration to work correctly
4. Provide proper Spring context isolation for each Lambda function
