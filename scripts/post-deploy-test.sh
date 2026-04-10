#!/bin/bash
set -e
set -o pipefail

# MockNest Serverless Post-Deployment Integration Tests
# This script validates a deployed MockNest Serverless application in AWS
# by executing comprehensive API tests including health checks, mock generation,
# and mock import operations.
#
# Usage:
#   $0 [TEST_SUITE] <API_URL> <API_KEY>
#   $0 [TEST_SUITE]  # if API_URL and API_KEY are set as environment variables
#
# TEST_SUITE options:
#   setup    - Run health checks and cleanup only
#   rest     - Run REST/OpenAPI generation and import tests
#   graphql  - Run GraphQL generation and import tests (future)
#   soap     - Run SOAP/WSDL generation and import tests (future)
#   webhook  - Run webhook delivery and redaction tests
#   all      - Run all tests sequentially (default)
#
# Examples:
#   $0 setup https://api.example.com abc123key
#   $0 rest https://api.example.com abc123key
#   $0 webhook https://api.example.com abc123key
#   API_URL=https://api.example.com API_KEY=abc123key $0 all

# Input validation
# Script accepts TEST_SUITE as first argument (optional, defaults to "all")
# API_URL and API_KEY can be provided as arguments or environment variables
TEST_SUITE="${1:-all}"

# Check if first argument looks like a URL (starts with http)
# If so, treat it as API_URL and default TEST_SUITE to "all"
if [[ "$TEST_SUITE" =~ ^https?:// ]]; then
  API_URL="$1"
  API_KEY="${2:-$API_KEY}"
  TEST_SUITE="all"
else
  # First argument is TEST_SUITE, get API_URL and API_KEY from remaining args or env
  API_URL="${2:-$API_URL}"
  API_KEY="${3:-$API_KEY}"
fi

if [ -z "$API_URL" ]; then
  echo "ERROR: API_URL is required"
  echo ""
  echo "Usage: $0 [TEST_SUITE] <API_URL> [API_KEY]"
  echo "   or: $0 [TEST_SUITE]  # if API_URL (and API_KEY for API_KEY mode) are set as environment variables"
  echo ""
  echo "TEST_SUITE options: setup, rest, graphql, soap, webhook, all (default: all)"
  echo ""
  echo "Examples:"
  echo "  $0 setup https://api.example.com abc123key"
  echo "  $0 rest https://api.example.com abc123key"
  echo "  API_URL=https://api.example.com API_KEY=abc123key $0 all"
  exit 2
fi

# Remove trailing slash from API_URL if present
API_URL="${API_URL%/}"

# Detect auth mode: AUTH_MODE env var takes precedence, otherwise require API_KEY
AUTH_MODE="${AUTH_MODE:-API_KEY}"

if [ "$AUTH_MODE" = "IAM" ]; then
  echo "Auth mode: IAM (SigV4 signing)"
  # Build curl options for IAM mode — no API key header, use SigV4
  CURL_OPTS=(
    --fail
    --silent
    --show-error
    --max-time 30
    --aws-sigv4 "aws:amz:${AWS_DEFAULT_REGION:-${AWS_REGION:-us-east-1}}:execute-api"
    --user "${AWS_ACCESS_KEY_ID}:${AWS_SECRET_ACCESS_KEY}"
    --header "x-amz-security-token: ${AWS_SESSION_TOKEN}"
    --header "Content-Type: application/json"
  )
else
  echo "Auth mode: API_KEY"
  if [ -z "$API_KEY" ]; then
    echo "ERROR: API_KEY is required in API_KEY auth mode"
    exit 2
  fi
  # Build curl options for API_KEY mode
  CURL_OPTS=(
    --fail
    --silent
    --show-error
    --max-time 30
    --header "x-api-key: $API_KEY"
    --header "Content-Type: application/json"
  )
fi

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

# Test delete all mappings
# Clears all existing mappings to ensure tests start with a clean state
test_delete_all_mappings() {
  echo "Testing delete all mappings..."
  
  local response
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request DELETE \
    "$API_URL/__admin/mappings" 2>&1) || {
    echo "ERROR: Delete all mappings request failed"
    echo "Response: $response"
    exit 1
  }
  
  parse_response "$response"
  
  if [ "$HTTP_CODE" != "200" ]; then
    echo "ERROR: Delete all mappings failed with HTTP $HTTP_CODE"
    echo "Response: $BODY"
    exit 1
  fi
  
  echo "✓ Delete all mappings passed"
}

# Test REST/OpenAPI mock generation
# Generates WireMock mappings from the Petstore OpenAPI specification
test_rest_generation() {
  echo "Testing REST/OpenAPI mock generation..."
  
  # Request payload matching Postman collection
  local request_body
  request_body='{
    "namespace": {
        "apiName": "petstore",
        "client": null
    },
    "specification": null,
    "specificationUrl": "https://petstore3.swagger.io/api/v3/openapi.json",
    "format": "OPENAPI_3",
    "description": "Generate mocks for 4 pets, only GET endpoints. 1 pet is a bird with image: https://media.s-bol.com/q0Q9jQ7vDjGR/wpzn5L1/550x550.jpg, available, new, tag id=1 name=new. API call to get all new pets returns that bird. The other 3 pets are available but not new.",
    "options": {
        "enableValidation": true
    }
}'
  
  local response
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request POST \
    --data "$request_body" \
    "$API_URL/ai/generation/from-spec" 2>&1) || {
    echo "ERROR: REST generation request failed"
    echo "Response: $response"
    exit 1
  }
  
  parse_response "$response"
  
  if [ "$HTTP_CODE" != "200" ]; then
    echo "ERROR: REST generation failed with HTTP $HTTP_CODE"
    echo "Response: $BODY"
    exit 1
  fi
  
  # Validate response contains "mappings" array
  if ! echo "$BODY" | grep -q '"mappings"'; then
    echo "ERROR: REST generation response missing 'mappings' array"
    echo "Response: $BODY"
    exit 1
  fi
  
  # Store generated mappings for import test
  REST_MAPPINGS="$BODY"
  
  echo "✓ REST mock generation passed"
}

