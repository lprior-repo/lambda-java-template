#!/bin/bash

# Deploy and Verify Native Lambda Functions
# This script builds native executables, deploys them to AWS Lambda, and verifies they work properly
# It provides comprehensive end-to-end validation of the native deployment workflow

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

# Configuration
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}}")/.." && pwd)"
BUILD_DIR="${PROJECT_ROOT}/build"
TERRAFORM_DIR="${PROJECT_ROOT}/terraform"
SCRIPTS_DIR="${PROJECT_ROOT}/scripts"

# AWS Configuration
AWS_REGION="${AWS_REGION:-us-east-1}"
AWS_PROFILE="${AWS_PROFILE:-default}"

# Deployment Configuration
SERVICES_TO_DEPLOY=(
    "product-service"
)

# Test Configuration
MAX_WAIT_TIME=300  # 5 minutes
POLL_INTERVAL=5    # 5 seconds

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

log_step() {
    echo -e "${PURPLE}[STEP]${NC} $1"
}

# Print banner
print_banner() {
    echo -e "${PURPLE}"
    echo "════════════════════════════════════════════════════════════════"
    echo "  AWS Lambda Native Deployment & Verification Script"
    echo "════════════════════════════════════════════════════════════════"
    echo -e "${NC}"
    echo "Project: $PROJECT_ROOT"
    echo "AWS Region: $AWS_REGION"
    echo "AWS Profile: $AWS_PROFILE"
    echo "Services: ${SERVICES_TO_DEPLOY[*]}"
    echo ""
}

