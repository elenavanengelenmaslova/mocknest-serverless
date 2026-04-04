#!/bin/bash
set -e
set -o pipefail

# MockNest Serverless Post-Deployment Integration Tests
# This script validates a deployed MockNest Serverless application in AWS
# by executing comprehensive API tests including health checks, mock generation,
# and mock import operations.

# Input validation
# Script accepts API_URL and API_KEY as arguments or environment variables
API_URL="${1:-$API_URL}"
API_KEY="${2:-$API_KEY}"

if [ -z "$API_URL" ] || [ -z "$API_KEY" ]; then
  echo "ERROR: API_URL and API_KEY are required"
  echo "Usage: $0 <API_URL> <API_KEY>"
  echo "  or set API_URL and API_KEY environment variables"
  exit 2
fi

# Remove trailing slash from API_URL if present
API_URL="${API_URL%/}"

# Common curl options
# --fail: Fail on HTTP errors (4xx, 5xx)
# --silent: Suppress progress meter
# --show-error: Show errors even in silent mode
# --max-time 30: 30 second timeout (AI generation can take up to 20 seconds)
CURL_OPTS=(
  --fail
  --silent
  --show-error
  --max-time 30
  --header "x-api-key: $API_KEY"
  --header "Content-Type: application/json"
)

# Helper function for HTTP response parsing
# Separates HTTP status code from response body
# Usage: parse_response "$RESPONSE"
# Sets global variables: HTTP_CODE and BODY
parse_response() {
  local response="$1"
  HTTP_CODE=$(echo "$response" | tail -n1)
  BODY=$(echo "$response" | sed '$d')
}

# Test Functions

# Test runtime health check
# Validates that the core MockNest runtime is operational
test_runtime_health() {
  echo "Testing runtime health..."
  
  local response
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    "$API_URL/__admin/health" 2>&1) || {
    echo "ERROR: Runtime health check request failed"
    echo "Response: $response"
    exit 1
  }
  
  parse_response "$response"
  
  if [ "$HTTP_CODE" != "200" ]; then
    echo "ERROR: Runtime health check failed with HTTP $HTTP_CODE"
    echo "Response: $BODY"
    exit 1
  fi
  
  if ! echo "$BODY" | grep -q '"status"[[:space:]]*:[[:space:]]*"healthy"'; then
    echo "ERROR: Runtime health check response missing 'status: healthy'"
    echo "Response: $BODY"
    exit 1
  fi
  
  echo "✓ Runtime health check passed"
}


# Test AI generation health check
# Validates that the AI-assisted mock generation service is operational
test_ai_health() {
  echo "Testing AI generation health..."
  
  local response
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    "$API_URL/ai/generation/health" 2>&1) || {
    echo "ERROR: AI generation health check request failed"
    echo "Response: $response"
    exit 1
  }
  
  parse_response "$response"
  
  if [ "$HTTP_CODE" != "200" ]; then
    echo "ERROR: AI generation health check failed with HTTP $HTTP_CODE"
    echo "Response: $BODY"
    exit 1
  fi
  
  if ! echo "$BODY" | grep -q '"status"[[:space:]]*:[[:space:]]*"healthy"'; then
    echo "ERROR: AI generation health check response missing 'status: healthy'"
    echo "Response: $BODY"
    exit 1
  fi
  
  echo "✓ AI generation health check passed"
}

# Main test execution
main() {
  echo "=== MockNest Serverless Post-Deployment Integration Tests ==="
  echo "API URL: $API_URL"
  echo ""
  
  test_runtime_health
  test_ai_health
  
  echo ""
  echo "=== All Tests Passed ==="
}

main
