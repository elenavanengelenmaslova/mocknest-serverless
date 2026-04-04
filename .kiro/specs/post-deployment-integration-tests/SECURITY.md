# Security Measures for Public Repository

This document outlines the security measures implemented in the post-deployment integration test workflow to protect sensitive information in a public repository.

## Sensitive Information

The following values are considered sensitive and must not be exposed in public logs:

1. **API URL** - The deployed API Gateway endpoint URL
2. **API Key ID** - The AWS API Gateway API key identifier
3. **API Key Value** - The actual API key used for authentication

## Masking Strategy

### GitHub Actions Masking

All sensitive values are masked using GitHub Actions' `::add-mask::` command:

```bash
echo "::add-mask::$API_URL"
echo "::add-mask::$API_KEY_ID"
echo "::add-mask::$API_KEY"
```

**How it works:**
- Once a value is masked, GitHub Actions replaces it with `***` in all logs
- Masking applies to:
  - Step logs
  - Job outputs
  - Workflow summaries
  - Environment variables in downstream jobs
  - Any subsequent references to that value

### Implementation Details

#### 1. Stack Output Retrieval (workflow-integration-test.yml)

```yaml
- name: Retrieve stack outputs
  id: stack-outputs
  run: |
    # Retrieve values
    API_URL=$(aws cloudformation describe-stacks ...)
    API_KEY_ID=$(aws cloudformation describe-stacks ...)
    
    # Mask immediately after retrieval
    echo "::add-mask::$API_URL"
    echo "::add-mask::$API_KEY_ID"
    
    # Safe to set as output - masking is propagated
    echo "api-url=$API_URL" >> $GITHUB_OUTPUT
    echo "api-key-id=$API_KEY_ID" >> $GITHUB_OUTPUT
```

#### 2. API Key Retrieval

```yaml
- name: Retrieve API key value
  id: api-key
  run: |
    API_KEY=$(aws apigateway get-api-key ...)
    
    # Mask immediately
    echo "::add-mask::$API_KEY"
    
    # Safe to set as output
    echo "api-key=$API_KEY" >> $GITHUB_OUTPUT
```

#### 3. Test Script (scripts/post-deploy-test.sh)

The test script avoids logging sensitive values:

```bash
# DO NOT log API_URL
echo "=== MockNest Serverless Post-Deployment Integration Tests ==="
echo "Test Suite: $TEST_SUITE"
# API_URL is NOT logged here
```

### Job Outputs

Job outputs are safe because masking is propagated:

```yaml
jobs:
  setup:
    outputs:
      api-url: ${{ steps.stack-outputs.outputs.api-url }}  # Masked
      api-key: ${{ steps.api-key.outputs.api-key }}        # Masked
```

Downstream jobs can safely use these outputs:

```yaml
  test-rest:
    needs: setup
    steps:
      - name: Run tests
        env:
          API_URL: ${{ needs.setup.outputs.api-url }}  # Still masked
          API_KEY: ${{ needs.setup.outputs.api-key }}  # Still masked
```

## Verification

To verify that sensitive values are properly masked:

1. **Check workflow logs** - All sensitive values should appear as `***`
2. **Check job outputs** - Sensitive values in the "Set up job" section should be masked
3. **Check workflow summaries** - No sensitive values should appear in summaries
4. **Check environment variables** - Sensitive values passed to downstream jobs should be masked

## What is NOT Masked

The following values are safe to expose and are NOT masked:

- Stack name (e.g., "mocknest-serverless")
- AWS region (e.g., "eu-west-1")
- Test suite names (e.g., "setup", "rest")
- Test results (pass/fail status)
- HTTP status codes from tests

## Best Practices

1. **Mask early** - Call `::add-mask::` immediately after retrieving sensitive values
2. **Avoid logging** - Don't echo sensitive values in scripts or workflows
3. **Use environment variables** - Pass sensitive values via environment variables, not command-line arguments
4. **Verify masking** - Always check workflow logs to ensure masking is working
5. **Document sensitivity** - Clearly mark which values are sensitive in code comments

## References

- [GitHub Actions: Masking values in logs](https://docs.github.com/en/actions/using-workflows/workflow-commands-for-github-actions#masking-a-value-in-log)
- [GitHub Actions: Security hardening](https://docs.github.com/en/actions/security-guides/security-hardening-for-github-actions)