# Test REST mock import
# Imports the generated REST/OpenAPI mocks into the WireMock runtime
test_rest_import() {
  echo "Testing REST mock import..."
  
  # Validate that REST_MAPPINGS is set from generation test
  if [ -z "$REST_MAPPINGS" ]; then
    echo "ERROR: REST_MAPPINGS not set. REST generation test must run first."
    exit 1
  fi
  
  local response
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request POST \
    --data "$REST_MAPPINGS" \
    "$API_URL/__admin/mappings/import" 2>&1) || {
    echo "ERROR: REST import request failed"
    echo "Response: $response"
    exit 1
  }
  
  parse_response "$response"
  
  if [ "$HTTP_CODE" != "200" ]; then
    echo "ERROR: REST import failed with HTTP $HTTP_CODE"
    echo "Response: $BODY"
    exit 1
  fi
  
  echo "✓ REST mock import passed"
  
  # Give the system a moment to persist the mappings
  sleep 2
  
  # Debug: List all mappings to verify import
  echo "  Verifying imported mappings..."
  local mappings_list
  mappings_list=$(curl "${CURL_OPTS[@]}" \
    --request GET \
    "$API_URL/__admin/mappings" 2>&1) || {
    echo "WARNING: Could not retrieve mappings list"
  }
  
  # Count how many mappings were imported
  local mapping_count
  mapping_count=$(echo "$mappings_list" | grep -o '"id"' | wc -l || echo "0")
  echo "  Found $mapping_count mappings after import"
}

