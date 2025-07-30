#!/bin/bash
# Test script for connector configurations
# knowledge-ingestion/scripts/test-connectors.sh

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
BASE_URL="http://localhost:8081"
GATEWAY_URL="http://localhost:8080"
TEMP_DIR="/tmp/ingestion-test"
TEST_RESULTS_FILE="$TEMP_DIR/test-results.json"

# Test configuration
TIMEOUT=30
MAX_RETRIES=3
WAIT_INTERVAL=2

echo -e "${GREEN}🧪 Testing Knowledge Ingestion Service Connectors${NC}"
echo -e "${GREEN}=================================================${NC}"

# Function to print section headers
print_section() {
    echo -e "\n${BLUE}===== $1 =====${NC}"
}

# Function to setup test environment
setup_test_environment() {
    print_section "Setting Up Test Environment"

    # Create temp directory for test artifacts
    mkdir -p "$TEMP_DIR"

    # Initialize test results
    echo '{"tests": [], "summary": {}}' > "$TEST_RESULTS_FILE"

    echo -e "${GREEN}✓ Test environment prepared${NC}"
    echo -e "  Temp directory: $TEMP_DIR"
    echo -e "  Results file: $TEST_RESULTS_FILE"
}

# Function to add test result
add_test_result() {
    local test_name="$1"
    local status="$2"
    local response_code="$3"
    local endpoint="$4"
    local message="${5:-}"

    local result=$(cat << EOF
{
  "name": "$test_name",
  "status": "$status",
  "responseCode": $response_code,
  "endpoint": "$endpoint",
  "message": "$message",
  "timestamp": "$(date -Iseconds)"
}
EOF
)

    # Add to results file (simplified - in production use jq)
    echo "$result" >> "$TEMP_DIR/test_result_$test_name.json"
}

# Function to test endpoint with comprehensive validation
test_endpoint() {
    local test_name="$1"
    local endpoint="$2"
    local expected_status="${3:-200}"
    local method="${4:-GET}"
    local data="${5:-}"
    local headers="${6:-}"

    echo -e "${BLUE}Testing: $test_name${NC}"
    echo -e "  Endpoint: $method $endpoint"
    echo -e "  Expected: HTTP $expected_status"

    local temp_response="$TEMP_DIR/response_${test_name}.json"
    local temp_headers="$TEMP_DIR/headers_${test_name}.txt"

    # Build curl command
    local curl_cmd="curl -s -w '%{http_code}' -o '$temp_response' -D '$temp_headers'"
    curl_cmd="$curl_cmd --max-time $TIMEOUT"

    if [ -n "$headers" ]; then
        curl_cmd="$curl_cmd $headers"
    fi

    if [ "$method" != "GET" ]; then
        curl_cmd="$curl_cmd -X $method"
    fi

    if [ -n "$data" ]; then
        curl_cmd="$curl_cmd -H 'Content-Type: application/json' -d '$data'"
    fi

    curl_cmd="$curl_cmd '$BASE_URL$endpoint'"

    # Execute request with retry logic
    local response_code="000"
    local retry_count=0

    while [ $retry_count -lt $MAX_RETRIES ]; do
        response_code=$(eval $curl_cmd 2>/dev/null || echo "000")

        if [ "$response_code" != "000" ]; then
            break
        fi

        retry_count=$((retry_count + 1))
        if [ $retry_count -lt $MAX_RETRIES ]; then
            echo -e "  ${YELLOW}Retry $retry_count/$MAX_RETRIES...${NC}"
            sleep $WAIT_INTERVAL
        fi
    done

    # Evaluate test result
    if [ "$response_code" = "$expected_status" ]; then
        echo -e "  ${GREEN}✅ PASS - Status: $response_code${NC}"
        add_test_result "$test_name" "PASS" "$response_code" "$endpoint" "Test passed successfully"

        # Parse and display response if JSON
        if [ -f "$temp_response" ] && [ "$method" = "GET" ]; then
            local content_type=$(grep -i "content-type" "$temp_headers" 2>/dev/null | grep -o "application/json" || echo "")
            if [ -n "$content_type" ]; then
                echo -e "  ${BLUE}Response preview:${NC}"
                head -c 200 "$temp_response" | sed 's/^/    /'
                if [ $(wc -c < "$temp_response") -gt 200 ]; then
                    echo "    ..."
                fi
            fi
        fi
        return 0
    else
        echo -e "  ${RED}❌ FAIL - Expected: $expected_status, Got: $response_code${NC}"
        add_test_result "$test_name" "FAIL" "$response_code" "$endpoint" "Unexpected response code"

        # Show error details
        if [ -f "$temp_response" ] && [ -s "$temp_response" ]; then
            echo -e "  ${RED}Error Response:${NC}"
            head -c 300 "$temp_response" | sed 's/^/    /'
        fi
        return 1
    fi
}

