# Integration Tests

This document describes how to run integration tests that use real AWS services.

## Prerequisites

### AWS Setup
1. **AWS Credentials**: Configure AWS credentials using one of the following methods:
   - AWS CLI: `aws configure`
   - Environment variables: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`
   - IAM roles (when running on EC2/Lambda)
   - AWS Profile: Set `AWS_PROFILE` environment variable

2. **AWS Region**: Set the AWS region (default: us-east-1):
   ```bash
   export AWS_REGION=us-east-1
   ```

3. **Required IAM Permissions**:
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Effect": "Allow",
         "Action": [
           "dynamodb:DescribeTable",
           "dynamodb:GetItem",
           "dynamodb:PutItem",
           "dynamodb:UpdateItem",
           "dynamodb:DeleteItem",
           "dynamodb:Query",
           "dynamodb:Scan"
         ],
         "Resource": [
           "arn:aws:dynamodb:*:*:table/your-products-table",
           "arn:aws:dynamodb:*:*:table/your-audit-table"
         ]
       }
     ]
   }
   ```

### DynamoDB Tables

#### Products Table
Create a DynamoDB table for products with the following configuration:
- **Table Name**: Set via `PRODUCTS_TABLE_NAME` environment variable
- **Partition Key**: `id` (String)
- **Billing Mode**: On-demand or Provisioned (5 RCU/WCU minimum for testing)

```bash
# Using AWS CLI
aws dynamodb create-table \
  --table-name lambda-java-products-test \
  --attribute-definitions AttributeName=id,AttributeType=S \
  --key-schema AttributeName=id,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region us-east-1
```

#### Audit Table
Create a DynamoDB table for audit logs:
- **Table Name**: Set via `AUDIT_TABLE_NAME` environment variable
- **Partition Key**: `event_id` (String)
- **TTL Attribute**: `ttl` (Number) - Enable TTL on this attribute
- **Billing Mode**: On-demand or Provisioned (5 RCU/WCU minimum for testing)

```bash
# Using AWS CLI
aws dynamodb create-table \
  --table-name lambda-java-audit-test \
  --attribute-definitions AttributeName=event_id,AttributeType=S \
  --key-schema AttributeName=event_id,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region us-east-1

# Enable TTL
aws dynamodb update-time-to-live \
  --table-name lambda-java-audit-test \
  --time-to-live-specification Enabled=true,AttributeName=ttl
```

## Running Integration Tests

### Environment Variables
Set the required environment variables:

```bash
export PRODUCTS_TABLE_NAME=lambda-java-products-test
export AUDIT_TABLE_NAME=lambda-java-audit-test
export AWS_REGION=us-east-1
export AWS_PROFILE=your-profile  # Optional if using default
```

### Running Tests

#### Run All Integration Tests
```bash
mvn test -Dtest="*IntegrationTest"
```

#### Run Specific Integration Test Classes
```bash
# Product Service Integration Tests
mvn test -Dtest=ProductServiceIntegrationTest \
  -DPRODUCTS_TABLE_NAME=lambda-java-products-test

# Product Handler Integration Tests
mvn test -Dtest=ProductHandlerIntegrationTest \
  -DPRODUCTS_TABLE_NAME=lambda-java-products-test

# Event Processor Integration Tests
mvn test -Dtest=EventProcessorIntegrationTest \
  -DAUDIT_TABLE_NAME=lambda-java-audit-test
```

#### Run Integration Tests in Specific Module
```bash
# Product Service only
cd product-service
mvn test -Dtest="*IntegrationTest" \
  -DPRODUCTS_TABLE_NAME=lambda-java-products-test

# Event Processor only
cd event-processor-service
mvn test -Dtest="*IntegrationTest" \
  -DAUDIT_TABLE_NAME=lambda-java-audit-test
```

### Test Configuration

#### Debug Configuration
Add this to see test configuration:
```bash
mvn test -Dtest=ProductServiceIntegrationTest \
  -DPRODUCTS_TABLE_NAME=lambda-java-products-test \
  -Dtest.debug=true
```

#### Custom AWS Profile
```bash
AWS_PROFILE=integration-test mvn test -Dtest="*IntegrationTest"
```

