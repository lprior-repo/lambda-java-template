#!/bin/bash
set -e

# Detect and set GraalVM environment
if [ -z "$JAVA_HOME" ]; then
    # Try common GraalVM locations
    if [ -d "~/graalvm/graalvm-jdk-21" ]; then
        export JAVA_HOME=~/graalvm/graalvm-jdk-21
    elif [ -d "/opt/hostedtoolcache/graalvm-jdk" ]; then
        # GitHub Actions GraalVM setup
        export JAVA_HOME=$(find /opt/hostedtoolcache/graalvm-jdk -name "graalvm-*" -type d | head -1)
    elif command -v native-image >/dev/null 2>&1; then
        # GraalVM already in PATH
        echo "Using GraalVM from PATH: $(which native-image)"
    else
        echo "ERROR: GraalVM not found. Please install GraalVM or set JAVA_HOME"
        exit 1
    fi
fi

export PATH=$JAVA_HOME/bin:$PATH

echo "Building GraalVM native Lambda functions..."
echo "Using GraalVM: $JAVA_HOME"
echo "Native image version: $(native-image --version || echo 'native-image not found')"

# Function to build native image for a service
build_native_lambda() {
    local service_name=$1
    local service_dir=$2

    echo "Building native image for $service_name..."

    cd $service_dir/target

    # Build native image with minimal configuration to avoid SLF4J issues
    native-image \
        --no-fallback \
        --enable-http \
        --enable-https \
        --enable-url-protocols=http,https \
        --report-unsupported-elements-at-runtime \
        --allow-incomplete-classpath \
        --initialize-at-run-time=org.slf4j,ch.qos.logback,software.amazon.awssdk.crt \
        --initialize-at-run-time=io.netty.handler.ssl,io.netty.channel.epoll,io.netty.channel.kqueue \
        --initialize-at-build-time=software.amazon.awssdk.regions.Region \
        -cp ${service_name}.jar \
        -H:+ReportExceptionStackTraces \
        -H:+PrintClassInitialization \
        -H:Name=${service_name}-native \
        -H:+UnlockExperimentalVMOptions \
        -H:+AllowVMInspection \
        --verbose \
        com.amazonaws.services.lambda.runtime.api.client.AWSLambda

    # Verify the native binary was created
    if [ -f "${service_name}-native" ]; then
        echo "Successfully created native binary: ${service_name}-native"

        # Create bootstrap script
        cat > bootstrap << EOF
#!/bin/sh
set -euo pipefail
exec ./${service_name}-native
EOF
        chmod +x bootstrap

        # Create deployment package
        zip -r ${service_name}-native.zip bootstrap ${service_name}-native
        echo "Created native deployment package: ${service_name}-native.zip"
        echo "Package size: $(du -h ${service_name}-native.zip | cut -f1)"

        # Test the binary quickly
        echo "Testing native binary..."
        timeout 5s ./${service_name}-native || echo "Binary test completed (expected timeout)"
    else
        echo "Failed to create native binary for ${service_name}"
        exit 1
    fi

    cd - > /dev/null
}

# Build native images for each service
echo "Starting GraalVM native compilation..."

# Product service
if [ -d "product-service" ]; then
    build_native_lambda "product-service" "product-service"
fi

# Authorizer service
if [ -d "authorizer-service" ]; then
    build_native_lambda "authorizer-service" "authorizer-service"
fi

# Event processor service
if [ -d "event-processor-service" ]; then
    build_native_lambda "event-processor-service" "event-processor-service"
fi

echo "All GraalVM native Lambda functions built successfully!"
echo "Ready for deployment with provided.al2 runtime"