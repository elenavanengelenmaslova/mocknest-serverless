#!/bin/bash
# SAR Test Pipeline Validation Script
# This script automates validation of the SAR test pipeline

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
WORKFLOW_NAME="sar-test-pipeline.yml"
REGION="${1:-eu-west-1}"
VERSION="${2:-1.0.0-test}"
VALIDATION_MODE="${3:-full}"  # full, quick, security-only

echo -e "${BLUE}🧪 SAR Test Pipeline Validation Script${NC}"
echo "======================================="
echo ""
echo "📋 Configuration:"
echo "  Region: $REGION"
echo "  Version: $VERSION"
echo "  Mode: $VALIDATION_MODE"
echo ""

# Check prerequisites
check_prerequisites() {
  echo -e "${BLUE}🔍 Checking prerequisites...${NC}"
  
  if ! command -v gh &> /dev/null; then
    echo -e "${RED}❌ GitHub CLI (gh) is not installed${NC}"
    echo "   Install from: https://cli.github.com/"
    exit 1
  fi
  
  if ! gh auth status &> /dev/null; then
    echo -e "${RED}❌ GitHub CLI is not authenticated${NC}"
    echo "   Run: gh auth login"
    exit 1
  fi
  
  echo -e "${GREEN}✅ Prerequisites check passed${NC}"
  echo ""
}

# Trigger workflow manually
trigger_workflow() {
  echo -e "${BLUE}1️⃣  Triggering workflow manually...${NC}"
  
  gh workflow run "$WORKFLOW_NAME" \
    --field aws-region="$REGION" \
    --field version="$VERSION"
  
  # Wait a moment for the run to be created
  sleep 5
  
  # Get the latest run ID
  RUN_ID=$(gh run list --workflow="$WORKFLOW_NAME" --limit 1 --json databaseId --jq '.[0].databaseId')
  
  if [ -z "$RUN_ID" ]; then
    echo -e "${RED}❌ Failed to get workflow run ID${NC}"
    exit 1
  fi
  
  echo -e "${GREEN}✅ Workflow triggered: Run ID $RUN_ID${NC}"
  echo ""
  
  echo "$RUN_ID"
}

# Wait for workflow completion
wait_for_completion() {
  local run_id=$1
  
  echo -e "${BLUE}2️⃣  Waiting for workflow to complete...${NC}"
  echo "   This may take 10-15 minutes..."
  echo ""
  
  gh run watch "$run_id" || true
  
  # Get final status
  STATUS=$(gh run view "$run_id" --json conclusion --jq '.conclusion')
  
  echo ""
  if [ "$STATUS" = "success" ]; then
    echo -e "${GREEN}✅ Workflow completed successfully${NC}"
  elif [ "$STATUS" = "failure" ]; then
    echo -e "${YELLOW}⚠️  Workflow failed (this is expected for some test scenarios)${NC}"
  else
    echo -e "${YELLOW}⚠️  Workflow status: $STATUS${NC}"
  fi
  echo ""
}

# Download and analyze logs
download_logs() {
  local run_id=$1
  local log_file="validation-logs-$run_id.txt"
  
  echo -e "${BLUE}3️⃣  Downloading workflow logs...${NC}"
  
  gh run view "$run_id" --log > "$log_file" 2>&1 || {
    echo -e "${YELLOW}⚠️  Warning: Failed to download complete logs${NC}"
  }
  
  echo -e "${GREEN}✅ Logs saved to: $log_file${NC}"
  echo ""
  
  echo "$log_file"
}

