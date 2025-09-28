# CloudWatch Dashboards and Alarms for Production Monitoring

# CloudWatch Dashboard for Lambda Functions
resource "aws_cloudwatch_dashboard" "lambda_dashboard" {
  dashboard_name = "${local.function_base_name}-dashboard"

  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "metric"
        x      = 0
        y      = 0
        width  = 12
        height = 6

        properties = {
          metrics = [
            for function_key, function_config in local.lambda_functions : [
              "AWS/Lambda",
              "Duration",
              "FunctionName",
              function_config.name
            ]
          ]
          view    = "timeSeries"
          stacked = false
          region  = var.aws_region
          title   = "Lambda Function Duration"
          period  = 300
          stat    = "Average"
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 0
        width  = 12
        height = 6

        properties = {
          metrics = [
            for function_key, function_config in local.lambda_functions : [
              "AWS/Lambda",
              "Errors",
              "FunctionName",
              function_config.name
            ]
          ]
          view    = "timeSeries"
          stacked = false
          region  = var.aws_region
          title   = "Lambda Function Errors"
          period  = 300
          stat    = "Sum"
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 6
        width  = 12
        height = 6

        properties = {
          metrics = [
            for function_key, function_config in local.lambda_functions : [
              "AWS/Lambda",
              "Invocations",
              "FunctionName",
              function_config.name
            ]
          ]
          view    = "timeSeries"
          stacked = false
          region  = var.aws_region
          title   = "Lambda Function Invocations"
          period  = 300
          stat    = "Sum"
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 6
        width  = 12
        height = 6

        properties = {
          metrics = [
            for function_key, function_config in local.lambda_functions : [
              "AWS/Lambda",
              "Throttles",
              "FunctionName",
              function_config.name
            ]
          ]
          view    = "timeSeries"
          stacked = false
          region  = var.aws_region
          title   = "Lambda Function Throttles"
          period  = 300
          stat    = "Sum"
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 12
        width  = 12
        height = 6

        properties = {
          metrics = [
            for function_key, function_config in local.lambda_functions : [
              "AWS/Lambda",
              "ConcurrentExecutions",
              "FunctionName",
              function_config.name
            ]
          ]
          view    = "timeSeries"
          stacked = false
          region  = var.aws_region
          title   = "Lambda Concurrent Executions"
          period  = 300
          stat    = "Maximum"
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 12
        width  = 12
        height = 6

        properties = {
          metrics = [
            ["AWS/DynamoDB", "ConsumedReadCapacityUnits", "TableName", "${local.function_base_name}-products"],
            [".", "ConsumedWriteCapacityUnits", ".", "."],
            [".", "ConsumedReadCapacityUnits", "TableName", "${local.function_base_name}-audit-logs"],
            [".", "ConsumedWriteCapacityUnits", ".", "."]
          ]
          view    = "timeSeries"
          stacked = false
          region  = var.aws_region
          title   = "DynamoDB Capacity Consumption"
          period  = 300
          stat    = "Sum"
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 18
        width  = 24
        height = 6

        properties = {
          metrics = [
            ["AWS/ApiGateway", "Count", "ApiName", local.api_gateway_name],
            [".", "Latency", ".", "."],
            [".", "4XXError", ".", "."],
            [".", "5XXError", ".", "."]
          ]
          view    = "timeSeries"
          stacked = false
          region  = var.aws_region
          title   = "API Gateway Metrics"
          period  = 300
          stat    = "Sum"
        }
      }
    ]
  })
}

# CloudWatch Alarms for Lambda Functions
resource "aws_cloudwatch_metric_alarm" "lambda_error_rate" {
  for_each = local.lambda_functions

  alarm_name          = "${each.value.name}-error-rate"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "Errors"
  namespace           = "AWS/Lambda"
  period              = "300"
  statistic           = "Sum"
  threshold           = "5"
  alarm_description   = "This metric monitors lambda error rate for ${each.value.name}"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]

  dimensions = {
    FunctionName = each.value.name
  }

  tags = local.common_tags
}

resource "aws_cloudwatch_metric_alarm" "lambda_duration" {
  for_each = local.lambda_functions

  alarm_name          = "${each.value.name}-duration"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "Duration"
  namespace           = "AWS/Lambda"
  period              = "300"
  statistic           = "Average"
  threshold           = "25000" # 25 seconds (function timeout is 30s)
  alarm_description   = "This metric monitors lambda duration for ${each.value.name}"
  alarm_actions       = [aws_sns_topic.alerts.arn]

  dimensions = {
    FunctionName = each.value.name
  }

  tags = local.common_tags
}

resource "aws_cloudwatch_metric_alarm" "lambda_throttles" {
  for_each = local.lambda_functions

  alarm_name          = "${each.value.name}-throttles"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "1"
  metric_name         = "Throttles"
  namespace           = "AWS/Lambda"
  period              = "300"
  statistic           = "Sum"
  threshold           = "0"
  alarm_description   = "This metric monitors lambda throttles for ${each.value.name}"
  alarm_actions       = [aws_sns_topic.alerts.arn]

  dimensions = {
    FunctionName = each.value.name
  }

  tags = local.common_tags
}

# CloudWatch Alarms for DynamoDB
resource "aws_cloudwatch_metric_alarm" "dynamodb_read_throttles" {
  for_each = local.table_configurations

  alarm_name          = "${each.value.name}-read-throttles"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "1"
  metric_name         = "ReadThrottles"
  namespace           = "AWS/DynamoDB"
  period              = "300"
  statistic           = "Sum"
  threshold           = "0"
  alarm_description   = "This metric monitors DynamoDB read throttles for ${each.value.name}"
  alarm_actions       = [aws_sns_topic.alerts.arn]

  dimensions = {
    TableName = each.value.name
  }

  tags = local.common_tags
}

resource "aws_cloudwatch_metric_alarm" "dynamodb_write_throttles" {
  for_each = local.table_configurations

  alarm_name          = "${each.value.name}-write-throttles"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "1"
  metric_name         = "WriteThrottles"
  namespace           = "AWS/DynamoDB"
  period              = "300"
  statistic           = "Sum"
  threshold           = "0"
  alarm_description   = "This metric monitors DynamoDB write throttles for ${each.value.name}"
  alarm_actions       = [aws_sns_topic.alerts.arn]

  dimensions = {
    TableName = each.value.name
  }

  tags = local.common_tags
}

# CloudWatch Alarms for API Gateway
resource "aws_cloudwatch_metric_alarm" "api_gateway_4xx_errors" {
  alarm_name          = "${local.api_gateway_name}-4xx-errors"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "4XXError"
  namespace           = "AWS/ApiGateway"
  period              = "300"
  statistic           = "Sum"
  threshold           = "10"
  alarm_description   = "This metric monitors API Gateway 4XX errors"
  alarm_actions       = [aws_sns_topic.alerts.arn]

  dimensions = {
    ApiName = local.api_gateway_name
  }

  tags = local.common_tags
}

resource "aws_cloudwatch_metric_alarm" "api_gateway_5xx_errors" {
  alarm_name          = "${local.api_gateway_name}-5xx-errors"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "1"
  metric_name         = "5XXError"
  namespace           = "AWS/ApiGateway"
  period              = "300"
  statistic           = "Sum"
  threshold           = "2"
  alarm_description   = "This metric monitors API Gateway 5XX errors"
  alarm_actions       = [aws_sns_topic.alerts.arn]

  dimensions = {
    ApiName = local.api_gateway_name
  }

  tags = local.common_tags
}

resource "aws_cloudwatch_metric_alarm" "api_gateway_latency" {
  alarm_name          = "${local.api_gateway_name}-latency"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "Latency"
  namespace           = "AWS/ApiGateway"
  period              = "300"
  statistic           = "Average"
  threshold           = "5000" # 5 seconds
  alarm_description   = "This metric monitors API Gateway latency"
  alarm_actions       = [aws_sns_topic.alerts.arn]

  dimensions = {
    ApiName = local.api_gateway_name
  }

  tags = local.common_tags
}

# SNS Topic for CloudWatch Alarms
resource "aws_sns_topic" "alerts" {
  name = "${local.function_base_name}-alerts"

  tags = local.common_tags
}

# CloudWatch Log Groups with proper retention
resource "aws_cloudwatch_log_group" "lambda_logs" {
  for_each = local.lambda_functions

  name              = "/aws/lambda/${each.value.name}"
  retention_in_days = var.log_retention_days

  tags = local.common_tags
}

# Custom CloudWatch Metrics from Application Logs
resource "aws_cloudwatch_log_metric_filter" "business_metrics" {
  name           = "${local.function_base_name}-business-metrics"
  log_group_name = aws_cloudwatch_log_group.lambda_logs["lambda1"].name
  pattern        = "[timestamp, request_id, level=\"INFO\", message, fields]"

  metric_transformation {
    name      = "ProductOperations"
    namespace = "ProductService/Business"
    value     = "1"
    
    default_value = "0"
  }
}

resource "aws_cloudwatch_log_metric_filter" "error_metrics" {
  for_each = local.lambda_functions

  name           = "${each.value.name}-errors"
  log_group_name = aws_cloudwatch_log_group.lambda_logs[each.key].name
  pattern        = "[timestamp, request_id, level=\"ERROR\", ...]"

  metric_transformation {
    name      = "ApplicationErrors"
    namespace = "ProductService/Errors"
    value     = "1"
    
    default_value = "0"
  }
}

# Business KPI Dashboard
resource "aws_cloudwatch_dashboard" "business_kpis" {
  dashboard_name = "${local.function_base_name}-business-kpis"

  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "metric"
        x      = 0
        y      = 0
        width  = 12
        height = 6

        properties = {
          metrics = [
            ["ProductService/Business", "ProductOperations"],
            ["ProductService/Errors", "ApplicationErrors"]
          ]
          view    = "timeSeries"
          stacked = false
          region  = var.aws_region
          title   = "Business Operations & Errors"
          period  = 300
          stat    = "Sum"
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 0
        width  = 12
        height = 6

        properties = {
          metrics = [
            for function_key, function_config in local.lambda_functions : [
              "AWS/Lambda",
              "Duration",
              "FunctionName",
              function_config.name
            ]
          ]
          view    = "singleValue"
          region  = var.aws_region
          title   = "Average Response Time"
          period  = 300
          stat    = "Average"
        }
      }
    ]
  })
}

# Cost monitoring alarm
resource "aws_cloudwatch_metric_alarm" "monthly_cost" {
  count = var.environment == "prod" ? 1 : 0

  alarm_name          = "${local.function_base_name}-monthly-cost"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "1"
  metric_name         = "EstimatedCharges"
  namespace           = "AWS/Billing"
  period              = "86400" # 24 hours
  statistic           = "Maximum"
  threshold           = "100" # $100 USD
  alarm_description   = "This metric monitors estimated monthly charges"
  alarm_actions       = [aws_sns_topic.alerts.arn]

  dimensions = {
    Currency = "USD"
  }

  tags = local.common_tags
}