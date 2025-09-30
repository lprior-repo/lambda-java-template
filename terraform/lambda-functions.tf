# Lambda function modules for each endpoint
module "lambda_functions" {
  source   = "terraform-aws-modules/lambda/aws"
  version  = "~> 8.1"
  for_each = { for k, v in local.lambda_functions : k => v if length(v.routes) > 0 }

  function_name = each.value.name
  description   = "Serverless function for ${each.key} endpoint"
  handler       = each.value.handler
  runtime       = each.value.runtime
  architectures = ["x86_64"]

  # Skip handler for native runtime (provided.al2)
  skip_destroy = false

  create_package = false
  s3_existing_package = {
    bucket = module.lambda_artifacts_bucket.s3_bucket_id
    key    = "${each.key}/${basename(each.value.source_dir)}"
  }

  timeout     = local.lambda_timeout
  memory_size = local.lambda_memory

  environment_variables = {
    ENVIRONMENT                      = local.environment
    LOG_LEVEL                        = "INFO"
    PRODUCTS_TABLE_NAME              = module.products_table.dynamodb_table_id
    AUDIT_TABLE_NAME                 = module.audit_logs_table.dynamodb_table_id
    SPRING_CLOUD_FUNCTION_DEFINITION = "springBootProductHandler"
    MAIN_CLASS                       = "software.amazonaws.example.product.ProductApplication"
  }

  # CloudWatch Logs
  attach_cloudwatch_logs_policy     = true
  cloudwatch_logs_retention_in_days = local.log_retention

  # X-Ray tracing
  tracing_mode          = local.xray_tracing ? "Active" : "PassThrough"
  attach_tracing_policy = local.xray_tracing

  # DynamoDB permissions
  attach_policy_statements = true
  policy_statements = {
    dynamodb = {
      effect = "Allow"
      actions = [
        "dynamodb:GetItem",
        "dynamodb:PutItem",
        "dynamodb:UpdateItem",
        "dynamodb:DeleteItem",
        "dynamodb:Query",
        "dynamodb:Scan"
      ]
      resources = [
        module.products_table.dynamodb_table_arn,
        "${module.products_table.dynamodb_table_arn}/*",
        module.audit_logs_table.dynamodb_table_arn,
        "${module.audit_logs_table.dynamodb_table_arn}/*"
      ]
    }
  }

  tags = local.common_tags
}

# Authorizer Lambda (separate module since it doesn't need routes)
module "lambda_authorizer" {
  source  = "terraform-aws-modules/lambda/aws"
  version = "~> 8.1"

  function_name = local.lambda_functions.authorizer_service.name
  description   = "API Key authorizer for API Gateway"
  handler       = local.lambda_functions.authorizer_service.handler
  runtime       = local.lambda_functions.authorizer_service.runtime
  architectures = ["x86_64"]

  # Skip handler for native runtime (provided.al2)
  skip_destroy = false

  create_package = false
  s3_existing_package = {
    bucket = module.lambda_artifacts_bucket.s3_bucket_id
    key    = "authorizer_service/${basename(local.lambda_functions.authorizer_service.source_dir)}"
  }

  timeout     = local.lambda_timeout
  memory_size = 256 # Authorizer can use less memory

  environment_variables = {
    ENVIRONMENT = local.environment
    LOG_LEVEL   = "INFO"
  }

  # CloudWatch Logs
  attach_cloudwatch_logs_policy     = true
  cloudwatch_logs_retention_in_days = local.log_retention

  # X-Ray tracing
  tracing_mode          = local.xray_tracing ? "Active" : "PassThrough"
  attach_tracing_policy = local.xray_tracing

  tags = local.common_tags
}


# Lambda permissions for API Gateway
resource "aws_lambda_permission" "api_gateway_lambda" {
  for_each = { for k, v in local.lambda_functions : k => v if length(v.routes) > 0 }

  statement_id  = "AllowExecutionFromAPIGateway-${each.key}"
  action        = "lambda:InvokeFunction"
  function_name = module.lambda_functions[each.key].lambda_function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${module.api_gateway.api_execution_arn}/*/*"
}

# Lambda permission for authorizer
resource "aws_lambda_permission" "authorizer" {
  statement_id  = "AllowExecutionFromAPIGateway-authorizer"
  action        = "lambda:InvokeFunction"
  function_name = module.lambda_authorizer.lambda_function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${module.api_gateway.api_execution_arn}/*/*"
}