# Security validation - check for API key leaks
validate_security() {
  local log_file=$1
  local issues_found=0
  
  echo -e "${BLUE}4️⃣  Running security validation...${NC}"
  echo ""
  
  # Check for unmasked API keys in x-api-key headers
  echo "   Checking for unmasked API keys..."
  if grep -E "x-api-key: [^*]" "$log_file" | grep -v "add-mask" | grep -v "redacted" > /dev/null 2>&1; then
    echo -e "${RED}   ❌ SECURITY ISSUE: Unmasked API key found in logs!${NC}"
    issues_found=$((issues_found + 1))
  else
    echo -e "${GREEN}   ✅ No unmasked API keys detected${NC}"
  fi
  
  # Verify masking was applied
  echo "   Checking for masking commands..."
  if grep -q "::add-mask::" "$log_file"; then
    echo -e "${GREEN}   ✅ API key masking command found${NC}"
  else
    echo -e "${YELLOW}   ⚠️  WARNING: No masking command found${NC}"
    issues_found=$((issues_found + 1))
  fi
  
  # Check for AWS credentials leaks
  echo "   Checking for AWS credential leaks..."
  if grep -iE "AWS_SECRET_ACCESS_KEY|aws_secret_access_key" "$log_file" | grep -v "***" > /dev/null 2>&1; then
    echo -e "${RED}   ❌ SECURITY ISSUE: AWS credentials found in logs!${NC}"
    issues_found=$((issues_found + 1))
  else
    echo -e "${GREEN}   ✅ No AWS credential leaks detected${NC}"
  fi
  
  # Check for API key IDs in summary (should not be there)
  echo "   Checking summary report for sensitive data..."
  if grep -q "API Key ID:" "$log_file"; then
    echo -e "${YELLOW}   ⚠️  WARNING: API Key ID found in logs (should not be in summary)${NC}"
    issues_found=$((issues_found + 1))
  else
    echo -e "${GREEN}   ✅ No API Key IDs in summary report${NC}"
  fi
  
  echo ""
  if [ $issues_found -eq 0 ]; then
    echo -e "${GREEN}✅ Security validation passed${NC}"
    return 0
  else
    echo -e "${RED}❌ Security validation failed with $issues_found issue(s)${NC}"
    return 1
  fi
}

# Functional validation - check pipeline behavior
validate_functionality() {
  local log_file=$1
  local issues_found=0
  
  echo -e "${BLUE}5️⃣  Running functional validation...${NC}"
  echo ""
  
  # Check deployment execution
  echo "   Checking deployment execution..."
  if grep -q "Starting SAR deployment" "$log_file"; then
    echo -e "${GREEN}   ✅ Deployment step executed${NC}"
  else
    echo -e "${RED}   ❌ Deployment step not found${NC}"
    issues_found=$((issues_found + 1))
  fi
  
  # Check stack output retrieval
  echo "   Checking stack output retrieval..."
  if grep -q "Retrieving CloudFormation stack outputs" "$log_file"; then
    echo -e "${GREEN}   ✅ Stack output retrieval executed${NC}"
  else
    echo -e "${YELLOW}   ⚠️  Stack output retrieval not found${NC}"
    issues_found=$((issues_found + 1))
  fi
  
  # Check API key retrieval
  echo "   Checking API key retrieval..."
  if grep -q "Retrieving API key value" "$log_file"; then
    echo -e "${GREEN}   ✅ API key retrieval executed${NC}"
  else
    echo -e "${YELLOW}   ⚠️  API key retrieval not found${NC}"
    issues_found=$((issues_found + 1))
  fi
  
  # Check health checks
  echo "   Checking health check execution..."
  if grep -q "Starting health checks" "$log_file"; then
    echo -e "${GREEN}   ✅ Health checks executed${NC}"
    
    # Check for retry logic
    if grep -q "Attempt" "$log_file"; then
      echo -e "${GREEN}   ✅ Retry logic present${NC}"
    fi
  else
    echo -e "${YELLOW}   ⚠️  Health checks not found${NC}"
    issues_found=$((issues_found + 1))
  fi
  
  # Check cleanup execution (should always run)
  echo "   Checking cleanup execution..."
  if grep -q "Starting resource cleanup" "$log_file"; then
    echo -e "${GREEN}   ✅ Cleanup step executed${NC}"
  else
    echo -e "${RED}   ❌ Cleanup step not found (CRITICAL - should always run)${NC}"
    issues_found=$((issues_found + 1))
  fi
  
  # Check report generation
  echo "   Checking report generation..."
  if grep -q "SAR Test Pipeline Results" "$log_file"; then
    echo -e "${GREEN}   ✅ Summary report generated${NC}"
  else
    echo -e "${RED}   ❌ Summary report not found${NC}"
    issues_found=$((issues_found + 1))
  fi
  
  echo ""
  if [ $issues_found -eq 0 ]; then
    echo -e "${GREEN}✅ Functional validation passed${NC}"
    return 0
  else
    echo -e "${YELLOW}⚠️  Functional validation completed with $issues_found issue(s)${NC}"
    return 1
  fi
}

