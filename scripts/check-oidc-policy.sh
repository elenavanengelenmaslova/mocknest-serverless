#!/bin/bash
# =============================================================================
# OIDC Policy Regression Check Script
# =============================================================================
# This script validates that the OIDC role configuration does NOT contain
# overly permissive patterns. It checks:
#   - Trust policy uses StringEquals (not StringLike) for subject claim
#   - No wildcard branch patterns (feature/*, bugfix/*, hotfix/*, pull_request)
#   - No PowerUserAccess managed policy
#   - No wildcard service permissions (s3:*, lambda:*, apigateway:*, sqs:*, logs:*, bedrock:*)
#   - iam:PassRole is scoped (not Resource: '*' without iam:PassedToService condition)
#   - workflow-deploy-aws.yml uses GitHub Environment
#   - workflow-sar-publish.yml uses GitHub Environment
#
# Exit code:
#   0 = All checks pass (secure configuration)
#   1 = One or more violations found (insecure configuration)
#
# Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.9, 2.9
# =============================================================================

set -euo pipefail

# File paths (relative to repository root)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

CFN_TEMPLATE="$REPO_ROOT/deployment/aws/shared/github-oidc-role.yaml"
UPDATE_SCRIPT="$REPO_ROOT/deployment/aws/shared/update-existing-oidc-role.sh"
DEPLOY_WORKFLOW="$REPO_ROOT/.github/workflows/workflow-deploy-aws.yml"
SAR_WORKFLOW="$REPO_ROOT/.github/workflows/workflow-sar-publish.yml"

VIOLATIONS=0
VIOLATION_DETAILS=""

# Helper function to record a violation
record_violation() {
  local check_name="$1"
  local detail="$2"
  VIOLATIONS=$((VIOLATIONS + 1))
  VIOLATION_DETAILS="${VIOLATION_DETAILS}\n  [VIOLATION ${VIOLATIONS}] ${check_name}: ${detail}"
}

echo "=============================================="
echo " OIDC Policy Security Check"
echo "=============================================="
echo ""

# --- Verify required files exist ---
for file in "$CFN_TEMPLATE" "$UPDATE_SCRIPT" "$DEPLOY_WORKFLOW" "$SAR_WORKFLOW"; do
  if [ ! -f "$file" ]; then
    echo "ERROR: Required file not found: $file"
    exit 2
  fi
done

echo "All required files found. Running checks..."
echo ""

# =============================================================================
# CHECK 1: Trust policy uses StringLike instead of StringEquals for subject claim
# (Requirement 1.1)
# =============================================================================
echo "--- Check 1: Trust policy condition operator (CloudFormation template) ---"
if grep -q "StringLike" "$CFN_TEMPLATE"; then
  # Verify it's used for the subject claim (not just audience)
  if grep -A2 "StringLike" "$CFN_TEMPLATE" | grep -q "token.actions.githubusercontent.com:sub"; then
    record_violation "Trust Policy Operator" \
      "StringLike used for 'token.actions.githubusercontent.com:sub' in $CFN_TEMPLATE (should be StringEquals)"
    echo "  FAIL: StringLike found for subject claim condition"
  fi
else
  echo "  PASS: No StringLike for subject claim"
fi

# =============================================================================
# CHECK 2: Trust policy contains wildcard branch patterns
# (Requirement 1.1)
# =============================================================================
echo "--- Check 2: Wildcard branch patterns in trust policy (CloudFormation template) ---"
BRANCH_PATTERNS=("feature/\*" "bugfix/\*" "hotfix/\*" "pull_request")
for pattern in "${BRANCH_PATTERNS[@]}"; do
  if grep -q "$pattern" "$CFN_TEMPLATE"; then
    record_violation "Wildcard Branch Pattern" \
      "Pattern '$pattern' found in trust policy in $CFN_TEMPLATE"
    echo "  FAIL: '$pattern' found in CloudFormation template"
  fi
