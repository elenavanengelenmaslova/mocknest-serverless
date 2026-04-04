# Spec Updates: GitHub Actions Job-Level Parallel Execution

## Summary

The post-deployment integration test spec has been updated to use GitHub Actions job-level parallelism for AI-powered tests. This approach provides faster execution with manual retry capability for investigating failures.

## What Changed

### 1. Requirements Document (requirements.md)

**Updated Requirement 17: Parallel Test Execution with Manual Retry**

- Run REST, GraphQL, and SOAP tests as separate parallel GitHub Actions jobs
- Setup job (health checks + cleanup) runs first as prerequisite
- Each job executes independently without blocking others
- Manual retry of individual failed jobs via GitHub Actions UI
- Selective test suite execution via workflow_dispatch inputs
- Reduces execution time from ~90s to ~30s

**Removed Requirements:**
- ~~Requirement 18: Retry Logic for AI-Powered Tests~~ (no automatic retry)
- ~~Requirement 19: Test Execution Configuration~~ (no configuration needed)

### 2. Design Document (design.md)

**Updated Test Execution Architecture:**

- **Job 1: Setup** (Sequential)
  - Runtime health check
  - AI generation health check
  - Delete all mappings
  - Output API credentials for parallel jobs

- **Jobs 2-4: Test Suites** (Parallel)
  - Job 2: REST generation + import
  - Job 3: GraphQL generation + import
  - Job 4: SOAP generation + import

**Added Section 4.1: GitHub Actions Job Structure Design**

- Workflow structure with job dependencies (`needs:`)
- Job outputs for passing API credentials
- Conditional job execution based on workflow_dispatch inputs
- Test script updates to support test suite selection (setup, rest, graphql, soap, all)

**Removed Sections:**
- ~~Section 4.1: Retry Logic Design~~ (no automatic retry)
- ~~Section 4.2: Parallel Execution Design~~ (replaced with job-level approach)

**Key Design Decisions:**

- GitHub Actions provides native parallelism and manual retry
- No bash background jobs or retry loops needed
- Each job can be retried individually after investigation
- workflow_dispatch allows running specific test suites for debugging

### 3. Tasks Document (tasks.md)

**Updated Iteration 2.5: GitHub Actions Job-Level Parallel Execution**

- **Task 15**: Refactor test script to support test suite selection
- **Task 16**: Create setup job in workflow
- **Task 17**: Create REST test job
- **Task 18**: Add workflow_dispatch inputs for selective execution
- **Task 19**: Test parallel execution and manual retry
- **Task 20**: Document manual retry process
- **Task 21**: PR and merge

**Updated Iteration 3 and 4:**

- Task 27: Create GraphQL test job (instead of updating bash function)
- Task 33: Create SOAP test job (instead of updating bash function)

**Updated Notes section:**

- GitHub Actions job-level parallelism explanation
- Manual retry process
- No automatic retry
- Selective execution via workflow_dispatch

## Benefits

### Performance
- **3x faster execution**: ~30 seconds vs ~90 seconds
- Native GitHub Actions parallelism (no bash complexity)
- No gateway scaling concerns

### Reliability
- **Manual retry after investigation**: No automatic retry noise
- **Individual job retry**: Only retry what failed
- **Clear failure reporting**: Detailed logs per job

### Flexibility
- **Selective execution**: Run individual test suites via workflow_dispatch
- **Simple implementation**: No retry loops or background jobs
- **GitHub Actions native**: Uses built-in features

## Manual Retry Process

When a test job fails:

1. **Investigate**: Review job logs to understand the failure
2. **Decide**: Determine if it's a transient issue or real problem
3. **Retry**: Click "Re-run failed jobs" in GitHub Actions UI
4. **Or Debug**: Use workflow_dispatch to run only the failed test suite

## Configuration Options

```bash
# Run all tests locally (sequential)
./scripts/post-deploy-test.sh all <API_URL> <API_KEY>

# Run only setup (health checks + cleanup)
./scripts/post-deploy-test.sh setup <API_URL> <API_KEY>

# Run only REST tests
./scripts/post-deploy-test.sh rest <API_URL> <API_KEY>

# Run only GraphQL tests
./scripts/post-deploy-test.sh graphql <API_URL> <API_KEY>

# Run only SOAP tests
./scripts/post-deploy-test.sh soap <API_URL> <API_KEY>
```

## Implementation Status

- ✅ Requirements updated (1 requirement updated, 2 removed)
- ✅ Design updated (job-level architecture, removed retry logic)
- ✅ Tasks updated (new Iteration 2.5 with 7 tasks)
- ⏳ Implementation pending (Iteration 2.5 tasks not yet started)

## Next Steps

1. Complete current Iteration 2 tasks (REST mock invocation tests)
2. Implement Iteration 2.5 (GitHub Actions job-level parallelism)
3. Test locally with each test suite option
4. Test in GitHub Actions workflow with parallel jobs
5. Test manual retry of failed jobs
6. Proceed with Iterations 3-5 (GraphQL, SOAP, auto-trigger)

## Related Files

- `.kiro/specs/post-deployment-integration-tests/requirements.md`
- `.kiro/specs/post-deployment-integration-tests/design.md`
- `.kiro/specs/post-deployment-integration-tests/tasks.md`
- `scripts/post-deploy-test.sh` (to be updated in Iteration 2.5)
- `.github/workflows/post-deploy-integration-test.yml` (to be updated in Iteration 2.5)