# Test REST mock invocation
# Validates that imported REST mocks respond correctly to requests
# Tests two endpoints from the Petstore API:
# 1. GET /pet/findByStatus - Find pets by status
# 2. GET /pet/{petId} - Get a specific pet by ID
test_rest_mock_invocation() {
  echo "Testing REST mock invocation..."
  
  # Test 1: GET /mocknest/petstore/pet/findByStatus?status=available
  echo "  Testing GET /mocknest/petstore/pet/findByStatus?status=available..."
  local response
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request GET \
    "$API_URL/mocknest/petstore/pet/findByStatus?status=available" 2>&1) || {
    echo "ERROR: GET /mocknest/petstore/pet/findByStatus request failed"
    echo "Response: $response"
    exit 1
  }
  
  parse_response "$response"
  
  if [ "$HTTP_CODE" != "200" ]; then
    echo "ERROR: GET /mocknest/petstore/pet/findByStatus failed with HTTP $HTTP_CODE"
    echo "Response: $BODY"
    exit 1
  fi
  
  # Validate response is a JSON array
  if ! echo "$BODY" | grep -q '^\['; then
    echo "ERROR: GET /mocknest/petstore/pet/findByStatus response is not a JSON array"
    echo "Response: $BODY"
    exit 1
  fi
  
  echo "  ✓ GET /mocknest/petstore/pet/findByStatus passed"
  
  # Test 2: GET /mocknest/petstore/pet/findByTags?tags=new
  echo "  Testing GET /mocknest/petstore/pet/findByTags?tags=new..."
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request GET \
    "$API_URL/mocknest/petstore/pet/findByTags?tags=new" 2>&1) || {
    echo "ERROR: GET /mocknest/petstore/pet/findByTags request failed"
    echo "Response: $response"
    exit 1
  }
  
  parse_response "$response"
  
  if [ "$HTTP_CODE" != "200" ]; then
    echo "ERROR: GET /mocknest/petstore/pet/findByTags failed with HTTP $HTTP_CODE"
    echo "Response: $BODY"
    exit 1
  fi
  
  # Validate response is a JSON array
  if ! echo "$BODY" | grep -q '^\['; then
    echo "ERROR: GET /mocknest/petstore/pet/findByTags response is not a JSON array"
    echo "Response: $BODY"
    exit 1
  fi
  
  echo "  ✓ GET /mocknest/petstore/pet/findByTags passed"
  
  echo "✓ REST mock invocation passed"
}

# Test GraphQL mock generation
# Generates WireMock mappings from the Countries GraphQL API
test_graphql_generation() {
  echo "Testing GraphQL mock generation..."
  
  # Request payload matching Postman collection
  # Using Countries GraphQL API as specified in requirements
  local request_body
  request_body='{
    "namespace": {
        "apiName": "countries",
        "client": null
    },
    "specification": null,
    "specificationUrl": "https://countries.trevorblades.com/graphql",
    "format": "GRAPHQL",
    "description": "Generate mocks for country queries including country details, continents, and languages with realistic test data. Include country code: \"NL\".",
    "options": {
        "enableValidation": true
    }
}'
  
  local response
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request POST \
    --data "$request_body" \
    "$API_URL/ai/generation/from-spec" 2>&1) || {
    echo "ERROR: GraphQL generation request failed"
    echo "Response: $response"
    exit 1
  }
  
  parse_response "$response"
  
  if [ "$HTTP_CODE" != "200" ]; then
    echo "ERROR: GraphQL generation failed with HTTP $HTTP_CODE"
    echo "Response: $BODY"
    exit 1
  fi
  
  # Validate response contains "mappings" array
  if ! echo "$BODY" | grep -q '"mappings"'; then
    echo "ERROR: GraphQL generation response missing 'mappings' array"
    echo "Response: $BODY"
    exit 1
  fi
  
  # Store generated mappings for import test
  GRAPHQL_MAPPINGS="$BODY"
  
  echo "✓ GraphQL mock generation passed"
}

# Test GraphQL mock import
# Imports the generated GraphQL mocks into the WireMock runtime
test_graphql_import() {
  echo "Testing GraphQL mock import..."
  
  # Validate that GRAPHQL_MAPPINGS is set from generation test
  if [ -z "$GRAPHQL_MAPPINGS" ]; then
    echo "ERROR: GRAPHQL_MAPPINGS not set. GraphQL generation test must run first."
    exit 1
  fi
  
  local response
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request POST \
    --data "$GRAPHQL_MAPPINGS" \
    "$API_URL/__admin/mappings/import" 2>&1) || {
    echo "ERROR: GraphQL import request failed"
    echo "Response: $response"
    exit 1
  }
  
  parse_response "$response"
  
  if [ "$HTTP_CODE" != "200" ]; then
    echo "ERROR: GraphQL import failed with HTTP $HTTP_CODE"
    echo "Response: $BODY"
    exit 1
  fi
  
  echo "✓ GraphQL mock import passed"
  
  # Give the system a moment to persist the mappings
  sleep 2
  
  # Debug: List all mappings to verify import
  echo "  Verifying imported mappings..."
  local mappings_list
  mappings_list=$(curl "${CURL_OPTS[@]}" \
    --request GET \
    "$API_URL/__admin/mappings" 2>&1) || {
    echo "WARNING: Could not retrieve mappings list"
  }
  
  # Count how many mappings were imported
  local mapping_count
  mapping_count=$(echo "$mappings_list" | grep -o '"id"' | wc -l || echo "0")
  echo "  Found $mapping_count mappings after import"
}

