#!/bin/bash
set -e

# Set GraalVM environment
export JAVA_HOME=~/graalvm/graalvm-jdk-21
export PATH=$JAVA_HOME/bin:$PATH

# Build the JAR first
echo "Building JAR packages..."
mvn clean package

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
if [ -d "product-service" ]; then
    build_native_service "product-service" "product-service"
fi

# Authorizer service
if [ -d "authorizer-service" ]; then
    build_native_service "authorizer-service" "authorizer-service"
fi

# Event processor service
if [ -d "event-processor-service" ]; then
    build_native_service "event-processor-service" "event-processor-service"
fi

echo "All native images built successfully!"