# Step Functions State Machine for Order Processing Workflow

# IAM role for Step Functions execution
resource "aws_iam_role" "step_functions_role" {
  name = "${local.function_base_name}-step-functions-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "states.amazonaws.com"
        }
      }
    ]
  })

  tags = local.common_tags
}

# IAM policy for Step Functions to invoke Lambda functions
resource "aws_iam_role_policy" "step_functions_lambda_policy" {
  name = "${local.function_base_name}-step-functions-lambda-policy"
  role = aws_iam_role.step_functions_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "lambda:InvokeFunction"
        ]
        Resource = [
          module.order_validation_lambda.lambda_function_arn,
          module.payment_lambda.lambda_function_arn,
          module.inventory_lambda.lambda_function_arn,
          module.notification_lambda.lambda_function_arn
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents",
          "logs:CreateLogDelivery",
          "logs:GetLogDelivery",
          "logs:UpdateLogDelivery",
          "logs:DeleteLogDelivery",
          "logs:ListLogDeliveries",
          "logs:PutResourcePolicy",
          "logs:DescribeResourcePolicies",
          "logs:DescribeLogGroups"
        ]
        Resource = "*"
      }
    ]
  })
}

# Step Functions State Machine
resource "aws_sfn_state_machine" "order_processing" {
  name     = "${local.function_base_name}-order-processing"
  role_arn = aws_iam_role.step_functions_role.arn

  definition = jsonencode({
    Comment = "Order Processing Workflow with parallel execution and error handling"
    StartAt = "ValidateOrder"
    States = {
      ValidateOrder = {
        Type     = "Task"
        Resource = module.order_validation_lambda.lambda_function_arn
        Next     = "CheckValidation"
        Retry = [
          {
            ErrorEquals     = ["Lambda.ServiceException", "Lambda.AWSLambdaException", "Lambda.SdkClientException"]
            IntervalSeconds = 2
            MaxAttempts     = 3
            BackoffRate     = 2.0
          }
        ]
        Catch = [
          {
            ErrorEquals = ["States.ALL"]
            Next        = "ValidationFailed"
            ResultPath  = "$.error"
          }
        ]
      }

      CheckValidation = {
        Type = "Choice"
        Choices = [
          {
            Variable      = "$.isValid"
            BooleanEquals = true
            Next          = "ParallelProcessing"
          }
        ]
        Default = "ValidationFailed"
      }

      ParallelProcessing = {
        Type = "Parallel"
        Branches = [
          {
            StartAt = "CheckInventory"
            States = {
              CheckInventory = {
                Type     = "Task"
                Resource = module.inventory_lambda.lambda_function_arn
                End      = true
                Retry = [
                  {
                    ErrorEquals     = ["Lambda.ServiceException", "Lambda.AWSLambdaException", "Lambda.SdkClientException"]
                    IntervalSeconds = 1
                    MaxAttempts     = 3
                    BackoffRate     = 2.0
                  }
                ]
                Catch = [
                  {
                    ErrorEquals = ["States.ALL"]
                    ResultPath  = "$.inventoryError"
                    Next        = "InventoryFailed"
                  }
                ]
              }
              InventoryFailed = {
                Type = "Pass"
                Result = {
                  availabilityStatus = "ERROR"
                  error             = "Inventory check failed"
                }
                End = true
              }
            }
          },
          {
            StartAt = "ProcessPayment"
            States = {
              ProcessPayment = {
                Type     = "Task"
                Resource = module.payment_lambda.lambda_function_arn
                End      = true
                Retry = [
                  {
                    ErrorEquals     = ["Lambda.ServiceException", "Lambda.AWSLambdaException", "Lambda.SdkClientException"]
                    IntervalSeconds = 1
                    MaxAttempts     = 2
                    BackoffRate     = 2.0
                  }
                ]
                Catch = [
                  {
                    ErrorEquals = ["States.ALL"]
                    ResultPath  = "$.paymentError"
                    Next        = "PaymentFailed"
                  }
                ]
              }
              PaymentFailed = {
                Type = "Pass"
                Result = {
                  paymentStatus = "FAILED"
                  error        = "Payment processing failed"
                }
                End = true
              }
            }
          }
        ]
        Next = "EvaluateResults"
        Catch = [
          {
            ErrorEquals = ["States.ALL"]
            Next        = "ProcessingFailed"
            ResultPath  = "$.parallelError"
          }
        ]
      }

      EvaluateResults = {
        Type = "Choice"
        Choices = [
          {
            And = [
              {
                Variable      = "$[0].availabilityStatus"
                StringEquals  = "AVAILABLE"
              },
              {
                Variable      = "$[1].paymentStatus"
                StringEquals  = "APPROVED"
              }
            ]
            Next = "OrderSuccess"
          },
          {
            Variable      = "$[0].availabilityStatus"
            StringEquals  = "OUT_OF_STOCK"
            Next          = "InventoryUnavailable"
          },
          {
            Variable      = "$[1].paymentStatus"
            StringEquals  = "DECLINED"
            Next          = "PaymentDeclined"
          }
        ]
        Default = "ProcessingFailed"
      }

      OrderSuccess = {
        Type = "Task"
        Resource = module.notification_lambda.lambda_function_arn
        Parameters = {
          "orderId.$"        = "$[0].orderId"
          "customerId.$"     = "$[0].customerId"
          "totalAmount.$"    = "$[0].totalAmount"
          "transactionId.$"  = "$[1].transactionId"
          "reservationId.$"  = "$[0].reservationId"
          "notificationType" = "ORDER_CONFIRMATION"
          "message"         = "Your order has been confirmed and is being processed."
        }
        End = true
        Retry = [
          {
            ErrorEquals     = ["Lambda.ServiceException", "Lambda.AWSLambdaException", "Lambda.SdkClientException"]
            IntervalSeconds = 1
            MaxAttempts     = 2
            BackoffRate     = 2.0
          }
        ]
      }

      InventoryUnavailable = {
        Type = "Task"
        Resource = module.notification_lambda.lambda_function_arn
        Parameters = {
          "orderId.$"        = "$[0].orderId"
          "customerId.$"     = "$[0].customerId"
          "notificationType" = "INVENTORY_UNAVAILABLE"
          "message"         = "Unfortunately, some items in your order are currently out of stock."
          "unavailabilityReason.$" = "$[0].unavailabilityReason"
        }
        End = true
      }

      PaymentDeclined = {
        Type = "Task"
        Resource = module.notification_lambda.lambda_function_arn
        Parameters = {
          "orderId.$"        = "$[0].orderId"
          "customerId.$"     = "$[0].customerId"
          "notificationType" = "PAYMENT_FAILED"
          "message"         = "Your payment could not be processed. Please try a different payment method."
          "paymentError.$"   = "$[1].paymentError"
        }
        End = true
      }

      ValidationFailed = {
        Type = "Task"
        Resource = module.notification_lambda.lambda_function_arn
        Parameters = {
          "orderId.$"        = "$.orderId"
          "customerId.$"     = "$.customerId"
          "notificationType" = "ORDER_FAILED"
          "message"         = "Your order could not be processed due to validation errors."
          "validationErrors.$" = "$.validationErrors"
        }
        End = true
      }

      ProcessingFailed = {
        Type = "Task"
        Resource = module.notification_lambda.lambda_function_arn
        Parameters = {
          "orderId.$"        = "$.orderId"
          "customerId.$"     = "$.customerId"
          "notificationType" = "ORDER_FAILED"
          "message"         = "Your order could not be processed due to a system error. Please try again later."
        }
        End = true
      }
    }
  })

  logging_configuration {
    log_destination        = "${aws_cloudwatch_log_group.step_functions_logs.arn}:*"
    include_execution_data = true
    level                  = "ALL"
  }

  tags = local.common_tags
}