done

# =============================================================================
# CHECK 3: PowerUserAccess managed policy is attached
# (Requirement 1.2)
# =============================================================================
echo "--- Check 3: PowerUserAccess managed policy ---"
if grep -q "PowerUserAccess" "$CFN_TEMPLATE"; then
  record_violation "PowerUserAccess" \
    "PowerUserAccess managed policy ARN found in ManagedPolicyArns in $CFN_TEMPLATE"
  echo "  FAIL: PowerUserAccess found"
else
  echo "  PASS: No PowerUserAccess"
fi

# =============================================================================
# CHECK 4: Wildcard service permissions with Resource: '*'
# (Requirement 1.3)
# =============================================================================
echo "--- Check 4: Wildcard service permissions ---"
WILDCARD_SERVICES=("s3:\*" "lambda:\*" "apigateway:\*" "sqs:\*" "logs:\*" "bedrock:\*")
for svc in "${WILDCARD_SERVICES[@]}"; do
  if grep -q "$svc" "$CFN_TEMPLATE"; then
    record_violation "Wildcard Service Permission" \
      "Action '$svc' with Resource: '*' found in $CFN_TEMPLATE"
    echo "  FAIL: '$svc' found"
  fi
done

# =============================================================================
# CHECK 5: iam:PassRole with Resource: '*' without iam:PassedToService condition
# (Requirement 1.4)
# =============================================================================
echo "--- Check 5: Unrestricted iam:PassRole ---"
if grep -q "iam:PassRole" "$CFN_TEMPLATE"; then
  # Check if there's a PassedToService condition anywhere in the template
  if ! grep -q "iam:PassedToService" "$CFN_TEMPLATE"; then
    record_violation "Unrestricted iam:PassRole" \
      "iam:PassRole found without iam:PassedToService condition in $CFN_TEMPLATE"
    echo "  FAIL: iam:PassRole without PassedToService condition"
  else
    echo "  PASS: iam:PassRole has PassedToService condition"
  fi
else
  echo "  PASS: No iam:PassRole found"
fi

# =============================================================================
# CHECK 6: update-existing-oidc-role.sh uses StringLike / wildcard branches
# (Requirement 1.5)
# =============================================================================
echo "--- Check 6: Trust policy in update script ---"
if grep -q "StringLike" "$UPDATE_SCRIPT"; then
  record_violation "Update Script StringLike" \
    "StringLike used in trust policy in $UPDATE_SCRIPT (should be StringEquals)"
  echo "  FAIL: StringLike found in update script"
else
  echo "  PASS: No StringLike in update script"
fi

# Check for wildcard branch patterns in the update script
for pattern in "${BRANCH_PATTERNS[@]}"; do
  if grep -q "$pattern" "$UPDATE_SCRIPT"; then
    record_violation "Update Script Branch Pattern" \
      "Pattern '$pattern' found in $UPDATE_SCRIPT"
    echo "  FAIL: '$pattern' found in update script"
  fi
done

# =============================================================================
# CHECK 7: workflow-deploy-aws.yml does not use GitHub Environment
# (Requirement 1.6)
# =============================================================================
echo "--- Check 7: GitHub Environment in workflow-deploy-aws.yml ---"
# Look for 'environment:' at the job level (not just as an input definition)
# The job should have 'environment: ${{ inputs.environment }}' or similar
if grep -A50 "build-and-deploy:" "$DEPLOY_WORKFLOW" | grep -q "^[[:space:]]*environment:"; then
  echo "  PASS: GitHub Environment found in build-and-deploy job"
else
  record_violation "Missing GitHub Environment (deploy)" \
    "workflow-deploy-aws.yml 'build-and-deploy' job does not use GitHub Environment"
  echo "  FAIL: No GitHub Environment in build-and-deploy job"
fi