# Test SOAP/WSDL mock generation
# Generates WireMock mappings from the Calculator WSDL specification
test_soap_generation() {
  echo "Testing SOAP/WSDL mock generation..."
  
  # Request payload matching Postman collection
  # Using Calculator WSDL as specified in requirements
  local request_body
  request_body='{
    "namespace": {
        "apiName": "calculator",
        "client": null
    },
    "specification": null,
    "specificationUrl": "http://www.dneonline.com/calculator.asmx?WSDL",
    "format": "WSDL",
    "description": "Generate mocks for the Calculator SOAP service with sample calculations including Add, Subtract, Multiply, and Divide operations.",
    "options": {
        "enableValidation": true
    }
}'
  
  local response
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request POST \
    --data "$request_body" \
    "$API_URL/ai/generation/from-spec" 2>&1) || {
    echo "ERROR: SOAP generation request failed"
    echo "Response: $response"
    exit 1
  }
  
  parse_response "$response"
  
  if [ "$HTTP_CODE" != "200" ]; then
    echo "ERROR: SOAP generation failed with HTTP $HTTP_CODE"
    echo "Response: $BODY"
    exit 1
  fi
  
  # Validate response contains "mappings" array
  if ! echo "$BODY" | grep -q '"mappings"'; then
    echo "ERROR: SOAP generation response missing 'mappings' array"
    echo "Response: $BODY"
    exit 1
  fi
  
  # Store generated mappings for import test
  SOAP_MAPPINGS="$BODY"
  
  echo "✓ SOAP mock generation passed"
}

# Test SOAP mock import
# Imports the generated SOAP/WSDL mocks into the WireMock runtime
test_soap_import() {
  echo "Testing SOAP mock import..."
  
  # Validate that SOAP_MAPPINGS is set from generation test
  if [ -z "$SOAP_MAPPINGS" ]; then
    echo "ERROR: SOAP_MAPPINGS not set. SOAP generation test must run first."
    exit 1
  fi
  
  local response
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request POST \
    --data "$SOAP_MAPPINGS" \
    "$API_URL/__admin/mappings/import" 2>&1) || {
    echo "ERROR: SOAP import request failed"
    echo "Response: $response"
    exit 1
  }
  
  parse_response "$response"
  
  if [ "$HTTP_CODE" != "200" ]; then
    echo "ERROR: SOAP import failed with HTTP $HTTP_CODE"
    echo "Response: $BODY"
    exit 1
  fi
  
  echo "✓ SOAP mock import passed"
  
  # Give the system a moment to persist the mappings
  sleep 2
  
  # Debug: List all mappings to verify import
  echo "  Verifying imported mappings..."
  local mappings_list
  mappings_list=$(curl "${CURL_OPTS[@]}" \
    --request GET \
    "$API_URL/__admin/mappings" 2>&1) || {
    echo "WARNING: Could not retrieve mappings list"
  }
  
  # Count how many mappings were imported
  local mapping_count
  mapping_count=$(echo "$mappings_list" | grep -o '"id"' | wc -l || echo "0")
  echo "  Found $mapping_count mappings after import"
}

