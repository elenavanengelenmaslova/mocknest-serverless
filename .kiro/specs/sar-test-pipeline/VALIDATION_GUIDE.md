# SAR Test Pipeline - Quick Validation Guide

This guide provides quick instructions for validating the SAR Test Pipeline implementation.

## Quick Start

### Prerequisites
```bash
# Install GitHub CLI if not already installed
brew install gh  # macOS
# or visit https://cli.github.com/

# Authenticate with GitHub
gh auth login

# Configure AWS credentials (for cleanup validation)
aws configure

# IMPORTANT: Ensure SAR application is public or shared with your AWS account
# The test pipeline deploys from eu-west-1 to any target region (cross-region deployment)
```

### Run Automated Validation

```bash
# Full validation (recommended)
./.kiro/specs/sar-test-pipeline/validate-pipeline.sh eu-west-1 1.0.0-test full

# Quick validation (skips AWS cleanup check)
./.kiro/specs/sar-test-pipeline/validate-pipeline.sh eu-west-1 1.0.0-test quick

# Security-only validation on existing logs
./.kiro/specs/sar-test-pipeline/validate-pipeline.sh eu-west-1 1.0.0-test security-only validation-logs-12345.txt
```

## Manual Validation Steps

### 1. Test Automatic Trigger

```bash
# Trigger the SAR beta test workflow (includes automatic test pipeline trigger)
gh workflow run sar-beta-test.yml \
  --field version=1.0.0-test \
  --field aws-region=eu-west-1

# Watch the workflow
gh run watch

# Check that test job runs after publish job
gh run view --web
```

**Expected Result:** Test pipeline runs automatically after publish completes.

---

### 2. Test Manual Trigger with Different Regions

```bash
# Test with default region (eu-west-1)
gh workflow run sar-test-pipeline.yml

# Test with us-east-1
gh workflow run sar-test-pipeline.yml \
  --field aws-region=us-east-1 \
  --field version=1.0.0-test

# Test with ap-southeast-1
gh workflow run sar-test-pipeline.yml \
  --field aws-region=ap-southeast-1 \
  --field version=1.0.0-test
```

**Expected Result:** Pipeline deploys to selected region successfully.

---

### 3. Verify API Key Masking

```bash
# Get the latest run
RUN_ID=$(gh run list --workflow=sar-test-pipeline.yml --limit 1 --json databaseId --jq '.[0].databaseId')

# Download logs
gh run view $RUN_ID --log > validation-logs.txt

# Search for potential API key leaks
grep -i "api.key" validation-logs.txt
grep -E "x-api-key: [^*]" validation-logs.txt | grep -v "add-mask"

# Verify masking command is present
grep "::add-mask::" validation-logs.txt
```

**Expected Result:** 
- ✅ API keys appear as `***` in logs
- ✅ `::add-mask::` command is present
- ✅ No plain-text API keys visible

---

### 4. Test Cleanup on Failure

```bash
# Modify workflow to simulate failure (e.g., wrong SAR app ID)
# Then trigger workflow
gh workflow run sar-test-pipeline.yml \
  --field aws-region=eu-west-1 \
  --field version=1.0.0-test

# Watch for cleanup execution
gh run watch

# Check logs for cleanup step
gh run view --log | grep "Starting resource cleanup"
```

**Expected Result:** Cleanup runs even when pipeline fails.

---

### 5. Verify Report Generation

```bash
# View the latest run summary
gh run view --web

# Or check summary in logs
gh run view --log | grep -A 50 "SAR Test Pipeline Results"
```

**Expected Result:** 
- ✅ Summary report is generated
- ✅ All sections are present (Deployment, Health Checks, Cleanup, Duration)
- ✅ No API keys in summary

---

### 6. Verify No Secrets in Logs

```bash
# Download logs
gh run view $RUN_ID --log > security-audit.txt

# Run comprehensive security checks
echo "Checking for API keys..."
grep -iE "api.?key" security-audit.txt | grep -v "***" | grep -v "add-mask"

echo "Checking for AWS credentials..."
grep -iE "aws.?secret" security-audit.txt | grep -v "***"

echo "Checking for passwords..."
grep -iE "password" security-audit.txt | grep -v "***"

echo "Checking x-api-key headers..."
grep -E "x-api-key: [^*]" security-audit.txt | grep -v "redacted"
```

**Expected Result:** No secrets found in plain text.

---

## Validation Checklist

Use this checklist to track validation progress:

### Automatic Trigger
- [ ] Trigger sar-beta-test.yml workflow
- [ ] Verify test job runs after publish job
- [ ] Verify correct inputs are passed
- [ ] Verify summary includes test status

### Manual Trigger
- [ ] Test with default region (eu-west-1)
- [ ] Test with us-east-1
- [ ] Test with us-west-2
- [ ] Test with eu-central-1
- [ ] Test with ap-southeast-1
- [ ] Test with ap-northeast-1
- [ ] Verify region dropdown works
- [ ] Verify version field is optional

### Security
- [ ] Download workflow logs
- [ ] Search for API key patterns
- [ ] Verify ::add-mask:: is present
- [ ] Check x-api-key header redaction
- [ ] Audit summary report
- [ ] Verify security warnings

