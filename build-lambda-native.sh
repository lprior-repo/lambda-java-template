#!/bin/bash
set -e

echo "Building GraalVM native Lambda using Amazon Linux 2 environment..."

# Create a simpler Dockerfile for Lambda compatibility
cat > Dockerfile.lambda << 'EOF'
FROM amazonlinux:2

# Install GraalVM
RUN yum update -y && \
    yum install -y wget tar gzip zip && \
    wget https://download.oracle.com/graalvm/21/latest/graalvm-jdk-21_linux-x64_bin.tar.gz && \
    tar -xzf graalvm-jdk-21_linux-x64_bin.tar.gz && \
    mv graalvm-jdk-21.* /opt/graalvm && \
    rm graalvm-jdk-21_linux-x64_bin.tar.gz

ENV JAVA_HOME=/opt/graalvm
ENV PATH=$JAVA_HOME/bin:$PATH

WORKDIR /build
COPY product-service/target/product-service.jar .

# Build native image with Amazon Linux 2 glibc compatibility
RUN native-image \
    --no-fallback \
    --enable-http \
    --enable-https \
    --enable-url-protocols=http,https \
    --report-unsupported-elements-at-runtime \
    --allow-incomplete-classpath \
    --initialize-at-run-time=org.slf4j,ch.qos.logback,software.amazon.awssdk.crt \
    -cp product-service.jar \
    -H:+ReportExceptionStackTraces \
    -H:Name=product-service-native \
    --verbose \
    com.amazonaws.services.lambda.runtime.api.client.AWSLambda

RUN echo '#!/bin/sh' > bootstrap && \
    echo 'set -euo pipefail' >> bootstrap && \
    echo 'exec ./product-service-native' >> bootstrap && \
    chmod +x bootstrap && \
    zip product-service-native.zip bootstrap product-service-native

CMD ["cp", "product-service-native.zip", "/output/"]
EOF

echo "Building native image in Amazon Linux 2 container..."
mkdir -p lambda-output

# Build the container
docker build -f Dockerfile.lambda -t lambda-al2-native .

# Run container and extract the built package
docker run --rm -v $(pwd)/lambda-output:/output lambda-al2-native

# Move the package to the target directory
if [ -f "lambda-output/product-service-native.zip" ]; then
    cp lambda-output/product-service-native.zip product-service/target/
    echo "Successfully created Lambda-compatible native package!"
    echo "Package size: $(du -h product-service/target/product-service-native.zip | cut -f1)"

    # Verify the binary
    cd lambda-output
    unzip -q product-service-native.zip
    echo "Binary info:"
    file product-service-native
    ldd product-service-native || echo "No dynamic dependencies found"
    echo "Bootstrap script:"
    cat bootstrap
    cd ..
else
    echo "Failed to create native package"
    exit 1
fi

# Clean up
rm -f Dockerfile.lambda
rm -rf lambda-output

echo "GraalVM native Lambda package ready for deployment!"