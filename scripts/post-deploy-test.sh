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
#   setup                - Run health checks and cleanup only
#   rest                 - Run REST/OpenAPI generation and import tests
#   graphql              - Run GraphQL generation and import tests (future)
#   soap                 - Run SOAP/WSDL generation and import tests (future)
#   webhook              - Run webhook delivery and redaction tests
#   mock-management      - Run extensive mock management CRUD tests
#   request-verification - Run extensive request verification tests
#   near-miss            - Run extensive near-miss analysis tests
#   files                - Run extensive file management CRUD tests
#   extensive            - Run all four extensive test groups sequentially
#   all                  - Run all standard tests sequentially (default)
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
  echo "TEST_SUITE options: setup, rest, graphql, soap, webhook, mock-management, request-verification, near-miss, files, extensive, all (default: all)"
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

# Assert expected HTTP status code, with detailed error reporting
# Usage: assert_http_code "GROUP" "METHOD" "/endpoint" "$RESPONSE" "EXPECTED_CODE"
assert_http_code() {
  local group="$1" method="$2" endpoint="$3" response="$4" expected="$5"
  parse_response "$response"
  if [ "$HTTP_CODE" != "$expected" ]; then
    echo "[$group] ERROR: $method $endpoint — expected HTTP $expected, got HTTP $HTTP_CODE"
    echo "[$group] Response body: $BODY"
    exit 1
  fi
}

# Extract a top-level JSON field from stdin using python3
# Usage: VALUE=$(echo "$JSON" | json_field "fieldName")
json_field() {
  python3 -c "import sys,json; print(json.load(sys.stdin)['$1'])"
}

# curl without --fail flag, for testing expected error responses (e.g. 404)
# Usage: curl_no_fail --write-out "\n%{http_code}" --request GET "$URL"
curl_no_fail() {
  local curl_opts_no_fail=()
  for opt in "${CURL_OPTS[@]}"; do
    if [ "$opt" != "--fail" ]; then
      curl_opts_no_fail+=("$opt")
    fi
  done
  curl "${curl_opts_no_fail[@]}" "$@"
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

  # Step 3: Call the trigger endpoint using SigV4 (CURL_OPTS already includes --aws-sigv4 for IAM mode).
  # A bounded retry loop tolerates mapping propagation delay — the trigger stub may not be
  # immediately visible after registration, so we retry up to 3 times with 1 s between attempts.
  echo "  Calling trigger endpoint (with retry for mapping propagation)..."
  local trigger_http_code=""
  local trigger_body=""
  local trigger_attempt=0
  while [ "$trigger_attempt" -lt 3 ]; do
    trigger_attempt=$((trigger_attempt + 1))
    local trigger_response
    trigger_response=$(curl "${CURL_OPTS[@]}" \
      --write-out "\n%{http_code}" \
      --request POST \
      --data '{"order": "test-order-1"}' \
      "$API_URL/mocknest/webhook-trigger" 2>&1) || true
    trigger_http_code=$(echo "$trigger_response" | tail -n1)
    trigger_body=$(echo "$trigger_response" | sed '$d')
    if [ "$trigger_http_code" != "404" ]; then
      break
    fi
    echo "  Trigger returned 404 (attempt $trigger_attempt/3) — waiting 1s for mapping propagation..."
    sleep 1
  done
  if [ "$trigger_http_code" != "202" ]; then
    echo "ERROR: Trigger endpoint returned HTTP $trigger_http_code (expected 202)"
    echo "Response: $trigger_body"
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

  # Step 6: Assert IAM-sensitive headers are [REDACTED] in the S3 record if present.
  # IMPORTANT: All redaction assertions operate on `callback_record` — the isolated S3 journal
  # entry that matched /webhook-callback — NOT on the full journal body. This prevents false
  # positives from stale entries written by prior test runs or unrelated requests.
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

# =============================================================================
# Extensive Test Functions — Mock Management
# =============================================================================

# Test mock management CRUD lifecycle
# Creates, reads, updates, lists, deletes a mapping, then verifies 404
test_mock_management_crud() {
  echo "[mock-management] Testing CRUD lifecycle..."

  # Step 1: POST /__admin/mappings — create mapping
  echo "[mock-management]   Creating mapping..."
  local create_body
  create_body='{
    "request": {
      "method": "GET",
      "urlPath": "/extensive-test/mock-mgmt"
    },
    "response": {
      "status": 200,
      "jsonBody": { "test": "mock-management" },
      "headers": { "Content-Type": "application/json" }
    }
  }'

  local response
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request POST \
    --data "$create_body" \
    "$API_URL/__admin/mappings" 2>&1) || {
    echo "[mock-management] ERROR: POST /__admin/mappings request failed"
    echo "[mock-management] Response: $response"
    exit 1
  }
  assert_http_code "mock-management" "POST" "/__admin/mappings" "$response" "201"

  MAPPING_ID=$(echo "$BODY" | json_field "id")
  echo "[mock-management]   ✓ Created mapping: $MAPPING_ID"

  # Step 2: GET /__admin/mappings/{id} — read mapping
  echo "[mock-management]   Reading mapping by ID..."
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request GET \
    "$API_URL/__admin/mappings/$MAPPING_ID" 2>&1) || {
    echo "[mock-management] ERROR: GET /__admin/mappings/$MAPPING_ID request failed"
    echo "[mock-management] Response: $response"
    exit 1
  }
  assert_http_code "mock-management" "GET" "/__admin/mappings/$MAPPING_ID" "$response" "200"

  if ! echo "$BODY" | grep -q '/extensive-test/mock-mgmt'; then
    echo "[mock-management] ERROR: GET response missing expected urlPath"
    echo "[mock-management] Response: $BODY"
    exit 1
  fi
  echo "[mock-management]   ✓ Read mapping passed"

  # Step 3: PUT /__admin/mappings/{id} — update mapping
  echo "[mock-management]   Updating mapping..."
  local update_body
  update_body="{
    \"id\": \"$MAPPING_ID\",
    \"request\": {
      \"method\": \"GET\",
      \"urlPath\": \"/extensive-test/mock-mgmt-updated\"
    },
    \"response\": {
      \"status\": 200,
      \"jsonBody\": { \"test\": \"updated\" },
      \"headers\": { \"Content-Type\": \"application/json\" }
    }
  }"

  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request PUT \
    --data "$update_body" \
    "$API_URL/__admin/mappings/$MAPPING_ID" 2>&1) || {
    echo "[mock-management] ERROR: PUT /__admin/mappings/$MAPPING_ID request failed"
    echo "[mock-management] Response: $response"
    exit 1
  }
  assert_http_code "mock-management" "PUT" "/__admin/mappings/$MAPPING_ID" "$response" "200"
  echo "[mock-management]   ✓ Update mapping passed"

  # Step 4: GET /__admin/mappings/{id} — verify update
  echo "[mock-management]   Verifying update..."
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request GET \
    "$API_URL/__admin/mappings/$MAPPING_ID" 2>&1) || {
    echo "[mock-management] ERROR: GET /__admin/mappings/$MAPPING_ID request failed"
    echo "[mock-management] Response: $response"
    exit 1
  }
  assert_http_code "mock-management" "GET" "/__admin/mappings/$MAPPING_ID" "$response" "200"

  if ! echo "$BODY" | grep -q '/extensive-test/mock-mgmt-updated'; then
    echo "[mock-management] ERROR: GET response missing updated urlPath"
    echo "[mock-management] Response: $BODY"
    exit 1
  fi
  echo "[mock-management]   ✓ Verify update passed"

  # Step 5: GET /__admin/mappings — list all, verify contains our mapping
  echo "[mock-management]   Listing all mappings..."
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request GET \
    "$API_URL/__admin/mappings" 2>&1) || {
    echo "[mock-management] ERROR: GET /__admin/mappings request failed"
    echo "[mock-management] Response: $response"
    exit 1
  }
  assert_http_code "mock-management" "GET" "/__admin/mappings" "$response" "200"

  if ! echo "$BODY" | grep -q "$MAPPING_ID"; then
    echo "[mock-management] ERROR: Mapping list does not contain $MAPPING_ID"
    echo "[mock-management] Response: $BODY"
    exit 1
  fi
  echo "[mock-management]   ✓ List mappings passed"

  # Step 6: DELETE /__admin/mappings/{id}
  echo "[mock-management]   Deleting mapping..."
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request DELETE \
    "$API_URL/__admin/mappings/$MAPPING_ID" 2>&1) || {
    echo "[mock-management] ERROR: DELETE /__admin/mappings/$MAPPING_ID request failed"
    echo "[mock-management] Response: $response"
    exit 1
  }
  assert_http_code "mock-management" "DELETE" "/__admin/mappings/$MAPPING_ID" "$response" "200"
  echo "[mock-management]   ✓ Delete mapping passed"

  # Step 7: GET /__admin/mappings/{id} — verify 404
  echo "[mock-management]   Verifying 404 after delete..."
  response=$(curl_no_fail \
    --write-out "\n%{http_code}" \
    --request GET \
    "$API_URL/__admin/mappings/$MAPPING_ID" 2>&1) || true
  assert_http_code "mock-management" "GET" "/__admin/mappings/$MAPPING_ID" "$response" "404"
  echo "[mock-management]   ✓ 404 after delete passed"

  echo "[mock-management] ✓ CRUD lifecycle passed"
}