### Failure Scenarios
- [ ] Test deployment failure
- [ ] Verify cleanup runs on deployment failure
- [ ] Test health check failure
- [ ] Verify cleanup runs on health check failure
- [ ] Verify error messages don't expose secrets

### Reports
- [ ] Check success case report
- [ ] Check failure case report
- [ ] Verify all sections present
- [ ] Verify duration formatting
- [ ] Verify emoji indicators
- [ ] Verify no secrets in report

### Cleanup
- [ ] Verify stack deleted after success
- [ ] Verify stack deleted after failure
- [ ] Verify S3 bucket deletion checked
- [ ] Check AWS Console for leftover resources

---

## Common Issues and Solutions

### Issue: ValidationError - Waiter ChangeSetCreateComplete failed
**Symptoms:**
```
ERROR]: Waiter ChangeSetCreateComplete failed: Waiter encountered a terminal failure state: 
Matched expected service error code: ValidationError
```

**Root Cause:** Missing required parameters when creating CloudFormation change set from SAR.

**Solution:** 
The fix has been implemented in the workflow. The pipeline now passes the `DeploymentName` parameter explicitly:
```bash
--parameter-overrides DeploymentName=sar-test-${{ github.run_id }}
```

This ensures each test deployment has a unique identifier and all required parameters are provided.

**If you still encounter this error:**
1. **Check SAR application is public or shared**
   ```bash
   aws serverlessrepo get-application \
     --application-id arn:aws:serverlessrepo:eu-west-1:021259937026:applications/MockNest-Serverless \
     --region ${{ inputs.aws-region }}
   ```

2. **Verify IAM permissions**
   ```bash
   aws iam get-role --role-name GitHubOIDCAdmin
   ```

3. **Check CloudFormation change set details**
   ```bash
   aws cloudformation describe-change-set \
     --stack-name mocknest-sar-test-<run-id> \
     --change-set-name <change-set-name> \
     --region <region>
   ```

### Issue: Workflow doesn't trigger
**Solution:** 
```bash
# Check workflow file syntax
gh workflow view sar-test-pipeline.yml

# Check repository permissions
gh auth status
```

### Issue: API key masking not working
**Solution:** 
- Verify `::add-mask::` command runs before any logging
- Check that API_KEY_VALUE is stored in GITHUB_ENV, not GITHUB_OUTPUT

### Issue: Cleanup fails
**Solution:**
```bash
# Check stack status in AWS
aws cloudformation describe-stacks \
  --stack-name mocknest-sar-test-<run-id> \
  --region eu-west-1

# Manually delete if needed
aws cloudformation delete-stack \
  --stack-name mocknest-sar-test-<run-id> \
  --region eu-west-1
```

### Issue: Health checks timeout
**Solution:**
- Verify Lambda function is running
- Check API Gateway endpoint is accessible
- Review Lambda logs in CloudWatch

---

## Test Matrix

| Scenario | Region | Version | Expected | Status |
|----------|--------|---------|----------|--------|
| Auto trigger | eu-west-1 | 1.0.0-test | ✅ Success | ⏳ |
| Manual default | eu-west-1 | latest | ✅ Success | ⏳ |
| Manual us-east-1 | us-east-1 | 1.0.0-test | ✅ Success | ⏳ |
| Manual ap-southeast-1 | ap-southeast-1 | 1.0.0-test | ✅ Success | ⏳ |
| Deployment failure | eu-west-1 | invalid | ❌ Fail + Cleanup | ⏳ |
| Health check failure | eu-west-1 | 1.0.0-test | ❌ Fail + Cleanup | ⏳ |
| API key masking | eu-west-1 | 1.0.0-test | ✅ No leaks | ⏳ |

---

## Validation Commands Reference

```bash
# List recent workflow runs
gh run list --workflow=sar-test-pipeline.yml --limit 5

# View specific run
gh run view <run-id>

# View run in browser
gh run view <run-id> --web

# Download logs
gh run view <run-id> --log > logs.txt

# Watch run in real-time
gh run watch <run-id>

# Trigger workflow
gh workflow run sar-test-pipeline.yml \
  --field aws-region=eu-west-1 \
  --field version=1.0.0-test

# Check AWS resources
aws cloudformation list-stacks --region eu-west-1
aws s3 ls
```

---

## Success Criteria

The pipeline is fully validated when:

✅ All automatic triggers work correctly  
✅ All manual triggers work with different regions  
✅ No API keys or secrets appear in logs or summaries  
✅ Cleanup executes successfully in all scenarios  
✅ Reports are generated correctly for success and failure  
✅ All security checks pass  
✅ All test matrix scenarios pass  

---

## Next Steps After Validation

Once validation is complete:

1. **Document Results**: Update VALIDATION_CHECKLIST.md with results
2. **Mark Task Complete**: Update tasks.md to mark task 9 as complete
3. **Create PR**: Submit changes for review
4. **Update Documentation**: Update main README if needed
5. **Announce**: Share validation results with team

---

_For detailed validation procedures, see VALIDATION_CHECKLIST.md_
