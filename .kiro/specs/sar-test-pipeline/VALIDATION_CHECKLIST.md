# SAR Test Pipeline - Final Validation Checklist

This document provides a comprehensive validation plan for the SAR Test Pipeline implementation.

## Overview

The SAR Test Pipeline validates published MockNest Serverless applications by:
1. Deploying from AWS Serverless Application Repository (SAR)
2. Retrieving deployment outputs and API keys
3. Executing health checks with retry logic
4. Cleaning up test resources
5. Generating comprehensive reports

## Validation Categories

### 1. Automatic Trigger from SAR Publish Workflow

**Test Scenario:** Verify the test pipeline executes automatically after SAR publication

**Steps:**
1. Trigger the `sar-beta-test.yml` workflow manually via workflow_dispatch
2. Provide required inputs:
   - version: e.g., "1.0.0-test"
   - aws-region: "eu-west-1" (default)
3. Monitor workflow execution to ensure:
   - `publish` job completes successfully
   - `test` job starts automatically after `publish` completes
   - `summary` job runs after both complete

**Expected Results:**
- ✅ Test pipeline receives correct inputs from publish workflow
- ✅ Test pipeline uses same AWS region as publish workflow
- ✅ Test pipeline uses same version as publish workflow
- ✅ Summary job includes test pipeline status

**Validation Commands:**
```bash
# Check workflow run status
gh run list --workflow=sar-beta-test.yml --limit 1

# View workflow run details
gh run view <run-id>

# Check job dependencies
gh run view <run-id> --log
```

---

### 2. Manual Trigger with Different Regions

**Test Scenario:** Verify manual workflow_dispatch trigger works with region selection

**Steps:**
1. Navigate to Actions → SAR Test Pipeline → Run workflow
2. Test each supported region:
   - us-east-1
   - us-west-2
   - eu-west-1 (default)
   - eu-central-1
   - ap-southeast-1
   - ap-northeast-1
3. Leave version field empty (should use "latest")

**Expected Results:**
- ✅ Workflow accepts manual trigger
- ✅ Region dropdown shows all 6 supported regions
- ✅ Default region is eu-west-1
- ✅ Pipeline deploys to selected region
- ✅ Version defaults to "latest" when not specified

**Validation Commands:**
```bash
# Trigger workflow manually for different regions
gh workflow run sar-test-pipeline.yml \
  --field aws-region=us-east-1

gh workflow run sar-test-pipeline.yml \
  --field aws-region=eu-central-1 \
  --field version=1.0.0-test

# Check workflow inputs
gh run view <run-id> --log | grep "Region:"
```

---

### 3. API Key Masking in Logs

**Test Scenario:** Verify API keys are never exposed in GitHub Actions logs

**Critical Security Checks:**
1. API key value is masked immediately after retrieval
2. API key never appears in workflow logs
3. API key never appears in error messages
4. API key never appears in summary reports
5. x-api-key header is redacted from HTTP request logs

**Steps:**
1. Run the test pipeline successfully
2. Download workflow logs
3. Search for potential API key leaks:
   ```bash
   # Search for API key patterns in logs
   grep -i "api.key" workflow.log
   grep -i "x-api-key" workflow.log
   grep -E "[A-Za-z0-9]{20,}" workflow.log
   ```

**Expected Results:**
- ✅ API key value is masked with `***` in logs
- ✅ No API key values appear in plain text
- ✅ x-api-key header shows as redacted in health check logs
- ✅ Error messages do not contain API key values
- ✅ Summary report does not contain API key values or IDs

**Validation Points in Workflow:**
- Step "Retrieve API key value" uses `echo "::add-mask::$API_KEY_VALUE"`
- Health check logs include note: "API key has been redacted from logs"
- Summary report includes warning: "API key values are masked in logs"

---

### 4. Cleanup Executes on Failure Scenarios

**Test Scenario:** Verify cleanup runs even when pipeline fails

**Failure Scenarios to Test:**