# Test mock management save and reset
# Creates a mapping, saves, creates another, resets, verifies zero mappings
test_mock_management_save_reset() {
  echo "[mock-management] Testing save and reset..."

  # Step 1: POST /__admin/mappings — create persistent mapping
  echo "[mock-management]   Creating persistent mapping..."
  local create_body
  create_body='{
    "request": {
      "method": "GET",
      "urlPath": "/extensive-test/save-reset"
    },
    "response": {
      "status": 200,
      "body": "save-reset-test"
    },
    "persistent": true
  }'

  local response
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request POST \
    --data "$create_body" \
    "$API_URL/__admin/mappings" 2>&1) || {
    echo "[mock-management] ERROR: POST /__admin/mappings request failed"
    echo "[mock-management] Response: $response"
    exit 1
  }
  assert_http_code "mock-management" "POST" "/__admin/mappings" "$response" "201"
  echo "[mock-management]   ✓ Created persistent mapping"

  # Step 2: POST /__admin/mappings/save
  echo "[mock-management]   Saving mappings..."
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request POST \
    "$API_URL/__admin/mappings/save" 2>&1) || {
    echo "[mock-management] ERROR: POST /__admin/mappings/save request failed"
    echo "[mock-management] Response: $response"
    exit 1
  }
  assert_http_code "mock-management" "POST" "/__admin/mappings/save" "$response" "200"
  echo "[mock-management]   ✓ Save mappings passed"

  # Step 3: POST /__admin/mappings — create second mapping
  echo "[mock-management]   Creating second mapping..."
  local second_body
  second_body='{
    "request": {
      "method": "GET",
      "urlPath": "/extensive-test/reset-target"
    },
    "response": {
      "status": 200,
      "body": "reset-target"
    }
  }'

  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request POST \
    --data "$second_body" \
    "$API_URL/__admin/mappings" 2>&1) || {
    echo "[mock-management] ERROR: POST /__admin/mappings request failed"
    echo "[mock-management] Response: $response"
    exit 1
  }
  assert_http_code "mock-management" "POST" "/__admin/mappings" "$response" "201"
  echo "[mock-management]   ✓ Created second mapping"

  # Step 4: DELETE /__admin/mappings — clear all mappings from S3 before reset
  # Reset reloads from S3, so we must delete first to ensure zero mappings after reset
  echo "[mock-management]   Deleting all mappings before reset..."
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request DELETE \
    "$API_URL/__admin/mappings" 2>&1) || {
    echo "[mock-management] ERROR: DELETE /__admin/mappings request failed"
    echo "[mock-management] Response: $response"
    exit 1
  }
  assert_http_code "mock-management" "DELETE" "/__admin/mappings" "$response" "200"
  echo "[mock-management]   ✓ Deleted all mappings"

  # Step 5: POST /__admin/reset
  echo "[mock-management]   Resetting server..."
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request POST \
    "$API_URL/__admin/reset" 2>&1) || {
    echo "[mock-management] ERROR: POST /__admin/reset request failed"
    echo "[mock-management] Response: $response"
    exit 1
  }
  assert_http_code "mock-management" "POST" "/__admin/reset" "$response" "200"
  echo "[mock-management]   ✓ Reset passed"

  # Step 6: GET /__admin/mappings — verify zero mappings
  echo "[mock-management]   Verifying zero mappings after reset..."
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request GET \
    "$API_URL/__admin/mappings" 2>&1) || {
    echo "[mock-management] ERROR: GET /__admin/mappings request failed"
    echo "[mock-management] Response: $response"
    exit 1
  }
  assert_http_code "mock-management" "GET" "/__admin/mappings" "$response" "200"

  # Check that mappings array is empty (no mapping IDs present)
  local mapping_count
  mapping_count=$(echo "$BODY" | grep -o '"id"' | wc -l | tr -d ' ' || echo "0")
  if [ "$mapping_count" -ne 0 ] 2>/dev/null; then
    echo "[mock-management] ERROR: Expected 0 mappings after reset, found $mapping_count"
    echo "[mock-management] Response: $BODY"
    exit 1
  fi
  echo "[mock-management]   ✓ Zero mappings after reset"

  echo "[mock-management] ✓ Save and reset passed"
}