# =============================================================================
# CHECK 8: workflow-sar-publish.yml does not use GitHub Environment
# (Requirement 1.7)
# =============================================================================
echo "--- Check 8: GitHub Environment in workflow-sar-publish.yml ---"
# Look for 'environment:' at the job level in the publish job
if grep -A50 "publish:" "$SAR_WORKFLOW" | grep -q "^[[:space:]]*environment:"; then
  echo "  PASS: GitHub Environment found in publish job"
else
  record_violation "Missing GitHub Environment (SAR publish)" \
    "workflow-sar-publish.yml 'publish' job does not use GitHub Environment"
  echo "  FAIL: No GitHub Environment in publish job"
fi

# =============================================================================
# BUG CONDITION RESULTS
# =============================================================================
echo ""
echo "=============================================="
echo " BUG CONDITION RESULTS"
echo "=============================================="

if [ $VIOLATIONS -gt 0 ]; then
  echo ""
  echo "  ❌ FAILED: $VIOLATIONS violation(s) found"
  echo -e "$VIOLATION_DETAILS"
  echo ""
  echo "  The OIDC role configuration is OVERLY PERMISSIVE."
  echo "  See the design document for the expected secure configuration."
  echo ""
  BUG_CHECK_FAILED=1
else
  echo ""
  echo "  ✅ PASSED: No bug condition violations found"
  echo ""
  BUG_CHECK_FAILED=0
fi

# =============================================================================
# =============================================================================
# PRESERVATION CHECKS (Property 2)
# =============================================================================
# These checks verify that deployment workflows continue to function correctly.
# They validate baseline behavior that MUST be preserved by the fix.
#
# Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7
# =============================================================================

PRESERVATION_FAILURES=0
PRESERVATION_DETAILS=""

# Helper function to record a preservation failure
record_preservation_failure() {
  local check_name="$1"
  local detail="$2"
  PRESERVATION_FAILURES=$((PRESERVATION_FAILURES + 1))
  PRESERVATION_DETAILS="${PRESERVATION_DETAILS}\n  [PRESERVATION FAILURE ${PRESERVATION_FAILURES}] ${check_name}: ${detail}"
}

echo ""
echo "=============================================="
echo " PRESERVATION CHECKS"
echo "=============================================="
echo ""

# File paths for preservation checks
FEATURE_WORKFLOW="$REPO_ROOT/.github/workflows/feature-aws.yml"
MAIN_WORKFLOW="$REPO_ROOT/.github/workflows/main-aws.yml"
SETUP_SCRIPT="$REPO_ROOT/deployment/aws/shared/setup-github-oidc.sh"
SAM_TEMPLATE="$REPO_ROOT/deployment/aws/sam/template.yaml"

# --- Verify preservation files exist ---
for file in "$FEATURE_WORKFLOW" "$MAIN_WORKFLOW" "$SETUP_SCRIPT" "$SAM_TEMPLATE"; do
  if [ ! -f "$file" ]; then
    echo "ERROR: Required preservation file not found: $file"
    exit 2
  fi
done

echo "All preservation files found. Running checks..."
echo ""

# =============================================================================
# PRESERVATION CHECK 1: feature-aws.yml does NOT contain id-token: write
# (Requirement 3.3 - feature branches do not assume AWS roles)
# =============================================================================
echo "--- Preservation Check 1: feature-aws.yml does NOT request id-token: write ---"
if grep -q "id-token:.*write" "$FEATURE_WORKFLOW"; then
  record_preservation_failure "Feature Branch OIDC" \
    "feature-aws.yml contains 'id-token: write' — feature branches should NOT assume AWS roles"
  echo "  FAIL: id-token: write found in feature-aws.yml"
else
  echo "  PASS: feature-aws.yml does NOT request id-token: write"
fi

