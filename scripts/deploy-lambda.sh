#!/bin/bash
set -e

# Deploy Lambda Functions Script
# This script deploys Lambda function code without using Terraform

# Configuration
FUNCTION_PREFIX="lambda-java-template-dev"
BUILD_DIR="build"
REGION="${AWS_REGION:-us-east-1}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

log_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

log_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

log_error() {
    echo -e "${RED}❌ $1${NC}"
}

# Function to deploy a Lambda function
deploy_lambda_function() {
    local function_name=$1
    local zip_file=$2
    local handler=$3
    local runtime=${4:-java21}
    
    log_info "Deploying $function_name..."
    
    if [ ! -f "$BUILD_DIR/$zip_file" ]; then
        log_error "ZIP file not found: $BUILD_DIR/$zip_file"
        return 1
    fi
    
    # Get file size for validation
    local file_size=$(stat -c%s "$BUILD_DIR/$zip_file" 2>/dev/null || stat -f%z "$BUILD_DIR/$zip_file" 2>/dev/null)
    local file_size_mb=$((file_size / 1024 / 1024))
    
    log_info "Package size: ${file_size_mb}MB"
    
    if [ $file_size_mb -gt 50 ]; then
        log_warning "Package size exceeds 50MB, using S3 upload method"
        
        # Upload to S3 first for large files
        local s3_bucket="lambda-java-template-dev-lambda-artifacts-67c28213"
        local s3_key="direct-deploy/${function_name}/${zip_file}"
        
        aws s3 cp "$BUILD_DIR/$zip_file" "s3://${s3_bucket}/${s3_key}" --region $REGION
        
        # Update function code from S3
        aws lambda update-function-code \
            --function-name "$function_name" \
            --s3-bucket "$s3_bucket" \
            --s3-key "$s3_key" \
            --region $REGION > /dev/null
    else
        # Direct upload for smaller files
        aws lambda update-function-code \
            --function-name "$function_name" \
            --zip-file "fileb://$BUILD_DIR/$zip_file" \
            --region $REGION > /dev/null
    fi
    
    # Update handler if provided
    if [ ! -z "$handler" ]; then
        log_info "Updating handler to: $handler"
        aws lambda update-function-configuration \
            --function-name "$function_name" \
            --handler "$handler" \
            --runtime "$runtime" \
            --region $REGION > /dev/null
    fi
    
    # Wait for function to be ready
    log_info "Waiting for function to be ready..."
    aws lambda wait function-updated \
        --function-name "$function_name" \
        --region $REGION
    
    log_success "Deployed $function_name successfully"
}

# Function to test Lambda function
test_lambda_function() {
    local function_name=$1
    local test_payload=${2:-'{}'}
    
    log_info "Testing $function_name..."
    
    local response=$(aws lambda invoke \
        --function-name "$function_name" \
        --payload "$test_payload" \
        --region $REGION \
        /tmp/lambda-response.json 2>&1)
    
    if [ $? -eq 0 ]; then
        local status_code=$(echo "$response" | jq -r '.StatusCode // 0')
        if [ "$status_code" = "200" ]; then
            log_success "Function $function_name executed successfully"
            log_info "Response: $(cat /tmp/lambda-response.json)"
        else
            log_warning "Function $function_name returned status code: $status_code"
            log_info "Response: $(cat /tmp/lambda-response.json)"
        fi
    else
        log_error "Failed to invoke $function_name: $response"
    fi
    
    rm -f /tmp/lambda-response.json
}