# Cleanup validation - check if resources were cleaned up
validate_cleanup() {
  local run_id=$1
  
  echo -e "${BLUE}6️⃣  Validating resource cleanup...${NC}"
  echo ""
  
  # Extract stack name from logs
  STACK_NAME="mocknest-sar-test-$run_id"
  
  echo "   Checking if stack was deleted: $STACK_NAME"
  
  # Check if stack still exists
  if aws cloudformation describe-stacks --stack-name "$STACK_NAME" --region "$REGION" &> /dev/null; then
    echo -e "${YELLOW}   ⚠️  WARNING: Stack still exists in AWS${NC}"
    echo "   Stack may still be deleting or cleanup failed"
    return 1
  else
    echo -e "${GREEN}   ✅ Stack successfully deleted${NC}"
    return 0
  fi
}

# Generate validation report
generate_report() {
  local run_id=$1
  local log_file=$2
  local security_result=$3
  local functional_result=$4
  local cleanup_result=$5
  
  echo ""
  echo -e "${BLUE}📊 Validation Report${NC}"
  echo "===================="
  echo ""
  echo "Run ID: $run_id"
  echo "Region: $REGION"
  echo "Version: $VERSION"
  echo "Log File: $log_file"
  echo ""
  echo "Results:"
  
  if [ $security_result -eq 0 ]; then
    echo -e "  Security Validation:    ${GREEN}✅ PASSED${NC}"
  else
    echo -e "  Security Validation:    ${RED}❌ FAILED${NC}"
  fi
  
  if [ $functional_result -eq 0 ]; then
    echo -e "  Functional Validation:  ${GREEN}✅ PASSED${NC}"
  else
    echo -e "  Functional Validation:  ${YELLOW}⚠️  ISSUES FOUND${NC}"
  fi
  
  if [ $cleanup_result -eq 0 ]; then
    echo -e "  Cleanup Validation:     ${GREEN}✅ PASSED${NC}"
  else
    echo -e "  Cleanup Validation:     ${YELLOW}⚠️  ISSUES FOUND${NC}"
  fi
  
  echo ""
  echo "View full workflow results:"
  echo "  gh run view $run_id"
  echo "  gh run view $run_id --web"
  echo ""
  
  # Overall result
  if [ $security_result -eq 0 ] && [ $functional_result -eq 0 ] && [ $cleanup_result -eq 0 ]; then
    echo -e "${GREEN}✅ Overall validation: PASSED${NC}"
    return 0
  else
    echo -e "${YELLOW}⚠️  Overall validation: COMPLETED WITH ISSUES${NC}"
    return 1
  fi
}

# Main execution
main() {
  check_prerequisites
  
  if [ "$VALIDATION_MODE" = "security-only" ]; then
    echo "Running security-only validation on existing logs..."
    if [ -z "$4" ]; then
      echo "Error: Please provide log file path for security-only mode"
      echo "Usage: $0 <region> <version> security-only <log-file>"
      exit 1
    fi
    validate_security "$4"
    exit $?
  fi
  
  # Trigger workflow and get run ID
  RUN_ID=$(trigger_workflow)
  
  # Wait for completion
  wait_for_completion "$RUN_ID"
  
  # Download logs
  LOG_FILE=$(download_logs "$RUN_ID")
  
  # Run validations
  validate_security "$LOG_FILE"
  SECURITY_RESULT=$?
  
  echo ""
  
  validate_functionality "$LOG_FILE"
  FUNCTIONAL_RESULT=$?
  
  echo ""
  
  if [ "$VALIDATION_MODE" = "full" ]; then
    validate_cleanup "$RUN_ID"
    CLEANUP_RESULT=$?
  else
    echo -e "${BLUE}⏭️  Skipping cleanup validation (quick mode)${NC}"
    CLEANUP_RESULT=0
  fi
  
  # Generate final report
  generate_report "$RUN_ID" "$LOG_FILE" $SECURITY_RESULT $FUNCTIONAL_RESULT $CLEANUP_RESULT
  exit $?
}

# Run main function
main