# Function to wait for service to be ready
wait_for_service() {
    print_section "Waiting for Service Readiness"

    echo -e "${YELLOW}⏳ Waiting for ingestion service to be ready...${NC}"

    local ready=false
    local attempts=0
    local max_attempts=30

    while [ $attempts -lt $max_attempts ] && [ "$ready" = false ]; do
        if curl -s --max-time 5 "$BASE_URL/actuator/health" >/dev/null 2>&1; then
            ready=true
            echo -e "${GREEN}✅ Service is ready!${NC}"
        else
            attempts=$((attempts + 1))
            echo -e "  Attempt $attempts/$max_attempts - waiting..."
            sleep $WAIT_INTERVAL
        fi
    done

    if [ "$ready" = false ]; then
        echo -e "${RED}❌ Service failed to become ready within timeout${NC}"
        echo -e "${YELLOW}Troubleshooting steps:${NC}"
        echo -e "  1. Check if service is running: docker-compose ps"
        echo -e "  2. Check service logs: docker-compose logs knowledge-ingestion"
        echo -e "  3. Verify port 8081 is accessible: netstat -tlnp | grep 8081"
        exit 1
    fi
}

# Function to test basic health endpoints
test_health_endpoints() {
    print_section "Testing Health Endpoints"

    # Basic health check
    test_endpoint "health_check" "/actuator/health" 200

    # Service info
    test_endpoint "service_info" "/actuator/info" 200

    # Metrics endpoint
    test_endpoint "metrics" "/actuator/metrics" 200

    # Application readiness
    test_endpoint "readiness" "/actuator/health/readiness" 200

    # Application liveness
    test_endpoint "liveness" "/actuator/health/liveness" 200
}

# Function to test connector management endpoints
test_connector_endpoints() {
    print_section "Testing Connector Management"

    # Note: These tests may fail with 401/403 without proper authentication
    # but they test endpoint availability and basic routing

    # List all connectors (may require auth)
    test_endpoint "list_connectors" "/api/connectors" 401

    # Get available connector types
    test_endpoint "available_connectors" "/api/ingestion/connectors" 401

    # Test connector stats (may require auth)
    test_endpoint "connector_stats" "/api/connectors/stats" 401

    # Test ingestion status
    test_endpoint "ingestion_status" "/api/ingestion/status" 401
}

# Function to test job monitoring endpoints
test_job_endpoints() {
    print_section "Testing Job Monitoring"

    # Running jobs
    test_endpoint "running_jobs" "/api/jobs/running" 401

    # Job statistics
    test_endpoint "job_stats" "/api/jobs/stats/by-connector" 401

    # Performance metrics
    test_endpoint "performance_metrics" "/api/jobs/metrics/performance" 401

    # Job summary
    test_endpoint "job_summary" "/api/jobs/summary" 401

    # Long running jobs
    test_endpoint "long_running_jobs" "/api/jobs/long-running" 401
}

# Function to test credential endpoints (should all require admin auth)
test_credential_endpoints() {
    print_section "Testing Credential Management"

    # These should all return 401/403 without proper admin authentication

    # Get credentials (admin only)
    test_endpoint "credentials_stats" "/api/credentials/stats" 401

    # Expiring credentials
    test_endpoint "expiring_credentials" "/api/credentials/expiring" 401

    # Expired credentials
    test_endpoint "expired_credentials" "/api/credentials/expired" 401
}

# Function to test error handling
test_error_handling() {
    print_section "Testing Error Handling"

    # Test 404 errors
    test_endpoint "not_found" "/api/nonexistent" 404

    # Test invalid endpoints
    test_endpoint "invalid_connector" "/api/connectors/invalid-uuid" 401

    # Test malformed requests
    test_endpoint "malformed_request" "/api/connectors" 401 "POST" '{"invalid": json}'
}

