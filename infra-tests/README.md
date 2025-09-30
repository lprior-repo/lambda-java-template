# Infrastructure Testing with Terratest

This directory contains comprehensive infrastructure tests using [Terratest](https://terratest.gruntwork.io/) to validate the AWS Lambda Java Template deployment.

## 🧪 Test Coverage

### Core Infrastructure Tests (`lambda_integration_test.go`)

1. **Lambda Functions Validation**
   - Function configuration (runtime, memory, timeout, handler)
   - X-Ray tracing enablement
   - Environment variables
   - Function state and deployment package
   - IAM roles and permissions
   - Resource tagging

2. **DynamoDB Tables Validation**
   - Table configuration (hash key, range key, billing mode)
   - Server-side encryption
   - Point-in-time recovery
   - Global Secondary Indexes (GSI)
   - Resource tagging

3. **API Gateway Integration**
   - API configuration (protocol, CORS)
   - Route configuration and mapping
   - Lambda integrations
   - Authorizer configuration
   - Endpoint functionality testing

4. **Security Configuration**
   - HTTPS enforcement
   - Lambda function isolation
   - DynamoDB encryption
   - Authorization mechanisms

5. **CloudWatch Monitoring**
   - Dashboard creation
   - Alarm configuration
   - Log group setup
   - Metric filters

6. **Performance Validation**
   - Cold start performance
   - Warm request performance
   - Response time validation

7. **Terraform Modules Validation** ⭐ **NEW**
   - `terraform-aws-modules/apigateway-v2/aws` configuration
   - `terraform-aws-modules/lambda/aws` setup
   - `terraform-aws-modules/dynamodb-table/aws` features
   - `terraform-aws-modules/s3-bucket/aws` configuration
   - Module consistency and naming patterns

## 🚀 Running Tests

### Prerequisites

```bash
# Install Go 1.23+
# Install AWS CLI and configure credentials
# Deploy infrastructure first
task tf:apply
```

### Test Commands

#### Run All Tests
```bash
# Complete test suite (recommended)
task terratest

# Alternative using go test directly
cd infra-tests
go test -v -timeout 20m -run TestLambdaIntegration
```

#### Run Specific Test Categories
```bash
# Test terraform-aws-modules configuration
task terratest:modules

# Test API endpoint functionality
task terratest:endpoints

# Test security configuration
task terratest:security

# Test performance characteristics
task terratest:performance
```

#### Run Individual Test Sections
```bash
cd infra-tests

# Lambda functions only
go test -v -timeout 10m -run TestLambdaIntegration/Lambda_Functions_Validation

# DynamoDB tables only
go test -v -timeout 10m -run TestLambdaIntegration/DynamoDB_Tables_Validation

# API Gateway only
go test -v -timeout 10m -run TestLambdaIntegration/API_Gateway_Integration

# Module validation only
go test -v -timeout 15m -run TestLambdaIntegration/Terraform_Modules_Validation
```

## 🌐 Endpoint Testing

### Comprehensive Endpoint Validation Script

The `scripts/validate-endpoints.sh` script provides comprehensive endpoint testing:

```bash
# Run endpoint validation
task test:endpoints

# Or run directly
./scripts/validate-endpoints.sh
```

#### What it tests:
- ✅ Health endpoint (no authentication)
- ✅ Authentication protection on secured endpoints
- ✅ Products CRUD operations
- ✅ Error handling and status codes
- ✅ Response format validation
- ✅ Performance characteristics

#### Configuration:
```bash
# Environment variables (optional)
export PROJECT_NAME="lambda-java-template"
export ENVIRONMENT="dev"
export AWS_REGION="us-east-1"
export API_KEY="test-api-key"
```

## 📊 Test Results

### Expected Outputs

#### ✅ Successful Test Run
```
=== RUN   TestLambdaIntegration
=== RUN   TestLambdaIntegration/Lambda_Functions_Validation
=== RUN   TestLambdaIntegration/DynamoDB_Tables_Validation
=== RUN   TestLambdaIntegration/API_Gateway_Integration
=== RUN   TestLambdaIntegration/Security_Configuration
=== RUN   TestLambdaIntegration/CloudWatch_Monitoring
=== RUN   TestLambdaIntegration/Performance_Validation
=== RUN   TestLambdaIntegration/Terraform_Modules_Validation
--- PASS: TestLambdaIntegration (180.25s)
PASS
```

#### ❌ Test Failures

Tests will fail with descriptive error messages:
```
--- FAIL: TestLambdaIntegration/Lambda_Functions_Validation/Function_product_service (5.23s)
    lambda_integration_test.go:96: Expected runtime to be java21, but got java17
```

### Performance Benchmarks

| Test | Expected | Threshold |
|------|----------|-----------|
| Java Cold Start | < 30 seconds | First request |
| Warm Requests | < 10 seconds | Subsequent requests |
| Health Endpoint | < 5 seconds | Always |

## 🛠️ Development Workflow

### Complete Validation Pipeline
```bash
# Full development validation
task validate

# This runs:
# 1. Unit tests (mvn test)
# 2. Integration tests (Java/Spring Boot)
# 3. Infrastructure tests (Terratest)
# 4. Endpoint validation (bash script)
```

### CI/CD Integration

Add to your CI/CD pipeline:
```yaml
- name: Infrastructure Tests
  run: |
    task terratest
    task test:endpoints
```

### Debugging Failed Tests

1. **Check AWS Resources**
   ```bash
   # Verify deployment
   task tf:plan
   
   # Check logs
   task logs
   ```

2. **Run Tests Individually**
   ```bash
   # Focus on failing test
   go test -v -run TestLambdaIntegration/Lambda_Functions_Validation
   ```

3. **Check API Gateway URL**
   ```bash
   # Manual URL discovery
   aws apigatewayv2 get-apis --query "Items[?Name=='lambda-java-template-dev-api'].ApiEndpoint"
   ```

## 🔧 Customization

### Adding New Tests

1. **Add to existing test functions** in `lambda_integration_test.go`
2. **Create new test categories** following the pattern:
   ```go
   t.Run("New_Test_Category", func(t *testing.T) {
       validateNewFeature(t, cfg, projectName, environment)
   })
   ```

### Environment-Specific Testing

```bash
# Test different environments
TF_VAR_environment=staging task terratest
TF_VAR_environment=prod task terratest
```

### Custom Configuration

Override test parameters:
```go
// In lambda_integration_test.go
awsRegion := os.Getenv("AWS_REGION")
if awsRegion == "" {
    awsRegion = "us-east-1"
}
```

## 📝 Test Documentation

### Test Structure
```
infra-tests/
├── go.mod                      # Go dependencies
├── go.sum                      # Go dependency lock
├── lambda_integration_test.go  # Main test file
└── README.md                   # This file
```

### Key Dependencies
- `github.com/gruntwork-io/terratest` - Infrastructure testing framework
- `github.com/aws/aws-sdk-go-v2` - AWS SDK for Go v2
- `github.com/stretchr/testify` - Test assertions and utilities

## 🎯 Best Practices

1. **Always run tests after infrastructure changes**
2. **Use specific test runs during development**
3. **Check AWS credentials before running tests**
4. **Review test timeouts for your environment**
5. **Monitor AWS costs during test runs**
6. **Clean up test resources appropriately**

## 🚨 Troubleshooting

### Common Issues

1. **AWS Credentials Not Configured**
   ```
   Error: NoCredentialsError
   Solution: aws configure
   ```

2. **Infrastructure Not Deployed**
   ```
   Error: Function not found
   Solution: task tf:apply
   ```

3. **Timeout Errors**
   ```
   Error: Test timeout
   Solution: Increase timeout or check AWS region latency
   ```

4. **API Gateway URL Not Found**
   ```
   Error: Could not find API Gateway
   Solution: Check deployment and naming conventions
   ```

For more issues, check the [main project README](../README.md) or [DEBUGGING.md](../DEBUGGING.md).