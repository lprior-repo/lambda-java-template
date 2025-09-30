locals {
  # Configuration
  aws_region       = var.aws_region
  project_name     = var.project_name
  environment      = var.is_ephemeral ? "ephemeral" : var.environment
  namespace        = var.namespace
  is_ephemeral_env = var.is_ephemeral

  # Computed values
  actual_namespace   = local.namespace != "" ? local.namespace : local.environment
  function_base_name = "${local.project_name}-${local.actual_namespace}"

  # Environment-specific configuration
  lambda_memory  = var.function_memory
  lambda_timeout = var.function_timeout
  xray_tracing   = var.enable_xray_tracing
  log_retention  = var.log_retention_days

  # DynamoDB configuration based on environment
  dynamodb_billing_mode   = var.billing_mode
  dynamodb_read_capacity  = var.billing_mode == "PROVISIONED" ? var.read_capacity : null
  dynamodb_write_capacity = var.billing_mode == "PROVISIONED" ? var.write_capacity : null

  # API Gateway configuration
  api_gateway_name = "${local.function_base_name}-api"

  # DynamoDB table configurations for monitoring
  table_configurations = {
    products = {
      name = "${local.function_base_name}-products"
    }
    audit_logs = {
      name = "${local.function_base_name}-audit-logs"
    }
  }

  # Lambda functions configuration
  lambda_functions = {
    product_service = {
      name       = "${local.function_base_name}-product-service"
      source_dir = "../build/product-service.jar"
      runtime    = "java21"
      handler    = "org.springframework.boot.loader.launch.JarLauncher"
      routes = [
        { path = "/health", method = "GET", auth = false },
        { path = "/products", method = "GET", auth = true },
        { path = "/products", method = "POST", auth = true },
        { path = "/products/{id}", method = "GET", auth = true },
        { path = "/products/{id}", method = "PUT", auth = true },
        { path = "/products/{id}", method = "DELETE", auth = true }
      ]
    }
    authorizer_service = {
      name       = "${local.function_base_name}-authorizer-service"
      source_dir = "../build/authorizer-service.jar"
      runtime    = "java21"
      handler    = "software.amazonaws.example.product.AuthorizerHandler::handleRequest"
      routes     = []
    }
  }

  # Common tags
  common_tags = merge({
    Project     = local.project_name
    Environment = local.environment
    Namespace   = local.actual_namespace
    ManagedBy   = "terraform"
    Ephemeral   = local.is_ephemeral_env ? "true" : "false"
  }, var.additional_tags)
}