# Main deployment function
deploy_all_functions() {
    log_info "Starting Lambda function deployment..."
    
    # Check if AWS CLI is configured
    if ! aws sts get-caller-identity > /dev/null 2>&1; then
        log_error "AWS CLI not configured or no valid credentials"
        exit 1
    fi
    
    # Check if build directory exists
    if [ ! -d "$BUILD_DIR" ]; then
        log_error "Build directory not found. Run 'task build' first."
        exit 1
    fi
    
    # Deploy functions with Spring Cloud Function handler
    local spring_handler="org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest"
    
    # Deploy lambda1 (product-service)
    if [ -f "$BUILD_DIR/product-service.zip" ]; then
        deploy_lambda_function \
            "${FUNCTION_PREFIX}-lambda1" \
            "product-service.zip" \
            "$spring_handler" \
            "java21"
        
        # Test with health check
        test_lambda_function \
            "${FUNCTION_PREFIX}-lambda1" \
            '{"httpMethod": "GET", "path": "/health", "headers": {}}'
    else
        log_warning "product-service.zip not found, skipping lambda1"
    fi
    
    # Deploy lambda2 (authorizer-service) 
    if [ -f "$BUILD_DIR/authorizer-service.zip" ]; then
        deploy_lambda_function \
            "${FUNCTION_PREFIX}-lambda2" \
            "authorizer-service.zip" \
            "$spring_handler" \
            "java21"
    else
        log_warning "authorizer-service.zip not found, skipping lambda2"
    fi
    
    # Deploy lambda3 (event-processor-service)
    if [ -f "$BUILD_DIR/event-processor-service.zip" ]; then
        deploy_lambda_function \
            "${FUNCTION_PREFIX}-lambda3" \
            "event-processor-service.zip" \
            "$spring_handler" \
            "java21"
    else
        log_warning "event-processor-service.zip not found, skipping lambda3"
    fi
    
    log_success "All Lambda functions deployed successfully!"
}

# Deploy native functions (when available)
deploy_native_functions() {
    log_info "Starting native Lambda function deployment..."
    
    local native_handler="bootstrap"
    local native_runtime="provided.al2"
    
    # Deploy native versions if available
    for func in lambda1 lambda2 lambda3; do
        local service_name=""
        case $func in
            lambda1) service_name="product-service" ;;
            lambda2) service_name="authorizer-service" ;;
            lambda3) service_name="event-processor-service" ;;
        esac
        
        if [ -f "$BUILD_DIR/${service_name}-native.zip" ]; then
            deploy_lambda_function \
                "${FUNCTION_PREFIX}-${func}" \
                "${service_name}-native.zip" \
                "$native_handler" \
                "$native_runtime"
        else
            log_warning "${service_name}-native.zip not found, skipping native deployment for $func"
        fi
    done
    
    log_success "Native Lambda function deployment completed!"
}

# Script usage
show_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --jvm        Deploy JVM-based Lambda functions (default)"
    echo "  --native     Deploy GraalVM native Lambda functions"
    echo "  --test       Test deployed functions after deployment"
    echo "  --help       Show this help message"
    echo ""
    echo "Environment Variables:"
    echo "  AWS_REGION   AWS region for deployment (default: us-east-1)"
    echo ""
    echo "Examples:"
    echo "  $0                    # Deploy JVM functions"
    echo "  $0 --native           # Deploy native functions"
    echo "  $0 --jvm --test       # Deploy JVM functions and test them"
}

# Parse command line arguments
DEPLOY_TYPE="jvm"
RUN_TESTS=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --jvm)
            DEPLOY_TYPE="jvm"
            shift
            ;;
        --native)
            DEPLOY_TYPE="native"
            shift
            ;;
        --test)
            RUN_TESTS=true
            shift
            ;;
        --help)
            show_usage
            exit 0
            ;;
        *)
            log_error "Unknown option: $1"
            show_usage
            exit 1
            ;;
    esac
done

# Main execution
main() {
    log_info "Lambda Deployment Script"
    log_info "Deployment type: $DEPLOY_TYPE"
    log_info "AWS Region: $REGION"
    
    case $DEPLOY_TYPE in
        jvm)
            deploy_all_functions
            ;;
        native)
            deploy_native_functions
            ;;
        *)
            log_error "Invalid deployment type: $DEPLOY_TYPE"
            exit 1
            ;;
    esac
    
    if [ "$RUN_TESTS" = true ]; then
        log_info "Running post-deployment tests..."
        test_lambda_function "${FUNCTION_PREFIX}-lambda1" '{"httpMethod": "GET", "path": "/health"}'
    fi
    
    log_success "Deployment completed successfully!"
}

# Run main function
main "$@"