# =============================================================================
# PRESERVATION CHECK 2: main-aws.yml still references workflow-deploy-aws.yml
# (Requirement 3.1 - main branch deployment continues to work)
# =============================================================================
echo "--- Preservation Check 2: main-aws.yml references workflow-deploy-aws.yml ---"
if grep -q "workflow-deploy-aws.yml" "$MAIN_WORKFLOW"; then
  echo "  PASS: main-aws.yml references workflow-deploy-aws.yml"
else
  record_preservation_failure "Main Deploy Reference" \
    "main-aws.yml does NOT reference workflow-deploy-aws.yml — deployment pipeline broken"
  echo "  FAIL: workflow-deploy-aws.yml reference missing from main-aws.yml"
fi

# =============================================================================
# PRESERVATION CHECK 3: setup-github-oidc.sh still creates the OIDC provider
# (Requirement 3.7 - initial setup continues to work)
# =============================================================================
echo "--- Preservation Check 3: setup-github-oidc.sh creates OIDC provider ---"
if grep -q "cloudformation deploy" "$SETUP_SCRIPT" && grep -q "github-oidc-role.yaml" "$SETUP_SCRIPT"; then
  echo "  PASS: setup-github-oidc.sh deploys the OIDC CloudFormation stack"
else
  record_preservation_failure "OIDC Setup Script" \
    "setup-github-oidc.sh does NOT deploy the OIDC CloudFormation stack"
  echo "  FAIL: OIDC provider creation missing from setup script"
fi

# =============================================================================
# PRESERVATION CHECK 4: SAM template is unchanged (not part of this fix)
# (Requirement 3.5 - SAM application resources unaffected)
# =============================================================================
echo "--- Preservation Check 4: SAM template is not modified by this fix ---"
# Check that the SAM template has not been modified in the working tree
if git -C "$REPO_ROOT" diff --quiet -- deployment/aws/sam/template.yaml 2>/dev/null; then
  # Also check staged changes
  if git -C "$REPO_ROOT" diff --cached --quiet -- deployment/aws/sam/template.yaml 2>/dev/null; then
    echo "  PASS: SAM template (deployment/aws/sam/template.yaml) is unchanged"
  else
    record_preservation_failure "SAM Template Modified (staged)" \
      "deployment/aws/sam/template.yaml has staged changes — this fix should NOT modify the SAM template"
    echo "  FAIL: SAM template has staged modifications"
  fi
else
  record_preservation_failure "SAM Template Modified" \
    "deployment/aws/sam/template.yaml has been modified — this fix should NOT modify the SAM template"
  echo "  FAIL: SAM template has been modified"
fi

# =============================================================================
# PRESERVATION RESULTS
# =============================================================================
echo ""
echo "=============================================="
echo " PRESERVATION RESULTS"
echo "=============================================="

if [ $PRESERVATION_FAILURES -gt 0 ]; then
  echo ""
  echo "  ❌ FAILED: $PRESERVATION_FAILURES preservation check(s) failed"
  echo -e "$PRESERVATION_DETAILS"
  echo ""
  echo "  Deployment workflows may be broken by the fix."
  echo ""
  PRESERVATION_FAILED=1
else
  echo ""
  echo "  ✅ PASSED: All preservation checks pass"
  echo ""
  echo "  Deployment workflows continue to function correctly."
  echo ""
  PRESERVATION_FAILED=0
fi

# =============================================================================
# OVERALL RESULTS
# =============================================================================
echo ""
echo "=============================================="
echo " OVERALL RESULTS"
echo "=============================================="
echo ""
echo "  Bug Condition Checks: $([ $BUG_CHECK_FAILED -eq 1 ] && echo '❌ FAILED' || echo '✅ PASSED')"
echo "  Preservation Checks:  $([ $PRESERVATION_FAILED -eq 1 ] && echo '❌ FAILED' || echo '✅ PASSED')"
echo ""

# Exit with failure if EITHER section fails
if [ $BUG_CHECK_FAILED -eq 1 ] || [ $PRESERVATION_FAILED -eq 1 ]; then
  exit 1
else
  exit 0
fi
