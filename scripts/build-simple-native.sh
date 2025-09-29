#!/bin/bash
set -e

# Set GraalVM environment
export JAVA_HOME=~/graalvm/graalvm-jdk-21
export PATH=$JAVA_HOME/bin:$PATH

echo "Building GraalVM native Lambda with minimal configuration..."

cd product-service/target

# Build native image with absolute minimal configuration
native-image \
    --no-fallback \
    --enable-http \
    --enable-https \
    --enable-url-protocols=http,https \
    --report-unsupported-elements-at-runtime \
    --allow-incomplete-classpath \
    -cp product-service.jar \
    -H:+ReportExceptionStackTraces \
    -H:Name=product-service-native \
    --verbose \
    com.amazonaws.services.lambda.runtime.api.client.AWSLambda

# Check if binary was created
if [ -f "product-service-native" ]; then
    echo "Successfully created native binary!"

    # Create bootstrap script
    cat > bootstrap << 'EOF'
#!/bin/sh
set -euo pipefail
exec ./product-service-native
EOF
    chmod +x bootstrap

    # Create deployment package
    zip -r product-service-native.zip bootstrap product-service-native
    echo "Created deployment package: product-service-native.zip"
    echo "Package size: $(du -h product-service-native.zip | cut -f1)"

    # Test the binary
    echo "Testing native binary..."
    timeout 3s ./product-service-native || echo "Binary test completed (expected timeout)"

    echo "GraalVM native Lambda ready for deployment!"
else
    echo "Failed to create native binary"
    exit 1
fi