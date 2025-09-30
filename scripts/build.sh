#!/bin/bash
set -euo pipefail

# Spring Native Lambda Build Script
# Builds all Lambda services as GraalVM native executables in ZIP packages

echo "🚀 Building Spring Native Lambda Functions"
echo "========================================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
BUILD_DIR="$(pwd)/build"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Detect services from src/ directory
SERVICES=($(find "$PROJECT_ROOT/src" -maxdepth 1 -type d -name "*-service" | xargs -I {} basename {}))

if [ ${#SERVICES[@]} -eq 0 ]; then
    echo -e "${RED}❌ No services found in src/ directory${NC}"
    exit 1
fi

echo -e "${BLUE}📋 Found ${#SERVICES[@]} services: ${SERVICES[*]}${NC}"

# Clean and create build directory
echo -e "${YELLOW}🧹 Cleaning build directory...${NC}"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

# First, do a full parent POM build to resolve dependencies
echo -e "${YELLOW}🏗️  Building parent POM and all modules...${NC}"
cd "$PROJECT_ROOT"
mvn clean compile -q

echo -e "${GREEN}✅ Parent POM build complete${NC}"

# Build each service
for service in "${SERVICES[@]}"; do
    echo -e "\n${BLUE}🔨 Building $service...${NC}"
    
    SERVICE_DIR="$PROJECT_ROOT/src/$service"
    
    if [ ! -f "$SERVICE_DIR/pom.xml" ]; then
        echo -e "${RED}❌ No pom.xml found for $service, skipping${NC}"
        continue
    fi
    
    # Step 1: Build JAR with Spring Native AOT processing
    echo -e "${YELLOW}  📦 Building JAR with Spring Native AOT...${NC}"
    cd "$SERVICE_DIR"
    
    mvn clean package -Pnative -DskipTests -q
    
    # Find the built JAR (handle version numbers)
    JAR_FILE=$(find target -name "*.jar" -not -name "*.original" | head -1)
    
    if [ ! -f "$JAR_FILE" ]; then
        echo -e "${RED}❌ JAR file not found for $service${NC}"
        continue
    fi
    
    echo -e "${GREEN}  ✅ JAR built: $(basename "$JAR_FILE")${NC}"
    
    # Step 2: Extract JAR for native compilation
    echo -e "${YELLOW}  📂 Extracting JAR...${NC}"
    cd target
    rm -rf native-build && mkdir native-build && cd native-build
    jar -xf "../$JAR_FILE"
    
    # Step 3: Build native executable
    echo -e "${YELLOW}  ⚡ Building native executable...${NC}"
    
    # Use native-image with Spring Boot specific configuration
    native-image \
        --no-fallback \
        --enable-http \
        --enable-https \
        --enable-url-protocols=http,https \
        --enable-all-security-services \
        --report-unsupported-elements-at-runtime \
        --allow-incomplete-classpath \
        --initialize-at-build-time=org.eclipse.jdt,org.apache.el,org.apache.tomcat \
        --initialize-at-run-time=org.springframework.boot.logging.logback,io.netty.channel.unix \
        -cp "." \
        -H:+ReportExceptionStackTraces \
        -H:+AddAllCharsets \
        -H:EnableURLProtocols=http,https \
        -H:Name="$service" \
        -H:Class=org.springframework.boot.loader.launch.JarLauncher \
        "$service"
    
    if [ ! -f "$service" ]; then
        echo -e "${RED}❌ Native executable not created for $service${NC}"
        continue
    fi
    
    echo -e "${GREEN}  ✅ Native executable built${NC}"
    
    # Step 4: Create bootstrap script
    echo -e "${YELLOW}  📜 Creating bootstrap script...${NC}"
    cat > bootstrap << 'EOF'
#!/bin/sh
set -euo pipefail
exec ./${SERVICE_NAME} "$@"
EOF
    
    # Replace placeholder with actual service name
    sed -i "s/\${SERVICE_NAME}/$service/g" bootstrap
    chmod +x bootstrap
    
    # Step 5: Create deployment ZIP
    echo -e "${YELLOW}  📦 Creating deployment package...${NC}"
    ZIP_NAME="${service}-native.zip"
    zip -r "$ZIP_NAME" bootstrap "$service"
    
    # Step 6: Copy to build directory
    cp "$ZIP_NAME" "$BUILD_DIR/"
    
    echo -e "${GREEN}  ✅ Package created: $ZIP_NAME${NC}"
    
    # Return to project root
    cd "$PROJECT_ROOT"
done

# Summary
echo -e "\n${GREEN}🎉 Build Complete!${NC}"
echo -e "${BLUE}📦 Built packages:${NC}"
ls -la "$BUILD_DIR"/*.zip 2>/dev/null || echo -e "${YELLOW}⚠️  No packages were built${NC}"

echo -e "\n${BLUE}📍 Build artifacts location: $BUILD_DIR${NC}"
echo -e "${GREEN}✅ Ready for deployment!${NC}"