# Test mock management metadata operations
# Creates mapping with metadata, finds by metadata, removes by metadata, verifies gone
test_mock_management_metadata() {
  echo "[mock-management] Testing metadata operations..."

  # Step 1: POST /__admin/mappings — create mapping with metadata
  echo "[mock-management]   Creating mapping with metadata..."
  local create_body
  create_body='{
    "request": {
      "method": "GET",
      "urlPath": "/extensive-test/metadata"
    },
    "response": {
      "status": 200,
      "body": "metadata-test"
    },
    "metadata": {
      "testGroup": "extensive",
      "testId": "metadata-1"
    }
  }'

  local response
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request POST \
    --data "$create_body" \
    "$API_URL/__admin/mappings" 2>&1) || {
    echo "[mock-management] ERROR: POST /__admin/mappings request failed"
    echo "[mock-management] Response: $response"
    exit 1
  }
  assert_http_code "mock-management" "POST" "/__admin/mappings" "$response" "201"

  METADATA_MAPPING_ID=$(echo "$BODY" | json_field "id")
  echo "[mock-management]   ✓ Created mapping with metadata: $METADATA_MAPPING_ID"

  # Step 2: POST /__admin/mappings/find-by-metadata
  echo "[mock-management]   Finding by metadata..."
  local find_body
  find_body='{
    "matchesJsonPath": {
      "expression": "$.testGroup",
      "equalTo": "extensive"
    }
  }'

  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request POST \
    --data "$find_body" \
    "$API_URL/__admin/mappings/find-by-metadata" 2>&1) || {
    echo "[mock-management] ERROR: POST /__admin/mappings/find-by-metadata request failed"
    echo "[mock-management] Response: $response"
    exit 1
  }
  assert_http_code "mock-management" "POST" "/__admin/mappings/find-by-metadata" "$response" "200"

  if ! echo "$BODY" | grep -q "$METADATA_MAPPING_ID"; then
    echo "[mock-management] ERROR: find-by-metadata response does not contain $METADATA_MAPPING_ID"
    echo "[mock-management] Response: $BODY"
    exit 1
  fi
  echo "[mock-management]   ✓ Find by metadata passed"

  # Step 3: POST /__admin/mappings/remove-by-metadata
  echo "[mock-management]   Removing by metadata..."
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request POST \
    --data "$find_body" \
    "$API_URL/__admin/mappings/remove-by-metadata" 2>&1) || {
    echo "[mock-management] ERROR: POST /__admin/mappings/remove-by-metadata request failed"
    echo "[mock-management] Response: $response"
    exit 1
  }
  assert_http_code "mock-management" "POST" "/__admin/mappings/remove-by-metadata" "$response" "200"
  echo "[mock-management]   ✓ Remove by metadata passed"

  # Step 4: GET /__admin/mappings — verify mapping is gone
  echo "[mock-management]   Verifying mapping removed..."
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request GET \
    "$API_URL/__admin/mappings" 2>&1) || {
    echo "[mock-management] ERROR: GET /__admin/mappings request failed"
    echo "[mock-management] Response: $response"
    exit 1
  }
  assert_http_code "mock-management" "GET" "/__admin/mappings" "$response" "200"

  if echo "$BODY" | grep -q "$METADATA_MAPPING_ID"; then
    echo "[mock-management] ERROR: Mapping $METADATA_MAPPING_ID still present after remove-by-metadata"
    echo "[mock-management] Response: $BODY"
    exit 1
  fi
  echo "[mock-management]   ✓ Mapping removed after remove-by-metadata"

  echo "[mock-management] ✓ Metadata operations passed"
}

