# Staging Environment Configuration
aws_region   = "us-east-1"
environment  = "staging"
project_name = "lambda-java-template"
namespace    = ""
is_ephemeral = false

# Staging-specific settings (more production-like)
function_memory  = 1024
function_timeout = 30

# Staging Lambda configuration
enable_xray_tracing = true
log_retention_days  = 14

# DynamoDB configuration for staging
billing_mode = "PAY_PER_REQUEST"

# Tags for staging environment
additional_tags = {
  CostCenter = "staging"
  Owner      = "qa-team"
}