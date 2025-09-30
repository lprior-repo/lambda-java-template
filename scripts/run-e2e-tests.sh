#!/bin/bash

# End-to-End Test Runner for Lambda Java Template
# This script runs end-to-end tests against deployed AWS infrastructure

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[E2E]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if environment is set up correctly
check_environment() {
    print_status "Checking environment setup..."
    
    # Check if AWS CLI is available
    if ! command -v aws &> /dev/null; then
        print_error "AWS CLI is not installed or not in PATH"
        exit 1
    fi
    
    # Check if we can access AWS
    if ! aws sts get-caller-identity &> /dev/null; then
        print_error "AWS credentials not configured or invalid"
        exit 1
    fi
    
    # Check if Maven is available
    if ! command -v mvn &> /dev/null; then
        print_error "Maven is not installed or not in PATH"
        exit 1
    fi
    
    print_success "Environment checks passed"
}

# Get API Gateway URL from Terraform output or parameter
get_api_gateway_url() {
    print_status "Getting API Gateway URL..."
    
    # Try to get from Terraform output first
    if [ -d "terraform" ]; then
        cd terraform
        API_GATEWAY_URL=$(terraform output -raw api_gateway_url 2>/dev/null || echo "")
        cd ..
        
        if [ -n "$API_GATEWAY_URL" ]; then
            print_success "Found API Gateway URL from Terraform: $API_GATEWAY_URL"
            return 0
        fi
    fi
    
    # Check if provided as environment variable
    if [ -n "$API_GATEWAY_URL" ]; then
        print_success "Using API Gateway URL from environment: $API_GATEWAY_URL"
        return 0
    fi
    
    # Check if provided as command line argument
    if [ -n "$1" ]; then
        API_GATEWAY_URL="$1"
        print_success "Using API Gateway URL from argument: $API_GATEWAY_URL"
        return 0
    fi
    
    print_error "API Gateway URL not found. Please:"
    print_error "1. Run from directory with terraform/ subdirectory, OR"
    print_error "2. Set API_GATEWAY_URL environment variable, OR"
    print_error "3. Pass URL as first argument: $0 <api-gateway-url>"
    exit 1
}

# Run the end-to-end tests
run_e2e_tests() {
    print_status "Running end-to-end tests against: $API_GATEWAY_URL"
    
    # Export the URL for the tests
    export API_GATEWAY_URL
    
    # Run only the end-to-end test class
    print_status "Executing Maven test with end-to-end test class..."
    
    cd src/product-service
    
    # Run the specific test class
    if mvn test -Dtest=ProductApiEndToEndTest -q; then
        print_success "End-to-end tests passed!"
    else
        print_error "End-to-end tests failed!"
        exit 1
    fi
    
    cd ..
}

# Run health check before tests
run_health_check() {
    print_status "Running health check against deployed API..."
    
    if command -v curl &> /dev/null; then
        HEALTH_URL="${API_GATEWAY_URL}/health"
        print_status "Checking health endpoint: $HEALTH_URL"
        
        if curl -s -f "$HEALTH_URL" > /dev/null; then
            print_success "Health check passed - API is accessible"
        else
            print_warning "Health check failed - API may not be ready or accessible"
            print_warning "Continuing with tests anyway..."
        fi
    else
        print_warning "curl not available - skipping health check"
    fi
}

# Main execution
main() {
    echo
    print_status "Lambda Java Template - End-to-End Test Runner"
    print_status "============================================="
    echo
    
    # Check environment
    check_environment
    
    # Get API Gateway URL
    get_api_gateway_url "$1"
    
    # Run health check
    run_health_check
    
    # Run the tests
    run_e2e_tests
    
    echo
    print_success "End-to-end testing completed successfully!"
    print_status "API Gateway URL: $API_GATEWAY_URL"
    echo
}

# Help function
show_help() {
    echo "Usage: $0 [API_GATEWAY_URL]"
    echo
    echo "Run end-to-end tests against deployed Lambda Java Template infrastructure."
    echo
    echo "Options:"
    echo "  API_GATEWAY_URL    Optional API Gateway URL (can also be set via env var)"
    echo "  -h, --help         Show this help message"
    echo
    echo "Examples:"
    echo "  $0                                    # Auto-detect from terraform output"
    echo "  $0 https://api.example.com/prod       # Use specific URL"
    echo "  API_GATEWAY_URL=https://api.example.com/prod $0  # Use env var"
    echo
    echo "Requirements:"
    echo "  - AWS CLI configured with valid credentials"
    echo "  - Maven installed and in PATH"
    echo "  - API Gateway deployed and accessible"
    echo
}

# Check for help flag
case "${1:-}" in
    -h|--help)
        show_help
        exit 0
        ;;
esac

# Run main function
main "$@"