# CloudWatch Log Group for Step Functions
resource "aws_cloudwatch_log_group" "step_functions_logs" {
  name              = "/aws/stepfunctions/${local.function_base_name}-order-processing"
  retention_in_days = var.log_retention_days

  tags = local.common_tags
}

# Additional Lambda functions for Step Functions workflow
module "order_validation_lambda" {
  source  = "terraform-aws-modules/lambda/aws"
  version = "~> 8.1"

  function_name = "${local.function_base_name}-order-validation"
  description   = "Order validation for Step Functions workflow"
  handler       = var.enable_native_deployment ? "bootstrap" : "software.amazonaws.example.ordervalidation.OrderValidationHandler::handleRequest"
  runtime       = var.enable_native_deployment ? "provided.al2" : "java21"
  architectures = ["arm64"]

  create_package         = false
  local_existing_package = var.enable_native_deployment ? "../build/order-validation-service-native.zip" : "../build/order-validation-service.zip"

  timeout     = local.lambda_timeout
  memory_size = 256

  environment_variables = {
    ENVIRONMENT = local.environment
    LOG_LEVEL   = "INFO"
  }

  attach_cloudwatch_logs_policy     = true
  cloudwatch_logs_retention_in_days = local.log_retention

  tracing_mode          = local.xray_tracing ? "Active" : "PassThrough"
  attach_tracing_policy = local.xray_tracing

  tags = local.common_tags
}

