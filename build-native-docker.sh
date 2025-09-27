#!/bin/bash
set -e

echo "Building GraalVM native Lambda using Docker for Lambda compatibility..."

# Create Dockerfile for building native image
cat > Dockerfile.native << 'EOF'
FROM amazonlinux:2

# Install required packages
RUN yum update -y && \
    yum install -y wget tar gzip which && \
    yum groupinstall -y "Development Tools"

# Install GraalVM
RUN wget https://download.oracle.com/graalvm/21/latest/graalvm-jdk-21_linux-x64_bin.tar.gz && \
    tar -xzf graalvm-jdk-21_linux-x64_bin.tar.gz && \
    mv graalvm-jdk-21.* /opt/graalvm && \
    rm graalvm-jdk-21_linux-x64_bin.tar.gz

# Set environment
ENV JAVA_HOME=/opt/graalvm
ENV PATH=$JAVA_HOME/bin:$PATH

WORKDIR /build

COPY product-service/target/product-service.jar .

# Build static native image compatible with Lambda
RUN native-image \
    --no-fallback \
    --static \
    --libc=glibc \
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

# Create bootstrap script
RUN echo '#!/bin/sh' > bootstrap && \
    echo 'set -euo pipefail' >> bootstrap && \
    echo 'exec ./product-service-native' >> bootstrap && \
    chmod +x bootstrap && \
    zip product-service-native.zip bootstrap product-service-native

# Set up output directory
CMD cp product-service-native.zip /output/
EOF

# Build the native image using Docker
echo "Building native image in Docker container..."
mkdir -p docker-output

docker build -f Dockerfile.native -t lambda-native-builder .

# Run container and copy output
docker run --rm -v $(pwd)/docker-output:/output lambda-native-builder

# Move the built package to target directory
if [ -f "docker-output/product-service-native.zip" ]; then
    cp docker-output/product-service-native.zip product-service/target/
    echo "Successfully created Lambda-compatible native package!"
    echo "Package size: $(du -h product-service/target/product-service-native.zip | cut -f1)"
else
    echo "Failed to create native package"
    exit 1
fi

# Clean up
rm -f Dockerfile.native
rm -rf docker-output

echo "GraalVM native Lambda package ready for deployment!"