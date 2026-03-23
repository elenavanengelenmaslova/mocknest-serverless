# SAR Test Pipeline - ValidationError Fix

## Issue
The SAR test pipeline was failing with:
```
ERROR]: Waiter ChangeSetCreateComplete failed: Waiter encountered a terminal failure state: 
Matched expected service error code: ValidationError
```

## Root Cause
The `aws serverlessrepo create-cloud-formation-change-set` command was missing required parameters. Even though the SAM template defines default values for parameters like `DeploymentName`, the AWS CLI requires these parameters to be explicitly passed when creating a change set from a SAR application, especially for cross-region deployments.

## The Fix
Added `--parameter-overrides` flag to the `create-cloud-formation-change-set` command:

```yaml
--parameter-overrides DeploymentName=sar-test-${{ github.run_id }}
```

This ensures:
1. Each test deployment has a unique identifier
2. Required parameters are explicitly provided
3. Cross-region deployment works correctly (eu-west-1 → any region)

## Changes Made

### 1. Workflow Update (.github/workflows/sar-test-pipeline.yml)
```yaml
CHANGE_SET_ID=$(aws serverlessrepo \
  create-cloud-formation-change-set \
  --application-id arn:aws:serverlessrepo:eu-west-1:021259937026:applications/MockNest-Serverless \
  --stack-name "$STACK_NAME" \
  --capabilities CAPABILITY_IAM CAPABILITY_NAMED_IAM \
  --parameter-overrides DeploymentName=sar-test-${{ github.run_id }} \  # ← ADDED
  --region ${{ inputs.aws-region }} \
  --query 'ChangeSetId' \
  --output text)
```

### 2. Documentation Updates
- Updated VALIDATION_GUIDE.md with correct troubleshooting steps
- Updated VALIDATION_CHECKLIST.md to remove incorrect region restrictions
- Clarified that cross-region deployment is supported

## How Cross-Region Deployment Works

The SAR application is published in `eu-west-1`, but can be deployed to any AWS region:

1. **Application ID**: Always references the source region (eu-west-1)
   ```
   arn:aws:serverlessrepo:eu-west-1:021259937026:applications/MockNest-Serverless
   ```

2. **Target Region**: Specified via `--region` flag
   ```
   --region us-west-2  # Deploy to us-west-2
   --region ap-southeast-1  # Deploy to ap-southeast-1
   ```

3. **CloudFormation Stack**: Created in the target region with resources deployed there

This is exactly how the AWS Console works - you select a region in the top-right, then deploy a SAR application from any source region.

## Testing the Fix

To verify the fix works:

```bash
# Test deployment to us-west-2
gh workflow run sar-test-pipeline.yml \
  --field aws-region=us-west-2 \
  --field version=1.0.0-test

# Test deployment to ap-southeast-1
gh workflow run sar-test-pipeline.yml \
  --field aws-region=ap-southeast-1 \
  --field version=1.0.0-test
```

## Expected Behavior After Fix

1. ✅ Change set creation succeeds
2. ✅ Stack deploys to target region
3. ✅ Unique DeploymentName for each test run
4. ✅ All default parameters used (LambdaMemorySize=1024, LambdaTimeout=120, etc.)
5. ✅ Health checks pass
6. ✅ Cleanup removes test resources

## Additional Notes

### Why DeploymentName is Important
The `DeploymentName` parameter is used as the API Gateway stage name. Each test run needs a unique value to avoid conflicts and ensure proper resource isolation.

### Default Parameters
The fix uses only the `DeploymentName` parameter override. All other parameters use their defaults from the SAM template:
- `LambdaMemorySize`: 1024 MB
- `LambdaTimeout`: 120 seconds
- `BedrockModelName`: AmazonNovaPro
- `BedrockInferenceMode`: AUTO
- `BedrockGenerationMaxRetries`: 1

### Future Enhancements
If needed, we can add more parameter overrides for testing different configurations:
```yaml
--parameter-overrides \
  DeploymentName=sar-test-${{ github.run_id }} \
  LambdaMemorySize=512 \
  LambdaTimeout=60
```

But for now, we're testing with default parameters as requested.

---

_Fix implemented: 2026-03-22_
