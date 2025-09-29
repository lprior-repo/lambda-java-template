#!/bin/bash

# Native Deployment Script for AWS Lambda Functions
# This script deploys native executables built by build-native.sh to AWS Lambda
# It works with the existing Terraform infrastructure and modules

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_DIR="${PROJECT_ROOT}/build"
TERRAFORM_DIR="${PROJECT_ROOT}/terraform"

# AWS Configuration
AWS_REGION="${AWS_REGION:-us-east-1}"
AWS_PROFILE="${AWS_PROFILE:-default}"

# Terraform module configuration mapping
# Maps service names to their Terraform module names and S3 keys
declare -A TF_MODULE_MAP=(
    ["product-service"]="lambda_functions[\"lambda1\"]"
    ["authorizer-service"]="lambda2"
    ["event-processor-service"]="lambda3"
    ["payment-service"]="payment_lambda"
    ["order-validation-service"]="order_validation_lambda"
    ["inventory-service"]="inventory_lambda"
    ["notification-service"]="notification_lambda"
)

declare -A S3_KEY_MAP=(
    ["product-service"]="lambda1/product-service.zip"
    ["authorizer-service"]="lambda2/authorizer-service-native.zip"
    ["event-processor-service"]="lambda3/event-processor-service-native.zip"
    ["payment-service"]="payment/payment-service-native.zip"
    ["order-validation-service"]="order-validation/order-validation-service-native.zip"
    ["inventory-service"]="inventory/inventory-service-native.zip"
    ["notification-service"]="notification/notification-service-native.zip"
)

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

# Check prerequisites
check_prerequisites() {
    log_info "Checking deployment prerequisites..."
    
    # Check AWS CLI
    if ! command -v aws &> /dev/null; then
        log_error "AWS CLI not found. Please install AWS CLI."
        exit 1
    fi
    
    # Check Terraform
    if ! command -v terraform &> /dev/null; then
        log_error "Terraform not found. Please install Terraform."
        exit 1
    fi
    
    # Check if in correct directory
    if [[ ! -d "$TERRAFORM_DIR" ]]; then
        log_error "Terraform directory not found: $TERRAFORM_DIR"
        exit 1
    fi
    
    # Check AWS credentials
    if ! aws sts get-caller-identity --profile "$AWS_PROFILE" > /dev/null 2>&1; then
        log_error "AWS credentials not configured for profile: $AWS_PROFILE"
        log_info "Configure with: aws configure --profile $AWS_PROFILE"
        exit 1
    fi
    
    log_success "Prerequisites check passed"
}

# Get S3 bucket name from Terraform output
get_s3_bucket() {
    cd "$TERRAFORM_DIR"
    local bucket=$(terraform output -raw lambda_artifacts_bucket_name 2>/dev/null || echo "")
    
    if [[ -z "$bucket" ]]; then
        # Try to extract from state if output doesn't exist
        bucket=$(terraform show -json | jq -r '.values.root_module.resources[] | select(.type == "aws_s3_bucket" and .name == "lambda_artifacts") | .values.bucket' 2>/dev/null || echo "")
    fi
    
    if [[ -z "$bucket" ]]; then
        log_error "Could not determine S3 bucket name from Terraform state"
        log_info "Make sure Terraform infrastructure is deployed"
        exit 1
    fi
    
    echo "$bucket"
}

# Get Lambda function name from Terraform
get_lambda_function_name() {
    local service_name="$1"
    local tf_module="${TF_MODULE_MAP[$service_name]}"
    
    cd "$TERRAFORM_DIR"
    
    # Try different ways to get the function name
    local function_name=""
    
    # Use variable to handle quotes in module address properly
    local module_address="module.${tf_module}"
    function_name=$(terraform show -json | jq -r --arg addr "$module_address" '.values.root_module.child_modules[] | select(.address == $addr) | .resources[] | select(.type == "aws_lambda_function") | .values.function_name' 2>/dev/null || echo "")
    
    if [[ -z "$function_name" ]]; then
        log_warning "Could not determine Lambda function name for $service_name"
        return 1
    fi
    
    echo "$function_name"
}

# Upload native package to S3
upload_to_s3() {
    local service_name="$1"
    local package_path="${BUILD_DIR}/${service_name}-native.zip"
    local s3_bucket="$2"
    local s3_key="${S3_KEY_MAP[$service_name]}"
    
    if [[ ! -f "$package_path" ]]; then
        log_error "Native package not found: $package_path"
        log_info "Run scripts/build-native.sh first"
        return 1
    fi
    
    log_info "Uploading $service_name to S3..."
    log_info "  Source: $package_path"
    log_info "  Destination: s3://$s3_bucket/$s3_key"
    
    if aws s3 cp "$package_path" "s3://$s3_bucket/$s3_key" --profile "$AWS_PROFILE" --region "$AWS_REGION"; then
        log_success "Uploaded $service_name to S3"
        return 0
    else
        log_error "Failed to upload $service_name to S3"
        return 1
    fi
}

# Update Lambda function code
update_lambda_function() {
    local service_name="$1"
    local s3_bucket="$2"
    local s3_key="${S3_KEY_MAP[$service_name]}"
    
    local function_name
    if ! function_name=$(get_lambda_function_name "$service_name"); then
        log_error "Cannot deploy $service_name: function name not found"
        return 1
    fi
    
    log_info "Updating Lambda function: $function_name"
    
    if aws lambda update-function-code \
        --function-name "$function_name" \
        --s3-bucket "$s3_bucket" \
        --s3-key "$s3_key" \
        --profile "$AWS_PROFILE" \
        --region "$AWS_REGION" \
        --output table > /dev/null; then
        log_success "Updated Lambda function: $function_name"
        return 0
    else
        log_error "Failed to update Lambda function: $function_name"
        return 1
    fi
}

