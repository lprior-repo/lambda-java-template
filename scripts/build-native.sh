#!/bin/bash
set -e

# Set GraalVM environment (works in CI container)
if [ -z "$JAVA_HOME" ]; then
    export JAVA_HOME=$(readlink -f /usr/bin/java | sed "s:/bin/java::")
fi
export PATH=$JAVA_HOME/bin:$PATH

echo "Using Java: $(java -version 2>&1 | head -1)"
echo "Using JAVA_HOME: $JAVA_HOME"

# Build using Maven native profile
echo "Building native packages with Maven..."
mvn clean package -Pnative-aot -DskipTests

# Create native packages for each service
echo "Creating native deployment packages..."

for service_dir in src/*/; do
    if [ -d "$service_dir" ] && [ "$service_dir" != "src/main/" ]; then
        service_name=$(basename "$service_dir")
        target_dir="$service_dir/target"
        
        if [ -f "$target_dir/$service_name" ]; then
            echo "📦 Creating native package for $service_name..."
            cd "$target_dir"
            
            # Create bootstrap script
            echo '#!/bin/sh' > bootstrap
            echo "./$service_name" >> bootstrap
            chmod +x bootstrap
            
            # Create deployment package
            zip -r "$service_name-native.zip" bootstrap "$service_name"
            
            echo "✅ Native package created: $service_name-native.zip"
            cd - > /dev/null
        else
            echo "⚠️  Native binary not found for $service_name, skipping..."
        fi
    fi
done

echo "🎉 All native packages completed!"