# Test webhook delivery and sensitive header redaction
# Validates that:
# 1. A webhook configured with aws_iam auth is dispatched asynchronously via SQS
# 2. The RuntimeAsync Lambda executes the outbound callback using SigV4 signing
# 3. The callback request record is persisted in the S3 journal under requests/ prefix
# 4. IAM-sensitive header values in the S3 record are [REDACTED]
test_webhook_delivery() {
  echo "Testing webhook delivery (IAM mode, async dispatch, S3 journal polling)..."

  # Require S3 bucket name for journal polling
  if [ -z "$MOCK_STORAGE_BUCKET" ]; then
    MOCK_STORAGE_BUCKET=$(aws cloudformation describe-stacks \
      --stack-name "$STACK_NAME" \
      --query 'Stacks[0].Outputs[?OutputKey==`MockStorageBucket`].OutputValue' \
      --output text 2>/dev/null || echo "")
  fi
  if [ -z "$MOCK_STORAGE_BUCKET" ]; then
    echo "ERROR: MOCK_STORAGE_BUCKET is required for webhook S3 journal polling"
    exit 1
  fi

  # Step 1: Register callback mock — POST /mocknest/webhook-callback → 200 OK
  echo "  Registering callback mock..."
  local callback_mapping
  callback_mapping='{
    "request": {
      "method": "POST",
      "urlPath": "/webhook-callback"
    },
    "response": {
      "status": 200,
      "body": "{\"received\": true}",
      "headers": { "Content-Type": "application/json" }
    },
    "persistent": true
  }'

  local response
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request POST \
    --data "$callback_mapping" \
    "$API_URL/__admin/mappings" 2>&1) || {
    echo "ERROR: Failed to register callback mock"
    echo "Response: $response"
    exit 1
  }
  parse_response "$response"
  if [ "$HTTP_CODE" != "201" ]; then
    echo "ERROR: Callback mock registration failed with HTTP $HTTP_CODE"
    echo "Response: $BODY"
    exit 1
  fi
  echo "  ✓ Callback mock registered"

  # Step 2: Register trigger mock with standard "webhook" serveEventListener and aws_iam auth
  echo "  Registering trigger mock..."
  local trigger_mapping
  trigger_mapping="{
    \"request\": {
      \"method\": \"POST\",
      \"urlPath\": \"/webhook-trigger\"
    },
    \"response\": {
      \"status\": 202,
      \"body\": \"{\\\"triggered\\\": true}\",
      \"headers\": { \"Content-Type\": \"application/json\" }
    },
    \"serveEventListeners\": [
      {
        \"name\": \"webhook\",
        \"parameters\": {
          \"method\": \"POST\",
          \"url\": \"$API_URL/mocknest/webhook-callback\",
          \"body\": \"{\\\"event\\\": \\\"triggered\\\"}\",
          \"headers\": { \"Content-Type\": \"application/json\" },
          \"auth\": {
            \"type\": \"aws_iam\"
          }
        }
      }
    ]
  }"

  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request POST \
    --data "$trigger_mapping" \
    "$API_URL/__admin/mappings" 2>&1) || {
    echo "ERROR: Failed to register trigger mock"
    echo "Response: $response"
    exit 1
  }
  parse_response "$response"
  if [ "$HTTP_CODE" != "201" ]; then
    echo "ERROR: Trigger mock registration failed with HTTP $HTTP_CODE"
    echo "Response: $BODY"
    exit 1
  fi
  echo "  ✓ Trigger mock registered"

  # Step 3: Call the trigger endpoint using SigV4 (CURL_OPTS already includes --aws-sigv4 for IAM mode)
  echo "  Calling trigger endpoint..."
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request POST \
    --data '{"order": "test-order-1"}' \
    "$API_URL/mocknest/webhook-trigger" 2>&1) || {
    echo "ERROR: Trigger request failed"
    echo "Response: $response"
    exit 1
  }
  parse_response "$response"
  if [ "$HTTP_CODE" != "202" ]; then
    echo "ERROR: Trigger endpoint returned HTTP $HTTP_CODE (expected 202)"
    echo "Response: $BODY"
    exit 1
  fi
  echo "  ✓ Trigger returned 202 Accepted"

  # Step 4: Poll S3 journal (requests/ prefix) for callback request record
  # End-to-end latency: SQS publish → SQS delivery → RuntimeAsync cold start → HTTP call → S3 write
  local POLL_TIMEOUT="${WEBHOOK_POLL_TIMEOUT_SECS:-60}"
  local POLL_INTERVAL="${WEBHOOK_POLL_INTERVAL_SECS:-3}"
  echo "  Polling S3 journal for callback request record (timeout=${POLL_TIMEOUT}s, interval=${POLL_INTERVAL}s)..."

  local elapsed=0
  local callback_found=false
  local callback_record=""

  while [ "$elapsed" -lt "$POLL_TIMEOUT" ]; do
    # List objects under requests/ prefix, sorted newest first
    local keys
    keys=$(aws s3api list-objects-v2 \
      --bucket "$MOCK_STORAGE_BUCKET" \
      --prefix "requests/" \
      --query 'sort_by(Contents, &LastModified)[-10:].Key' \
      --output text 2>/dev/null || echo "")

    for key in $keys; do
      [ -z "$key" ] && continue
      # Fetch the record and check if it contains the callback path
      local record
      record=$(aws s3api get-object \
        --bucket "$MOCK_STORAGE_BUCKET" \
        --key "$key" \
        /dev/stdout 2>/dev/null || echo "")

      # Match on the URL path — the record contains the request URL
      if echo "$record" | grep -q 'webhook-callback'; then
        callback_found=true
        callback_record="$record"
        echo "  Found callback record in key: $key"
        break 2
      fi
    done

    echo "  Not found yet (${elapsed}s elapsed) — retrying in ${POLL_INTERVAL}s..."
    sleep "$POLL_INTERVAL"
    elapsed=$((elapsed + POLL_INTERVAL))
  done

  # Step 5: Assert callback was found
  if [ "$callback_found" != "true" ]; then
    echo "ERROR: Webhook callback request not found in S3 journal after ${POLL_TIMEOUT}s"
    echo "Expected a POST to /webhook-callback in S3 bucket=$MOCK_STORAGE_BUCKET prefix=requests/"
    exit 1
  fi
  echo "  ✓ Callback request found in S3 journal"

  # Step 6: Assert IAM-sensitive headers are [REDACTED] in the S3 record if present
  echo "  Verifying sensitive headers are redacted in S3 journal record (if present)..."
  local redaction_issues=0
  for sensitive_header in "authorization" "x-amz-security-token"; do
    # Only check if the header key appears in the record at all
    if echo "$callback_record" | python3 -c "