# Test mock management unmatched mappings
# Creates a mapping that won't be invoked, lists unmatched, deletes unmatched
test_mock_management_unmatched() {
  echo "[mock-management] Testing unmatched mappings..."

  # Step 1: POST /__admin/mappings — create mapping that won't be invoked
  echo "[mock-management]   Creating unmatched mapping..."
  local create_body
  create_body='{
    "request": {
      "method": "GET",
      "urlPath": "/extensive-test/unmatched-mapping"
    },
    "response": {
      "status": 200,
      "body": "unmatched"
    }
  }'

  local response
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request POST \
    --data "$create_body" \
    "$API_URL/__admin/mappings" 2>&1) || {
    echo "[mock-management] ERROR: POST /__admin/mappings request failed"
    echo "[mock-management] Response: $response"
    exit 1
  }
  assert_http_code "mock-management" "POST" "/__admin/mappings" "$response" "201"
  echo "[mock-management]   ✓ Created unmatched mapping"

  # Step 2: GET /__admin/mappings/unmatched
  echo "[mock-management]   Listing unmatched mappings..."
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request GET \
    "$API_URL/__admin/mappings/unmatched" 2>&1) || {
    echo "[mock-management] ERROR: GET /__admin/mappings/unmatched request failed"
    echo "[mock-management] Response: $response"
    exit 1
  }
  assert_http_code "mock-management" "GET" "/__admin/mappings/unmatched" "$response" "200"

  if ! echo "$BODY" | grep -q '"mappings"'; then
    echo "[mock-management] ERROR: Unmatched response missing mappings array"
    echo "[mock-management] Response: $BODY"
    exit 1
  fi
  echo "[mock-management]   ✓ List unmatched passed"

  # Step 3: DELETE /__admin/mappings/unmatched
  echo "[mock-management]   Deleting unmatched mappings..."
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request DELETE \
    "$API_URL/__admin/mappings/unmatched" 2>&1) || {
    echo "[mock-management] ERROR: DELETE /__admin/mappings/unmatched request failed"
    echo "[mock-management] Response: $response"
    exit 1
  }
  assert_http_code "mock-management" "DELETE" "/__admin/mappings/unmatched" "$response" "200"
  echo "[mock-management]   ✓ Delete unmatched passed"

  echo "[mock-management] ✓ Unmatched mappings passed"
}

# Cleanup all mappings created during mock-management tests
test_mock_management_cleanup() {
  echo "[mock-management] Cleaning up test data..."
  curl "${CURL_OPTS[@]}" \
    --request DELETE \
    "$API_URL/__admin/mappings" 2>/dev/null || true
  echo "[mock-management] ✓ Cleanup complete"
}

# =============================================================================
# Extensive Test Functions — Request Verification
# =============================================================================

# Shared state for request verification tests
REQ_VERIFY_MAPPING_ID=""
REQUEST_ID=""

# Setup: create mapping and invoke it to generate a journal entry
test_request_verification_setup() {
  echo "[request-verification] Setting up request verification tests..."

  # Step 1: POST /__admin/mappings — create mapping for POST /extensive-test/req-verify
  echo "[request-verification]   Creating mapping..."
  local create_body
  create_body='{
    "request": {
      "method": "POST",
      "urlPath": "/extensive-test/req-verify"
    },
    "response": {
      "status": 200,
      "jsonBody": { "received": true },
      "headers": { "Content-Type": "application/json" }
    }
  }'

  local response
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request POST \
    --data "$create_body" \
    "$API_URL/__admin/mappings" 2>&1) || {
    echo "[request-verification] ERROR: POST /__admin/mappings request failed"
    echo "[request-verification] Response: $response"
    exit 1
  }
  assert_http_code "request-verification" "POST" "/__admin/mappings" "$response" "201"

  REQ_VERIFY_MAPPING_ID=$(echo "$BODY" | json_field "id")
  echo "[request-verification]   ✓ Created mapping: $REQ_VERIFY_MAPPING_ID"

  # Brief pause to allow mapping propagation
  sleep 1

  # Step 2: POST /mocknest/extensive-test/req-verify — invoke mock to generate journal entry
  # Retry up to 3 times to tolerate mapping propagation delay
  echo "[request-verification]   Invoking mock to generate journal entry..."
  local invoke_http_code=""
  local invoke_attempt=0
  while [ "$invoke_attempt" -lt 3 ]; do
    invoke_attempt=$((invoke_attempt + 1))
    response=$(curl "${CURL_OPTS[@]}" \
      --write-out "\n%{http_code}" \
      --request POST \
      --data '{"testData": "request-verification"}' \
      "$API_URL/mocknest/extensive-test/req-verify" 2>&1) || true
    invoke_http_code=$(echo "$response" | tail -n1)
    if [ "$invoke_http_code" != "404" ]; then
      break
    fi
    echo "[request-verification]   Mock returned 404 (attempt $invoke_attempt/3) — waiting 1s for mapping propagation..."
    sleep 1
  done
  assert_http_code "request-verification" "POST" "/mocknest/extensive-test/req-verify" "$response" "200"
  echo "[request-verification]   ✓ Mock invoked"

  echo "[request-verification] ✓ Setup complete"
}

