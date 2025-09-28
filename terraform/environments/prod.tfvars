# Production Environment Configuration
aws_region   = "us-east-1"
environment  = "prod"
project_name = "lambda-java-template"
namespace    = ""
is_ephemeral = false

# Production-specific settings (optimized for performance and reliability)
function_memory  = 1024
function_timeout = 30

# Production Lambda configuration
enable_xray_tracing = true
log_retention_days  = 30

# DynamoDB configuration for production
billing_mode   = "PROVISIONED"
read_capacity  = 5
write_capacity = 5

# Tags for production environment
additional_tags = {
  CostCenter = "production"
  Owner      = "platform-team"
  Backup     = "required"
}