import sys, json
data = json.load(sys.stdin)
def find_header(obj, name):
    if isinstance(obj, dict):
        for k, v in obj.items():
            if k == 'headers' and isinstance(v, dict):
                for hk in v:
                    if hk.lower() == name:
                        print(v[hk])
                        return
            else:
                find_header(v, name)
    elif isinstance(obj, list):
        for item in obj:
            find_header(item, name)
find_header(data, '$sensitive_header')
" 2>/dev/null | grep -q .; then
      header_value=$(echo "$callback_record" | python3 -c "
import sys, json
data = json.load(sys.stdin)
def find_header(obj, name):
    if isinstance(obj, dict):
        for k, v in obj.items():
            if k == 'headers' and isinstance(v, dict):
                for hk in v:
                    if hk.lower() == name:
                        print(v[hk])
                        return
            else:
                find_header(v, name)
    elif isinstance(obj, list):
        for item in obj:
            find_header(item, name)
find_header(data, '$sensitive_header')
" 2>/dev/null || echo "")
      if [ "$header_value" = "[REDACTED]" ]; then
        echo "  ✓ $sensitive_header is [REDACTED] in S3 journal record"
      else
        echo "  ERROR: $sensitive_header header value is NOT redacted (found: $header_value)"
        redaction_issues=$((redaction_issues + 1))
      fi
    else
      echo "  ℹ $sensitive_header not present in callback record headers — skipping redaction check"
    fi
  done
  if [ "$redaction_issues" -gt 0 ]; then
    echo "ERROR: $redaction_issues sensitive header(s) were not redacted in S3 journal record"
    exit 1
  fi

  echo "✓ Webhook delivery test passed"
}

# Main test execution
main() {
  echo "=== MockNest Serverless Post-Deployment Integration Tests ==="
  echo "Test Suite: $TEST_SUITE"
  echo ""
  
  case "$TEST_SUITE" in
    setup)
      echo "Running setup tests (health checks and cleanup)..."
      test_runtime_health
      test_ai_health
      test_delete_all_mappings
      ;;
    rest)
      echo "Running REST/OpenAPI tests..."
      test_rest_generation
      test_rest_import
      ;;
    graphql)
      echo "Running GraphQL tests..."
      test_graphql_generation
      test_graphql_import
      ;;
    soap)
      echo "Running SOAP/WSDL tests..."
      test_soap_generation
      test_soap_import
      ;;
    webhook)
      echo "Running webhook tests..."
      test_webhook_delivery
      ;;
    all)
      echo "Running all tests..."
      test_runtime_health
      test_ai_health
      test_delete_all_mappings
      test_rest_generation
      test_rest_import
      test_graphql_generation
      test_graphql_import
      test_soap_generation
      test_soap_import
      test_webhook_delivery
      ;;
    *)
      echo "ERROR: Unknown test suite: $TEST_SUITE"
      echo "Valid options: setup, rest, graphql, soap, webhook, all"
      exit 2
      ;;
  esac
  
  echo ""
  echo "=== All Tests Passed ==="
}

main