# Test list requests, find requests, and count requests
test_request_verification_list_find_count() {
  echo "[request-verification] Testing list, find, and count..."

  # Step 1: GET /__admin/requests — poll until journal entry appears (S3 persistence delay)
  echo "[request-verification]   Listing requests (polling for journal entry)..."
  local response
  local poll_attempt=0
  local max_poll=10
  REQUEST_ID=""
  while [ "$poll_attempt" -lt "$max_poll" ]; do
    poll_attempt=$((poll_attempt + 1))
    response=$(curl "${CURL_OPTS[@]}" \
      --write-out "\n%{http_code}" \
      --request GET \
      "$API_URL/__admin/requests" 2>&1) || {
      echo "[request-verification] ERROR: GET /__admin/requests request failed"
      echo "[request-verification] Response: $response"
      exit 1
    }
    assert_http_code "request-verification" "GET" "/__admin/requests" "$response" "200"

    # Extract REQUEST_ID from a matching request using python3
    REQUEST_ID=$(echo "$BODY" | python3 -c "
import sys, json
data = json.load(sys.stdin)
for req in data.get('requests', []):
    url = req.get('request', {}).get('url', '') if 'request' in req else req.get('url', '')
    if 'req-verify' in url:
        print(req['id'])
        break
" 2>/dev/null || echo "")

    if [ -n "$REQUEST_ID" ]; then
      break
    fi
    echo "[request-verification]   Journal empty (attempt $poll_attempt/$max_poll) — waiting 3s for S3 persistence..."
    sleep 3
  done

  if [ -z "$REQUEST_ID" ]; then
    echo "[request-verification] ERROR: Could not find a matching request with 'req-verify' in URL after ${max_poll} attempts"
    echo "[request-verification] Response: $BODY"
    exit 1
  fi
  echo "[request-verification]   ✓ List requests passed, found REQUEST_ID: $REQUEST_ID"

  # Step 2: POST /__admin/requests/find
  echo "[request-verification]   Finding requests..."
  local find_body
  find_body='{ "method": "POST", "urlPath": "/extensive-test/req-verify" }'

  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request POST \
    --data "$find_body" \
    "$API_URL/__admin/requests/find" 2>&1) || {
    echo "[request-verification] ERROR: POST /__admin/requests/find request failed"
    echo "[request-verification] Response: $response"
    exit 1
  }
  assert_http_code "request-verification" "POST" "/__admin/requests/find" "$response" "200"
  echo "[request-verification]   ✓ Find requests passed"

  # Step 3: POST /__admin/requests/count
  echo "[request-verification]   Counting requests..."
  local count_body
  count_body='{ "method": "POST", "urlPath": "/extensive-test/req-verify" }'

  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request POST \
    --data "$count_body" \
    "$API_URL/__admin/requests/count" 2>&1) || {
    echo "[request-verification] ERROR: POST /__admin/requests/count request failed"
    echo "[request-verification] Response: $response"
    exit 1
  }
  assert_http_code "request-verification" "POST" "/__admin/requests/count" "$response" "200"

  # Verify count >= 1
  local count
  count=$(echo "$BODY" | json_field "count" 2>/dev/null || echo "0")
  if [ "$count" -lt 1 ] 2>/dev/null; then
    echo "[request-verification] ERROR: Expected count >= 1, got $count"
    echo "[request-verification] Response: $BODY"
    exit 1
  fi
  echo "[request-verification]   ✓ Count requests passed (count=$count)"

  echo "[request-verification] ✓ List, find, and count passed"
}

# Test get request by ID
test_request_verification_get_by_id() {
  echo "[request-verification] Testing get request by ID..."

  local response
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request GET \
    "$API_URL/__admin/requests/$REQUEST_ID" 2>&1) || {
    echo "[request-verification] ERROR: GET /__admin/requests/$REQUEST_ID request failed"
    echo "[request-verification] Response: $response"
    exit 1
  }
  assert_http_code "request-verification" "GET" "/__admin/requests/$REQUEST_ID" "$response" "200"

  if ! echo "$BODY" | grep -q 'req-verify'; then
    echo "[request-verification] ERROR: GET response does not contain 'req-verify'"
    echo "[request-verification] Response: $BODY"
    exit 1
  fi
  echo "[request-verification]   ✓ Get by ID passed"

  echo "[request-verification] ✓ Get request by ID passed"
}

# Test unmatched requests
test_request_verification_unmatched() {
  echo "[request-verification] Testing unmatched requests..."

  local response
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request GET \
    "$API_URL/__admin/requests/unmatched" 2>&1) || {
    echo "[request-verification] ERROR: GET /__admin/requests/unmatched request failed"
    echo "[request-verification] Response: $response"
    exit 1
  }
  assert_http_code "request-verification" "GET" "/__admin/requests/unmatched" "$response" "200"

  if ! echo "$BODY" | grep -q '"requests"'; then
    echo "[request-verification] ERROR: Unmatched response missing requests array"
    echo "[request-verification] Response: $BODY"
    exit 1
  fi
  echo "[request-verification]   ✓ Unmatched requests passed"

  echo "[request-verification] ✓ Unmatched requests passed"
}

