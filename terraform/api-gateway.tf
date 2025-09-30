# API Gateway v2 (HTTP API) using terraform-aws-modules
module "api_gateway" {
  source  = "terraform-aws-modules/apigateway-v2/aws"
  version = "~> 5.0"

  name         = "${local.function_base_name}-api"
  description  = "Serverless HTTP API Gateway for ${var.project_name}"
  protocol_type = "HTTP"

  # CORS Configuration
  cors_configuration = {
    allow_credentials = false
    allow_headers     = ["authorization", "content-type", "x-amz-date", "x-amz-security-token", "x-amz-user-agent", "x-api-key", "x-request-id"]
    allow_methods     = ["DELETE", "GET", "OPTIONS", "POST", "PUT"]
    allow_origins     = ["*"]
    expose_headers    = ["x-request-id", "x-service", "x-version"]
    max_age           = 86400
  }

  # Routes (combines integrations and routes in v5.0)
  routes = {
    for route in flatten([
      for func_key, func_config in local.lambda_functions : [
        for route in func_config.routes : {
          key           = "${route.method} ${route.path}"
          func_key      = func_key
          method        = route.method
          path          = route.path
          auth          = route.auth
        }
      ]
    ]) : route.key => {
      integration = {
        uri                    = module.lambda_functions[route.func_key].lambda_function_invoke_arn
        payload_format_version = "2.0"
        timeout_milliseconds   = 30000
      }
      authorization_type = route.auth ? "CUSTOM" : "NONE"
      authorizer_key     = route.auth ? "api_key" : null
    }
  }

  # Authorizer
  authorizers = {
    api_key = {
      authorizer_type                   = "REQUEST"
      authorizer_uri                    = module.lambda_authorizer.lambda_function_invoke_arn
      identity_sources                  = ["$request.header.x-api-key"]
      name                              = "${local.function_base_name}-key-authorizer"
      authorizer_payload_format_version = "2.0"
      authorizer_result_ttl_in_seconds  = 300
      enable_simple_responses           = true
    }
  }

  tags = local.common_tags
}

