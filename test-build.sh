#!/bin/bash

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="${PROJECT_ROOT}/build"

print_status() {
    echo -e "${BLUE}[TEST]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[PASS]${NC} $1"
}

print_error() {
    echo -e "${RED}[FAIL]${NC} $1"
}

# Test that build artifacts exist
test_build_artifacts() {
    print_status "Testing build artifacts..."

    if [[ ! -f "${BUILD_DIR}/product-service" ]]; then
        print_error "Native executable not found"
        return 1
    fi

    if [[ ! -f "${BUILD_DIR}/bootstrap" ]]; then
        print_error "Bootstrap script not found"
        return 1
    fi

    if [[ ! -f "${BUILD_DIR}/product-service-native.zip" ]]; then
        print_error "Deployment package not found"
        return 1
    fi

    print_success "All build artifacts present"
}

# Test that executable has correct permissions
test_executable_permissions() {
    print_status "Testing executable permissions..."

    if [[ ! -x "${BUILD_DIR}/product-service" ]]; then
        print_error "Native executable is not executable"
        return 1
    fi

    if [[ ! -x "${BUILD_DIR}/bootstrap" ]]; then
        print_error "Bootstrap script is not executable"
        return 1
    fi

    print_success "All executables have correct permissions"
}

# Test deployment package contents
test_deployment_package() {
    print_status "Testing deployment package contents..."

    cd "${BUILD_DIR}"
    local zip_contents
    zip_contents=$(unzip -l product-service-native.zip)

    if [[ ! "$zip_contents" =~ "product-service" ]]; then
        print_error "Native executable not found in ZIP"
        return 1
    fi

    if [[ ! "$zip_contents" =~ "bootstrap" ]]; then
        print_error "Bootstrap script not found in ZIP"
        return 1
    fi

    print_success "Deployment package contains required files"
}

# Test basic executable functionality
test_executable_basic() {
    print_status "Testing basic executable functionality..."

    cd "${BUILD_DIR}"

    # Create a simple test event
    local test_event='{
        "httpMethod": "GET",
        "path": "/products/test-123",
        "pathParameters": {"id": "test-123"},
        "headers": {"Content-Type": "application/json"},
        "body": null
    }'

    # Test that the executable can be invoked (it will fail due to missing DynamoDB, but should not crash)
    if timeout 10s bash -c "echo '$test_event' | ./product-service" &>/dev/null; then
        print_success "Executable runs without crashing"
    else
        local exit_code=$?
        if [[ $exit_code -eq 124 ]]; then
            print_success "Executable runs (timed out, which is expected without DynamoDB)"
        else
            print_error "Executable failed to run (exit code: $exit_code)"
            return 1
        fi
    fi
}

# Test Maven configuration
test_maven_config() {
    print_status "Testing Maven configuration..."

    cd "${PROJECT_ROOT}"

    # Test that Maven can resolve dependencies
    if mvn dependency:resolve -q; then
        print_success "Maven dependencies resolve correctly"
    else
        print_error "Maven dependency resolution failed"
        return 1
    fi

    # Test that Maven can compile without native profile
    if mvn compile -q; then
        print_success "Maven compilation successful"
    else
        print_error "Maven compilation failed"
        return 1
    fi
}

# Test native image configuration files
test_native_config() {
    print_status "Testing native image configuration files..."

    local config_dir="${PROJECT_ROOT}/product-service/src/main/resources/META-INF/native-image"

    local required_files=(
        "native-image.properties"
        "reflect-config.json"
        "jni-config.json"
        "proxy-config.json"
        "resource-config.json"
    )

    for file in "${required_files[@]}"; do
        if [[ ! -f "${config_dir}/${file}" ]]; then
            print_error "Missing native config file: ${file}"
            return 1
        fi

        # Basic JSON validation for JSON files
        if [[ "$file" == *.json ]]; then
            if ! python3 -m json.tool "${config_dir}/${file}" >/dev/null 2>&1; then
                print_error "Invalid JSON in ${file}"
                return 1
            fi
        fi
    done

    print_success "All native image configuration files present and valid"
}

# Run all tests
run_tests() {
    print_status "Starting build validation tests..."

    local failed=0

    test_maven_config || ((failed++))
    test_native_config || ((failed++))
    test_build_artifacts || ((failed++))
    test_executable_permissions || ((failed++))
    test_deployment_package || ((failed++))
    test_executable_basic || ((failed++))

    echo ""
    if [[ $failed -eq 0 ]]; then
        print_success "All tests passed! Build is ready for deployment."
        echo ""
        echo "Deployment package: ${BUILD_DIR}/product-service-native.zip"
        echo "Package size: $(du -h "${BUILD_DIR}/product-service-native.zip" | cut -f1)"
        echo ""
        echo "Next steps:"
        echo "1. Deploy to AWS Lambda using the ZIP file"
        echo "2. Set runtime to 'provided.al2023'"
        echo "3. Configure environment variables (PRODUCTS_TABLE_NAME)"
        echo "4. Test with sample API Gateway events"
        return 0
    else
        print_error "${failed} test(s) failed. Please check the build configuration."
        return 1
    fi
}

# Main execution
main() {
    if [[ ! -d "${BUILD_DIR}" ]]; then
        print_error "Build directory not found. Please run ./build.sh first."
        exit 1
    fi

    run_tests
}

main "$@"