#### Scenario A: Deployment Failure
1. Modify SAR application ID to invalid value
2. Run pipeline
3. Verify cleanup attempts to run (even though stack doesn't exist)

#### Scenario B: Health Check Failure
1. Deploy successfully
2. Simulate health check failure (e.g., wrong API endpoint)
3. Verify cleanup deletes the stack

#### Scenario C: API Key Retrieval Failure
1. Deploy successfully
2. Simulate API key retrieval failure
3. Verify cleanup deletes the stack

**Expected Results:**
- ✅ Cleanup step has `if: always()` condition
- ✅ Cleanup executes regardless of previous step failures
- ✅ Cleanup logs warnings instead of failing pipeline
- ✅ Cleanup status is included in summary report
- ✅ Stack is deleted even on failure
- ✅ S3 bucket deletion is verified

**Validation Commands:**
```bash
# Check if stack was cleaned up after failure
aws cloudformation describe-stacks \
  --stack-name mocknest-sar-test-<run-id> \
  --region eu-west-1

# Should return: "Stack with id mocknest-sar-test-<run-id> does not exist"
```

---

### 5. Report Generation for Success and Failure Cases

**Test Scenario:** Verify comprehensive reports are generated in all cases

#### Success Case Report Validation
**Expected Sections:**
- ✅ Overall Status: ✅ Success
- ✅ Deployment Status with stack name, region, duration
- ✅ Health Check Results showing both endpoints passed
- ✅ Cleanup Status showing successful deletion
- ✅ API Gateway Endpoint URL (for manual testing)
- ✅ Execution Duration Breakdown table
- ✅ Security note about masked API keys

#### Failure Case Report Validation
**Expected Sections:**
- ✅ Overall Status: ❌ Failed
- ✅ Deployment Status (if deployment failed)
- ✅ Health Check Results with error details (if health checks failed)
- ✅ Cleanup Status (should still show cleanup attempted)
- ✅ Execution Duration Breakdown (partial durations)
- ✅ Links to workflow logs for detailed error messages

**Validation Steps:**
1. Run pipeline successfully
2. Check GitHub Actions Summary tab
3. Verify all sections are present and formatted correctly
4. Run pipeline with induced failure
5. Check summary includes failure details
6. Verify error messages are descriptive but don't expose secrets

---

### 6. No Secrets in GitHub Actions Logs or Summary

**Test Scenario:** Comprehensive security audit of all outputs

**Secrets to Protect:**
- API key values
- API key IDs (should not be in summary)
- AWS credentials
- Internal resource identifiers

**Audit Checklist:**

#### Workflow Logs Audit
```bash
# Download workflow logs
gh run view <run-id> --log > workflow.log

# Search for potential secrets
grep -i "api.key" workflow.log
grep -i "secret" workflow.log
grep -i "password" workflow.log
grep -E "x-api-key: [^*]" workflow.log  # Should only show masked values

# Verify masking is working
grep "::add-mask::" workflow.log  # Should find masking command
grep "***" workflow.log  # Should find masked values
```

#### Summary Report Audit
1. Open GitHub Actions Summary tab
2. Verify:
   - ✅ No API key values visible
   - ✅ No API key IDs visible
   - ✅ Security warning about masked keys is present
   - ✅ Only API Gateway URL is shown (which is public)

#### Environment Variables Audit
- ✅ API_KEY_VALUE stored in GITHUB_ENV (masked)
- ✅ API_KEY_VALUE never stored in GITHUB_OUTPUT
- ✅ API_KEY_ID not exposed in summary report

---

## Comprehensive Test Matrix

| Test Case | Region | Version | Expected Result | Validation |
|-----------|--------|---------|-----------------|------------|
| Auto trigger from SAR publish | eu-west-1 | 1.0.0-test | ✅ Success | Verify test job runs after publish |
| Manual trigger - default region | eu-west-1 | latest | ✅ Success | Verify default values work |
| Manual trigger - us-east-1 | us-east-1 | 1.0.0-test | ✅ Success | Verify region selection |
| Manual trigger - ap-southeast-1 | ap-southeast-1 | 1.0.0-test | ✅ Success | Verify Asia Pacific region |
| Deployment failure | eu-west-1 | invalid | ❌ Fail + Cleanup | Verify cleanup runs |
| Health check failure | eu-west-1 | 1.0.0-test | ❌ Fail + Cleanup | Verify retry logic and cleanup |
| API key masking | eu-west-1 | 1.0.0-test | ✅ Success | Audit logs for secrets |

---

## Validation Script

Here's a bash script to automate some validation checks:

```bash
#!/bin/bash
set -e

echo "🧪 SAR Test Pipeline Validation Script"
echo "======================================="
echo ""

# Configuration
WORKFLOW_NAME="sar-test-pipeline.yml"
REGION="${1:-eu-west-1}"
VERSION="${2:-1.0.0-test}"

echo "📋 Configuration:"
echo "  Region: $REGION"
echo "  Version: $VERSION"
echo ""

# 1. Trigger workflow manually
echo "1️⃣  Triggering workflow manually..."
RUN_ID=$(gh workflow run "$WORKFLOW_NAME" \
  --field aws-region="$REGION" \
  --field version="$VERSION" \
  --json databaseId --jq '.databaseId')

echo "   Workflow triggered: Run ID $RUN_ID"
echo ""

# 2. Wait for workflow to complete
echo "2️⃣  Waiting for workflow to complete..."
gh run watch "$RUN_ID"
echo ""

# 3. Download logs
echo "3️⃣  Downloading workflow logs..."
gh run view "$RUN_ID" --log > "validation-logs-$RUN_ID.txt"
echo "   Logs saved to: validation-logs-$RUN_ID.txt"
echo ""

# 4. Check for API key leaks
echo "4️⃣  Checking for API key leaks..."
if grep -E "x-api-key: [^*]" "validation-logs-$RUN_ID.txt" | grep -v "add-mask"; then
  echo "   ❌ SECURITY ISSUE: Unmasked API key found in logs!"
  exit 1
else
  echo "   ✅ No API key leaks detected"
fi
echo ""

# 5. Verify masking was applied
echo "5️⃣  Verifying API key masking..."
if grep -q "::add-mask::" "validation-logs-$RUN_ID.txt"; then
  echo "   ✅ API key masking command found"
else
  echo "   ❌ WARNING: No masking command found"
fi
echo ""

# 6. Check cleanup execution
echo "6️⃣  Checking cleanup execution..."
if grep -q "Starting resource cleanup" "validation-logs-$RUN_ID.txt"; then
  echo "   ✅ Cleanup step executed"
else
  echo "   ❌ WARNING: Cleanup step not found"
fi
echo ""

# 7. Verify report generation
echo "7️⃣  Verifying report generation..."
if grep -q "SAR Test Pipeline Results" "validation-logs-$RUN_ID.txt"; then
  echo "   ✅ Summary report generated"
else
  echo "   ❌ WARNING: Summary report not found"
fi
echo ""

echo "✅ Validation complete!"
echo ""
echo "📊 View full results:"
echo "   gh run view $RUN_ID"
echo "   gh run view $RUN_ID --web"
```

---

## Manual Validation Checklist

Use this checklist when manually validating the pipeline:

### Pre-Validation Setup
- [ ] AWS credentials configured with OIDC role
- [ ] GitHub repository secrets configured (AWS_ACCOUNT_ID)
- [ ] SAR application published in target region
- [ ] GitHub CLI installed and authenticated

### Automatic Trigger Validation
- [ ] Trigger sar-beta-test.yml workflow
- [ ] Verify publish job completes
- [ ] Verify test job starts automatically
- [ ] Verify test job receives correct inputs
- [ ] Verify summary job includes test status

### Manual Trigger Validation
- [ ] Test manual trigger with default region (eu-west-1)
- [ ] Test manual trigger with us-east-1
- [ ] Test manual trigger with ap-southeast-1
- [ ] Verify region dropdown shows all 6 options
- [ ] Verify version field is optional

### Security Validation
- [ ] Download workflow logs
- [ ] Search for API key patterns
- [ ] Verify ::add-mask:: command is present
- [ ] Verify x-api-key header is redacted
- [ ] Check summary report for secrets
- [ ] Verify security warnings are present

### Failure Scenario Validation
- [ ] Test deployment failure (invalid SAR app)
- [ ] Verify cleanup runs on deployment failure
- [ ] Test health check failure
- [ ] Verify cleanup runs on health check failure
- [ ] Verify error messages are descriptive
- [ ] Verify no secrets in error messages

### Report Validation
- [ ] Check success case report completeness
- [ ] Check failure case report completeness
- [ ] Verify all sections are present
- [ ] Verify duration formatting is correct
- [ ] Verify emoji indicators work
- [ ] Verify links to logs are correct

### Cleanup Validation
- [ ] Verify stack is deleted after success
- [ ] Verify stack is deleted after failure
- [ ] Verify S3 bucket deletion is checked
- [ ] Verify cleanup warnings are logged
- [ ] Check AWS Console for leftover resources

---

## Known Issues and Limitations

### Current Limitations
1. **SAR Application Availability**: Pipeline requires SAR application to be published in target region
2. **OIDC Role**: Requires pre-configured GitHub OIDC role with correct permissions
3. **Region Support**: Limited to 6 Nova Pro supported regions
4. **Timeout Values**: Fixed timeouts (15min deployment, 10min cleanup)

### Future Enhancements
1. Add support for custom timeout values
2. Add support for additional AWS regions
3. Add performance benchmarking metrics
4. Add cost tracking and reporting
5. Add integration with external monitoring tools

---

## Troubleshooting Guide

### Issue: Workflow doesn't trigger automatically
**Solution:** Check workflow_call configuration in sar-beta-test.yml

### Issue: API key masking not working
**Solution:** Verify ::add-mask:: command runs before any logging

### Issue: Cleanup fails
**Solution:** Check CloudFormation stack status in AWS Console

### Issue: Health checks timeout
**Solution:** Verify Lambda function is running and API Gateway is accessible

### Issue: Stack creation fails
**Solution:** Check CloudFormation events for detailed error messages

---

## Success Criteria

The SAR Test Pipeline is considered fully validated when:

✅ All automatic triggers work correctly
✅ All manual triggers work with different regions
✅ No API keys or secrets appear in logs or summaries
✅ Cleanup executes successfully in all scenarios
✅ Reports are generated correctly for success and failure
✅ All security checks pass
✅ All test matrix scenarios pass
✅ Documentation is complete and accurate

---

## Sign-off

| Validation Area | Status | Validated By | Date |
|----------------|--------|--------------|------|
| Automatic Triggers | ⏳ Pending | | |
| Manual Triggers | ⏳ Pending | | |
| API Key Masking | ⏳ Pending | | |
| Cleanup on Failure | ⏳ Pending | | |
| Report Generation | ⏳ Pending | | |
| Security Audit | ⏳ Pending | | |

---

_Last Updated: 2026-03-22_