module "payment_lambda" {
  source  = "terraform-aws-modules/lambda/aws"
  version = "~> 8.1"

  function_name = "${local.function_base_name}-payment"
  description   = "Payment processing for Step Functions workflow"
  handler       = var.enable_native_deployment ? "bootstrap" : "software.amazonaws.example.payment.PaymentHandler::handleRequest"
  runtime       = var.enable_native_deployment ? "provided.al2" : "java21"
  architectures = ["arm64"]

  create_package         = false
  local_existing_package = var.enable_native_deployment ? "../build/payment-service-native.zip" : "../build/payment-service.zip"

  timeout     = local.lambda_timeout
  memory_size = 256

  environment_variables = {
    ENVIRONMENT = local.environment
    LOG_LEVEL   = "INFO"
  }

  attach_cloudwatch_logs_policy     = true
  cloudwatch_logs_retention_in_days = local.log_retention

  tracing_mode          = local.xray_tracing ? "Active" : "PassThrough"
  attach_tracing_policy = local.xray_tracing

  tags = local.common_tags
}

module "inventory_lambda" {
  source  = "terraform-aws-modules/lambda/aws"
  version = "~> 8.1"

  function_name = "${local.function_base_name}-inventory"
  description   = "Inventory checking for Step Functions workflow"
  handler       = var.enable_native_deployment ? "bootstrap" : "software.amazonaws.example.inventory.InventoryHandler::handleRequest"
  runtime       = var.enable_native_deployment ? "provided.al2" : "java21"
  architectures = ["arm64"]

  create_package         = false
  local_existing_package = var.enable_native_deployment ? "../build/inventory-service-native.zip" : "../build/inventory-service.zip"

  timeout     = local.lambda_timeout
  memory_size = 256

  environment_variables = {
    ENVIRONMENT = local.environment
    LOG_LEVEL   = "INFO"
  }

  attach_cloudwatch_logs_policy     = true
  cloudwatch_logs_retention_in_days = local.log_retention

  tracing_mode          = local.xray_tracing ? "Active" : "PassThrough"
  attach_tracing_policy = local.xray_tracing

  tags = local.common_tags
}

module "notification_lambda" {
  source  = "terraform-aws-modules/lambda/aws"
  version = "~> 8.1"

  function_name = "${local.function_base_name}-notification"
  description   = "Notification service for Step Functions workflow"
  handler       = var.enable_native_deployment ? "bootstrap" : "software.amazonaws.example.notification.NotificationHandler::handleRequest"
  runtime       = var.enable_native_deployment ? "provided.al2" : "java21"
  architectures = ["arm64"]

  create_package         = false
  local_existing_package = var.enable_native_deployment ? "../build/notification-service-native.zip" : "../build/notification-service.zip"

  timeout     = local.lambda_timeout
  memory_size = 256

  environment_variables = {
    ENVIRONMENT = local.environment
    LOG_LEVEL   = "INFO"
  }

  attach_cloudwatch_logs_policy     = true
  cloudwatch_logs_retention_in_days = local.log_retention

  tracing_mode          = local.xray_tracing ? "Active" : "PassThrough"
  attach_tracing_policy = local.xray_tracing

  tags = local.common_tags
}

# Lambda permission for Step Functions to invoke the functions
resource "aws_lambda_permission" "step_functions_invoke_order_validation" {
  statement_id  = "AllowExecutionFromStepFunctions-order-validation"
  action        = "lambda:InvokeFunction"
  function_name = module.order_validation_lambda.lambda_function_name
  principal     = "states.amazonaws.com"
  source_arn    = aws_sfn_state_machine.order_processing.arn
}

resource "aws_lambda_permission" "step_functions_invoke_payment" {
  statement_id  = "AllowExecutionFromStepFunctions-payment"
  action        = "lambda:InvokeFunction"
  function_name = module.payment_lambda.lambda_function_name
  principal     = "states.amazonaws.com"
  source_arn    = aws_sfn_state_machine.order_processing.arn
}

resource "aws_lambda_permission" "step_functions_invoke_inventory" {
  statement_id  = "AllowExecutionFromStepFunctions-inventory"
  action        = "lambda:InvokeFunction"
  function_name = module.inventory_lambda.lambda_function_name
  principal     = "states.amazonaws.com"
  source_arn    = aws_sfn_state_machine.order_processing.arn
}

resource "aws_lambda_permission" "step_functions_invoke_notification" {
  statement_id  = "AllowExecutionFromStepFunctions-notification"
  action        = "lambda:InvokeFunction"
  function_name = module.notification_lambda.lambda_function_name
  principal     = "states.amazonaws.com"
  source_arn    = aws_sfn_state_machine.order_processing.arn
}