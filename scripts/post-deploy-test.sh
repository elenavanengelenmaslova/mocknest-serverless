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
#   all      - Run all tests sequentially (default)
#
# Examples:
#   $0 setup https://api.example.com abc123key
#   $0 rest https://api.example.com abc123key
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

if [ -z "$API_URL" ] || [ -z "$API_KEY" ]; then
  echo "ERROR: API_URL and API_KEY are required"
  echo ""
  echo "Usage: $0 [TEST_SUITE] <API_URL> <API_KEY>"
  echo "   or: $0 [TEST_SUITE]  # if API_URL and API_KEY are set as environment variables"
  echo ""
  echo "TEST_SUITE options: setup, rest, graphql, soap, all (default: all)"
  echo ""
  echo "Examples:"
  echo "  $0 setup https://api.example.com abc123key"
  echo "  $0 rest https://api.example.com abc123key"
  echo "  API_URL=https://api.example.com API_KEY=abc123key $0 all"
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

# Test GraphQL mock invocation
# Validates that imported GraphQL mocks respond correctly to requests
# Tests two GraphQL queries from the Countries API:
# 1. Query to get a specific country by code (Netherlands - NL)
# 2. Query to get all continents
test_graphql_mock_invocation() {
  echo "Testing GraphQL mock invocation..."
  
  # Test 1: Query for a specific country (Netherlands)
  echo "  Testing GraphQL query: country(code: \"NL\")..."
  local query1
  query1='{
    "query": "query { country(code: \"NL\") { code name capital currency continent { name } languages { name } } }"
  }'
  
  local response
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request POST \
    --data "$query1" \
    "$API_URL/mocknest/countries/graphql" 2>&1) || {
    echo "ERROR: GraphQL country query request failed"
    echo "Response: $response"
    exit 1
  }
  
  parse_response "$response"
  
  if [ "$HTTP_CODE" != "200" ]; then
    echo "ERROR: GraphQL country query failed with HTTP $HTTP_CODE"
    echo "Response: $BODY"
    exit 1
  fi
  
  # Validate response contains "data" field (standard GraphQL response structure)
  if ! echo "$BODY" | grep -q '"data"'; then
    echo "ERROR: GraphQL country query response missing 'data' field"
    echo "Response: $BODY"
    exit 1
  fi
  
  # Validate response contains country data
  if ! echo "$BODY" | grep -q '"country"'; then
    echo "ERROR: GraphQL country query response missing 'country' field"
    echo "Response: $BODY"
    exit 1
  fi
  
  echo "  ✓ GraphQL country query passed"
  
  # Test 2: Query for all continents
  echo "  Testing GraphQL query: continents..."
  local query2
  query2='{
    "query": "query { continents { code name } }"
  }'
  
  response=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code}" \
    --request POST \
    --data "$query2" \
    "$API_URL/mocknest/countries/graphql" 2>&1) || {
    echo "ERROR: GraphQL continents query request failed"
    echo "Response: $response"
    exit 1
  }
  
  parse_response "$response"
  
  if [ "$HTTP_CODE" != "200" ]; then
    echo "ERROR: GraphQL continents query failed with HTTP $HTTP_CODE"
    echo "Response: $BODY"
    exit 1
  fi
  
  # Validate response contains "data" field
  if ! echo "$BODY" | grep -q '"data"'; then
    echo "ERROR: GraphQL continents query response missing 'data' field"
    echo "Response: $BODY"
    exit 1
  fi
  
  # Validate response contains continents array
  if ! echo "$BODY" | grep -q '"continents"'; then
    echo "ERROR: GraphQL continents query response missing 'continents' field"
    echo "Response: $BODY"
    exit 1
  fi
  
  echo "  ✓ GraphQL continents query passed"
  
  echo "✓ GraphQL mock invocation passed"
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
      test_graphql_mock_invocation
      ;;
    soap)
      echo "Running SOAP/WSDL tests..."
      test_soap_generation
      test_soap_import
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
      test_graphql_mock_invocation
      test_soap_generation
      test_soap_import
      ;;
    *)
      echo "ERROR: Unknown test suite: $TEST_SUITE"
      echo "Valid options: setup, rest, graphql, soap, all"
      exit 2
      ;;
  esac
  
  echo ""
  echo "=== All Tests Passed ==="
}

main
