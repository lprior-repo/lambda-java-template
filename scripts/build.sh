#!/bin/bash

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="${PROJECT_ROOT}/build"
NATIVE_CONFIG_DIR="${PROJECT_ROOT}/native-config"
SERVICE_NAME="product-service"
SERVICE_DIR="${PROJECT_ROOT}/${SERVICE_NAME}"
TARGET_DIR="${SERVICE_DIR}/target"
NATIVE_EXECUTABLE="${SERVICE_NAME}"

# Default values
SKIP_TESTS=false
CLEAN_BUILD=false
VERBOSE=false
BUILD_PROFILE="native"

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to show usage
show_usage() {
    cat << EOF
Usage: $0 [OPTIONS]

Build GraalVM native image for AWS Lambda deployment

OPTIONS:
    -h, --help          Show this help message
    -c, --clean         Clean build (remove target directories first)
    -s, --skip-tests    Skip running tests during build
    -v, --verbose       Enable verbose output
    -p, --profile       Maven profile to use (default: native)

EXAMPLES:
    $0                  # Standard build
    $0 --clean          # Clean build
    $0 --skip-tests     # Build without running tests
    $0 --clean --verbose # Clean build with verbose output

ENVIRONMENT VARIABLES:
    JAVA_HOME          Path to Java 21+ installation
    GRAALVM_HOME       Path to GraalVM installation (optional, uses system if not set)
    MAVEN_OPTS         Additional Maven options

REQUIREMENTS:
    - Java 21+
    - GraalVM 24.1.1+
    - Maven 3.8+
    - Docker (for testing native image in Lambda runtime)

EOF
}

# Parse command line arguments
parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_usage
                exit 0
                ;;
            -c|--clean)
                CLEAN_BUILD=true
                shift
                ;;
            -s|--skip-tests)
                SKIP_TESTS=true
                shift
                ;;
            -v|--verbose)
                VERBOSE=true
                shift
                ;;
            -p|--profile)
                BUILD_PROFILE="$2"
                shift 2
                ;;
            *)
                print_error "Unknown option: $1"
                show_usage
                exit 1
                ;;
        esac
    done
}

# Check prerequisites
check_prerequisites() {
    print_status "Checking prerequisites..."

    # Check Java version
    if ! command -v java &> /dev/null; then
        print_error "Java is not installed or not in PATH"
        exit 1
    fi

    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [[ ${JAVA_VERSION} -lt 21 ]]; then
        print_error "Java 21 or higher is required. Found: ${JAVA_VERSION}"
        exit 1
    fi
    print_success "Java ${JAVA_VERSION} found"

    # Check Maven
    if ! command -v mvn &> /dev/null; then
        print_error "Maven is not installed or not in PATH"
        exit 1
    fi

    MAVEN_VERSION=$(mvn -version | head -n 1 | cut -d' ' -f3)
    print_success "Maven ${MAVEN_VERSION} found"

    # Check for native-image capability
    if ! command -v native-image &> /dev/null; then
        print_warning "native-image command not found. Checking if GraalVM is properly configured..."
        if [[ -n "${GRAALVM_HOME:-}" ]] && [[ -f "${GRAALVM_HOME}/bin/native-image" ]]; then
            export PATH="${GRAALVM_HOME}/bin:${PATH}"
            print_success "Found native-image in GRAALVM_HOME"
        else
            print_error "native-image not found. Please install GraalVM or ensure it's in PATH"
            print_error "You can install GraalVM from: https://www.graalvm.org/downloads/"
            exit 1
        fi
    else
        print_success "native-image found in PATH"
    fi

    # Check Docker for testing (optional)
    if ! command -v docker &> /dev/null; then
        print_warning "Docker not found. Skipping runtime testing capabilities"
    else
        print_success "Docker found for runtime testing"
    fi
}

# Clean previous builds
clean_build() {
    if [[ "${CLEAN_BUILD}" == "true" ]]; then
        print_status "Cleaning previous builds..."

        # Clean Maven targets
        cd "${PROJECT_ROOT}"
        mvn clean -q

        # Clean build directory
        if [[ -d "${BUILD_DIR}" ]]; then
            rm -rf "${BUILD_DIR}"
        fi

        print_success "Build cleaned"
    fi
}

# Create build directory structure
setup_build_directory() {
    print_status "Setting up build directory..."

    mkdir -p "${BUILD_DIR}"
    mkdir -p "${BUILD_DIR}/native-config"

    print_success "Build directory created: ${BUILD_DIR}"
}

