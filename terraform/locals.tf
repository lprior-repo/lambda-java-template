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

  # Lambda functions configuration for Java template
  lambda_functions = {
    health = {
      name       = "${local.function_base_name}-health"
      source_dir = "../build/health.zip"
      runtime    = "java21"
      handler    = "com.example.Handler::handleRequest"
      routes     = [{ path = "/health", method = "GET", auth = false }]
    }
    users = {
      name       = "${local.function_base_name}-users"
      source_dir = "../build/users.zip"
      runtime    = "java21"
      handler    = "com.example.Handler::handleRequest"
      routes     = [{ path = "/users", method = "ANY", auth = true }]
    }
    posts = {
      name       = "${local.function_base_name}-posts"
      source_dir = "../build/posts.zip"
      runtime    = "java21"
      handler    = "com.example.Handler::handleRequest"
      routes     = [{ path = "/posts", method = "ANY", auth = true }]
    }
    authorizer = {
      name       = "${local.function_base_name}-authorizer"
      source_dir = "../build/authorizer.zip"
      runtime    = "java21"
      handler    = "com.example.Handler::handleRequest"
      routes     = []
    }
    event_processor = {
      name       = "${local.function_base_name}-event-processor"
      source_dir = "../build/event-processor.zip"
      runtime    = "java21"
      handler    = "com.example.Handler::handleRequest"
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