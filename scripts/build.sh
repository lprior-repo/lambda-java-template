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
    
    # Build native executable using Maven profile
    echo -e "${YELLOW}  ⚡ Building native executable with Maven...${NC}"
    cd "$SERVICE_DIR"
    
    mvn clean package -Pnative -DskipTests -q
    
    # Check if native executable was created
    NATIVE_EXE="target/$service"
    if [ ! -f "$NATIVE_EXE" ]; then
        echo -e "${RED}❌ Native executable not found: $NATIVE_EXE${NC}"
        continue
    fi
    
    echo -e "${GREEN}  ✅ Native executable built: $(ls -lh "$NATIVE_EXE" | awk '{print $5}')${NC}"
    
    # Check if assembly ZIP was created
    ASSEMBLY_ZIP=$(find target -name "*-native.zip" | head -1)
    if [ -f "$ASSEMBLY_ZIP" ]; then
        echo -e "${GREEN}  ✅ Assembly ZIP found: $(basename "$ASSEMBLY_ZIP")${NC}"
        cp "$ASSEMBLY_ZIP" "$BUILD_DIR/"
    else
        echo -e "${YELLOW}  📦 Creating deployment package manually...${NC}"
        cd target
        
        # Create bootstrap script
        cat > bootstrap << EOF
#!/bin/sh
set -euo pipefail
exec ./$service "\$@"
EOF
        chmod +x bootstrap
        
        # Create deployment ZIP
        ZIP_NAME="${service}-native.zip"
        zip -r "$ZIP_NAME" bootstrap "$service"
        cp "$ZIP_NAME" "$BUILD_DIR/"
        echo -e "${GREEN}  ✅ Package created: $ZIP_NAME${NC}"
    fi
    
    # Return to project root
    cd "$PROJECT_ROOT"
done

# Summary
echo -e "\n${GREEN}🎉 Build Complete!${NC}"
echo -e "${BLUE}📦 Built packages:${NC}"
ls -la "$BUILD_DIR"/*.zip 2>/dev/null || echo -e "${YELLOW}⚠️  No packages were built${NC}"

echo -e "\n${BLUE}📍 Build artifacts location: $BUILD_DIR${NC}"
echo -e "${GREEN}✅ Ready for deployment!${NC}"