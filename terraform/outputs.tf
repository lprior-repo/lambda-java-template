output "product_service_function_arn" {
  description = "ARN of Product Service function"
  value       = module.lambda_functions["product_service"].lambda_function_arn
}

output "api_gateway_url" {
  description = "URL of the API Gateway"
  value       = module.api_gateway.api_endpoint
}

output "health_endpoint" {
  description = "Health endpoint URL"
  value       = "${module.api_gateway.api_endpoint}/health"
}

output "products_endpoint" {
  description = "Products endpoint URL"
  value       = "${module.api_gateway.api_endpoint}/products"
}

output "products_table_name" {
  description = "Name of the Products DynamoDB table"
  value       = module.products_table.dynamodb_table_id
}

output "audit_logs_table_name" {
  description = "Name of the Audit Logs DynamoDB table"
  value       = module.audit_logs_table.dynamodb_table_id
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


output "lambda_artifacts_bucket_name" {
  description = "Name of the S3 bucket for Lambda deployment artifacts"
  value       = module.lambda_artifacts_bucket.s3_bucket_id
}