#!/bin/bash
set -euo pipefail

# Comprehensive API Endpoint Validation Script
# Tests all endpoints with proper authentication and validates responses

# Configuration
PROJECT_NAME="${PROJECT_NAME:-lambda-java-template}"
ENVIRONMENT="${ENVIRONMENT:-dev}"
AWS_REGION="${AWS_REGION:-us-east-1}"
API_KEY="${API_KEY:-test-api-key}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Get API Gateway URL dynamically
get_api_gateway_url() {
    log_info "Discovering API Gateway URL..."
    
    local api_name="${PROJECT_NAME}-${ENVIRONMENT}-api"
    local api_url
    
    api_url=$(aws apigatewayv2 get-apis \
        --region "${AWS_REGION}" \
        --query "Items[?Name=='${api_name}'].ApiEndpoint" \
        --output text 2>/dev/null || echo "")
    
    if [[ -z "$api_url" || "$api_url" == "None" ]]; then
        log_error "Could not find API Gateway: ${api_name}"
        log_info "Available APIs:"
        aws apigatewayv2 get-apis --region "${AWS_REGION}" --query "Items[*].{Name:Name,Endpoint:ApiEndpoint}" --output table || true
        return 1
    fi
    
    log_success "Found API Gateway URL: ${api_url}"
    echo "$api_url"
}