# Test delete request by ID
test_request_verification_delete_by_id() {
  echo "[request-verification] Testing delete request by ID..."

  # Step 1: POST /mocknest/extensive-test/req-verify — generate a new journal entry
  echo "[request-verification]   Invoking mock to generate new journal entry..."
  local response
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request POST \
    --data '{"testData": "delete-by-id-test"}' \
    "$API_URL/mocknest/extensive-test/req-verify" 2>&1) || {
    echo "[request-verification] ERROR: POST /mocknest/extensive-test/req-verify request failed"
    echo "[request-verification] Response: $response"
    exit 1
  }
  assert_http_code "request-verification" "POST" "/mocknest/extensive-test/req-verify" "$response" "200"
  echo "[request-verification]   ✓ Mock invoked"

  # Step 2: GET /__admin/requests — poll for the new request ID
  echo "[request-verification]   Finding new request ID (polling)..."
  local DELETE_REQUEST_ID=""
  local del_poll=0
  while [ "$del_poll" -lt 10 ]; do
    del_poll=$((del_poll + 1))
    response=$(curl "${CURL_OPTS[@]}" \
      --write-out "\n%{http_code}" \
      --request GET \
      "$API_URL/__admin/requests" 2>&1) || {
      echo "[request-verification] ERROR: GET /__admin/requests request failed"
      echo "[request-verification] Response: $response"
      exit 1
    }
    assert_http_code "request-verification" "GET" "/__admin/requests" "$response" "200"

    DELETE_REQUEST_ID=$(echo "$BODY" | python3 -c "
import sys, json
data = json.load(sys.stdin)
for req in data.get('requests', []):
    url = req.get('request', {}).get('url', '') if 'request' in req else req.get('url', '')
    if 'req-verify' in url:
        print(req['id'])
        break
" 2>/dev/null || echo "")

    if [ -n "$DELETE_REQUEST_ID" ]; then
      break
    fi
    echo "[request-verification]   Journal empty (attempt $del_poll/10) — waiting 3s..."
    sleep 3
  done

  if [ -z "$DELETE_REQUEST_ID" ]; then
    echo "[request-verification] ERROR: Could not find a matching request for deletion"
    echo "[request-verification] Response: $BODY"
    exit 1
  fi
  echo "[request-verification]   ✓ Found request to delete: $DELETE_REQUEST_ID"

  # Step 3: DELETE /__admin/requests/{id}
  echo "[request-verification]   Deleting request by ID..."
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request DELETE \
    "$API_URL/__admin/requests/$DELETE_REQUEST_ID" 2>&1) || {
    echo "[request-verification] ERROR: DELETE /__admin/requests/$DELETE_REQUEST_ID request failed"
    echo "[request-verification] Response: $response"
    exit 1
  }
  assert_http_code "request-verification" "DELETE" "/__admin/requests/$DELETE_REQUEST_ID" "$response" "200"
  echo "[request-verification]   ✓ Delete by ID passed"

  echo "[request-verification] ✓ Delete request by ID passed"
}

# Test remove requests by criteria
test_request_verification_remove() {
  echo "[request-verification] Testing remove requests..."

  # Step 1: POST /mocknest/extensive-test/req-verify — generate another journal entry
  echo "[request-verification]   Invoking mock to generate journal entry..."
  local response
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request POST \
    --data '{"testData": "remove-test"}' \
    "$API_URL/mocknest/extensive-test/req-verify" 2>&1) || {
    echo "[request-verification] ERROR: POST /mocknest/extensive-test/req-verify request failed"
    echo "[request-verification] Response: $response"
    exit 1
  }
  assert_http_code "request-verification" "POST" "/mocknest/extensive-test/req-verify" "$response" "200"
  echo "[request-verification]   ✓ Mock invoked"

  # Wait for S3 journal persistence
  sleep 5

  # Step 2: POST /__admin/requests/remove
  echo "[request-verification]   Removing requests by criteria..."
  local remove_body
  remove_body='{ "method": "POST", "urlPath": "/extensive-test/req-verify" }'

  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request POST \
    --data "$remove_body" \
    "$API_URL/__admin/requests/remove" 2>&1) || {
    echo "[request-verification] ERROR: POST /__admin/requests/remove request failed"
    echo "[request-verification] Response: $response"
    exit 1
  }
  assert_http_code "request-verification" "POST" "/__admin/requests/remove" "$response" "200"
  echo "[request-verification]   ✓ Remove requests passed"

  echo "[request-verification] ✓ Remove requests passed"
}

# Test remove requests by metadata
test_request_verification_metadata() {
  echo "[request-verification] Testing remove by metadata..."

  local metadata_body
  metadata_body='{
    "matchesJsonPath": {
      "expression": "$.testGroup",
      "equalTo": "extensive"
    }
  }'

  local response
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request POST \
    --data "$metadata_body" \
    "$API_URL/__admin/requests/remove-by-metadata" 2>&1) || {
    echo "[request-verification] ERROR: POST /__admin/requests/remove-by-metadata request failed"
    echo "[request-verification] Response: $response"
    exit 1
  }
  assert_http_code "request-verification" "POST" "/__admin/requests/remove-by-metadata" "$response" "200"
  echo "[request-verification]   ✓ Remove by metadata passed"

  echo "[request-verification] ✓ Remove by metadata passed"
}

# Test clear and reset request journal
test_request_verification_clear_reset() {
  echo "[request-verification] Testing clear and reset..."

  # Step 1: DELETE /__admin/requests
  echo "[request-verification]   Clearing all requests..."
  local response
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request DELETE \
    "$API_URL/__admin/requests" 2>&1) || {
    echo "[request-verification] ERROR: DELETE /__admin/requests request failed"
    echo "[request-verification] Response: $response"
    exit 1
  }
  assert_http_code "request-verification" "DELETE" "/__admin/requests" "$response" "200"
  echo "[request-verification]   ✓ Clear requests passed"

  # Step 2: POST /__admin/requests/reset
  echo "[request-verification]   Resetting request journal..."
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request POST \
    "$API_URL/__admin/requests/reset" 2>&1) || {
    echo "[request-verification] ERROR: POST /__admin/requests/reset request failed"
    echo "[request-verification] Response: $response"
    exit 1
  }
  assert_http_code "request-verification" "POST" "/__admin/requests/reset" "$response" "200"
  echo "[request-verification]   ✓ Reset request journal passed"

  echo "[request-verification] ✓ Clear and reset passed"
}

# Cleanup request verification test data
test_request_verification_cleanup() {
  echo "[request-verification] Cleaning up test data..."
  curl "${CURL_OPTS[@]}" \
    --request DELETE \
    "$API_URL/__admin/mappings/$REQ_VERIFY_MAPPING_ID" 2>/dev/null || true
  curl "${CURL_OPTS[@]}" \
    --request DELETE \
    "$API_URL/__admin/requests" 2>/dev/null || true
  echo "[request-verification] ✓ Cleanup complete"
}

# =============================================================================
# Extensive Test Functions — Near-Miss Analysis
# =============================================================================