# Deploy a specific service
deploy_service() {
    local service_name="$1"
    local s3_bucket="$2"
    
    log_info "Deploying $service_name..."
    
    # Upload to S3
    if ! upload_to_s3 "$service_name" "$s3_bucket"; then
        return 1
    fi
    
    # Update Lambda function
    if ! update_lambda_function "$service_name" "$s3_bucket"; then
        return 1
    fi
    
    log_success "Successfully deployed $service_name"
    return 0
}

# Deploy all services
deploy_all_services() {
    local s3_bucket="$1"
    
    log_info "Deploying all native services..."
    
    local failed_services=()
    local success_count=0
    local available_services=()
    
    # Check which services have native packages
    for service in "${!TF_MODULE_MAP[@]}"; do
        if [[ -f "${BUILD_DIR}/${service}-native.zip" ]]; then
            available_services+=("$service")
        fi
    done
    
    if [[ ${#available_services[@]} -eq 0 ]]; then
        log_error "No native packages found in $BUILD_DIR"
        log_info "Run scripts/build-native.sh first"
        return 1
    fi
    
    log_info "Found ${#available_services[@]} native packages to deploy"
    
    for service in "${available_services[@]}"; do
        if deploy_service "$service" "$s3_bucket"; then
            ((success_count++))
        else
            failed_services+=("$service")
        fi
        echo # Empty line for readability
    done
    
    # Summary
    log_info "Deployment Summary:"
    log_success "Successfully deployed: $success_count/${#available_services[@]} services"
    
    if [[ ${#failed_services[@]} -gt 0 ]]; then
        log_error "Failed services: ${failed_services[*]}"
        return 1
    fi
    
    return 0
}

# Wait for Lambda functions to be updated
wait_for_updates() {
    log_info "Waiting for Lambda functions to be updated..."
    sleep 5
    log_success "Lambda functions should now be ready"
}

# Test deployed functions
test_deployments() {
    log_info "Testing deployed functions..."
    
    # Get API Gateway URL from Terraform
    cd "$TERRAFORM_DIR"
    local api_url=$(terraform output -raw api_gateway_url 2>/dev/null || echo "")
    
    if [[ -n "$api_url" ]]; then
        log_info "Testing health endpoint: $api_url/health"
        
        local response=$(curl -s -w ",%{http_code}" "$api_url/health" || echo ",000")
        local body=$(echo "$response" | cut -d',' -f1)
        local status=$(echo "$response" | cut -d',' -f2)
        
        if [[ "$status" == "200" ]]; then
            log_success "Health endpoint test passed"
            log_info "Response: $body"
        else
            log_warning "Health endpoint returned status: $status"
            log_info "Response: $body"
        fi
    else
        log_warning "Could not determine API Gateway URL for testing"
    fi
}

# Show deployment information
show_deployment_info() {
    log_info "Deployment Information:"
    
    cd "$TERRAFORM_DIR"
    
    # API Gateway URL
    local api_url=$(terraform output -raw api_gateway_url 2>/dev/null || echo "Not available")
    echo "  API Gateway URL: $api_url"
    
    # Health endpoint
    if [[ "$api_url" != "Not available" ]]; then
        echo "  Health Endpoint: $api_url/health"
        echo "  Products Endpoint: $api_url/products"
    fi
    
    # Lambda functions
    echo "  Lambda Functions:"
    for service in "${!TF_MODULE_MAP[@]}"; do
        if [[ -f "${BUILD_DIR}/${service}-native.zip" ]]; then
            local function_name
            if function_name=$(get_lambda_function_name "$service" 2>/dev/null); then
                echo "    $service -> $function_name"
            fi
        fi
    done
}

# Main execution
main() {
    local deploy_service_name="${1:-}"
    
    log_info "Starting native deployment process..."
    log_info "Project root: $PROJECT_ROOT"
    log_info "AWS Profile: $AWS_PROFILE"
    log_info "AWS Region: $AWS_REGION"
    
    check_prerequisites
    
    # Get S3 bucket name
    local s3_bucket
    if ! s3_bucket=$(get_s3_bucket); then
        exit 1
    fi
    log_info "Using S3 bucket: $s3_bucket"
    
    # Deploy services
    if [[ -n "$deploy_service_name" ]]; then
        # Deploy specific service
        if [[ -v TF_MODULE_MAP["$deploy_service_name"] ]]; then
            if deploy_service "$deploy_service_name" "$s3_bucket"; then
                wait_for_updates
                test_deployments
                show_deployment_info
                log_success "Native deployment completed for $deploy_service_name!"
            else
                log_error "Native deployment failed for $deploy_service_name!"
                exit 1
            fi
        else
            log_error "Unknown service: $deploy_service_name"
            log_info "Available services: ${!TF_MODULE_MAP[*]}"
            exit 1
        fi
    else
        # Deploy all services
        if deploy_all_services "$s3_bucket"; then
            wait_for_updates
            test_deployments
            show_deployment_info
            log_success "Native deployment completed successfully!"
        else
            log_error "Native deployment failed!"
            exit 1
        fi
    fi
}

# Handle script arguments
case "${1:-}" in
    --help|-h)
        echo "Usage: $0 [service-name]"
        echo "  Deploy native executables to AWS Lambda"
        echo "  If service-name is provided, deploys only that service"
        echo "  Otherwise, deploys all available native packages"
        echo ""
        echo "Available services: ${!TF_MODULE_MAP[*]}"
        echo ""
        echo "Environment Variables:"
        echo "  AWS_PROFILE - AWS profile to use (default: default)"
        echo "  AWS_REGION  - AWS region to use (default: us-east-1)"
        exit 0
        ;;
    *)
        main "$@"
        ;;
esac