#### Custom Region
```bash
AWS_REGION=eu-west-1 mvn test -Dtest="*IntegrationTest"
```

## Test Types

### ProductServiceIntegrationTest
Tests the core business logic with real DynamoDB:
- Product creation, retrieval, update, deletion
- Data validation and error handling
- DynamoDB persistence verification

### ProductHandlerIntegrationTest
Tests the complete Lambda handler flow:
- API Gateway event processing
- HTTP method routing (GET, POST, PUT, DELETE)
- Request/response serialization
- Error handling and status codes
- CORS headers

### EventProcessorIntegrationTest
Tests EventBridge event processing:
- EventBridge event handling
- DynamoDB audit log creation
- TTL configuration
- Complex event detail serialization
- Error handling for malformed events

## Test Data Management

### Automatic Cleanup
All integration tests include automatic cleanup of test data:
- Products created during tests are automatically deleted
- Audit entries are removed after test completion
- Failed tests may leave orphaned data (check manually)

### Manual Cleanup
If tests fail and leave orphaned data:

```bash
# List items in products table
aws dynamodb scan --table-name lambda-java-products-test \
  --filter-expression "contains(#name, :test)" \
  --expression-attribute-names '{"#name": "name"}' \
  --expression-attribute-values '{":test": {"S": "Integration Test"}}'

# List items in audit table
aws dynamodb scan --table-name lambda-java-audit-test \
  --filter-expression "contains(detail, :test)" \
  --expression-attribute-values '{":test": {"S": "integration-test"}}'
```

## Troubleshooting

### Common Issues

1. **AccessDeniedException**
   ```
   Solution: Check IAM permissions and AWS credentials
   ```

2. **ResourceNotFoundException**
   ```
   Solution: Verify table names and that tables exist in the correct region
   ```

3. **Tests are skipped**
   ```
   Solution: Ensure environment variables are set correctly
   Check: @EnabledIfEnvironmentVariable conditions
   ```

4. **ValidationException**
   ```
   Solution: Verify table schema matches expected key attributes
   ```

### Debug Commands

```bash
# Check AWS credentials
aws sts get-caller-identity

# Check table status
aws dynamodb describe-table --table-name lambda-java-products-test

# Check table TTL configuration
aws dynamodb describe-time-to-live --table-name lambda-java-audit-test

# List tables in region
aws dynamodb list-tables --region us-east-1
```

### Verbose Logging
Enable detailed AWS SDK logging:
```bash
mvn test -Dtest="*IntegrationTest" \
  -Daws.java.v2.debug=true \
  -Dorg.slf4j.simpleLogger.defaultLogLevel=debug
```

## CI/CD Integration

### GitHub Actions Example
```yaml
env:
  AWS_REGION: us-east-1
  PRODUCTS_TABLE_NAME: lambda-java-products-test-${{ github.run_id }}
  AUDIT_TABLE_NAME: lambda-java-audit-test-${{ github.run_id }}

steps:
  - name: Configure AWS Credentials
    uses: aws-actions/configure-aws-credentials@v2
    with:
      role-to-assume: ${{ secrets.AWS_ROLE_ARN }}
      aws-region: us-east-1

  - name: Create Test Tables
    run: |
      aws dynamodb create-table --cli-input-json file://products-table.json
      aws dynamodb create-table --cli-input-json file://audit-table.json

  - name: Run Integration Tests
    run: mvn test -Dtest="*IntegrationTest"

  - name: Cleanup Test Tables
    if: always()
    run: |
      aws dynamodb delete-table --table-name $PRODUCTS_TABLE_NAME
      aws dynamodb delete-table --table-name $AUDIT_TABLE_NAME
```

## Cost Considerations

- Use PAY_PER_REQUEST billing mode for test tables
- Enable TTL on audit table to automatically clean up old entries
- Consider using DynamoDB Local for development
- Delete test tables when not actively testing
- Use separate AWS account for integration testing

## Security Best Practices

- Use least-privilege IAM policies
- Don't hardcode AWS credentials in tests
- Use separate test tables from production
- Consider using temporary credentials for CI/CD
- Enable CloudTrail logging for audit purposes