# Test function with retries
test_endpoint() {
    local method="$1"
    local url="$2"
    local expected_status="$3"
    local description="$4"
    local auth_required="${5:-false}"
    local max_retries=3
    local retry_count=0
    
    log_info "Testing: ${description}"
    log_info "  ${method} ${url}"
    
    while [[ $retry_count -lt $max_retries ]]; do
        local headers=()
        local response_file
        response_file=$(mktemp)
        
        # Add authentication header if required
        if [[ "$auth_required" == "true" ]]; then
            headers+=("-H" "x-api-key: ${API_KEY}")
        fi
        
        # Add content type for POST/PUT requests
        if [[ "$method" == "POST" || "$method" == "PUT" ]]; then
            headers+=("-H" "Content-Type: application/json")
        fi
        
        # Make the request
        local status_code
        local response_body
        
        if [[ "$method" == "POST" || "$method" == "PUT" ]]; then
            local test_data='{"name":"Test Product","description":"Test Description","price":99.99}'
            status_code=$(curl -s -w "%{http_code}" -X "$method" \
                "${headers[@]}" \
                -d "$test_data" \
                "$url" \
                -o "$response_file" || echo "000")
        else
            status_code=$(curl -s -w "%{http_code}" -X "$method" \
                "${headers[@]}" \
                "$url" \
                -o "$response_file" || echo "000")
        fi
        
        response_body=$(cat "$response_file" 2>/dev/null || echo "")
        rm -f "$response_file"
        
        # Check if we got the expected status code
        if [[ "$status_code" == "$expected_status" ]]; then
            log_success "  ✅ Status: ${status_code} (Expected: ${expected_status})"
            
            # Additional validation for specific endpoints
            case "$url" in
                */health)
                    if [[ "$response_body" == *"healthy"* ]] || [[ "$response_body" == *"UP"* ]]; then
                        log_success "  ✅ Health check response valid"
                    else
                        log_warning "  ⚠️  Health check response format unexpected: ${response_body}"
                    fi
                    ;;
                */products)
                    if [[ "$method" == "GET" ]]; then
                        if [[ "$response_body" == *"["* ]] || [[ "$response_body" == *"products"* ]]; then
                            log_success "  ✅ Products list response valid"
                        else
                            log_warning "  ⚠️  Products list response format unexpected: ${response_body}"
                        fi
                    elif [[ "$method" == "POST" ]]; then
                        if [[ "$response_body" == *"id"* ]] || [[ "$status_code" == "201" ]]; then
                            log_success "  ✅ Product creation response valid"
                        else
                            log_warning "  ⚠️  Product creation response unexpected: ${response_body}"
                        fi
                    fi
                    ;;
            esac
            
            if [[ -n "$response_body" ]] && [[ ${#response_body} -lt 500 ]]; then
                log_info "  📄 Response: ${response_body}"
            elif [[ -n "$response_body" ]]; then
                log_info "  📄 Response: ${response_body:0:200}... (truncated)"
            fi
            
            return 0
        else
            retry_count=$((retry_count + 1))
            log_warning "  ⚠️  Attempt ${retry_count}/${max_retries} - Status: ${status_code} (Expected: ${expected_status})"
            
            if [[ -n "$response_body" ]]; then
                log_warning "  📄 Response: ${response_body}"
            fi
            
            if [[ $retry_count -lt $max_retries ]]; then
                log_info "  🔄 Retrying in 5 seconds..."
                sleep 5
            fi
        fi
    done
    
    log_error "  ❌ Failed after ${max_retries} attempts"
    return 1
}

# Validate infrastructure state
validate_infrastructure() {
    log_info "Validating infrastructure state..."
    
    # Check Lambda functions
    local functions=("${PROJECT_NAME}-${ENVIRONMENT}-product-service" "${PROJECT_NAME}-${ENVIRONMENT}-authorizer-service")
    for func in "${functions[@]}"; do
        log_info "Checking Lambda function: ${func}"
        if aws lambda get-function --function-name "$func" --region "${AWS_REGION}" >/dev/null 2>&1; then
            local state
            state=$(aws lambda get-function --function-name "$func" --region "${AWS_REGION}" --query 'Configuration.State' --output text)
            if [[ "$state" == "Active" ]]; then
                log_success "  ✅ Function ${func} is Active"
            else
                log_warning "  ⚠️  Function ${func} state: ${state}"
            fi
        else
            log_error "  ❌ Function ${func} not found"
            return 1
        fi
    done
    
    # Check DynamoDB tables
    local tables=("${PROJECT_NAME}-${ENVIRONMENT}-products" "${PROJECT_NAME}-${ENVIRONMENT}-audit-logs")
    for table in "${tables[@]}"; do
        log_info "Checking DynamoDB table: ${table}"
        if aws dynamodb describe-table --table-name "$table" --region "${AWS_REGION}" >/dev/null 2>&1; then
            local status
            status=$(aws dynamodb describe-table --table-name "$table" --region "${AWS_REGION}" --query 'Table.TableStatus' --output text)
            if [[ "$status" == "ACTIVE" ]]; then
                log_success "  ✅ Table ${table} is Active"
            else
                log_warning "  ⚠️  Table ${table} status: ${status}"
            fi
        else
            log_error "  ❌ Table ${table} not found"
            return 1
        fi
    done
    
    log_success "Infrastructure validation completed"
}

# Run comprehensive endpoint tests
run_endpoint_tests() {
    local api_url="$1"
    local test_results=()
    
    log_info "Starting comprehensive endpoint tests..."
    echo "============================================"
    
    # Test 1: Health endpoint (no auth)
    if test_endpoint "GET" "${api_url}/health" "200" "Health Check (No Auth)" "false"; then
        test_results+=("✅ Health Check")
    else
        test_results+=("❌ Health Check")
    fi
    
    echo ""
    
    # Test 2: Products list without auth (should fail)
    if test_endpoint "GET" "${api_url}/products" "401" "Products List (No Auth - Should Fail)" "false"; then
        test_results+=("✅ Products Auth Protection")
    else
        test_results+=("❌ Products Auth Protection")
    fi
    
    echo ""
    
    # Test 3: Products list with auth
    if test_endpoint "GET" "${api_url}/products" "200" "Products List (With Auth)" "true"; then
        test_results+=("✅ Products List")
    else
        test_results+=("❌ Products List")
    fi
    
    echo ""
    
    # Test 4: Create product
    if test_endpoint "POST" "${api_url}/products" "201" "Create Product" "true"; then
        test_results+=("✅ Create Product")
    else
        test_results+=("❌ Create Product")
    fi
    
    echo ""
    
    # Test 5: Get specific product (may not exist, so 404 is acceptable)
    if test_endpoint "GET" "${api_url}/products/test-id" "404" "Get Product by ID (Not Found Expected)" "true"; then
        test_results+=("✅ Get Product by ID")
    else
        # Try 200 in case product exists
        if test_endpoint "GET" "${api_url}/products/test-id" "200" "Get Product by ID (Found)" "true"; then
            test_results+=("✅ Get Product by ID")
        else
            test_results+=("❌ Get Product by ID")
        fi
    fi
    
    echo ""
    
    # Test 6: Update product (may not exist)
    if test_endpoint "PUT" "${api_url}/products/test-id" "404" "Update Product (Not Found Expected)" "true"; then
        test_results+=("✅ Update Product")
    else
        test_results+=("❌ Update Product")
    fi
    
    echo ""
    
    # Test 7: Delete product (may not exist)
    if test_endpoint "DELETE" "${api_url}/products/test-id" "404" "Delete Product (Not Found Expected)" "true"; then
        test_results+=("✅ Delete Product")
    else
        test_results+=("❌ Delete Product")
    fi
    
    echo ""
    echo "============================================"
    log_info "Test Results Summary:"
    for result in "${test_results[@]}"; do
        echo "  $result"
    done
    
    # Count failures
    local failed_count
    failed_count=$(printf '%s\n' "${test_results[@]}" | grep -c "❌" || true)
    
    if [[ $failed_count -eq 0 ]]; then
        log_success "All endpoint tests passed! 🎉"
        return 0
    else
        log_error "${failed_count} endpoint tests failed"
        return 1
    fi
}

# Performance test
run_performance_test() {
    local api_url="$1"
    
    log_info "Running performance tests..."
    
    local health_url="${api_url}/health"
    local total_time=0
    local test_count=5
    
    for i in $(seq 1 $test_count); do
        log_info "Performance test ${i}/${test_count}..."
        local start_time
        start_time=$(date +%s%N)
        
        local status_code
        status_code=$(curl -s -w "%{http_code}" -o /dev/null "$health_url" || echo "000")
        
        local end_time
        end_time=$(date +%s%N)
        local duration_ms=$(((end_time - start_time) / 1000000))
        
        log_info "  Request ${i}: ${duration_ms}ms (Status: ${status_code})"
        total_time=$((total_time + duration_ms))
        
        sleep 1
    done
    
    local avg_time=$((total_time / test_count))
    log_info "Average response time: ${avg_time}ms"
    
    if [[ $avg_time -lt 10000 ]]; then  # Less than 10 seconds
        log_success "Performance test passed (avg: ${avg_time}ms)"
        return 0
    else
        log_warning "Performance test concerning (avg: ${avg_time}ms)"
        return 1
    fi
}

# Main execution
main() {
    echo "🧪 AWS Lambda Java Template - Comprehensive Endpoint Validation"
    echo "==============================================================="
    
    # Validate prerequisites
    if ! command -v aws >/dev/null 2>&1; then
        log_error "AWS CLI not found. Please install and configure AWS CLI."
        exit 1
    fi
    
    if ! command -v curl >/dev/null 2>&1; then
        log_error "curl not found. Please install curl."
        exit 1
    fi
    
    # Check AWS credentials
    if ! aws sts get-caller-identity >/dev/null 2>&1; then
        log_error "AWS credentials not configured. Please run 'aws configure'."
        exit 1
    fi
    
    log_info "Using configuration:"
    log_info "  Project: ${PROJECT_NAME}"
    log_info "  Environment: ${ENVIRONMENT}"
    log_info "  Region: ${AWS_REGION}"
    log_info "  API Key: ${API_KEY}"
    echo ""
    
    # Step 1: Validate infrastructure
    if ! validate_infrastructure; then
        log_error "Infrastructure validation failed"
        exit 1
    fi
    
    echo ""
    
    # Step 2: Get API Gateway URL
    local api_url
    if ! api_url=$(get_api_gateway_url); then
        exit 1
    fi
    
    echo ""
    
    # Step 3: Run endpoint tests
    local endpoint_result=0
    if ! run_endpoint_tests "$api_url"; then
        endpoint_result=1
    fi
    
    echo ""
    
    # Step 4: Run performance tests
    local performance_result=0
    if ! run_performance_test "$api_url"; then
        performance_result=1
    fi
    
    echo ""
    echo "==============================================================="
    
    # Final summary
    if [[ $endpoint_result -eq 0 && $performance_result -eq 0 ]]; then
        log_success "🎉 All validations passed! Infrastructure and endpoints are working correctly."
        exit 0
    else
        log_error "❌ Some validations failed. Please check the output above."
        exit 1
    fi
}

# Run main function
main "$@"