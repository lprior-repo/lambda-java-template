# Ephemeral Environment Configuration for Local Development
# This file demonstrates how to configure namespace isolation for developer environments

# Core project configuration
project_name = "lambda-java-template"
environment  = "ephemeral"

# Ephemeral environment settings
ephemeral_enabled    = true
auto_destroy_hours   = 24
developer_id         = "" # Will be set via CLI: -var="developer_id=alice"
branch_name          = "" # Will be set via CLI: -var="branch_name=feature/new-api"

# Optimized for development
function_memory      = 256  # Lower memory for cost savings
function_timeout     = 15   # Shorter timeout for faster feedback
log_retention_days   = 3    # Short retention for ephemeral env
billing_mode         = "PAY_PER_REQUEST"  # Cost-effective for low usage

# Enable debugging and development features
enable_xray_tracing       = true
enable_native_deployment  = false  # Use JVM mode for faster development

# Development-specific tags
additional_tags = {
  Purpose         = "ephemeral-development"
  DeveloperWorkflow = "local-development"
  AutoDestroy     = "true"
  CostOptimized   = "true"
}

# AWS region for development (can be different from production)
aws_region = "us-east-1"