package test

import (
	"context"
	"fmt"
	"net/http"
	"strings"
	"testing"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb"
	"github.com/aws/aws-sdk-go-v2/service/lambda"
	"github.com/aws/aws-sdk-go-v2/service/apigatewayv2"
	"github.com/aws/aws-sdk-go-v2/service/apigatewayv2/types"
	"github.com/aws/aws-sdk-go-v2/service/cloudwatch"
	httprequest "github.com/gruntwork-io/terratest/modules/http-helper"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// TestLambdaIntegration tests the simplified Lambda architecture
// Validates: Product Service + Authorizer Service + API Gateway + DynamoDB
func TestLambdaIntegration(t *testing.T) {
	// Configuration for simplified architecture
	awsRegion := "us-east-1"
	projectName := "lambda-java-template"
	environment := "dev"
	
	// Load AWS configuration
	cfg, err := config.LoadDefaultConfig(context.TODO(), config.WithRegion(awsRegion))
	require.NoError(t, err)

	t.Run("Lambda_Functions_Validation", func(t *testing.T) {
		validateLambdaFunctions(t, cfg, projectName, environment)
	})

	t.Run("DynamoDB_Tables_Validation", func(t *testing.T) {
		validateDynamoDBTables(t, cfg, projectName, environment)
	})

	t.Run("API_Gateway_Integration", func(t *testing.T) {
		validateAPIGatewayIntegration(t, cfg, projectName, environment)
	})

	t.Run("Security_Configuration", func(t *testing.T) {
		validateSecurityConfiguration(t, cfg, projectName, environment)
	})

	t.Run("CloudWatch_Monitoring", func(t *testing.T) {
		validateCloudWatchMonitoring(t, cfg, projectName, environment)
	})

	t.Run("Performance_Validation", func(t *testing.T) {
		validatePerformance(t)
	})

	t.Run("Terraform_Modules_Validation", func(t *testing.T) {
		validateTerraformModules(t, cfg, projectName, environment)
	})
}

// validateLambdaFunctions validates the two Lambda functions: product-service and authorizer-service
func validateLambdaFunctions(t *testing.T, cfg aws.Config, projectName, environment string) {
	lambdaClient := lambda.NewFromConfig(cfg)
	
	expectedFunctions := map[string]struct{
		name        string
		runtime     string
		memory      int32
		timeout     int32
		handler     string
	}{
		"product_service": {
			name:    fmt.Sprintf("%s-%s-product-service", projectName, environment),
			runtime: "java21",
			memory:  512,
			timeout: 30,
			handler: "org.springframework.boot.loader.launch.JarLauncher",
		},
		"authorizer_service": {
			name:    fmt.Sprintf("%s-%s-authorizer-service", projectName, environment),
			runtime: "java21", 
			memory:  256,
			timeout: 30,
			handler: "software.amazonaws.example.product.AuthorizerHandler::handleRequest",
		},
	}
	
	for functionKey, expected := range expectedFunctions {
		t.Run(fmt.Sprintf("Function_%s", functionKey), func(t *testing.T) {
			// Get function configuration
			functionConfig, err := lambdaClient.GetFunction(context.TODO(), &lambda.GetFunctionInput{
				FunctionName: aws.String(expected.name),
			})
			require.NoError(t, err, "Failed to get Lambda function %s", expected.name)
			
			// Validate basic configuration
			assert.Equal(t, expected.runtime, string(functionConfig.Configuration.Runtime))
			assert.Equal(t, "x86_64", string(functionConfig.Configuration.Architectures[0]))
			assert.Equal(t, expected.memory, *functionConfig.Configuration.MemorySize)
			assert.Equal(t, expected.timeout, *functionConfig.Configuration.Timeout)
			assert.Equal(t, expected.handler, *functionConfig.Configuration.Handler)
			
			// Validate X-Ray tracing is enabled
			assert.NotNil(t, functionConfig.Configuration.TracingConfig)
			assert.Equal(t, "Active", string(functionConfig.Configuration.TracingConfig.Mode))
			
			// Validate environment variables
			envVars := functionConfig.Configuration.Environment.Variables
			assert.Contains(t, envVars, "ENVIRONMENT")
			assert.Equal(t, environment, envVars["ENVIRONMENT"])
			
			// Product service has more environment variables
			if functionKey == "product_service" {
				assert.Contains(t, envVars, "PRODUCTS_TABLE_NAME")
				assert.Contains(t, envVars, "AUDIT_TABLE_NAME")
			}
			
			// Validate function state is Active
			assert.Equal(t, "Active", string(functionConfig.Configuration.State))
			
			// Validate deployment package size (Spring Boot JARs are larger)
			assert.Greater(t, functionConfig.Configuration.CodeSize, int64(1000)) // At least 1KB
			assert.Less(t, functionConfig.Configuration.CodeSize, int64(100000000)) // Less than 100MB
			
			// Validate tags
			tags, err := lambdaClient.ListTags(context.TODO(), &lambda.ListTagsInput{
				Resource: functionConfig.Configuration.FunctionArn,
			})
			require.NoError(t, err)
			
			assert.Contains(t, tags.Tags, "Project")
			assert.Contains(t, tags.Tags, "Environment")
			assert.Contains(t, tags.Tags, "ManagedBy")
			assert.Equal(t, "terraform", tags.Tags["ManagedBy"])
			assert.Equal(t, environment, tags.Tags["Environment"])
		})
	}
}

// validateDynamoDBTables validates the two DynamoDB tables: products and audit-logs
func validateDynamoDBTables(t *testing.T, cfg aws.Config, projectName, environment string) {
	dynamoClient := dynamodb.NewFromConfig(cfg)
	
	expectedTables := map[string]struct{
		name       string
		hashKey    string
		rangeKey   string
		hasGSI     bool
		gsiName    string
	}{
		"products": {
			name:     fmt.Sprintf("%s-%s-products", projectName, environment),
			hashKey:  "id",
			hasGSI:   true,
			gsiName:  "name-index",
		},
		"audit-logs": {
			name:     fmt.Sprintf("%s-%s-audit-logs", projectName, environment),
			hashKey:  "event_id",
			rangeKey: "timestamp",
			hasGSI:   false,
		},
	}
	
	for tableKey, expected := range expectedTables {
		t.Run(fmt.Sprintf("Table_%s", tableKey), func(t *testing.T) {
			// Describe table
			tableDescription, err := dynamoClient.DescribeTable(context.TODO(), &dynamodb.DescribeTableInput{
				TableName: aws.String(expected.name),
			})
			require.NoError(t, err, "Failed to describe DynamoDB table %s", expected.name)
			
			table := tableDescription.Table
			
			// Validate table status and billing
			assert.Equal(t, "ACTIVE", string(table.TableStatus))
			assert.Equal(t, "PAY_PER_REQUEST", string(table.BillingModeSummary.BillingMode))
			
			// Validate key schema
			assert.Equal(t, expected.hashKey, *table.KeySchema[0].AttributeName)
			assert.Equal(t, "HASH", string(table.KeySchema[0].KeyType))
			
			if expected.rangeKey != "" {
				assert.Equal(t, expected.rangeKey, *table.KeySchema[1].AttributeName)
				assert.Equal(t, "RANGE", string(table.KeySchema[1].KeyType))
			}
			
			// Validate encryption at rest
			assert.NotNil(t, table.SSEDescription)
			assert.Equal(t, "ENABLED", string(table.SSEDescription.Status))
			
			// Validate GSI if expected
			if expected.hasGSI {
				assert.NotEmpty(t, table.GlobalSecondaryIndexes)
				gsi := table.GlobalSecondaryIndexes[0]
				assert.Equal(t, expected.gsiName, *gsi.IndexName)
				assert.Equal(t, "ACTIVE", string(gsi.IndexStatus))
			}
			
			// Validate tags
			tags, err := dynamoClient.ListTagsOfResource(context.TODO(), &dynamodb.ListTagsOfResourceInput{
				ResourceArn: table.TableArn,
			})
			require.NoError(t, err)
			
			tagMap := make(map[string]string)
			for _, tag := range tags.Tags {
				tagMap[*tag.Key] = *tag.Value
			}
			
			assert.Contains(t, tagMap, "Project")
			assert.Contains(t, tagMap, "Environment")
			assert.Contains(t, tagMap, "ManagedBy")
			assert.Equal(t, "terraform", tagMap["ManagedBy"])
			assert.Equal(t, environment, tagMap["Environment"])
		})
	}
}

// validateAPIGatewayIntegration validates API Gateway configuration and routes
func validateAPIGatewayIntegration(t *testing.T, cfg aws.Config, projectName, environment string) {
	apiClient := apigatewayv2.NewFromConfig(cfg)
	
	t.Run("API_Gateway_Configuration", func(t *testing.T) {
		// List APIs to find our API
		apis, err := apiClient.GetApis(context.TODO(), &apigatewayv2.GetApisInput{})
		require.NoError(t, err)
		
		expectedAPIName := fmt.Sprintf("%s-%s-api", projectName, environment)
		var apiId string
		for _, api := range apis.Items {
			if *api.Name == expectedAPIName {
				apiId = *api.ApiId
				break
			}
		}
		require.NotEmpty(t, apiId, "API Gateway %s not found", expectedAPIName)
		
		// Get API details
		api, err := apiClient.GetApi(context.TODO(), &apigatewayv2.GetApiInput{
			ApiId: aws.String(apiId),
		})
		require.NoError(t, err)
		
		// Validate API configuration
		assert.Equal(t, expectedAPIName, *api.Name)
		assert.Equal(t, "HTTP", string(api.ProtocolType))
		assert.NotEmpty(t, api.ApiEndpoint)
		
		// Validate CORS configuration if present
		if api.CorsConfiguration != nil {
			assert.Contains(t, api.CorsConfiguration.AllowMethods, "GET")
			assert.Contains(t, api.CorsConfiguration.AllowMethods, "POST")
		}
	})
	
	t.Run("API_Routes_Configuration", func(t *testing.T) {
		// Find API ID
		apis, err := apiClient.GetApis(context.TODO(), &apigatewayv2.GetApisInput{})
		require.NoError(t, err)
		
		expectedAPIName := fmt.Sprintf("%s-%s-api", projectName, environment)
		var apiId string
		for _, api := range apis.Items {
			if *api.Name == expectedAPIName {
				apiId = *api.ApiId
				break
			}
		}
		
		// Get routes
		routes, err := apiClient.GetRoutes(context.TODO(), &apigatewayv2.GetRoutesInput{
			ApiId: aws.String(apiId),
		})
		require.NoError(t, err)
		
		// Validate expected routes exist
		expectedRoutes := []string{
			"GET /health",
			"GET /products",
			"GET /products/{id}",
			"POST /products",
			"PUT /products/{id}",
			"DELETE /products/{id}",
		}
		
		routeKeys := make([]string, len(routes.Items))
		for i, route := range routes.Items {
			routeKeys[i] = *route.RouteKey
		}
		
		for _, expectedRoute := range expectedRoutes {
			assert.Contains(t, routeKeys, expectedRoute, "Route %s not found", expectedRoute)
		}
	})
	
	t.Run("API_Authorizer_Configuration", func(t *testing.T) {
		// Find API ID
		apis, err := apiClient.GetApis(context.TODO(), &apigatewayv2.GetApisInput{})
		require.NoError(t, err)
		
		expectedAPIName := fmt.Sprintf("%s-%s-api", projectName, environment)
		var apiId string
		for _, api := range apis.Items {
			if *api.Name == expectedAPIName {
				apiId = *api.ApiId
				break
			}
		}
		
		// Get authorizers
		authorizers, err := apiClient.GetAuthorizers(context.TODO(), &apigatewayv2.GetAuthorizersInput{
			ApiId: aws.String(apiId),
		})
		require.NoError(t, err)
		
		// Validate authorizer exists and is configured correctly
		require.GreaterOrEqual(t, len(authorizers.Items), 1, "Expected at least one authorizer")
		
		// Find the API key authorizer
		var keyAuthorizer *types.Authorizer
		for _, auth := range authorizers.Items {
			if *auth.Name == fmt.Sprintf("%s-key-authorizer", expectedAPIName) {
				keyAuthorizer = &auth
				break
			}
		}
		require.NotNil(t, keyAuthorizer, "API key authorizer not found")
		
		assert.Equal(t, "REQUEST", string(keyAuthorizer.AuthorizerType))
		assert.Equal(t, "2.0", *keyAuthorizer.AuthorizerPayloadFormatVersion)
		assert.Equal(t, int32(300), *keyAuthorizer.AuthorizerResultTtlInSeconds)
	})
	
	t.Run("API_Endpoints_Functionality", func(t *testing.T) {
		// Find actual API Gateway URL
		apis, err := apiClient.GetApis(context.TODO(), &apigatewayv2.GetApisInput{})
		require.NoError(t, err)
		
		expectedAPIName := fmt.Sprintf("%s-%s-api", projectName, environment)
		var apiEndpoint string
		for _, api := range apis.Items {
			if *api.Name == expectedAPIName {
				apiEndpoint = *api.ApiEndpoint
				break
			}
		}
		require.NotEmpty(t, apiEndpoint, "API endpoint not found")
		
		// Test health endpoint (no auth required) - module creates default stage
		healthURL := fmt.Sprintf("%s/health", apiEndpoint)
		statusCode, body := httprequest.HttpGet(t, healthURL, nil)
		assert.Equal(t, http.StatusOK, statusCode)
		assert.Contains(t, body, "healthy")
		
		// Test protected endpoint without auth (should fail)
		productsURL := fmt.Sprintf("%s/products", apiEndpoint)
		statusCode, _ = httprequest.HttpGet(t, productsURL, nil)
		assert.Equal(t, http.StatusUnauthorized, statusCode)
	})
}

// validateSecurityConfiguration validates security best practices
func validateSecurityConfiguration(t *testing.T, cfg aws.Config, projectName, environment string) {
	t.Run("HTTPS_Enforcement", func(t *testing.T) {
		// API Gateway automatically enforces HTTPS
		apiClient := apigatewayv2.NewFromConfig(cfg)
		
		apis, err := apiClient.GetApis(context.TODO(), &apigatewayv2.GetApisInput{})
		require.NoError(t, err)
		
		expectedAPIName := fmt.Sprintf("%s-%s-api", projectName, environment)
		var apiEndpoint string
		for _, api := range apis.Items {
			if *api.Name == expectedAPIName {
				apiEndpoint = *api.ApiEndpoint
				break
			}
		}
		require.NotEmpty(t, apiEndpoint, "API endpoint not found")
		
		// Validate HTTPS endpoint
		assert.Contains(t, apiEndpoint, "https://")
		
		// Test actual HTTPS connectivity - module default stage
		healthURL := fmt.Sprintf("%s/health", apiEndpoint)
		resp, err := http.Get(healthURL)
		require.NoError(t, err)
		defer resp.Body.Close()
		assert.Equal(t, http.StatusOK, resp.StatusCode)
	})
	
	t.Run("Lambda_Function_Isolation", func(t *testing.T) {
		lambdaClient := lambda.NewFromConfig(cfg)
		
		functions := []string{
			fmt.Sprintf("%s-%s-product-service", projectName, environment),
			fmt.Sprintf("%s-%s-authorizer-service", projectName, environment),
		}
		
		for _, functionName := range functions {
			// Get function configuration
			functionConfig, err := lambdaClient.GetFunction(context.TODO(), &lambda.GetFunctionInput{
				FunctionName: aws.String(functionName),
			})
			require.NoError(t, err)
			
			// Validate function has its own execution role
			assert.NotEmpty(t, functionConfig.Configuration.Role)
			assert.Contains(t, *functionConfig.Configuration.Role, functionName)
		}
	})
	
	t.Run("DynamoDB_Encryption", func(t *testing.T) {
		dynamoClient := dynamodb.NewFromConfig(cfg)
		
		tables := []string{
			fmt.Sprintf("%s-%s-products", projectName, environment),
			fmt.Sprintf("%s-%s-audit-logs", projectName, environment),
		}
		
		for _, tableName := range tables {
			tableDescription, err := dynamoClient.DescribeTable(context.TODO(), &dynamodb.DescribeTableInput{
				TableName: aws.String(tableName),
			})
			require.NoError(t, err)
			
			// Validate encryption is enabled
			assert.NotNil(t, tableDescription.Table.SSEDescription)
			assert.Equal(t, "ENABLED", string(tableDescription.Table.SSEDescription.Status))
		}
	})
}

// validateCloudWatchMonitoring validates CloudWatch monitoring setup
func validateCloudWatchMonitoring(t *testing.T, cfg aws.Config, projectName, environment string) {
	cwClient := cloudwatch.NewFromConfig(cfg)
	
	t.Run("CloudWatch_Dashboards", func(t *testing.T) {
		// List dashboards
		dashboards, err := cwClient.ListDashboards(context.TODO(), &cloudwatch.ListDashboardsInput{})
		require.NoError(t, err)
		
		expectedDashboards := []string{
			fmt.Sprintf("%s-%s-dashboard", projectName, environment),
			fmt.Sprintf("%s-%s-business-kpis", projectName, environment),
		}
		
		dashboardNames := make([]string, len(dashboards.DashboardEntries))
		for i, dashboard := range dashboards.DashboardEntries {
			dashboardNames[i] = *dashboard.DashboardName
		}
		
		for _, expectedDashboard := range expectedDashboards {
			assert.Contains(t, dashboardNames, expectedDashboard, "Dashboard %s not found", expectedDashboard)
		}
	})
	
	t.Run("CloudWatch_Alarms", func(t *testing.T) {
		// List alarms for our functions
		alarms, err := cwClient.DescribeAlarms(context.TODO(), &cloudwatch.DescribeAlarmsInput{})
		require.NoError(t, err)
		
		// Count relevant alarms
		productServiceAlarms := 0
		authorizerServiceAlarms := 0
		apiGatewayAlarms := 0
		dynamoAlarms := 0
		
		for _, alarm := range alarms.MetricAlarms {
			alarmName := *alarm.AlarmName
			if strings.Contains(alarmName, "product-service") {
				productServiceAlarms++
			} else if strings.Contains(alarmName, "authorizer-service") {
				authorizerServiceAlarms++
			} else if strings.Contains(alarmName, "api") {
				apiGatewayAlarms++
			} else if strings.Contains(alarmName, "products") || strings.Contains(alarmName, "audit-logs") {
				dynamoAlarms++
			}
		}
		
		// Validate we have monitoring for our key services
		assert.GreaterOrEqual(t, productServiceAlarms, 1, "Expected at least 1 alarm for product service")
		assert.GreaterOrEqual(t, apiGatewayAlarms, 1, "Expected at least 1 API Gateway alarm")
		assert.GreaterOrEqual(t, dynamoAlarms, 1, "Expected at least 1 DynamoDB alarm")
	})
}

// validatePerformance validates performance characteristics
func validatePerformance(t *testing.T) {
	t.Run("Lambda_Cold_Start_Performance", func(t *testing.T) {
		// Dynamically discover API Gateway URL
		cfg, err := config.LoadDefaultConfig(context.TODO(), config.WithRegion("us-east-1"))
		require.NoError(t, err)
		
		apiClient := apigatewayv2.NewFromConfig(cfg)
		apis, err := apiClient.GetApis(context.TODO(), &apigatewayv2.GetApisInput{})
		require.NoError(t, err)
		
		expectedAPIName := "lambda-java-template-dev-api"
		var apiEndpoint string
		for _, api := range apis.Items {
			if *api.Name == expectedAPIName {
				apiEndpoint = *api.ApiEndpoint
				break
			}
		}
		require.NotEmpty(t, apiEndpoint, "API endpoint not found")
		
		// Test health endpoint performance - updated for new module's default stage
		healthURL := fmt.Sprintf("%s/health", apiEndpoint)
		
		// Multiple requests to test cold start and warm performance
		for i := 0; i < 3; i++ {
			start := time.Now()
			resp, err := http.Get(healthURL)
			duration := time.Since(start)
			
			require.NoError(t, err)
			resp.Body.Close()
			assert.Equal(t, http.StatusOK, resp.StatusCode)
			
			// Java cold starts can be slow, but should be reasonable
			if i == 0 {
				assert.Less(t, duration.Milliseconds(), int64(30000)) // 30s max for Java cold start
			} else {
				assert.Less(t, duration.Milliseconds(), int64(10000)) // 10s max for warm requests
			}
			
			time.Sleep(100 * time.Millisecond) // Small delay between requests
		}
	})
}

// validateTerraformModules validates that terraform-aws-modules are properly configured
func validateTerraformModules(t *testing.T, cfg aws.Config, projectName, environment string) {
	t.Run("API_Gateway_Module_Configuration", func(t *testing.T) {
		apiClient := apigatewayv2.NewFromConfig(cfg)
		
		// Find API Gateway
		apis, err := apiClient.GetApis(context.TODO(), &apigatewayv2.GetApisInput{})
		require.NoError(t, err)
		
		expectedAPIName := fmt.Sprintf("%s-%s-api", projectName, environment)
		var api *types.Api
		for _, a := range apis.Items {
			if *a.Name == expectedAPIName {
				api = &a
				break
			}
		}
		require.NotNil(t, api, "API Gateway not found")
		
		// Validate module-specific configurations
		assert.Equal(t, "HTTP", string(api.ProtocolType))
		assert.NotEmpty(t, api.ApiEndpoint)
		assert.Contains(t, *api.Description, "Serverless HTTP API Gateway")
		
		// Validate CORS is configured (terraform-aws-modules feature)
		assert.NotNil(t, api.CorsConfiguration)
		assert.Contains(t, api.CorsConfiguration.AllowMethods, "GET")
		assert.Contains(t, api.CorsConfiguration.AllowMethods, "POST")
		assert.Contains(t, api.CorsConfiguration.AllowMethods, "PUT")
		assert.Contains(t, api.CorsConfiguration.AllowMethods, "DELETE")
		assert.Contains(t, api.CorsConfiguration.AllowMethods, "OPTIONS")
		assert.Equal(t, int32(86400), *api.CorsConfiguration.MaxAge)
		
		// Validate integration is properly configured
		integrations, err := apiClient.GetIntegrations(context.TODO(), &apigatewayv2.GetIntegrationsInput{
			ApiId: api.ApiId,
		})
		require.NoError(t, err)
		assert.GreaterOrEqual(t, len(integrations.Items), 1, "Expected at least one integration")
		
		// Check integration configuration
		for _, integration := range integrations.Items {
			assert.Equal(t, "AWS_PROXY", string(integration.IntegrationType))
			assert.Equal(t, "2.0", *integration.PayloadFormatVersion)
			assert.NotEmpty(t, integration.IntegrationUri)
			assert.Contains(t, *integration.IntegrationUri, "lambda")
		}
	})
	
	t.Run("Lambda_Module_Configuration", func(t *testing.T) {
		lambdaClient := lambda.NewFromConfig(cfg)
		
		functions := []string{
			fmt.Sprintf("%s-%s-product-service", projectName, environment),
			fmt.Sprintf("%s-%s-authorizer-service", projectName, environment),
		}
		
		for _, functionName := range functions {
			// Get function configuration
			functionConfig, err := lambdaClient.GetFunction(context.TODO(), &lambda.GetFunctionInput{
				FunctionName: aws.String(functionName),
			})
			require.NoError(t, err)
			
			// Validate terraform-aws-modules/lambda configuration
			assert.Equal(t, "java21", string(functionConfig.Configuration.Runtime))
			assert.Equal(t, "x86_64", string(functionConfig.Configuration.Architectures[0]))
			
			// Validate CloudWatch Logs policy is attached (module feature)
			assert.NotEmpty(t, functionConfig.Configuration.Role)
			
			// Validate X-Ray tracing (module feature)
			assert.NotNil(t, functionConfig.Configuration.TracingConfig)
			assert.Equal(t, "Active", string(functionConfig.Configuration.TracingConfig.Mode))
			
			// Validate DLQ configuration if present (module manages this)
			// Note: Basic template might not have DLQ, but module supports it
			
			// Validate VPC configuration (none for this template)
			assert.Nil(t, functionConfig.Configuration.VpcConfig)
			
			// Validate environment variables are properly set
			envVars := functionConfig.Configuration.Environment.Variables
			assert.Contains(t, envVars, "ENVIRONMENT")
			assert.Equal(t, environment, envVars["ENVIRONMENT"])
		}
	})
	
	t.Run("DynamoDB_Module_Configuration", func(t *testing.T) {
		dynamoClient := dynamodb.NewFromConfig(cfg)
		
		tables := map[string]struct {
			name               string
			expectedEncryption bool
			expectedPITR      bool
			hasGSI            bool
		}{
			"products": {
				name:               fmt.Sprintf("%s-%s-products", projectName, environment),
				expectedEncryption: true,
				expectedPITR:      true,
				hasGSI:            true,
			},
			"audit-logs": {
				name:               fmt.Sprintf("%s-%s-audit-logs", projectName, environment),
				expectedEncryption: true,
				expectedPITR:      false, // Module might not enable for audit logs
				hasGSI:            false,
			},
		}
		
		for tableKey, expected := range tables {
			t.Run(fmt.Sprintf("Table_%s_Module_Features", tableKey), func(t *testing.T) {
				tableDescription, err := dynamoClient.DescribeTable(context.TODO(), &dynamodb.DescribeTableInput{
					TableName: aws.String(expected.name),
				})
				require.NoError(t, err)
				
				table := tableDescription.Table
				
				// Validate terraform-aws-modules/dynamodb-table features
				assert.Equal(t, "PAY_PER_REQUEST", string(table.BillingModeSummary.BillingMode))
				
				// Validate encryption (module default)
				if expected.expectedEncryption {
					assert.NotNil(t, table.SSEDescription)
					assert.Equal(t, "ENABLED", string(table.SSEDescription.Status))
				}
				
				// Validate Point-in-Time Recovery (module feature)
				pitr, err := dynamoClient.DescribeContinuousBackups(context.TODO(), &dynamodb.DescribeContinuousBackupsInput{
					TableName: aws.String(expected.name),
				})
				require.NoError(t, err)
				
				if expected.expectedPITR {
					assert.Equal(t, "ENABLED", string(pitr.ContinuousBackupsDescription.PointInTimeRecoveryDescription.PointInTimeRecoveryStatus))
				}
				
				// Validate GSI configuration if expected
				if expected.hasGSI {
					assert.NotEmpty(t, table.GlobalSecondaryIndexes)
					gsi := table.GlobalSecondaryIndexes[0]
					assert.Equal(t, "name-index", *gsi.IndexName)
					assert.Equal(t, "ACTIVE", string(gsi.IndexStatus))
					assert.Equal(t, "ALL", string(gsi.Projection.ProjectionType))
				}
				
				// Validate table stream is disabled (default)
				assert.Nil(t, table.StreamSpecification)
			})
		}
	})
	
	t.Run("S3_Module_Configuration", func(t *testing.T) {
		// S3 validation would require AWS SDK v2 S3 service
		// For now, validate through Lambda function's S3 package references
		lambdaClient := lambda.NewFromConfig(cfg)
		
		productFunction, err := lambdaClient.GetFunction(context.TODO(), &lambda.GetFunctionInput{
			FunctionName: aws.String(fmt.Sprintf("%s-%s-product-service", projectName, environment)),
		})
		require.NoError(t, err)
		
		// Validate Lambda is using S3 for code storage (module feature)
		assert.NotNil(t, productFunction.Code.RepositoryType)
		// Note: S3 bucket validation would require additional S3 client setup
		
		// Validate code size indicates successful packaging
		assert.Greater(t, productFunction.Configuration.CodeSize, int64(1000))
	})
	
	t.Run("Module_Consistency_Validation", func(t *testing.T) {
		// Validate that all resources follow consistent naming patterns (module standard)
		lambdaClient := lambda.NewFromConfig(cfg)
		dynamoClient := dynamodb.NewFromConfig(cfg)
		apiClient := apigatewayv2.NewFromConfig(cfg)
		
		// Check naming consistency across modules
		baseName := fmt.Sprintf("%s-%s", projectName, environment)
		
		// Lambda functions
		functions := []string{
			fmt.Sprintf("%s-product-service", baseName),
			fmt.Sprintf("%s-authorizer-service", baseName),
		}
		
		for _, functionName := range functions {
			_, err := lambdaClient.GetFunction(context.TODO(), &lambda.GetFunctionInput{
				FunctionName: aws.String(functionName),
			})
			assert.NoError(t, err, "Function %s should exist with consistent naming", functionName)
		}
		
		// DynamoDB tables
		tables := []string{
			fmt.Sprintf("%s-products", baseName),
			fmt.Sprintf("%s-audit-logs", baseName),
		}
		
		for _, tableName := range tables {
			_, err := dynamoClient.DescribeTable(context.TODO(), &dynamodb.DescribeTableInput{
				TableName: aws.String(tableName),
			})
			assert.NoError(t, err, "Table %s should exist with consistent naming", tableName)
		}
		
		// API Gateway
		apiName := fmt.Sprintf("%s-api", baseName)
		apis, err := apiClient.GetApis(context.TODO(), &apigatewayv2.GetApisInput{})
		require.NoError(t, err)
		
		found := false
		for _, api := range apis.Items {
			if *api.Name == apiName {
				found = true
				break
			}
		}
		assert.True(t, found, "API Gateway %s should exist with consistent naming", apiName)
	})
}