#!/bin/bash
set -e

# Set GraalVM environment
export JAVA_HOME=~/graalvm/graalvm-jdk-21
export PATH=$JAVA_HOME/bin:$PATH

# Build the JAR first
echo "Building JAR packages..."
cd ..
mvn clean package
cd scripts

# Function to build native image for a service
build_native_service() {
    local service_name=$1
    local service_dir=$2

    echo "Building native image for $service_name..."

    cd $service_dir/target

    # Extract the fat JAR
    jar -xf ${service_name}.jar

    # Build native image with simplified configuration
    native-image \
        --no-fallback \
        --enable-http \
        --enable-https \
        --enable-url-protocols=http,https \
        --enable-all-security-services \
        --report-unsupported-elements-at-runtime \
        --allow-incomplete-classpath \
        --initialize-at-run-time=org.slf4j,software.amazon.awssdk.crt,io.netty.handler.ssl,io.netty.channel.epoll,io.netty.channel.kqueue \
        -cp ${service_name}.jar \
        -H:+ReportExceptionStackTraces \
        -H:Name=${service_name}-native \
        com.amazonaws.services.lambda.runtime.api.client.AWSLambda

    # Create bootstrap script
    echo '#!/bin/sh' > bootstrap
    echo "./$(basename ${service_name})-native" >> bootstrap
    chmod +x bootstrap

    # Create deployment package
    zip -r ${service_name}-native.zip bootstrap ${service_name}-native

    echo "Native image built: ${service_name}-native.zip"
    cd - > /dev/null
}

# Build native images for each service
echo "Starting native image builds..."

# Product service
if [ -d "../src/product-service" ]; then
    build_native_service "product-service" "../src/product-service"
fi

# Authorizer service
if [ -d "../src/authorizer-service" ]; then
    build_native_service "authorizer-service" "../src/authorizer-service"
fi

# Event processor service
if [ -d "../src/event-processor-service" ]; then
    build_native_service "event-processor-service" "../src/event-processor-service"
fi

# Payment service
if [ -d "../src/payment-service" ]; then
    build_native_service "payment-service" "../src/payment-service"
fi

# Order validation service
if [ -d "../src/order-validation-service" ]; then
    build_native_service "order-validation-service" "../src/order-validation-service"
fi

# Inventory service
if [ -d "../src/inventory-service" ]; then
    build_native_service "inventory-service" "../src/inventory-service"
fi

# Notification service
if [ -d "../src/notification-service" ]; then
    build_native_service "notification-service" "../src/notification-service"
fi

echo "All native images built successfully!"