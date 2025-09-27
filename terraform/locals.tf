locals {
  # Configuration
  aws_region            = var.aws_region
  project_name          = var.project_name
  environment           = var.is_ephemeral ? "ephemeral" : "dev"
  namespace             = var.namespace
  ephemeral_environment = var.is_ephemeral

  # Computed values
  actual_namespace   = local.namespace != "" ? local.namespace : local.environment
  function_base_name = "${local.project_name}-${local.actual_namespace}"

  # Lambda functions configuration for Java product service with deployment packages
  lambda_functions = {
    lambda1 = {
      name       = "${local.function_base_name}-lambda1"
      source_dir = "../build/product-service.zip"
      runtime    = "java21"
      handler    = "software.amazonaws.example.product.ProductHandler::handleRequest"
      routes = [
        { path = "/health", method = "GET", auth = false },
        { path = "/products", method = "GET", auth = true },
        { path = "/products", method = "POST", auth = true },
        { path = "/products/{id}", method = "GET", auth = true },
        { path = "/products/{id}", method = "PUT", auth = true },
        { path = "/products/{id}", method = "DELETE", auth = true }
      ]
    }
    lambda2 = {
      name       = "${local.function_base_name}-lambda2"
      source_dir = "../build/authorizer-service.zip"
      runtime    = "java21"
      handler    = "software.amazonaws.example.product.AuthorizerHandler::handleRequest"
      routes     = []
    }
    lambda3 = {
      name       = "${local.function_base_name}-lambda3"
      source_dir = "../build/event-processor-service.zip"
      runtime    = "java21"
      handler    = "software.amazonaws.example.product.EventProcessorHandler::handleRequest"
      routes     = []
    }
  }

  # Common tags
  common_tags = {
    Project     = local.project_name
    Environment = local.environment
    Namespace   = local.actual_namespace
    ManagedBy   = "terraform"
    Ephemeral   = local.ephemeral_environment ? "true" : "false"
  }
}