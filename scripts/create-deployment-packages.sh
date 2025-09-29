#!/bin/bash
set -e

echo "Creating deployment packages for Lambda functions..."

# Function to create a deployment package for a service
create_deployment_package() {
    local service_name=$1
    local service_dir=$2

    echo "Creating deployment package for $service_name..."

    cd $service_dir/target

    # Use the shaded JAR (already named correctly by Maven shade plugin)
    # The JAR should already be named ${service_name}.jar

    # Create bootstrap script for custom runtime (future native conversion)
    cat > bootstrap << 'EOF'
#!/bin/sh
set -euo pipefail
export _LAMBDA_SERVER_PORT=${_LAMBDA_SERVER_PORT:-8080}
export _LAMBDA_RUNTIME_DIR=${_LAMBDA_RUNTIME_DIR:-/var/runtime}
export AWS_EXECUTION_ENV=${AWS_EXECUTION_ENV:-"AWS_Lambda_java21"}

# Check if running native binary exists (for future use)
if [ -f "./${service_name}-native" ]; then
    echo "Running native binary..."
    exec ./${service_name}-native
else
    # Fall back to Java runtime
    echo "Running Java JAR..."
    exec java -XX:TieredStopAtLevel=1 -noverify -cp ${service_name}.jar com.amazonaws.services.lambda.runtime.api.client.AWSLambda
fi
EOF

    chmod +x bootstrap

    # Create deployment package with both bootstrap and JAR
    zip -r ${service_name}-deployment.zip bootstrap ${service_name}.jar

    echo "Created deployment package: ${service_name}-deployment.zip"
    echo "Package size: $(du -h ${service_name}-deployment.zip | cut -f1)"

    cd - > /dev/null
}

# Create deployment packages for each service
echo "Starting deployment package creation..."

# Product service
if [ -d "product-service" ]; then
    create_deployment_package "product-service" "product-service"
fi

# Authorizer service
if [ -d "authorizer-service" ]; then
    create_deployment_package "authorizer-service" "authorizer-service"
fi

# Event processor service
if [ -d "event-processor-service" ]; then
    create_deployment_package "event-processor-service" "event-processor-service"
fi

echo "All deployment packages created successfully!"
echo "Packages are ready for Lambda deployment with provided.al2 runtime"
echo "The bootstrap script supports both JAR and future native binary execution"