# Shared state for near-miss tests
NEAR_MISS_MAPPING_ID=""

# Setup: create mapping with header requirement and send a near-miss request
test_near_miss_setup() {
  echo "[near-miss] Setting up near-miss tests..."

  # Step 1: POST /__admin/mappings — create mapping requiring X-Custom: expected-value
  echo "[near-miss]   Creating mapping with header requirement..."
  local create_body
  create_body='{
    "request": {
      "method": "GET",
      "urlPath": "/extensive-test/near-miss-target",
      "headers": {
        "X-Custom": {
          "equalTo": "expected-value"
        }
      }
    },
    "response": {
      "status": 200,
      "body": "near-miss-target"
    }
  }'

  local response
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request POST \
    --data "$create_body" \
    "$API_URL/__admin/mappings" 2>&1) || {
    echo "[near-miss] ERROR: POST /__admin/mappings request failed"
    echo "[near-miss] Response: $response"
    exit 1
  }
  assert_http_code "near-miss" "POST" "/__admin/mappings" "$response" "201"

  NEAR_MISS_MAPPING_ID=$(echo "$BODY" | json_field "id")
  echo "[near-miss]   ✓ Created mapping: $NEAR_MISS_MAPPING_ID"

  # Step 2: Send near-miss request (wrong header value, correct path)
  echo "[near-miss]   Sending near-miss request with wrong header value..."
  response=$(curl_no_fail \
    --write-out "\n%{http_code}" \
    --request GET \
    --header "X-Custom: wrong-value" \
    "$API_URL/mocknest/extensive-test/near-miss-target" 2>&1) || true
  assert_http_code "near-miss" "GET" "/mocknest/extensive-test/near-miss-target" "$response" "404"
  echo "[near-miss]   ✓ Near-miss request returned 404 as expected"

  # Sleep to allow journal persistence
  sleep 2

  echo "[near-miss] ✓ Setup complete"
}

# Test unmatched near-misses
test_near_miss_unmatched() {
  echo "[near-miss] Testing unmatched near-misses..."

  local response
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request GET \
    "$API_URL/__admin/requests/unmatched/near-misses" 2>&1) || {
    echo "[near-miss] ERROR: GET /__admin/requests/unmatched/near-misses request failed"
    echo "[near-miss] Response: $response"
    exit 1
  }
  assert_http_code "near-miss" "GET" "/__admin/requests/unmatched/near-misses" "$response" "200"

  if ! echo "$BODY" | grep -q '"nearMisses"'; then
    echo "[near-miss] ERROR: Response missing nearMisses array"
    echo "[near-miss] Response: $BODY"
    exit 1
  fi
  echo "[near-miss]   ✓ Unmatched near-misses passed"

  echo "[near-miss] ✓ Unmatched near-misses passed"
}

# Test near-misses for a specific request
test_near_miss_for_request() {
  echo "[near-miss] Testing near-misses for request..."

  local request_body
  request_body='{
    "url": "/extensive-test/near-miss-target",
    "method": "GET",
    "headers": {
      "X-Custom": "wrong-value"
    }
  }'

  local response
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request POST \
    --data "$request_body" \
    "$API_URL/__admin/near-misses/request" 2>&1) || {
    echo "[near-miss] ERROR: POST /__admin/near-misses/request request failed"
    echo "[near-miss] Response: $response"
    exit 1
  }
  assert_http_code "near-miss" "POST" "/__admin/near-misses/request" "$response" "200"
  echo "[near-miss]   ✓ Near-misses for request passed"

  echo "[near-miss] ✓ Near-misses for request passed"
}

# Test near-misses for a request pattern
test_near_miss_for_request_pattern() {
  echo "[near-miss] Testing near-misses for request pattern..."

  local pattern_body
  pattern_body='{
    "urlPath": "/extensive-test/near-miss-target",
    "method": "GET"
  }'

  local response
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request POST \
    --data "$pattern_body" \
    "$API_URL/__admin/near-misses/request-pattern" 2>&1) || {
    echo "[near-miss] ERROR: POST /__admin/near-misses/request-pattern request failed"
    echo "[near-miss] Response: $response"
    exit 1
  }
  assert_http_code "near-miss" "POST" "/__admin/near-misses/request-pattern" "$response" "200"
  echo "[near-miss]   ✓ Near-misses for request pattern passed"

  echo "[near-miss] ✓ Near-misses for request pattern passed"
}

# Cleanup near-miss test data
test_near_miss_cleanup() {
  echo "[near-miss] Cleaning up test data..."
  curl "${CURL_OPTS[@]}" \
    --request DELETE \
    "$API_URL/__admin/mappings/$NEAR_MISS_MAPPING_ID" 2>/dev/null || true
  curl "${CURL_OPTS[@]}" \
    --request DELETE \
    "$API_URL/__admin/requests" 2>/dev/null || true
  echo "[near-miss] ✓ Cleanup complete"
}

# =============================================================================
# Extensive Test Functions — File Management
# =============================================================================

