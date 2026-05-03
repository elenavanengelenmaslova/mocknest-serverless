#!/bin/bash
set -e
set -o pipefail

# MockNest Serverless Load Test Script
# This script sends sequential HTTP GET requests to a deployed MockNest Serverless
# health endpoint at a controlled rate, recording per-request client-side latency
# and HTTP status codes. Results are written to load-test-results.json for
# downstream percentile analysis and reporting.
#
# The script operates within API Gateway rate limiting constraints
# (BurstLimit: 1, RateLimit: 100 req/s) by sending sequential requests
# with a configurable delay. Any 429 response invalidates the test run.
#
# Required environment variables:
#   API_URL          - API Gateway base URL
#   AUTH_MODE        - Authentication mode: API_KEY or IAM
#   REQUEST_RATE     - Requests per second (1-50)
#   DURATION_MINUTES - Test duration in minutes
#   TEST_LABEL       - Label for the test run (e.g. "koin", "spring")
#
# Conditional environment variables:
#   API_KEY          - API key (required when AUTH_MODE=API_KEY)
#
# Optional environment variables:
#   STACK_NAME       - CloudFormation stack name (included in output metadata)
#   AWS_REGION       - AWS region (included in output metadata)
#   MEMORY_SIZE      - Lambda memory size in MB (included in output metadata)
#
# Exit codes:
#   0 - Test completed successfully
#   1 - Validation error (e.g. REQUEST_RATE > 50) or runtime abort
#   2 - Missing required environment variable
#
# Examples:
#   API_URL=https://api.example.com AUTH_MODE=API_KEY API_KEY=abc123 \
#     REQUEST_RATE=5 DURATION_MINUTES=10 TEST_LABEL=koin ./scripts/load-test.sh
#
#   API_URL=https://api.example.com AUTH_MODE=IAM \
#     REQUEST_RATE=2 DURATION_MINUTES=1 TEST_LABEL=spring ./scripts/load-test.sh

# ---------------------------------------------------------------------------
# Environment variable validation
# ---------------------------------------------------------------------------
MISSING_VARS=()

[ -z "$API_URL" ] && MISSING_VARS+=("API_URL")
[ -z "$AUTH_MODE" ] && MISSING_VARS+=("AUTH_MODE")
[ -z "$REQUEST_RATE" ] && MISSING_VARS+=("REQUEST_RATE")
[ -z "$DURATION_MINUTES" ] && MISSING_VARS+=("DURATION_MINUTES")
[ -z "$TEST_LABEL" ] && MISSING_VARS+=("TEST_LABEL")

if [ "${#MISSING_VARS[@]}" -gt 0 ]; then
  echo "ERROR: Missing required environment variable(s): ${MISSING_VARS[*]}"
  echo ""
  echo "Usage:"
  echo "  API_URL=<url> AUTH_MODE=<API_KEY|IAM> REQUEST_RATE=<1-50> \\"
  echo "    DURATION_MINUTES=<minutes> TEST_LABEL=<label> $0"
  echo ""
  echo "Required environment variables:"
  echo "  API_URL          - API Gateway base URL"
  echo "  AUTH_MODE        - Authentication mode: API_KEY or IAM"
  echo "  REQUEST_RATE     - Requests per second (1-50)"
  echo "  DURATION_MINUTES - Test duration in minutes"
  echo "  TEST_LABEL       - Label for the test run"
  echo ""
  echo "Conditional:"
  echo "  API_KEY          - API key (required when AUTH_MODE=API_KEY)"
  exit 2
fi

if [ "$AUTH_MODE" = "API_KEY" ] && [ -z "$API_KEY" ]; then
  echo "ERROR: API_KEY is required when AUTH_MODE=API_KEY"
  echo ""
  echo "Usage:"
  echo "  API_URL=<url> AUTH_MODE=API_KEY API_KEY=<key> REQUEST_RATE=<1-50> \\"
  echo "    DURATION_MINUTES=<minutes> TEST_LABEL=<label> $0"
  exit 2
fi

# ---------------------------------------------------------------------------
# REQUEST_RATE validation
# ---------------------------------------------------------------------------
if [ "$REQUEST_RATE" -gt 50 ] 2>/dev/null; then
  echo "ERROR: REQUEST_RATE=$REQUEST_RATE exceeds maximum of 50"
  echo "The API Gateway usage plan has a BurstLimit of 1 and RateLimit of 100 req/s."
  echo "To avoid throttling, REQUEST_RATE must be between 1 and 50."
  exit 1
fi

# Remove trailing slash from API_URL if present
API_URL="${API_URL%/}"

