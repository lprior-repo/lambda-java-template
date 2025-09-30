#!/bin/bash

# Single Deploy Script for Java Lambda Template
# Simplified deployment without Step Functions and EventBridge
# Usage: ./deploy.sh [environment] [action]
# Environment: dev, staging, prod
# Action: plan, apply, destroy

set -e

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default values
ENVIRONMENT=${1:-dev}
ACTION=${2:-plan}

# Print colored output
print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_step() {
    echo -e "${BLUE}🚀 $1${NC}"
}

# Validate environment
case $ENVIRONMENT in
  dev|staging|prod)
    print_success "Deploying to $ENVIRONMENT environment"
    ;;
  *)
    print_error "Invalid environment. Use: dev, staging, or prod"
    exit 1
    ;;
esac

# Validate action
case $ACTION in
  plan|apply|destroy)
    print_success "Running terraform $ACTION"
    ;;
  *)
    print_error "Invalid action. Use: plan, apply, or destroy"
    exit 1
    ;;
esac

# Check prerequisites
print_step "Checking prerequisites..."

if ! command -v java >/dev/null 2>&1; then
    print_error "Java is not installed"
    exit 1
fi

if ! command -v mvn >/dev/null 2>&1; then
    print_error "Maven is not installed"
    exit 1
fi

if ! command -v terraform >/dev/null 2>&1; then
    print_error "Terraform is not installed"
    exit 1
fi

if ! command -v aws >/dev/null 2>&1; then
    print_error "AWS CLI is not installed"
    exit 1
fi

print_success "All prerequisites met"

# Build and package for non-destroy actions
if [ "$ACTION" != "destroy" ]; then
    print_step "Building Java Lambda functions..."
    
    # Clean previous builds
    mvn clean -q
    
    # Download dependencies
    print_info "Downloading Maven dependencies..."
    mvn dependency:resolve -q
    
    # Compile and package
    print_info "Compiling and packaging Lambda functions..."
    mvn package -DskipTests -q
    
    # Create build directory and copy artifacts
    print_info "Preparing deployment artifacts..."
    mkdir -p build
    
    # Auto-discover and copy service JARs for active services only
    for service_name in "product-service" "authorizer-service"; do
        service_dir="src/$service_name"
        if [ -d "$service_dir" ]; then
            # Look for JAR files with version numbers
            jar_file=$(find "$service_dir/target/" -name "$service_name-*.jar" -not -name "*-aws.jar" 2>/dev/null | head -1)
            if [ -f "$jar_file" ]; then
                # Copy with simplified name
                cp "$jar_file" "build/$service_name.jar"
                print_success "Packaged: $service_name.jar"
            else
                print_warning "JAR file for $service_name not found, skipping"
            fi
        fi
    done
    
    # Check for main project JAR
    if [ -f "target/lambda-java-template.jar" ]; then
        cp target/lambda-java-template.jar build/
        print_success "Packaged: lambda-java-template.jar"
    fi
    
    # Verify we have artifacts
    if [ ! "$(ls -A build/ 2>/dev/null)" ]; then
        print_error "No JAR files found in build directory"
        exit 1
    fi
    
    print_success "Build completed successfully"
    ls -la build/*.jar 2>/dev/null || true
fi

# Terraform operations
print_step "Running Terraform operations..."

cd terraform

# Set variables file
VARS_FILE="environments/${ENVIRONMENT}.tfvars"

if [ ! -f "$VARS_FILE" ]; then
    print_error "Variables file $VARS_FILE not found"
    exit 1
fi

# Initialize Terraform if needed
if [ ! -d ".terraform" ]; then
    print_info "Initializing Terraform..."
    terraform init
fi

# Validate Terraform configuration
print_info "Validating Terraform configuration..."
terraform validate
terraform fmt -check=true

# Run Terraform command
print_step "Running terraform $ACTION for $ENVIRONMENT environment..."
print_info "Using variables from: $VARS_FILE"

case $ACTION in
    plan)
        terraform plan -var-file="$VARS_FILE" -out="${ENVIRONMENT}.tfplan"
        print_success "Plan complete. Review the changes above."
        print_info "To apply: ./deploy.sh $ENVIRONMENT apply"
        ;;
    apply)
        if [ -f "${ENVIRONMENT}.tfplan" ]; then
            terraform apply "${ENVIRONMENT}.tfplan"
            rm -f "${ENVIRONMENT}.tfplan"
        else
            terraform apply -var-file="$VARS_FILE" -auto-approve
        fi
        print_success "Deployment to $ENVIRONMENT complete!"
        
        # Show important outputs
        print_step "Deployment Summary:"
        API_URL=$(terraform output -raw api_gateway_url 2>/dev/null || echo "Not available")
        HEALTH_URL=$(terraform output -raw health_endpoint 2>/dev/null || echo "Not available")
        
        print_info "API Gateway URL: $API_URL"
        print_info "Health Endpoint: $HEALTH_URL"
        print_info "Products Table: $(terraform output -raw products_table_name 2>/dev/null || echo 'Not available')"
        print_info "Audit Logs Table: $(terraform output -raw audit_logs_table_name 2>/dev/null || echo 'Not available')"
        
        # Test health endpoint if available
        if [ "$HEALTH_URL" != "Not available" ]; then
            print_step "Testing health endpoint..."
            if curl -f -s "$HEALTH_URL" >/dev/null; then
                print_success "Health endpoint is responding"
            else
                print_warning "Health endpoint test failed"
            fi
        fi
        ;;
    destroy)
        print_warning "This will destroy all resources in $ENVIRONMENT!"
        print_warning "Type 'yes' to confirm destruction:"
        read -r confirmation
        if [ "$confirmation" = "yes" ]; then
            terraform destroy -var-file="$VARS_FILE" -auto-approve
            print_success "Resources in $ENVIRONMENT destroyed"
        else
            print_error "Destruction cancelled"
            exit 1
        fi
        ;;
esac

cd ..

print_success "Deployment script completed successfully!"