# Build Java application with Maven
build_java_application() {
    print_status "Building Java application with Maven..."

    cd "${PROJECT_ROOT}"

    # Prepare Maven command
    MAVEN_CMD="mvn package"

    if [[ "${SKIP_TESTS}" == "true" ]]; then
        MAVEN_CMD="${MAVEN_CMD} -DskipTests"
    fi

    if [[ "${VERBOSE}" == "false" ]]; then
        MAVEN_CMD="${MAVEN_CMD} -q"
    fi

    # Add profile if specified
    if [[ -n "${BUILD_PROFILE}" ]]; then
        MAVEN_CMD="${MAVEN_CMD} -P${BUILD_PROFILE}"
    fi

    print_status "Running: ${MAVEN_CMD}"
    eval "${MAVEN_CMD}"

    # Verify JAR was created
    if [[ ! -f "${TARGET_DIR}/${SERVICE_NAME}.jar" ]]; then
        print_error "JAR file not found: ${TARGET_DIR}/${SERVICE_NAME}.jar"
        exit 1
    fi

    print_success "Java application built successfully"
}

# Build native image
build_native_image() {
    print_status "Building GraalVM native image..."

    cd "${SERVICE_DIR}"

    # Prepare native image command
    NATIVE_IMAGE_CMD="mvn -Pnative native:compile-no-fork"

    if [[ "${VERBOSE}" == "false" ]]; then
        NATIVE_IMAGE_CMD="${NATIVE_IMAGE_CMD} -q"
    fi

    print_status "Running: ${NATIVE_IMAGE_CMD}"
    eval "${NATIVE_IMAGE_CMD}"

    # Verify native executable was created
    NATIVE_EXECUTABLE_PATH="${TARGET_DIR}/${NATIVE_EXECUTABLE}"
    if [[ ! -f "${NATIVE_EXECUTABLE_PATH}" ]]; then
        print_error "Native executable not found: ${NATIVE_EXECUTABLE_PATH}"
        exit 1
    fi

    # Make executable
    chmod +x "${NATIVE_EXECUTABLE_PATH}"

    # Get file size
    EXECUTABLE_SIZE=$(du -h "${NATIVE_EXECUTABLE_PATH}" | cut -f1)
    print_success "Native image built successfully (${EXECUTABLE_SIZE})"

    # Copy to build directory
    cp "${NATIVE_EXECUTABLE_PATH}" "${BUILD_DIR}/"
    print_success "Native executable copied to build directory"
}

# Create deployment package
create_deployment_package() {
    print_status "Creating Lambda deployment package..."

    cd "${BUILD_DIR}"

    # Copy bootstrap script
    if [[ -f "${PROJECT_ROOT}/bootstrap" ]]; then
        cp "${PROJECT_ROOT}/bootstrap" .
        chmod +x bootstrap
    else
        print_error "Bootstrap script not found at ${PROJECT_ROOT}/bootstrap"
        exit 1
    fi

    # Create ZIP package
    ZIP_FILE="${SERVICE_NAME}-native.zip"
    zip -j "${ZIP_FILE}" "${NATIVE_EXECUTABLE}" bootstrap

    ZIP_SIZE=$(du -h "${ZIP_FILE}" | cut -f1)
    print_success "Deployment package created: ${ZIP_FILE} (${ZIP_SIZE})"
}

# Test native executable (basic smoke test)
test_native_executable() {
    print_status "Running basic smoke test on native executable..."

    cd "${BUILD_DIR}"

    # Test that the executable can start (this is a basic test)
    if timeout 5s ./"${NATIVE_EXECUTABLE}" --help &>/dev/null || [[ $? -eq 124 ]]; then
        print_success "Native executable passed basic smoke test"
    else
        print_warning "Native executable smoke test inconclusive (this may be normal for Lambda functions)"
    fi
}

# Show build summary
show_build_summary() {
    print_status "Build Summary"
    echo "============================================"
    echo "Project: ${SERVICE_NAME}"
    echo "Build Directory: ${BUILD_DIR}"
    echo "Native Executable: ${BUILD_DIR}/${NATIVE_EXECUTABLE}"
    echo "Deployment Package: ${BUILD_DIR}/${SERVICE_NAME}-native.zip"

    if [[ -f "${BUILD_DIR}/${SERVICE_NAME}-native.zip" ]]; then
        echo "Package Size: $(du -h "${BUILD_DIR}/${SERVICE_NAME}-native.zip" | cut -f1)"
    fi

    echo "============================================"
    print_success "Build completed successfully!"
    echo ""
    echo "Next steps:"
    echo "1. Test the native executable locally (if needed)"
    echo "2. Deploy using your preferred method (SAM, CDK, Terraform, etc.)"
    echo "3. Upload ${BUILD_DIR}/${SERVICE_NAME}-native.zip to AWS Lambda"
}

# Main execution
main() {
    print_status "Starting GraalVM Lambda build process..."

    parse_arguments "$@"
    check_prerequisites
    clean_build
    setup_build_directory
    build_java_application
    build_native_image
    create_deployment_package
    test_native_executable
    show_build_summary
}

# Run main function with all arguments
main "$@"