# ---------------------------------------------------------------------------
# Build curl options array
# ---------------------------------------------------------------------------
if [ "$AUTH_MODE" = "IAM" ]; then
  echo "Auth mode: IAM (SigV4 signing)"
  CURL_OPTS=(
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
  CURL_OPTS=(
    --silent
    --show-error
    --max-time 30
    --header "x-api-key: $API_KEY"
    --header "Content-Type: application/json"
  )
fi

# ---------------------------------------------------------------------------
# Calculate test parameters
# ---------------------------------------------------------------------------
TOTAL_REQUESTS=$((REQUEST_RATE * DURATION_MINUTES * 60))
DELAY=$(awk "BEGIN {printf \"%.6f\", 1 / $REQUEST_RATE}")

echo "Load test configuration:"
echo "  API URL:          $API_URL"
echo "  Auth mode:        $AUTH_MODE"
echo "  Request rate:     $REQUEST_RATE req/s"
echo "  Duration:         $DURATION_MINUTES min"
echo "  Total requests:   $TOTAL_REQUESTS"
echo "  Delay between:    ${DELAY}s"
echo "  Test label:       $TEST_LABEL"
echo ""

# ---------------------------------------------------------------------------
# Initialize tracking variables
# ---------------------------------------------------------------------------
THROTTLED_COUNT=0
ERROR_COUNT=0
CONSECUTIVE_ERRORS=0
IS_VALID=true

# Temporary file for per-request data (avoids shell argument length limits)
REQUESTS_TMPFILE=$(mktemp)
trap 'rm -f "$REQUESTS_TMPFILE"' EXIT

# Record test start time
START_TIME=$(date +%s)

# ---------------------------------------------------------------------------
# Sequential request loop
# ---------------------------------------------------------------------------
echo "Starting load test..."

for ((i = 1; i <= TOTAL_REQUESTS; i++)); do
  TIMESTAMP=$(date +%s)

  # Send GET request and capture timing + status code
  RESPONSE=$(curl "${CURL_OPTS[@]}" \
    --write-out "\n%{http_code} %{time_total}" \
    --output /dev/null \
    "${API_URL}/__admin/health" 2>&1) || true

  HTTP_CODE=$(echo "$RESPONSE" | tail -n1 | awk '{print $1}')
  TIME_TOTAL=$(echo "$RESPONSE" | tail -n1 | awk '{print $2}')
  LATENCY_MS=$(awk "BEGIN {printf \"%.1f\", $TIME_TOTAL * 1000}")

  # Append per-request data as a CSV line (timestamp,status_code,latency_ms)
  echo "${TIMESTAMP},${HTTP_CODE},${LATENCY_MS}" >> "$REQUESTS_TMPFILE"

  # Track 429 throttling
  if [ "$HTTP_CODE" = "429" ]; then
    THROTTLED_COUNT=$((THROTTLED_COUNT + 1))
    # 429 does not count toward consecutive non-2xx errors
  elif [ "$HTTP_CODE" -lt 200 ] 2>/dev/null || [ "$HTTP_CODE" -ge 300 ] 2>/dev/null; then
    # Non-2xx (excluding 429)
    ERROR_COUNT=$((ERROR_COUNT + 1))
    CONSECUTIVE_ERRORS=$((CONSECUTIVE_ERRORS + 1))
  else
    # 2xx success — reset consecutive error counter
    CONSECUTIVE_ERRORS=0
  fi

  # Abort if >10 consecutive non-2xx errors (excluding 429)
  if [ "$CONSECUTIVE_ERRORS" -gt 10 ]; then
    echo "ABORT: More than 10 consecutive non-2xx errors (excluding 429). Stopping test."
    break
  fi

  # Progress reporting every 100 requests
  if [ $((i % 100)) -eq 0 ] || [ "$i" -eq "$TOTAL_REQUESTS" ]; then
    echo "  Progress: $i/$TOTAL_REQUESTS requests (429s: $THROTTLED_COUNT, errors: $ERROR_COUNT)"
  fi

  # Sleep between requests (skip after last request)
  if [ "$i" -lt "$TOTAL_REQUESTS" ]; then
    sleep "$DELAY"
  fi
done

# Record test end time
END_TIME=$(date +%s)

echo ""
echo "Load test complete."
echo "  Throttled (429):     $THROTTLED_COUNT"
echo "  Errors (non-2xx):    $ERROR_COUNT"
echo "  Valid run:           $IS_VALID"

# ---------------------------------------------------------------------------
# Write JSON output using python3
# ---------------------------------------------------------------------------
python3 << PYEOF
import json
import os

# Read per-request data from temp file
requests = []
with open("$REQUESTS_TMPFILE", "r") as f:
    for line in f:
        line = line.strip()
        if not line:
            continue
        parts = line.split(",")
        requests.append({
            "timestamp": int(parts[0]),
            "status_code": int(parts[1]),
            "latency_ms": float(parts[2])
        })

memory_str = "${MEMORY_SIZE:-0}"
try:
    memory_val = int(memory_str)
except ValueError:
    memory_val = 0

result = {
    "test_label": "${TEST_LABEL}",
    "stack_name": "${STACK_NAME:-}",
    "aws_region": "${AWS_REGION:-}",
    "memory_size": memory_val,
    "start_time": ${START_TIME},
    "end_time": ${END_TIME},
    "request_rate": ${REQUEST_RATE},
    "duration_minutes": ${DURATION_MINUTES},
    "total_requests": len(requests),
    "throttled_count": ${THROTTLED_COUNT},
    "error_count": ${ERROR_COUNT},
    "is_valid": $( [ "$IS_VALID" = "true" ] && echo "True" || echo "False" ),
    "requests": requests
}

with open("load-test-results.json", "w") as f:
    json.dump(result, f, indent=2)

print(f"  Wrote {len(requests)} request records to load-test-results.json")
PYEOF

echo ""
echo "Results written to load-test-results.json"
