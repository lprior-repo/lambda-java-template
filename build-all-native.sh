#!/bin/bash
set -e

# Set GraalVM environment
export JAVA_HOME=~/graalvm/graalvm-jdk-21
export PATH=$JAVA_HOME/bin:$PATH

echo "Building all GraalVM native Lambda packages..."

# Services to build
services=("product-service" "authorizer-service" "event-processor-service")

for service in "${services[@]}"; do
    echo "Building $service..."

    if [ ! -d "$service" ]; then
        echo "Warning: $service directory not found, skipping..."
        continue
    fi

    cd "$service/target"

    # Find the JAR file
    jar_file=$(find . -maxdepth 1 -name "${service}*.jar" ! -name "*-clean.jar" ! -name "*-1.0.0.jar" | head -1)

    if [ -z "$jar_file" ]; then
        echo "Error: JAR file not found for $service"
        cd ../..
        continue
    fi

    echo "Using JAR: $jar_file"

    # Build native image with proper error handling
    echo "Building native image for $service..."
    if native-image \
        --no-fallback \
        --enable-http \
        --enable-https \
        --enable-url-protocols=http,https \
        --report-unsupported-elements-at-runtime \
        --allow-incomplete-classpath \
        --initialize-at-build-time= \
        --initialize-at-run-time=org.slf4j,ch.qos.logback,software.amazon.awssdk.crt \
        -cp "$jar_file" \
        -H:+ReportExceptionStackTraces \
        -H:Name="${service}-native" \
        com.amazonaws.services.lambda.runtime.api.client.AWSLambda; then

        echo "Native image built successfully for $service"

        # Create bootstrap script
        cat > bootstrap << 'EOF'
#!/bin/sh
set -euo pipefail
exec ./${SERVICE_NAME}-native
EOF

        # Replace placeholder with actual service name
        sed -i "s/\${SERVICE_NAME}/$service/g" bootstrap
        chmod +x bootstrap

        # Create deployment package
        zip "${service}-native.zip" bootstrap "${service}-native"
        echo "Created deployment package: ${service}-native.zip"
        echo "Package size: $(du -h ${service}-native.zip | cut -f1)"

    else
        echo "Failed to build native image for $service"
        # Clean up any partial files
        rm -f "${service}-native" bootstrap "${service}-native.zip"
    fi

    cd ../..
done

echo "Native build process completed!"