# Function to test CORS and security headers
test_security_headers() {
    print_section "Testing Security Headers"

    local temp_headers="$TEMP_DIR/security_headers.txt"

    # Test CORS preflight
    echo -e "${BLUE}Testing CORS preflight...${NC}"
    curl -s -I -X OPTIONS \
        -H "Origin: http://localhost:3000" \
        -H "Access-Control-Request-Method: POST" \
        -H "Access-Control-Request-Headers: Content-Type" \
        "$BASE_URL/api/connectors" > "$temp_headers" 2>/dev/null || true

    if grep -q "Access-Control-Allow-Origin" "$temp_headers"; then
        echo -e "  ${GREEN}✅ CORS headers present${NC}"
    else
        echo -e "  ${YELLOW}⚠️  CORS headers not found (may be expected)${NC}"
    fi

    # Test security headers on health endpoint
    echo -e "${BLUE}Testing security headers...${NC}"
    curl -s -I "$BASE_URL/actuator/health" > "$temp_headers" 2>/dev/null || true

    if grep -q "X-Frame-Options\|X-Content-Type-Options\|X-XSS-Protection" "$temp_headers"; then
        echo -e "  ${GREEN}✅ Security headers present${NC}"
    else
        echo -e "  ${YELLOW}⚠️  Security headers not found${NC}"
    fi
}

# Function to test service integration
test_service_integration() {
    print_section "Testing Service Integration"

    # Test database connectivity (via health endpoint)
    local temp_response="$TEMP_DIR/health_detail.json"
    curl -s "$BASE_URL/actuator/health" > "$temp_response" 2>/dev/null || true

    if [ -f "$temp_response" ]; then
        echo -e "${BLUE}Health check details:${NC}"

        # Check for database connectivity
        if grep -q "db\|r2dbc\|database" "$temp_response"; then
            echo -e "  ${GREEN}✅ Database connectivity detected${NC}"
        else
            echo -e "  ${YELLOW}⚠️  Database status unclear${NC}"
        fi

        # Check overall status
        if grep -q '"status":"UP"' "$temp_response"; then
            echo -e "  ${GREEN}✅ Service status: UP${NC}"
        else
            echo -e "  ${YELLOW}⚠️  Service status unclear${NC}"
        fi
    fi
}

# Function to generate test report
generate_test_report() {
    print_section "Generating Test Report"

    local report_file="$TEMP_DIR/test_report.txt"
    local total_tests=0
    local passed_tests=0
    local failed_tests=0

    # Count test results
    for result_file in "$TEMP_DIR"/test_result_*.json; do
        if [ -f "$result_file" ]; then
            total_tests=$((total_tests + 1))
            if grep -q '"status": "PASS"' "$result_file"; then
                passed_tests=$((passed_tests + 1))
            else
                failed_tests=$((failed_tests + 1))
            fi
        fi
    done

    # Generate report
    cat > "$report_file" << EOF
Knowledge Ingestion Service Test Report
======================================
Generated: $(date)
Service URL: $BASE_URL

Test Summary:
- Total Tests: $total_tests
- Passed: $passed_tests
- Failed: $failed_tests
- Success Rate: $(( passed_tests * 100 / total_tests ))%

Test Categories:
✓ Health Endpoints
✓ Connector Management
✓ Job Monitoring
✓ Credential Management
✓ Error Handling
✓ Security Headers
✓ Service Integration

Notes:
- Authentication failures (401/403) are expected for protected endpoints
- This test validates service availability and basic functionality
- Full functionality requires proper JWT authentication setup

Recommendations:
EOF

    # Add recommendations based on results
    if [ $failed_tests -gt 0 ]; then
        echo "- Investigate failed tests for potential issues" >> "$report_file"
    fi

    if [ $passed_tests -eq $total_tests ]; then
        echo "- All tests passed! Service appears to be functioning correctly" >> "$report_file"
    fi

    echo "- Configure authentication to test protected endpoints" >> "$report_file"
    echo "- Add connector configurations and credentials for full testing" >> "$report_file"
    echo "- Monitor service logs for any error patterns" >> "$report_file"

    # Display report
    echo -e "${GREEN}📊 Test Report Generated${NC}"
    echo -e "  Location: $report_file"
    echo ""
    cat "$report_file"

    # Summary
    echo -e "\n${BLUE}Test Summary:${NC}"
    echo -e "  Total: $total_tests"
    echo -e "  ${GREEN}Passed: $passed_tests${NC}"
    echo -e "  ${RED}Failed: $failed_tests${NC}"

    if [ $failed_tests -eq 0 ]; then
        echo -e "\n${GREEN}🎉 All tests completed successfully!${NC}"
        return 0
    else
        echo -e "\n${YELLOW}⚠️  Some tests failed. Check the details above.${NC}"
        return 1
    fi
}

