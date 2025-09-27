# Infrastructure Testing with Terratest

This directory contains Go-based infrastructure tests using Terratest framework to validate our AWS Lambda Java template deployment.

## Prerequisites

- Go 1.21+
- AWS CLI configured with appropriate credentials
- Terraform installed
- Lambda function artifacts built (JAR/ZIP files)

## Test Structure

### Infrastructure Tests
- `infrastructure_test.go` - Comprehensive tests for the entire Lambda infrastructure
- Validates Lambda functions, API Gateway, DynamoDB tables, and CloudWatch resources
- Uses ephemeral environments to avoid conflicts

### Test Features
- **Parallel Execution**: Tests run in parallel for faster execution
- **Random Naming**: Uses unique IDs to avoid resource conflicts
- **Automatic Cleanup**: Terraform destroy runs after tests complete
- **Comprehensive Validation**: Tests all infrastructure components

## Running Tests

### Run All Tests
```bash
cd tests
go test -v -timeout 30m
```

### Run Specific Tests
```bash
# Infrastructure validation only
go test -v -run TestTerraformValidation

# Full infrastructure deployment test
go test -v -run TestTerraformLambdaJavaTemplate -timeout 30m
```

### Run Tests in Parallel
```bash
go test -v -parallel 4 -timeout 30m
```

## Test Configuration

Tests use the following configuration:
- **AWS Region**: us-east-1 (configurable)
- **Ephemeral Mode**: `is_ephemeral=true` for isolated testing
- **Unique Naming**: Random suffixes to avoid conflicts
- **Timeout**: 30 minutes for complete infrastructure deployment

## Environment Variables

Set these environment variables for customization:
```bash
export AWS_DEFAULT_REGION=us-east-1
export AWS_PROFILE=your-profile
```

## Test Validation Points

### Lambda Functions
- ✅ Function creation and configuration
- ✅ Runtime (Java 21) and architecture (ARM64)
- ✅ X-Ray tracing enabled
- ✅ Environment variables set correctly
- ✅ Proper tagging applied

### API Gateway
- ✅ HTTP API creation
- ✅ Routes configured correctly
- ✅ Lambda integrations working

### DynamoDB
- ✅ Tables created with correct configuration
- ✅ Pay-per-request billing mode
- ✅ Encryption at rest enabled
- ✅ Point-in-time recovery enabled

### CloudWatch
- ✅ Log groups created
- ✅ Metrics being published
- ✅ Retention policies applied

## Best Practices

1. **Isolation**: Each test uses unique resource names
2. **Cleanup**: Always destroy resources after testing
3. **Timeouts**: Set appropriate timeouts for AWS operations
4. **Retries**: Handle transient AWS API errors
5. **Validation**: Test both resource creation and configuration

## Troubleshooting

### Common Issues
- **Timeout**: Increase test timeout for slow AWS operations
- **Permissions**: Ensure AWS credentials have required permissions
- **Conflicts**: Check for resource naming conflicts
- **Artifacts**: Ensure Lambda artifacts are built before testing

### Debug Mode
Run tests with verbose output:
```bash
go test -v -run TestTerraformLambdaJavaTemplate
```

### Manual Cleanup
If tests fail to cleanup automatically:
```bash
cd ../terraform
terraform destroy -var="project_name=lambda-java-test-UNIQUE_ID" -auto-approve
```