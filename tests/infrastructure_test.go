package test

import (
	"context"
	"fmt"
	"testing"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/apigatewayv2"
	"github.com/aws/aws-sdk-go-v2/service/cloudwatch"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb"
	"github.com/aws/aws-sdk-go-v2/service/lambda"
	"github.com/gruntwork-io/terratest/modules/random"
	"github.com/gruntwork-io/terratest/modules/terraform"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// TestTerraformLambdaJavaTemplate tests the complete Lambda Java template infrastructure
func TestTerraformLambdaJavaTemplate(t *testing.T) {
	t.Parallel()

	// Generate unique names for resources to avoid conflicts
	uniqueID := random.UniqueId()
	projectName := fmt.Sprintf("lambda-java-test-%s", uniqueID)
	
	// Pick a random AWS region to test in
	awsRegion := "us-east-1"

	// Construct the terraform options with default retryable errors to handle the most common retryable errors in terraform testing
	terraformOptions := &terraform.Options{
		// The path to where our Terraform code is located
		TerraformDir: "../terraform",

		// Variables to pass to our Terraform code using -var options
		Vars: map[string]interface{}{
			"project_name":  projectName,
			"aws_region":    awsRegion,
			"is_ephemeral":  true,
			"namespace":     uniqueID,
		},

		// Environment variables to set when running Terraform
		EnvVars: map[string]string{
			"AWS_DEFAULT_REGION": awsRegion,
		},

		// Configure a backup (if the deployment fails, we can't destroy, but we can try again)
		BackendConfig: map[string]interface{}{
			"region": awsRegion,
		},

		// Disable colors in Terraform commands so its easier to parse stdout/stderr
		NoColor: true,

		// Reconfigure is required to use different backend for testing
		Reconfigure: true,
	}

	// At the end of the test, run `terraform destroy` to clean up any resources that were created
	defer terraform.Destroy(t, terraformOptions)

	// This will run `terraform init` and `terraform apply` and fail the test if there are any errors
	terraform.InitAndApply(t, terraformOptions)

	// Validate the infrastructure was created correctly
	validateInfrastructure(t, terraformOptions, awsRegion, projectName, uniqueID)
}

// validateInfrastructure validates that all infrastructure components were created correctly
func validateInfrastructure(t *testing.T, terraformOptions *terraform.Options, awsRegion, projectName, namespace string) {
	// Load AWS configuration
	cfg, err := config.LoadDefaultConfig(context.TODO(), config.WithRegion(awsRegion))
	require.NoError(t, err)

	// Test Lambda functions
	validateLambdaFunctions(t, cfg, projectName, namespace)

	// Test API Gateway
	validateAPIGateway(t, terraformOptions)

	// Test DynamoDB tables
	validateDynamoDBTables(t, cfg, projectName, namespace)

	// Test CloudWatch resources
	validateCloudWatchResources(t, cfg, projectName, namespace)
}

// validateLambdaFunctions validates that Lambda functions are created and configured correctly
func validateLambdaFunctions(t *testing.T, cfg aws.Config, projectName, namespace string) {
	lambdaClient := lambda.NewFromConfig(cfg)
	
	expectedFunctions := []string{"lambda1", "lambda2", "lambda3"}
	
	for _, functionSuffix := range expectedFunctions {
		functionName := fmt.Sprintf("%s-%s-%s", projectName, namespace, functionSuffix)
		
		// Get function configuration
		getConfigInput := &lambda.GetFunctionInput{
			FunctionName: aws.String(functionName),
		}
		
		functionConfig, err := lambdaClient.GetFunction(context.TODO(), getConfigInput)
		require.NoError(t, err, "Failed to get Lambda function %s", functionName)
		
		// Validate function configuration
		assert.Equal(t, "java21", *functionConfig.Configuration.Runtime)
		assert.Equal(t, []string{"arm64"}, functionConfig.Configuration.Architectures)
		assert.NotNil(t, functionConfig.Configuration.TracingConfig)
		assert.Equal(t, "Active", string(functionConfig.Configuration.TracingConfig.Mode))
		
		// Validate environment variables
		envVars := functionConfig.Configuration.Environment.Variables
		assert.Contains(t, envVars, "ENVIRONMENT")
		assert.Contains(t, envVars, "LOG_LEVEL")
		
		// Validate tags
		listTagsInput := &lambda.ListTagsInput{
			Resource: functionConfig.Configuration.FunctionArn,
		}
		tags, err := lambdaClient.ListTags(context.TODO(), listTagsInput)
		require.NoError(t, err)
		
		assert.Contains(t, tags.Tags, "Project")
		assert.Contains(t, tags.Tags, "Environment")
		assert.Contains(t, tags.Tags, "ManagedBy")
		assert.Equal(t, "terraform", tags.Tags["ManagedBy"])
	}
}

// validateAPIGateway validates that API Gateway is created and configured correctly
func validateAPIGateway(t *testing.T, terraformOptions *terraform.Options) {
	// Get API Gateway URL from Terraform output
	apiGatewayURL := terraform.Output(t, terraformOptions, "api_gateway_url")
	assert.NotEmpty(t, apiGatewayURL, "API Gateway URL should not be empty")
	
	// Test that the URL follows expected format
	assert.Contains(t, apiGatewayURL, "execute-api")
	assert.Contains(t, apiGatewayURL, "amazonaws.com")
}

// validateDynamoDBTables validates that DynamoDB tables are created correctly
func validateDynamoDBTables(t *testing.T, cfg aws.Config, projectName, namespace string) {
	dynamoClient := dynamodb.NewFromConfig(cfg)
	
	expectedTables := []string{"products", "audit-logs"}
	
	for _, tableSuffix := range expectedTables {
		tableName := fmt.Sprintf("%s-%s-%s", projectName, namespace, tableSuffix)
		
		// Describe table
		describeInput := &dynamodb.DescribeTableInput{
			TableName: aws.String(tableName),
		}
		
		tableDescription, err := dynamoClient.DescribeTable(context.TODO(), describeInput)
		require.NoError(t, err, "Failed to describe DynamoDB table %s", tableName)
		
		// Validate table configuration
		assert.Equal(t, "ACTIVE", string(tableDescription.Table.TableStatus))
		assert.Equal(t, "PAY_PER_REQUEST", string(tableDescription.Table.BillingModeSummary.BillingMode))
		
		// Validate encryption
		assert.NotNil(t, tableDescription.Table.SSEDescription)
		assert.Equal(t, "ENABLED", string(tableDescription.Table.SSEDescription.Status))
		
		// Validate point-in-time recovery
		assert.True(t, tableDescription.Table.ContinuousBackupsDescription.PointInTimeRecoveryDescription.PointInTimeRecoveryStatus == "ENABLED")
	}
}

// validateCloudWatchResources validates CloudWatch logs and other monitoring resources
func validateCloudWatchResources(t *testing.T, cfg aws.Config, projectName, namespace string) {
	cloudwatchClient := cloudwatch.NewFromConfig(cfg)
	
	// List metrics to verify Lambda metrics are being published
	listMetricsInput := &cloudwatch.ListMetricsInput{
		Namespace: aws.String("AWS/Lambda"),
	}
	
	metrics, err := cloudwatchClient.ListMetrics(context.TODO(), listMetricsInput)
	require.NoError(t, err)
	
	// Should have some Lambda metrics
	assert.NotEmpty(t, metrics.Metrics, "Should have Lambda metrics in CloudWatch")
}

// TestTerraformValidation tests Terraform configuration validation
func TestTerraformValidation(t *testing.T) {
	t.Parallel()

	terraformOptions := &terraform.Options{
		TerraformDir: "../terraform",
		NoColor:      true,
	}

	// Run terraform validate to ensure configuration is valid
	terraform.Validate(t, terraformOptions)
}

// TestTerraformFormat tests Terraform formatting
func TestTerraformFormat(t *testing.T) {
	t.Parallel()

	terraformOptions := &terraform.Options{
		TerraformDir: "../terraform",
		NoColor:      true,
	}

	// Check if terraform files are properly formatted
	terraform.RunTerraformCommand(t, terraformOptions, "fmt", "-check=true", "-recursive")
}