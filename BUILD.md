# GraalVM Lambda Build Guide

This document provides comprehensive instructions for building and deploying the GraalVM-based AWS Lambda function.

## Prerequisites

### Required Software

1. **Java 21+**: Download from [OpenJDK](https://adoptopenjdk.net/) or [Oracle JDK](https://www.oracle.com/java/technologies/javase-downloads.html)
2. **GraalVM 24.1.1+**: Download from [GraalVM Downloads](https://www.graalvm.org/downloads/)
3. **Maven 3.8+**: Download from [Apache Maven](https://maven.apache.org/download.cgi)
4. **Docker** (optional): For testing Lambda runtime locally

### Environment Setup

```bash
# Set JAVA_HOME to GraalVM installation
export JAVA_HOME=/path/to/graalvm
export PATH=$JAVA_HOME/bin:$PATH

# Verify installation
java -version
native-image --version
mvn -version
```

## Build Process

### Quick Start

```bash
# Standard build
./build.sh

# Clean build with verbose output
./build.sh --clean --verbose

# Build without running tests
./build.sh --skip-tests
```

### Manual Build Steps

If you prefer to run the build manually:

```bash
# 1. Clean previous builds
mvn clean

# 2. Compile and package Java application
mvn package

# 3. Build native image
cd product-service
mvn -Pnative native:compile-no-fork

# 4. Create deployment package
mkdir -p build
cp target/product-service build/
cp ../bootstrap build/
cd build
zip -j product-service-native.zip product-service bootstrap
```

## Build Configuration Files

### Native Image Configuration

- **`native-image.properties`**: Main configuration for GraalVM native image build
- **`reflect-config.json`**: Reflection configuration for runtime access
- **`jni-config.json`**: JNI interface configuration
- **`proxy-config.json`**: Dynamic proxy configuration
- **`resource-config.json`**: Resource inclusion configuration

### Build Scripts

- **`build.sh`**: Main build script with multiple options
- **`bootstrap`**: Lambda custom runtime bootstrap script

## Deployment

### AWS Lambda Deployment Package

The build process creates `product-service-native.zip` in the `build/` directory containing:

- `product-service`: Native executable
- `bootstrap`: Lambda runtime bootstrap script

### Lambda Configuration

When deploying to AWS Lambda, use these settings:

- **Runtime**: `provided.al2023` (Custom runtime)
- **Handler**: Not applicable (handled by bootstrap script)
- **Architecture**: `x86_64` or `arm64` (match your build environment)
- **Memory**: 256MB minimum (native images have smaller memory footprint)
- **Timeout**: 30 seconds (native images start faster)

### Environment Variables

Set these environment variables in your Lambda function:

- `PRODUCTS_TABLE_NAME`: DynamoDB table name for product storage
- `AWS_REGION`: AWS region (usually auto-set by Lambda)
- `LOG_LEVEL`: `INFO`, `DEBUG`, or `ERROR` (optional, defaults to INFO)

## Testing

### Local Testing

Test the native executable locally:

```bash
# Test basic functionality
echo '{"httpMethod":"GET","path":"/products/123","pathParameters":{"id":"123"}}' | ./build/product-service
```

### Lambda Testing

Use AWS SAM for local Lambda testing:

```bash
# Install AWS SAM CLI first
sam local start-api --template template.yaml
```

## Troubleshooting

### Common Build Issues

1. **Native Image Build Fails**
   - Verify GraalVM installation and PATH
   - Check reflection configuration completeness
   - Review native-image build logs for missing dependencies

2. **Runtime Errors**
   - Check reflection configuration for missing classes
   - Verify all required resources are included
   - Review Lambda CloudWatch logs

3. **Performance Issues**
   - Adjust memory allocation in Lambda configuration
   - Review native image optimization flags
   - Consider warm-up strategies for infrequently used functions

### Debug Mode

Enable debug output during build:

```bash
./build.sh --verbose
```

For native image debugging:

```bash
mvn -Pnative native:compile-no-fork -Dverbose=true -DquickBuild=false
```

## Performance Optimization

### Native Image Optimizations

The build includes several optimizations:

- **Dead code elimination**: Removes unused code
- **Ahead-of-time compilation**: Eliminates JIT compilation overhead
- **Static linking**: Reduces runtime dependencies
- **Memory optimization**: Lower memory footprint

### Lambda-Specific Optimizations

- **Cold start reduction**: Native images start in milliseconds
- **Memory efficiency**: Lower memory usage compared to JVM
- **Package size**: Smaller deployment packages

## File Structure

```
lambda-java-template/
├── build.sh                          # Main build script
├── bootstrap                         # Lambda runtime bootstrap
├── BUILD.md                          # This file
├── pom.xml                           # Root Maven configuration
├── product-service/
│   ├── pom.xml                       # Service Maven configuration
│   ├── src/main/java/                # Java source code
│   ├── src/main/resources/META-INF/native-image/
│   │   ├── native-image.properties   # Native image build config
│   │   ├── reflect-config.json       # Reflection configuration
│   │   ├── jni-config.json          # JNI configuration
│   │   ├── proxy-config.json        # Proxy configuration
│   │   └── resource-config.json     # Resource configuration
│   └── target/                       # Build output (git-ignored)
└── build/                            # Final build artifacts (git-ignored)
```

## Advanced Configuration

### Custom Build Profiles

Create custom Maven profiles for different environments:

```xml
<profile>
    <id>production</id>
    <properties>
        <native.optimization>true</native.optimization>
    </properties>
</profile>
```

### CI/CD Integration

For automated builds in CI/CD pipelines:

```bash
# Install GraalVM in CI environment
./build.sh --clean --skip-tests --verbose
```

## Support

For issues and questions:

1. Check CloudWatch logs for runtime errors
2. Review GraalVM documentation for native image issues
3. Verify AWS Lambda custom runtime documentation
4. Test locally before deploying to AWS

## References

- [GraalVM Native Image Documentation](https://www.graalvm.org/reference-manual/native-image/)
- [AWS Lambda Custom Runtime](https://docs.aws.amazon.com/lambda/latest/dg/runtimes-custom.html)
- [AWS Lambda Java Guide](https://docs.aws.amazon.com/lambda/latest/dg/lambda-java.html)
- [Maven Native Plugin](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html)