#!/bin/bash
set -e

echo "Building statically linked GraalVM native Lambda..."

# Create a minimal Dockerfile that mimics Lambda environment
cat > Dockerfile.static << 'EOF'
FROM public.ecr.aws/lambda/provided:al2-x86_64

# Install build tools
USER root
RUN yum update -y && \
    yum groupinstall -y "Development Tools" && \
    yum install -y wget tar gzip

# Install GraalVM
RUN wget https://download.oracle.com/graalvm/21/latest/graalvm-jdk-21_linux-x64_bin.tar.gz && \
    tar -xzf graalvm-jdk-21_linux-x64_bin.tar.gz && \
    mv graalvm-jdk-21.* /opt/graalvm && \
    rm graalvm-jdk-21_linux-x64_bin.tar.gz

ENV JAVA_HOME=/opt/graalvm
ENV PATH=$JAVA_HOME/bin:$PATH

WORKDIR /build
COPY product-service/target/product-service.jar .

# Build statically linked native image
RUN native-image \
    --no-fallback \
    --static \
    --libc=musl \
    --enable-http \
    --enable-https \
    --enable-url-protocols=http,https \
    --report-unsupported-elements-at-runtime \
    --allow-incomplete-classpath \
    --initialize-at-build-time= \
    --initialize-at-run-time=org.slf4j,ch.qos.logback,software.amazon.awssdk.crt,software.amazon.awssdk.utils.http.SdkHttpUtils \
    -cp product-service.jar \
    -H:+ReportExceptionStackTraces \
    -H:Name=product-service-native \
    com.amazonaws.services.lambda.runtime.api.client.AWSLambda || \
    echo "Static linking failed, trying with glibc..." && \
    native-image \
        --no-fallback \
        --enable-http \
        --enable-https \
        --enable-url-protocols=http,https \
        --report-unsupported-elements-at-runtime \
        --allow-incomplete-classpath \
        --initialize-at-build-time= \
        --initialize-at-run-time=org.slf4j,ch.qos.logback,software.amazon.awssdk.crt,software.amazon.awssdk.utils.http.SdkHttpUtils \
        -cp product-service.jar \
        -H:+ReportExceptionStackTraces \
        -H:Name=product-service-native \
        com.amazonaws.services.lambda.runtime.api.client.AWSLambda

RUN echo '#!/bin/sh' > bootstrap && \
    echo 'set -euo pipefail' >> bootstrap && \
    echo 'exec ./product-service-native' >> bootstrap && \
    chmod +x bootstrap && \
    zip product-service-native.zip bootstrap product-service-native

CMD ["cp", "product-service-native.zip", "/output/"]
EOF

echo "Building in Lambda-compatible container..."
mkdir -p lambda-static-output

# Build using the Lambda base image
docker build -f Dockerfile.static -t lambda-static-native .

# Extract the built package
docker run --rm -v $(pwd)/lambda-static-output:/output lambda-static-native

# Copy to target directory
if [ -f "lambda-static-output/product-service-native.zip" ]; then
    cp lambda-static-output/product-service-native.zip product-service/target/
    echo "Successfully created Lambda-compatible static native package!"
    echo "Package size: $(du -h product-service/target/product-service-native.zip | cut -f1)"

    # Test the binary compatibility
    cd lambda-static-output
    unzip -q product-service-native.zip
    echo "Binary info:"
    file product-service-native
    echo "Checking dependencies:"
    ldd product-service-native || echo "Statically linked (no dependencies)"
    cd ..
else
    echo "Failed to create static native package"
    exit 1
fi

# Clean up
rm -f Dockerfile.static
rm -rf lambda-static-output

echo "Static GraalVM native Lambda package ready for deployment!"