# Function to cleanup test artifacts
cleanup_test_environment() {
    print_section "Cleaning Up Test Environment"

    echo -e "${BLUE}Cleaning up temporary files...${NC}"

    # Archive test results
    local archive_name="ingestion_test_$(date +%Y%m%d_%H%M%S).tar.gz"
    tar -czf "$archive_name" -C "$TEMP_DIR" . 2>/dev/null || true

    if [ -f "$archive_name" ]; then
        echo -e "  ${GREEN}✓ Test results archived: $archive_name${NC}"
    fi

    # Clean up temp directory
    rm -rf "$TEMP_DIR" 2>/dev/null || true
    echo -e "  ${GREEN}✓ Temporary files cleaned up${NC}"
}

# Function to show help
show_help() {
    cat << EOF
Knowledge Ingestion Service Connector Test Script

Usage: $0 [OPTIONS]

Options:
  --base-url URL       Base URL for the service (default: http://localhost:8081)
  --timeout SECONDS    Timeout for individual requests (default: 30)
  --retries COUNT      Number of retries for failed requests (default: 3)
  --quick              Run only basic health checks (skip detailed tests)
  --verbose            Enable verbose output
  --help, -h           Show this help message

Environment Variables:
  INGESTION_BASE_URL   Override base URL
  TEST_TIMEOUT         Override timeout value
  MAX_RETRIES          Override retry count

Examples:
  $0                              # Run all tests with defaults
  $0 --quick                      # Run only health checks
  $0 --base-url http://staging:8081  # Test staging environment
  $0 --timeout 60 --retries 5    # Custom timeout and retries

Test Categories:
  - Health Endpoints: Basic service health and readiness
  - Connector Management: Connector configuration endpoints
  - Job Monitoring: Ingestion job status and metrics
  - Credential Management: API credential endpoints
  - Error Handling: Error response validation
  - Security: CORS and security headers
  - Integration: Database and service connectivity

Note: Many endpoints require authentication and will return 401/403.
This is expected behavior and indicates proper security implementation.
EOF
}

# Main execution function
main() {
    local quick_test=false
    local verbose=false

    # Parse command line arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --base-url)
                BASE_URL="$2"
                shift 2
                ;;
            --timeout)
                TIMEOUT="$2"
                shift 2
                ;;
            --retries)
                MAX_RETRIES="$2"
                shift 2
                ;;
            --quick)
                quick_test=true
                shift
                ;;
            --verbose)
                verbose=true
                set -x
                shift
                ;;
            --help|-h)
                show_help
                exit 0
                ;;
            *)
                echo -e "${RED}Unknown option: $1${NC}"
                show_help
                exit 1
                ;;
        esac
    done

    # Override with environment variables if set
    BASE_URL="${INGESTION_BASE_URL:-$BASE_URL}"
    TIMEOUT="${TEST_TIMEOUT:-$TIMEOUT}"
    MAX_RETRIES="${MAX_RETRIES:-$MAX_RETRIES}"

    # Print test configuration
    echo -e "${BLUE}Test Configuration:${NC}"
    echo -e "  Service URL: $BASE_URL"
    echo -e "  Timeout: ${TIMEOUT}s"
    echo -e "  Max Retries: $MAX_RETRIES"
    echo -e "  Quick Test: $quick_test"

    # Execute test suite
    setup_test_environment
    wait_for_service
    test_health_endpoints

    if [ "$quick_test" = false ]; then
        test_connector_endpoints
        test_job_endpoints
        test_credential_endpoints
        test_error_handling
        test_security_headers
        test_service_integration
    fi

    local test_result=0
    generate_test_report || test_result=1
    cleanup_test_environment

    exit $test_result
}

# Handle script interruption
trap 'echo -e "\n${RED}Test interrupted!${NC}"; cleanup_test_environment; exit 1' INT TERM

# Run main function with all arguments
main "$@"