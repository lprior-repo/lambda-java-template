output "product_service_lambda_function_arn" {
  description = "ARN of the Product Service Lambda function"
  value       = module.lambda_functions["product_service"].lambda_function_arn
}

output "event_processor_lambda_function_arn" {
  description = "ARN of the Event Processor Lambda function"
  value       = module.event_processor.lambda_function_arn
}

output "api_gateway_url" {
  description = "URL of the API Gateway"
  value       = "https://${aws_apigatewayv2_api.api.id}.execute-api.${local.aws_region}.amazonaws.com/${aws_apigatewayv2_stage.api.name}"
}

output "health_endpoint" {
  description = "Health endpoint URL"
  value       = "https://${aws_apigatewayv2_api.api.id}.execute-api.${local.aws_region}.amazonaws.com/${aws_apigatewayv2_stage.api.name}/health"
}

output "products_endpoint" {
  description = "Products endpoint URL"
  value       = "https://${aws_apigatewayv2_api.api.id}.execute-api.${local.aws_region}.amazonaws.com/${aws_apigatewayv2_stage.api.name}/products"
}

output "products_table_name" {
  description = "Name of the Products DynamoDB table"
  value       = aws_dynamodb_table.products.name
}

output "audit_logs_table_name" {
  description = "Name of the Audit Logs DynamoDB table"
  value       = aws_dynamodb_table.audit_logs.name
}

output "event_bus_name" {
  description = "Name of the custom EventBridge bus"
  value       = aws_cloudwatch_event_bus.app_events.name
}