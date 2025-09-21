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

  # Lambda functions configuration for GraalVM Java product service
  lambda_functions = {
    lambda1 = {
      name       = "${local.function_base_name}-lambda1"
      source_dir = "../product-service/target/product-service.zip"
      runtime    = "provided.al2"
      handler    = "not-used-for-native"
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
      source_dir = "../product-service/target/authorizer.zip"
      runtime    = "provided.al2"
      handler    = "not-used-for-native"
      routes     = []
    }
    lambda3 = {
      name       = "${local.function_base_name}-lambda3"
      source_dir = "../product-service/target/event-processor.zip"
      runtime    = "provided.al2"
      handler    = "not-used-for-native"
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