# Development Environment Configuration
aws_region    = "us-east-1"
environment   = "dev"
project_name  = "lambda-java-template"
namespace     = ""
is_ephemeral  = false

# Development-specific settings
function_memory = 512
function_timeout = 30

# Development Lambda configuration
enable_xray_tracing = true
log_retention_days = 7

# DynamoDB configuration for dev
billing_mode = "PAY_PER_REQUEST"

# Tags for development environment
additional_tags = {
  CostCenter = "development"
  Owner      = "dev-team"
}