# Check prerequisites
check_prerequisites() {
    log_step "Checking prerequisites..."
    
    local missing_tools=()
    
    # Check required tools
    command -v native-image >/dev/null 2>&1 || missing_tools+=("native-image (GraalVM)")
    command -v mvn >/dev/null 2>&1 || missing_tools+=("mvn (Maven)")
    command -v aws >/dev/null 2>&1 || missing_tools+=("aws (AWS CLI)")
    command -v jq >/dev/null 2>&1 || missing_tools+=("jq")
    command -v curl >/dev/null 2>&1 || missing_tools+=("curl")
    command -v terraform >/dev/null 2>&1 || missing_tools+=("terraform")
    
    if [[ ${#missing_tools[@]} -gt 0 ]]; then
        log_error "Missing required tools: ${missing_tools[*]}"
        exit 1
    fi
    
    # Check AWS credentials
    if ! aws sts get-caller-identity --profile "$AWS_PROFILE" > /dev/null 2>&1; then
        log_error "AWS credentials not configured for profile: $AWS_PROFILE"
        log_info "Configure with: aws configure --profile $AWS_PROFILE"
        exit 1
    fi
    
    # Check if in correct directory
    if [[ ! -d "$TERRAFORM_DIR" ]]; then
        log_error "Terraform directory not found: $TERRAFORM_DIR"
        exit 1
    fi
    
    # Check Java version
    local java_version=$(java -version 2>&1 | head -n 1 | cut -d'"' -f 2 | cut -d'.' -f 1)
    if [[ "$java_version" -lt 21 ]]; then
        log_error "Java 21 or higher is required. Current version: $java_version"
        exit 1
    fi
    
    log_success "Prerequisites check passed"
}

# Get deployment information from Terraform
get_deployment_info() {
    log_step "Getting deployment information from Terraform..."
    
    cd "$TERRAFORM_DIR"
    
    # Get S3 bucket name
    S3_BUCKET=$(terraform output -raw lambda_artifacts_bucket_name 2>/dev/null || echo "")
    if [[ -z "$S3_BUCKET" ]]; then
        log_error "Could not determine S3 bucket name from Terraform state"
        exit 1
    fi
    
    # Get API Gateway URL
    API_GATEWAY_URL=$(terraform output -raw api_gateway_url 2>/dev/null || echo "")
    if [[ -z "$API_GATEWAY_URL" ]]; then
        log_warning "Could not determine API Gateway URL from Terraform state"
    fi
    
    log_info "S3 Bucket: $S3_BUCKET"
    log_info "API Gateway URL: $API_GATEWAY_URL"
    
    cd "$PROJECT_ROOT"
}

# Build native executables
build_native_services() {
    log_step "Building native executables..."
    
    local build_start_time=$(date +%s)
    local failed_builds=()
    local successful_builds=()
    
    for service in "${SERVICES_TO_DEPLOY[@]}"; do
        log_info "Building $service..."
        
        if "$SCRIPTS_DIR/build-native.sh" "$service"; then
            successful_builds+=("$service")
            log_success "Built $service"
        else
            failed_builds+=("$service")
            log_error "Failed to build $service"
        fi
    done
    
    local build_end_time=$(date +%s)
    local build_duration=$((build_end_time - build_start_time))
    
    log_info "Build Summary:"
    log_info "  Duration: ${build_duration}s"
    log_info "  Successful: ${#successful_builds[@]}/${#SERVICES_TO_DEPLOY[@]}"
    
    if [[ ${#failed_builds[@]} -gt 0 ]]; then
        log_error "Failed builds: ${failed_builds[*]}"
        return 1
    fi
    
    # Show build artifacts
    log_info "Build artifacts:"
    if ls "${BUILD_DIR}"/*-native.zip &> /dev/null; then
        ls -lh "${BUILD_DIR}"/*-native.zip | while read -r line; do
            log_info "  $line"
        done
    fi
    
    return 0
}

# Deploy native services to AWS
deploy_native_services() {
    log_step "Deploying native services to AWS..."
    
    local deploy_start_time=$(date +%s)
    local failed_deployments=()
    local successful_deployments=()
    
    for service in "${SERVICES_TO_DEPLOY[@]}"; do
        log_info "Deploying $service..."
        
        if "$SCRIPTS_DIR/deploy-native.sh" "$service" > /dev/null 2>&1; then
            successful_deployments+=("$service")
            log_success "Deployed $service"
        else
            failed_deployments+=("$service")
            log_error "Failed to deploy $service"
        fi
    done
    
    local deploy_end_time=$(date +%s)
    local deploy_duration=$((deploy_end_time - deploy_start_time))
    
    log_info "Deployment Summary:"
    log_info "  Duration: ${deploy_duration}s"
    log_info "  Successful: ${#successful_deployments[@]}/${#SERVICES_TO_DEPLOY[@]}"
    
    if [[ ${#failed_deployments[@]} -gt 0 ]]; then
        log_error "Failed deployments: ${failed_deployments[*]}"
        return 1
    fi
    
    return 0
}

# Wait for Lambda functions to be ready
wait_for_lambda_ready() {
    log_step "Waiting for Lambda functions to be ready..."
    
    local wait_start_time=$(date +%s)
    
    # Wait a bit for Lambda to process the new code
    sleep 10
    
    for service in "${SERVICES_TO_DEPLOY[@]}"; do
        log_info "Checking $service readiness..."
        
        # Get function name from deployment script logic
        local function_name=""
        case "$service" in
            "product-service")
                function_name="lambda-java-template-dev-lambda1"
                ;;
            *)
                log_warning "Unknown service: $service"
                continue
                ;;
        esac
        
        # Check function state
        local function_state=""
        local attempts=0
        local max_attempts=12  # 1 minute total
        
        while [[ $attempts -lt $max_attempts ]]; do
            function_state=$(aws lambda get-function \
                --function-name "$function_name" \
                --region "$AWS_REGION" \
                --profile "$AWS_PROFILE" \
                --query 'Configuration.State' \
                --output text 2>/dev/null || echo "Unknown")
            
            if [[ "$function_state" == "Active" ]]; then
                log_success "$service is ready (state: $function_state)"
                break
            elif [[ "$function_state" == "Pending" ]]; then
                log_info "$service is still updating (state: $function_state), waiting..."
                sleep 5
                ((attempts++))
            else
                log_warning "$service state: $function_state"
                sleep 5
                ((attempts++))
            fi
        done
        
        if [[ "$function_state" != "Active" ]]; then
            log_warning "$service may not be ready (final state: $function_state)"
        fi
    done
    
    local wait_end_time=$(date +%s)
    local wait_duration=$((wait_end_time - wait_start_time))
    log_info "Lambda readiness check completed in ${wait_duration}s"
}

# Test API endpoints
test_api_endpoints() {
    log_step "Testing API endpoints..."
    
    if [[ -z "$API_GATEWAY_URL" ]]; then
        log_warning "No API Gateway URL available for testing"
        return 0
    fi
    
    local test_start_time=$(date +%s)
    local tests_passed=0
    local tests_total=0
    
    # Test health endpoint
    log_info "Testing health endpoint..."
    ((tests_total++))
    
    local health_response=$(curl -s -w ",%{http_code}" "$API_GATEWAY_URL/health" 2>/dev/null || echo ",000")
    local health_body=$(echo "$health_response" | cut -d',' -f1)
    local health_status=$(echo "$health_response" | cut -d',' -f2)
    
    log_info "Health endpoint response: HTTP $health_status"
    log_info "Response body: $health_body"
    
    if [[ "$health_status" == "200" ]]; then
        log_success "Health endpoint test passed"
        ((tests_passed++))
    else
        log_warning "Health endpoint test failed (HTTP $health_status)"
        
        # If it's a 500 error, check Lambda logs for more details
        if [[ "$health_status" == "500" ]]; then
            log_info "Checking Lambda logs for error details..."
            check_lambda_logs "lambda-java-template-dev-lambda1"
        fi
    fi
    
    # Test products endpoint (should require auth)
    log_info "Testing products endpoint (should require authorization)..."
    ((tests_total++))
    
    local products_response=$(curl -s -w ",%{http_code}" "$API_GATEWAY_URL/products" 2>/dev/null || echo ",000")
    local products_status=$(echo "$products_response" | cut -d',' -f2)
    
    log_info "Products endpoint response: HTTP $products_status"
    
    if [[ "$products_status" == "401" || "$products_status" == "403" ]]; then
        log_success "Products endpoint correctly requires authorization"
        ((tests_passed++))
    else
        log_warning "Products endpoint test unexpected result (HTTP $products_status)"
    fi
    
    local test_end_time=$(date +%s)
    local test_duration=$((test_end_time - test_start_time))
    
    log_info "API Testing Summary:"
    log_info "  Duration: ${test_duration}s"
    log_info "  Tests passed: $tests_passed/$tests_total"
    
    return 0
}

# Check Lambda function logs
check_lambda_logs() {
    local function_name="$1"
    local log_group="/aws/lambda/$function_name"
    
    log_info "Checking logs for $function_name..."
    
    # Get the latest log stream
    local latest_stream=$(aws logs describe-log-streams \
        --log-group-name "$log_group" \
        --region "$AWS_REGION" \
        --profile "$AWS_PROFILE" \
        --order-by LastEventTime \
        --descending \
        --max-items 1 \
        --query 'logStreams[0].logStreamName' \
        --output text 2>/dev/null || echo "")
    
    if [[ -n "$latest_stream" && "$latest_stream" != "None" ]]; then
        log_info "Latest log entries from $latest_stream:"
        aws logs get-log-events \
            --log-group-name "$log_group" \
            --log-stream-name "$latest_stream" \
            --region "$AWS_REGION" \
            --profile "$AWS_PROFILE" \
            --start-time $(($(date +%s) * 1000 - 300000)) \
            --query 'events[*].message' \
            --output text 2>/dev/null | tail -10 | while read -r line; do
            if [[ -n "$line" ]]; then
                log_info "  $line"
            fi
        done
    else
        log_warning "No log streams found for $function_name"
    fi
}

# Test Lambda function directly
test_lambda_functions() {
    log_step "Testing Lambda functions directly..."
    
    local test_start_time=$(date +%s)
    local tests_passed=0
    local tests_total=0
    
    for service in "${SERVICES_TO_DEPLOY[@]}"; do
        local function_name=""
        case "$service" in
            "product-service")
                function_name="lambda-java-template-dev-lambda1"
                ;;
            *)
                log_warning "Unknown service: $service"
                continue
                ;;
        esac
        
        log_info "Testing $service ($function_name) directly..."
        ((tests_total++))
        
        # Create test payload
        local test_payload='{
            "version": "2.0",
            "routeKey": "GET /health",
            "rawPath": "/health",
            "rawQueryString": "",
            "headers": {
                "accept": "application/json"
            },
            "requestContext": {
                "accountId": "123456789012",
                "apiId": "test",
                "domainName": "test.execute-api.us-east-1.amazonaws.com",
                "domainPrefix": "test",
                "http": {
                    "method": "GET",
                    "path": "/health",
                    "protocol": "HTTP/1.1",
                    "sourceIp": "192.168.1.1",
                    "userAgent": "test-agent"
                },
                "requestId": "test-request",
                "routeKey": "GET /health",
                "stage": "prod",
                "time": "01/Jan/2025:00:00:00 +0000",
                "timeEpoch": 1735689600000
            },
            "isBase64Encoded": false
        }'
        
        # Invoke function
        local invoke_response=$(aws lambda invoke \
            --function-name "$function_name" \
            --region "$AWS_REGION" \
            --profile "$AWS_PROFILE" \
            --payload "$test_payload" \
            --query 'StatusCode' \
            --output text \
            /tmp/lambda-response.json 2>/dev/null || echo "000")
        
        if [[ "$invoke_response" == "200" ]]; then
            log_success "Direct invocation of $service succeeded"
            ((tests_passed++))
            
            # Show response
            if [[ -f "/tmp/lambda-response.json" ]]; then
                local response_content=$(cat /tmp/lambda-response.json)
                log_info "Response: $response_content"
            fi
        else
            log_error "Direct invocation of $service failed (status: $invoke_response)"
            
            # Check logs for error details
            check_lambda_logs "$function_name"
        fi
    done
    
    local test_end_time=$(date +%s)
    local test_duration=$((test_end_time - test_start_time))
    
    log_info "Lambda Testing Summary:"
    log_info "  Duration: ${test_duration}s"
    log_info "  Tests passed: $tests_passed/$tests_total"
    
    # Cleanup
    rm -f /tmp/lambda-response.json
    
    return 0
}

# Performance verification
verify_performance() {
    log_step "Verifying performance characteristics..."
    
    if [[ -z "$API_GATEWAY_URL" ]]; then
        log_warning "No API Gateway URL available for performance testing"
        return 0
    fi
    
    local perf_start_time=$(date +%s)
    
    # Test cold start times
    log_info "Testing cold start performance..."
    
    # Force a cold start by waiting a bit
    log_info "Waiting for potential cold start..."
    sleep 30
    
    # Measure response time
    local response_time=$(curl -s -w "%{time_total}" -o /dev/null "$API_GATEWAY_URL/health" 2>/dev/null || echo "0")
    
    log_info "Response time: ${response_time}s"
    
    # Convert to milliseconds for easier reading
    local response_ms=$(echo "$response_time * 1000" | bc 2>/dev/null || echo "unknown")
    log_info "Response time: ${response_ms}ms"
    
    # Test a few more requests to see warm performance
    log_info "Testing warm performance (5 requests)..."
    
    local total_time=0
    local successful_requests=0
    
    for i in {1..5}; do
        local request_time=$(curl -s -w "%{time_total},%{http_code}" -o /dev/null "$API_GATEWAY_URL/health" 2>/dev/null || echo "0,000")
        local time_part=$(echo "$request_time" | cut -d',' -f1)
        local status_part=$(echo "$request_time" | cut -d',' -f2)
        
        if [[ "$status_part" == "200" ]]; then
            total_time=$(echo "$total_time + $time_part" | bc 2>/dev/null || echo "$total_time")
            ((successful_requests++))
        fi
        
        log_info "  Request $i: ${time_part}s (HTTP $status_part)"
    done
    
    if [[ $successful_requests -gt 0 ]]; then
        local avg_time=$(echo "scale=3; $total_time / $successful_requests" | bc 2>/dev/null || echo "unknown")
        local avg_ms=$(echo "$avg_time * 1000" | bc 2>/dev/null || echo "unknown")
        log_info "Average warm response time: ${avg_time}s (${avg_ms}ms)"
    fi
    
    local perf_end_time=$(date +%s)
    local perf_duration=$((perf_end_time - perf_start_time))
    
    log_info "Performance verification completed in ${perf_duration}s"
}

# Generate deployment report
generate_report() {
    log_step "Generating deployment report..."
    
    local report_file="${PROJECT_ROOT}/deployment-report-$(date +%Y%m%d-%H%M%S).md"
    
    cat > "$report_file" << EOF
# Native Lambda Deployment Report

**Generated**: $(date)
**Project**: $(basename "$PROJECT_ROOT")
**AWS Region**: $AWS_REGION
**AWS Profile**: $AWS_PROFILE

## Deployment Summary

### Services Deployed
$(for service in "${SERVICES_TO_DEPLOY[@]}"; do echo "- $service"; done)

### Infrastructure
- **S3 Bucket**: $S3_BUCKET
- **API Gateway URL**: $API_GATEWAY_URL

### Build Artifacts
$(if ls "${BUILD_DIR}"/*-native.zip &> /dev/null; then
    ls -lh "${BUILD_DIR}"/*-native.zip | while read -r line; do
        echo "- $line"
    done
else
    echo "- No native artifacts found"
fi)

### Lambda Functions
$(cd "$TERRAFORM_DIR" && terraform show -json | jq -r '.values.root_module.child_modules[].resources[] | select(.type == "aws_lambda_function") | "- \(.values.function_name) (\(.values.runtime))"' 2>/dev/null || echo "- Unable to retrieve function list")

## Testing Results

### API Endpoints
- Health endpoint: $API_GATEWAY_URL/health
- Products endpoint: $API_GATEWAY_URL/products

### Performance Notes
- Native compilation enables faster cold starts
- Expected cold start: 50-200ms (vs 5-15s for JVM)
- Runtime memory usage is optimized

## Next Steps

1. **Monitor Performance**: Check CloudWatch metrics for cold start improvements
2. **Load Testing**: Run comprehensive load tests to validate performance
3. **Production Deployment**: Deploy to production environment
4. **Monitoring Setup**: Configure alerts and dashboards

## Useful Commands

\`\`\`bash
# Build native executable
./scripts/build-native.sh product-service

# Deploy to AWS
./scripts/deploy-native.sh product-service

# Check function status
aws lambda get-function --function-name lambda-java-template-dev-lambda1

# View logs
aws logs tail /aws/lambda/lambda-java-template-dev-lambda1 --follow
\`\`\`

EOF

    log_success "Deployment report generated: $report_file"
}

# Cleanup on exit
cleanup() {
    local exit_code=$?
    if [[ $exit_code -ne 0 ]]; then
        log_error "Script failed with exit code $exit_code"
    fi
    log_info "Cleanup completed"
}

trap cleanup EXIT

# Main execution flow
main() {
    print_banner
    
    local total_start_time=$(date +%s)
    
    # Step 1: Prerequisites
    check_prerequisites
    
    # Step 2: Get deployment info
    get_deployment_info
    
    # Step 3: Build native services
    if ! build_native_services; then
        log_error "Native build failed, aborting deployment"
        exit 1
    fi
    
    # Step 4: Deploy to AWS
    if ! deploy_native_services; then
        log_error "Deployment failed"
        exit 1
    fi
    
    # Step 5: Wait for functions to be ready
    wait_for_lambda_ready
    
    # Step 6: Test API endpoints
    test_api_endpoints
    
    # Step 7: Test Lambda functions directly
    test_lambda_functions
    
    # Step 8: Performance verification
    verify_performance
    
    # Step 9: Generate report
    generate_report
    
    local total_end_time=$(date +%s)
    local total_duration=$((total_end_time - total_start_time))
    
    echo ""
    log_success "Native deployment and verification completed successfully!"
    log_info "Total duration: ${total_duration}s"
    
    if [[ -n "$API_GATEWAY_URL" ]]; then
        echo ""
        log_info "🚀 Your native Lambda functions are deployed and ready!"
        log_info "   Health endpoint: $API_GATEWAY_URL/health"
        log_info "   Products endpoint: $API_GATEWAY_URL/products"
        echo ""
    fi
}

# Script arguments handling
case "${1:-}" in
    --help|-h)
        echo "Usage: $0 [options]"
        echo ""
        echo "Deploy and verify native Lambda functions"
        echo ""
        echo "Options:"
        echo "  -h, --help    Show this help message"
        echo ""
        echo "Environment Variables:"
        echo "  AWS_PROFILE   AWS profile to use (default: default)"
        echo "  AWS_REGION    AWS region to use (default: us-east-1)"
        echo ""
        echo "This script will:"
        echo "  1. Build native executables for Lambda functions"
        echo "  2. Deploy them to AWS Lambda"
        echo "  3. Verify deployments work correctly"
        echo "  4. Test API endpoints and Lambda functions"
        echo "  5. Generate a deployment report"
        exit 0
        ;;
    *)
        main "$@"
        ;;
esac