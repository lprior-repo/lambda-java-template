output "lambda1_function_arn" {
  description = "ARN of Lambda1 function (Product Service)"
  value       = module.lambda_functions["lambda1"].lambda_function_arn
}

output "lambda3_function_arn" {
  description = "ARN of Lambda3 function (Event Processor)"
  value       = module.lambda3.lambda_function_arn
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

# CloudWatch Monitoring Outputs
output "lambda_dashboard_url" {
  description = "URL to the Lambda CloudWatch Dashboard"
  value       = "https://${local.aws_region}.console.aws.amazon.com/cloudwatch/home?region=${local.aws_region}#dashboards:name=${aws_cloudwatch_dashboard.lambda_dashboard.dashboard_name}"
}

output "business_kpis_dashboard_url" {
  description = "URL to the Business KPIs CloudWatch Dashboard"
  value       = "https://${local.aws_region}.console.aws.amazon.com/cloudwatch/home?region=${local.aws_region}#dashboards:name=${aws_cloudwatch_dashboard.business_kpis.dashboard_name}"
}

output "sns_alerts_topic_arn" {
  description = "ARN of the SNS topic for CloudWatch alarms"
  value       = aws_sns_topic.alerts.arn
}

# Step Functions outputs
output "step_functions_arn" {
  description = "ARN of the Step Functions state machine"
  value       = aws_sfn_state_machine.order_processing.arn
}

output "step_functions_name" {
  description = "Name of the Step Functions state machine"
  value       = aws_sfn_state_machine.order_processing.name
}

output "order_processing_endpoint" {
  description = "API Gateway endpoint for order processing workflow"
  value       = "${aws_apigatewayv2_api.api.api_endpoint}/orders"
}

output "lambda_artifacts_bucket_name" {
  description = "Name of the S3 bucket for Lambda deployment artifacts"
  value       = aws_s3_bucket.lambda_artifacts.bucket
}