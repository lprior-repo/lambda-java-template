# 🐛 Lambda Java Debugging Guide

## Overview

This project provides multiple debugging approaches for the best developer experience when working with Spring Boot Lambda functions.

## 🚀 Quick Start

1. **Start local environment**:
   ```bash
   task dev:local
   ```

2. **Run app with debugging**:
   ```bash
   task dev:run
   ```

3. **Test endpoints**:
   ```bash
   task dev:test
   ```

## 🔧 Debugging Options

### 1. Local Spring Boot Server (Recommended for Development)

**Best for**: Active development, setting breakpoints, step-through debugging

```bash
# Start local services (DynamoDB, etc.)
task dev:local

# Run Spring Boot locally with hot reload + debugging
task dev:run
```

**Features**:
- ✅ Hot reload on code changes
- ✅ Full IDE debugging support
- ✅ Breakpoints and step-through
- ✅ Local DynamoDB with sample data
- ✅ Same business logic as Lambda
- ✅ Fast startup (~10 seconds)

**Access**:
- API: http://localhost:8080/api/products
- Health: http://localhost:8080/api/health
- DynamoDB Admin: http://localhost:8001

### 2. Remote Debugging (Suspended Start)

**Best for**: Debugging startup issues, complex initialization problems

```bash
task dev:debug
```

This starts the app and **waits** for debugger attachment before continuing.

### 3. SAM Local Invocation

**Best for**: Testing Lambda-specific behavior, cold starts, timeouts

```bash
# Build the function first
task jar

# Invoke specific function
sam local invoke ProductFunction --event events/test-event.json

# Start API Gateway locally
sam local start-api
```

### 4. Native Binary Testing

**Best for**: Testing final deployment artifact, performance testing

```bash
# Build native binary
task native

# Test with SAM
sam local invoke ProductFunction --event events/test-event.json
```

## 🔌 IDE Setup

### VS Code

1. Open project in VS Code
2. Install Java Extension Pack
3. Use provided launch configurations:
   - "Debug Local Spring Boot" - Direct debugging
   - "Attach to Local App" - Attach to running app

### IntelliJ IDEA

1. Open project in IntelliJ
2. Use provided run configuration: "Local Debug Spring Boot"
3. Or create new Spring Boot run config with:
   - Main class: `software.amazonaws.example.product.ProductApplication`
   - Active profiles: `local`
   - VM options: `-Dspring.profiles.active=local`

### Eclipse

1. Import as Maven project
2. Create Debug Configuration:
   - Type: Java Application
   - Main class: `software.amazonaws.example.product.ProductApplication`
   - Arguments tab > VM arguments: `-Dspring.profiles.active=local`

## 🏗️ Local Environment

### Services

| Service | Port | Purpose |
|---------|------|---------|
| Spring Boot App | 8080 | Main application |
| DynamoDB Local | 8000 | Local database |
| DynamoDB Admin | 8001 | Database management UI |
| Debug Port | 5005 | Remote debugging |
| LocalStack | 4566 | Additional AWS services |

### Sample Data

The setup script creates sample products:
- `sample-product-1` - Sample Product ($29.99)
- `sample-product-2` - Debug Product ($19.99)

### Environment Variables

```yaml
ENVIRONMENT: local
LOG_LEVEL: DEBUG
PRODUCTS_TABLE_NAME: products-local
AUDIT_TABLE_NAME: audit-logs-local
```

## 🧪 Testing Workflow

### 1. Unit Testing
```bash
task test:unit
```

### 2. Integration Testing
```bash
task test:integration
```

### 3. Local API Testing
```bash
# Start local environment
task dev:local
task dev:run

# In another terminal
task dev:test

# Or manual testing
curl http://localhost:8080/api/products
curl -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{"name":"Test Product","description":"For testing","price":99.99}'
```

### 4. Lambda Testing
```bash
# Build and test as Lambda
task jar
sam local invoke ProductFunction --event events/get-products.json
```

## 🔍 Debugging Techniques

### 1. Logging

The local profile enables DEBUG logging for:
- Your application code
- Spring Cloud Function
- Spring Web
- AWS SDK (INFO level)

### 2. Breakpoints

Set breakpoints in:
- `ProductService` methods
- `SpringBootProductHandler.apply()`
- `LocalDevelopmentController` endpoints

### 3. Hot Reload

Spring DevTools enables automatic restart when you:
- Modify Java files
- Change configuration files
- Update static resources

### 4. Actuator Endpoints

Available at: http://localhost:8080/actuator/
- `/health` - Application health
- `/info` - Application info
- `/metrics` - Application metrics
- `/loggers` - Logging configuration

## 🚨 Troubleshooting

### Port Already in Use
```bash
# Kill process on port 8080
lsof -ti:8080 | xargs kill -9

# Or use different port
mvn spring-boot:run -Dspring-boot.run.profiles=local -Dserver.port=8081
```

### DynamoDB Connection Issues
```bash
# Restart local DynamoDB
task dev:stop
task dev:local
```

### Debug Port in Use
```bash
# Kill process on debug port
lsof -ti:5005 | xargs kill -9
```

### Hot Reload Not Working
1. Ensure DevTools dependency is included
2. IDE should be set to "Build Automatically"
3. Restart the application

## 📊 Performance Comparison

| Method | Startup Time | Debug Support | Hot Reload | Lambda Accurate |
|--------|-------------|---------------|------------|-----------------|
| Local Spring Boot | ~10s | ✅ Full | ✅ Yes | 🟡 Business Logic |
| SAM Local | ~30s | 🟡 Limited | ❌ No | ✅ Yes |
| Native Binary | ~45s | ❌ No | ❌ No | ✅ Yes |

## 🎯 Best Practices

1. **Use Local Spring Boot for active development** - fastest feedback loop
2. **Test with SAM Local before deployment** - catches Lambda-specific issues
3. **Build native binary for final testing** - validates production artifact
4. **Keep local environment running** - faster iteration cycles
5. **Use meaningful breakpoints** - focus on business logic, not framework code
6. **Monitor logs at DEBUG level** - catch issues early

## 🧹 Cleanup

```bash
# Stop all local services
task dev:stop

# Clean all data and volumes
task dev:clean
```