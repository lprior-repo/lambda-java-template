#!/bin/bash
set -euo pipefail

# Comprehensive Testing Demo Script
# Demonstrates all testing capabilities of the AWS Lambda Java Template

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Logging functions
log_section() {
    echo -e "\n${PURPLE}===================================================${NC}"
    echo -e "${PURPLE}$1${NC}"
    echo -e "${PURPLE}===================================================${NC}\n"
}

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

# Demo mode flag
DEMO_MODE="${DEMO_MODE:-false}"

# Function to run commands with demo pause
run_command() {
    local cmd="$1"
    local description="$2"
    
    log_info "Running: ${description}"
    echo -e "${CYAN}$ ${cmd}${NC}"
    
    if [[ "$DEMO_MODE" == "true" ]]; then
        echo -e "${YELLOW}Press Enter to continue...${NC}"
        read -r
    fi
    
    echo ""
    if eval "$cmd"; then
        log_success "✅ ${description} completed"
    else
        log_error "❌ ${description} failed"
        return 1
    fi
    echo ""
}

# Main demo function
main() {
    log_section "AWS Lambda Java Template - Testing Capabilities Demo"
    
    log_info "This script demonstrates comprehensive testing capabilities:"
    log_info "• Unit Tests (Java/Spring Boot)"
    log_info "• Integration Tests (AWS services)"  
    log_info "• Infrastructure Tests (Terratest)"
    log_info "• Endpoint Validation (API testing)"
    log_info "• terraform-aws-modules validation"
    echo ""
    
    if [[ "$DEMO_MODE" == "true" ]]; then
        log_warning "Running in DEMO mode - will pause between commands"
        echo -e "${YELLOW}Press Enter to start...${NC}"
        read -r
    fi

    # 1. Unit Tests
    log_section "1. Java Unit Tests"
    log_info "Testing individual components in isolation"
    
    run_command "task test:unit" "Unit Tests (ProductService, AuthorizerHandler)"
    
    # 2. Integration Tests  
    log_section "2. Integration Tests"
    log_info "Testing components with real AWS services"
    
    run_command "task test:integration" "Integration Tests (DynamoDB, Spring Boot)"
    
    # 3. Infrastructure Validation
    log_section "3. Infrastructure Testing with Terratest"
    log_info "Comprehensive infrastructure validation"
    
    run_command "task terratest:modules" "terraform-aws-modules Validation"
    run_command "task terratest:security" "Security Configuration Tests"
    run_command "task terratest:performance" "Performance Validation"
    
    # 4. Endpoint Testing
    log_section "4. API Endpoint Validation"
    log_info "Testing all API endpoints with authentication"
    
    run_command "task test:endpoints" "Comprehensive Endpoint Testing"
    
    # 5. Complete Validation Suite
    log_section "5. Complete Validation Demonstration"
    log_info "Running the complete validation suite (production-ready validation)"
    
    if [[ "$DEMO_MODE" == "true" ]]; then
        log_warning "This will run the complete test suite. Continue?"
        echo -e "${YELLOW}Press Enter to run complete validation...${NC}"
        read -r
    fi
    
    log_info "Running: task validate"
    echo -e "${CYAN}$ task validate${NC}"
    echo ""
    
    # Show what task validate does
    log_info "Complete validation includes:"
    log_info "  1. Unit tests (Java components)"
    log_info "  2. Integration tests (AWS services)"
    log_info "  3. Infrastructure tests (Terratest)"
    log_info "  4. Endpoint validation (API testing)"
    echo ""
    
    if [[ "$DEMO_MODE" != "true" ]]; then
        if task validate; then
            log_success "✅ Complete validation suite passed!"
        else
            log_error "❌ Complete validation suite failed"
            return 1
        fi
    else
        log_info "Skipping full validation in demo mode"
    fi
    
    # 6. Summary and Next Steps
    log_section "Testing Capabilities Summary"
    
    echo -e "${GREEN}✅ Available Testing Commands:${NC}"
    echo ""
    echo -e "${CYAN}Unit & Integration Tests:${NC}"
    echo "  task test:unit           # Java unit tests"
    echo "  task test:integration    # AWS integration tests"
    echo ""
    echo -e "${CYAN}Infrastructure Tests:${NC}"
    echo "  task terratest           # Complete Terratest suite"
    echo "  task terratest:modules   # terraform-aws-modules validation"
    echo "  task terratest:endpoints # API Gateway functionality"
    echo "  task terratest:security  # Security configuration"
    echo "  task terratest:performance # Performance benchmarks"
    echo ""
    echo -e "${CYAN}Endpoint Validation:${NC}"
    echo "  task test:endpoints      # Comprehensive API testing"
    echo ""
    echo -e "${CYAN}Complete Validation:${NC}"
    echo "  task validate           # Production-ready validation suite"
    echo ""
    
    echo -e "${GREEN}🎯 Key Features Validated:${NC}"
    echo "  • terraform-aws-modules consistency"
    echo "  • API Gateway routes and integrations"  
    echo "  • Lambda function configuration"
    echo "  • DynamoDB table setup and encryption"
    echo "  • Authentication and authorization"
    echo "  • Performance characteristics"
    echo "  • Security best practices"
    echo ""
    
    echo -e "${BLUE}📊 Test Coverage:${NC}"
    echo "  • 25+ infrastructure test cases"
    echo "  • 7 API endpoint scenarios" 
    echo "  • Security validation"
    echo "  • Performance benchmarking"
    echo "  • Module consistency checks"
    echo ""
    
    echo -e "${PURPLE}🚀 Ready for Production!${NC}"
    echo "All components validated and tested comprehensively."
    echo ""
    
    log_success "🎉 Testing demo completed successfully!"
}

# Help function
show_help() {
    echo "AWS Lambda Java Template - Testing Demo"
    echo ""
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --demo     Run in demo mode (pauses between commands)"
    echo "  --help     Show this help message"
    echo ""
    echo "Environment Variables:"
    echo "  DEMO_MODE=true    Enable demo mode"
    echo ""
    echo "Examples:"
    echo "  $0                 # Run full demo"
    echo "  $0 --demo         # Run with pauses"
    echo "  DEMO_MODE=true $0  # Run with pauses"
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --demo)
            DEMO_MODE="true"
            shift
            ;;
        --help)
            show_help
            exit 0
            ;;
        *)
            log_error "Unknown option: $1"
            show_help
            exit 1
            ;;
    esac
done

# Check prerequisites
if ! command -v task >/dev/null 2>&1; then
    log_error "Task (taskfile.dev) not found. Please install Task."
    log_info "Installation: https://taskfile.dev/installation/"
    exit 1
fi

if ! command -v mvn >/dev/null 2>&1; then
    log_error "Maven not found. Please install Maven."
    exit 1
fi

if ! command -v go >/dev/null 2>&1; then
    log_error "Go not found. Please install Go for Terratest."
    exit 1
fi

if ! command -v aws >/dev/null 2>&1; then
    log_error "AWS CLI not found. Please install and configure AWS CLI."
    exit 1
fi

# Check if infrastructure is deployed
if ! aws lambda get-function --function-name "lambda-java-template-dev-product-service" >/dev/null 2>&1; then
    log_warning "Infrastructure not deployed. Some tests may fail."
    log_info "Deploy first with: task tf:apply"
    echo ""
fi

# Run main function
main "$@"