# Test file management CRUD lifecycle
# Creates, reads, lists, updates, re-reads, deletes a file, then verifies 404
test_file_management_crud() {
  echo "[files] Testing file CRUD lifecycle..."

  local FILE_ID="extensive-test-file.json"

  # Step 1: PUT /__admin/files/{FILE_ID} — create file
  echo "[files]   Creating file..."
  local response
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request PUT \
    --header "Content-Type: application/octet-stream" \
    --data '{"test": "file-management", "created": true}' \
    "$API_URL/__admin/files/$FILE_ID" 2>&1) || {
    echo "[files] ERROR: PUT /__admin/files/$FILE_ID request failed"
    echo "[files] Response: $response"
    exit 1
  }
  assert_http_code "files" "PUT" "/__admin/files/$FILE_ID" "$response" "200"
  echo "[files]   ✓ Create file passed"

  # Step 2: GET /__admin/files/{FILE_ID} — read file
  echo "[files]   Reading file..."
  response=$(curl_no_fail \
    --write-out "\n%{http_code}" \
    --request GET \
    "$API_URL/__admin/files/$FILE_ID" 2>&1) || true
  assert_http_code "files" "GET" "/__admin/files/$FILE_ID" "$response" "200"

  if ! echo "$BODY" | grep -q 'created'; then
    echo "[files] ERROR: File content does not contain 'created'"
    echo "[files] Response: $BODY"
    exit 1
  fi
  echo "[files]   ✓ Read file passed"

  # Step 3: GET /__admin/files — list files
  echo "[files]   Listing files..."
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request GET \
    "$API_URL/__admin/files" 2>&1) || {
    echo "[files] ERROR: GET /__admin/files request failed"
    echo "[files] Response: $response"
    exit 1
  }
  assert_http_code "files" "GET" "/__admin/files" "$response" "200"

  if ! echo "$BODY" | grep -q "$FILE_ID"; then
    echo "[files] ERROR: File list does not contain $FILE_ID"
    echo "[files] Response: $BODY"
    exit 1
  fi
  echo "[files]   ✓ List files passed"

  # Step 4: PUT /__admin/files/{FILE_ID} — update file
  echo "[files]   Updating file..."
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request PUT \
    --header "Content-Type: application/octet-stream" \
    --data '{"test": "file-management", "updated": true}' \
    "$API_URL/__admin/files/$FILE_ID" 2>&1) || {
    echo "[files] ERROR: PUT /__admin/files/$FILE_ID request failed"
    echo "[files] Response: $response"
    exit 1
  }
  assert_http_code "files" "PUT" "/__admin/files/$FILE_ID" "$response" "200"
  echo "[files]   ✓ Update file passed"

  # Step 5: GET /__admin/files/{FILE_ID} — verify updated content
  echo "[files]   Verifying updated file..."
  response=$(curl_no_fail \
    --write-out "\n%{http_code}" \
    --request GET \
    "$API_URL/__admin/files/$FILE_ID" 2>&1) || true
  assert_http_code "files" "GET" "/__admin/files/$FILE_ID" "$response" "200"

  if ! echo "$BODY" | grep -q 'updated'; then
    echo "[files] ERROR: File content does not contain 'updated'"
    echo "[files] Response: $BODY"
    exit 1
  fi
  echo "[files]   ✓ Verify updated file passed"

  # Step 6: DELETE /__admin/files/{FILE_ID}
  echo "[files]   Deleting file..."
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request DELETE \
    "$API_URL/__admin/files/$FILE_ID" 2>&1) || {
    echo "[files] ERROR: DELETE /__admin/files/$FILE_ID request failed"
    echo "[files] Response: $response"
    exit 1
  }
  assert_http_code "files" "DELETE" "/__admin/files/$FILE_ID" "$response" "200"
  echo "[files]   ✓ Delete file passed"

  # Step 7: GET /__admin/files/{FILE_ID} — verify 404
  echo "[files]   Verifying 404 after delete..."
  response=$(curl_no_fail \
    --write-out "\n%{http_code}" \
    --request GET \
    "$API_URL/__admin/files/$FILE_ID" 2>&1) || true
  assert_http_code "files" "GET" "/__admin/files/$FILE_ID" "$response" "404"
  echo "[files]   ✓ 404 after delete passed"

  echo "[files] ✓ File CRUD lifecycle passed"
}

# Cleanup file management test data
test_file_management_cleanup() {
  echo "[files] Cleaning up test data..."
  curl "${CURL_OPTS[@]}" \
    --request DELETE \
    "$API_URL/__admin/files/extensive-test-file.json" 2>/dev/null || true
  echo "[files] ✓ Cleanup complete"
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
    mock-management)
      echo "Running mock management extensive tests..."
      test_mock_management_crud
      test_mock_management_save_reset
      test_mock_management_metadata
      test_mock_management_unmatched
      test_mock_management_cleanup
      ;;
    request-verification)
      echo "Running request verification extensive tests..."
      test_request_verification_setup
      test_request_verification_list_find_count
      test_request_verification_get_by_id
      test_request_verification_unmatched
      test_request_verification_delete_by_id
      test_request_verification_remove
      test_request_verification_metadata
      test_request_verification_clear_reset
      test_request_verification_cleanup
      ;;
    near-miss)
      echo "Running near-miss extensive tests..."
      test_near_miss_setup
      test_near_miss_unmatched
      test_near_miss_for_request
      test_near_miss_for_request_pattern
      test_near_miss_cleanup
      ;;
    files)
      echo "Running file management extensive tests..."
      test_file_management_crud
      test_file_management_cleanup
      ;;
    extensive)
      echo "Running all extensive tests..."
      test_mock_management_crud
      test_mock_management_save_reset
      test_mock_management_metadata
      test_mock_management_unmatched
      test_mock_management_cleanup
      test_request_verification_setup
      test_request_verification_list_find_count
      test_request_verification_get_by_id
      test_request_verification_unmatched
      test_request_verification_delete_by_id
      test_request_verification_remove
      test_request_verification_metadata
      test_request_verification_clear_reset
      test_request_verification_cleanup
      test_near_miss_setup
      test_near_miss_unmatched
      test_near_miss_for_request
      test_near_miss_for_request_pattern
      test_near_miss_cleanup
      test_file_management_crud
      test_file_management_cleanup
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
      echo "Valid options: setup, rest, graphql, soap, webhook, mock-management, request-verification, near-miss, files, extensive, all"
      exit 2
      ;;
  esac
  
  echo ""
  echo "=== All Tests Passed ==="
}

main
