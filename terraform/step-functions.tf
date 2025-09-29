# Step Functions State Machine for Order Processing Workflow

# SNS topic for workflow failure notifications
resource "aws_sns_topic" "workflow_failures" {
  name = "${local.function_base_name}-workflow-failures"

  tags = local.common_tags
}

# SNS topic policy to allow Step Functions to publish
resource "aws_sns_topic_policy" "workflow_failures" {
  arn = aws_sns_topic.workflow_failures.arn

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "states.amazonaws.com"
        }
        Action   = "sns:Publish"
        Resource = aws_sns_topic.workflow_failures.arn
        Condition = {
          StringEquals = {
            "aws:SourceAccount" = data.aws_caller_identity.current.account_id
          }
        }
      }
    ]
  })
}

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
      },
      {
        Effect = "Allow"
        Action = [
          "sns:Publish"
        ]
        Resource = aws_sns_topic.workflow_failures.arn
      }
    ]
  })
}

# Step Functions State Machine
resource "aws_sfn_state_machine" "order_processing" {
  name     = "${local.function_base_name}-order-processing"
  role_arn = aws_iam_role.step_functions_role.arn

  definition = jsonencode({
    Comment       = "Order Processing Workflow with JSONata, parallel execution, error handling, and redrive capability"
    QueryLanguage = "JSONata"
    StartAt       = "InitializeWorkflow"
    States = {
      InitializeWorkflow = {
        Type          = "Pass"
        Comment       = "Initialize workflow variables and prepare input data"
        QueryLanguage = "JSONata"
        Assign = {
          workflowId         = "{% $states.context.Execution.Name %}"
          executionStartTime = "{% $now() %}"
          originalInput      = "{% $states.input %}"
        }
        Output = "{% $states.input %}"
        Next   = "ValidateOrder"
      }

      ValidateOrder = {
        Type           = "Task"
        Comment        = "Validates order data and business rules"
        Resource       = module.order_validation_lambda.lambda_function_arn
        QueryLanguage  = "JSONata"
        Arguments      = "{% {'orderId': $states.input.orderId, 'customerId': $states.input.customerId, 'items': $states.input.items, 'totalAmount': $states.input.totalAmount, 'paymentMethod': $states.input.paymentMethod, 'traceId': $workflowId} %}"
        Output         = "{% $merge([$originalInput, {'validationResult': $states.result}]) %}"
        TimeoutSeconds = "{% $states.input.timeoutSettings.validation ? $states.input.timeoutSettings.validation : 30 %}"
        Next           = "CheckValidation"
        Retry = [
          {
            ErrorEquals     = ["Lambda.ServiceException", "Lambda.AWSLambdaException", "Lambda.SdkClientException"]
            IntervalSeconds = 2
            MaxAttempts     = 3
            BackoffRate     = 2.0
            JitterStrategy  = "FULL"
          },
          {
            ErrorEquals     = ["States.Timeout"]
            IntervalSeconds = 1
            MaxAttempts     = 2
            BackoffRate     = 1.5
          }
        ]
        Catch = [
          {
            ErrorEquals = ["States.ALL"]
            Output      = "{% $merge([$states.input, {'errorDetails': $states.errorOutput, 'failedState': $states.context.State.Name, 'executionName': $states.context.Execution.Name, 'isRedriveCandidate': true}]) %}"
            Next        = "NotifyValidationFailure"
          }
        ]
      }

      CheckValidation = {
        Type          = "Choice"
        Comment       = "Routes workflow based on validation results"
        QueryLanguage = "JSONata"
        Choices = [
          {
            Condition = "{% $states.input.validationResult.isValid = true %}"
            Next      = "ParallelProcessing"
          }
        ]
        Default = "ValidationFailed"
      }

      ParallelProcessing = {
        Type          = "Parallel"
        Comment       = "Processes inventory check and payment in parallel for optimal performance"
        QueryLanguage = "JSONata"
        Arguments     = "{% $states.input %}"
        Branches = [
          {
            StartAt = "CheckInventory"
            States = {
              CheckInventory = {
                Type           = "Task"
                Comment        = "Verifies product availability and reserves inventory"
                Resource       = module.inventory_lambda.lambda_function_arn
                QueryLanguage  = "JSONata"
                Arguments      = "{% {'orderId': $states.input.orderId, 'customerId': $states.input.customerId, 'items': $states.input.items, 'traceId': $workflowId} %}"
                Output         = "{% $merge([{'branchType': 'inventory'}, $states.result]) %}"
                TimeoutSeconds = "{% $states.input.timeoutSettings.inventory ? $states.input.timeoutSettings.inventory : 60 %}"
                End            = true
                Retry = [
                  {
                    ErrorEquals     = ["Lambda.ServiceException", "Lambda.AWSLambdaException", "Lambda.SdkClientException"]
                    IntervalSeconds = 1
                    MaxAttempts     = 3
                    BackoffRate     = 2.0
                    JitterStrategy  = "FULL"
                  }
                ]
                Catch = [
                  {
                    ErrorEquals = ["States.ALL"]
                    Output      = "{% {'branchType': 'inventory', 'availabilityStatus': 'ERROR', 'orderId': $states.input.orderId, 'customerId': $states.input.customerId, 'error': 'Inventory check failed', 'errorDetails': $states.errorOutput} %}"
                    Next        = "InventoryFailed"
                  }
                ]
              }
              InventoryFailed = {
                Type          = "Pass"
                Comment       = "Handles inventory check failures gracefully"
                QueryLanguage = "JSONata"
                Output        = "{% $states.input %}"
                End           = true
              }
            }
          },
          {
            StartAt = "ProcessPayment"
            States = {
              ProcessPayment = {
                Type           = "Task"
                Comment        = "Processes payment authorization and capture"
                Resource       = module.payment_lambda.lambda_function_arn
                QueryLanguage  = "JSONata"
                Arguments      = "{% {'orderId': $states.input.orderId, 'customerId': $states.input.customerId, 'totalAmount': $states.input.totalAmount, 'paymentMethod': $states.input.paymentMethod, 'traceId': $workflowId} %}"
                Output         = "{% $merge([{'branchType': 'payment'}, $states.result]) %}"
                TimeoutSeconds = "{% $states.input.timeoutSettings.payment ? $states.input.timeoutSettings.payment : 45 %}"
                End            = true
                Retry = [
                  {
                    ErrorEquals     = ["Lambda.ServiceException", "Lambda.AWSLambdaException", "Lambda.SdkClientException"]
                    IntervalSeconds = 1
                    MaxAttempts     = 2
                    BackoffRate     = 2.0
                    JitterStrategy  = "FULL"
                  }
                ]
                Catch = [
                  {
                    ErrorEquals = ["States.ALL"]
                    Output      = "{% {'branchType': 'payment', 'paymentStatus': 'FAILED', 'orderId': $states.input.orderId, 'customerId': $states.input.customerId, 'error': 'Payment processing failed', 'errorDetails': $states.errorOutput} %}"
                    Next        = "PaymentFailed"
                  }
                ]
              }
              PaymentFailed = {
                Type          = "Pass"
                Comment       = "Handles payment processing failures gracefully"
                QueryLanguage = "JSONata"
                Output        = "{% $states.input %}"
                End           = true
              }
            }
          }
        ]
        Output = "{% $merge([$originalInput, {'parallelResults': $states.result}]) %}"
        Next   = "EvaluateResults"
        Catch = [
          {
            ErrorEquals = ["States.ALL"]
            Output      = "{% $merge([$states.input, {'errorDetails': $states.errorOutput, 'failedState': $states.context.State.Name, 'executionName': $states.context.Execution.Name, 'isRedriveCandidate': true}]) %}"
            Next        = "NotifyProcessingFailure"
          }
        ]
      }

      EvaluateResults = {
        Type          = "Choice"
        Comment       = "Evaluates parallel processing results to determine next action"
        QueryLanguage = "JSONata"
        Assign = {
          inventoryResult = "{% $filter($states.input.parallelResults, function($v) { $v.branchType = 'inventory' })[0] %}"
          paymentResult   = "{% $filter($states.input.parallelResults, function($v) { $v.branchType = 'payment' })[0] %}"
        }
        Choices = [
          {
            Condition = "{% $inventoryResult.availabilityStatus = 'AVAILABLE' and $paymentResult.paymentStatus = 'APPROVED' %}"
            Output    = "{% $merge([$states.input, {'finalStatus': 'SUCCESS', 'inventory': $inventoryResult, 'payment': $paymentResult}]) %}"
            Next      = "OrderSuccess"
          },
          {
            Condition = "{% $inventoryResult.availabilityStatus = 'OUT_OF_STOCK' %}"
            Output    = "{% $merge([$states.input, {'finalStatus': 'INVENTORY_UNAVAILABLE', 'inventory': $inventoryResult, 'payment': $paymentResult}]) %}"
            Next      = "InventoryUnavailable"
          },
          {
            Condition = "{% $paymentResult.paymentStatus = 'DECLINED' %}"
            Output    = "{% $merge([$states.input, {'finalStatus': 'PAYMENT_DECLINED', 'inventory': $inventoryResult, 'payment': $paymentResult}]) %}"
            Next      = "PaymentDeclined"
          }
        ]
        Default = "ProcessingFailed"
      }

      OrderSuccess = {
        Type           = "Task"
        Comment        = "Sends confirmation notification for successful orders"
        Resource       = module.notification_lambda.lambda_function_arn
        QueryLanguage  = "JSONata"
        Arguments      = "{% {'orderId': $states.input.orderId, 'customerId': $states.input.customerId, 'totalAmount': $states.input.totalAmount, 'transactionId': $states.input.payment.transactionId, 'reservationId': $states.input.inventory.reservationId, 'notificationType': 'ORDER_CONFIRMATION', 'message': 'Your order has been confirmed and is being processed.', 'traceId': $workflowId} %}"
        Output         = "{% $merge([$states.input, {'notificationResult': $states.result, 'completedAt': $now()}]) %}"
        TimeoutSeconds = "{% $states.input.timeoutSettings.notification ? $states.input.timeoutSettings.notification : 30 %}"
        End            = true
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
            Output      = "{% $merge([$states.input, {'errorDetails': $states.errorOutput, 'failedState': $states.context.State.Name, 'executionName': $states.context.Execution.Name, 'isRedriveCandidate': true}]) %}"
            Next        = "NotifyNotificationFailure"
          }
        ]
      }

      InventoryUnavailable = {
        Type           = "Task"
        Comment        = "Sends notification for inventory unavailable scenarios"
        Resource       = module.notification_lambda.lambda_function_arn
        QueryLanguage  = "JSONata"
        Arguments      = "{% {'orderId': $states.input.orderId, 'customerId': $states.input.customerId, 'notificationType': 'INVENTORY_UNAVAILABLE', 'message': 'Unfortunately, some items in your order are currently out of stock.', 'unavailabilityReason': $states.input.inventory.unavailabilityReason, 'traceId': $workflowId} %}"
        Output         = "{% $merge([$states.input, {'notificationResult': $states.result, 'completedAt': $now()}]) %}"
        TimeoutSeconds = "{% $states.input.timeoutSettings.notification ? $states.input.timeoutSettings.notification : 30 %}"
        End            = true
      }

      PaymentDeclined = {
        Type           = "Task"
        Comment        = "Sends notification for payment declined scenarios"
        Resource       = module.notification_lambda.lambda_function_arn
        QueryLanguage  = "JSONata"
        Arguments      = "{% {'orderId': $states.input.orderId, 'customerId': $states.input.customerId, 'notificationType': 'PAYMENT_FAILED', 'message': 'Your payment could not be processed. Please try a different payment method.', 'paymentError': $states.input.payment.paymentError, 'traceId': $workflowId} %}"
        Output         = "{% $merge([$states.input, {'notificationResult': $states.result, 'completedAt': $now()}]) %}"
        TimeoutSeconds = "{% $states.input.timeoutSettings.notification ? $states.input.timeoutSettings.notification : 30 %}"
        End            = true
      }

      ValidationFailed = {
        Type           = "Task"
        Comment        = "Sends notification for validation failures"
        Resource       = module.notification_lambda.lambda_function_arn
        QueryLanguage  = "JSONata"
        Arguments      = "{% {'orderId': $states.input.orderId, 'customerId': $states.input.customerId, 'notificationType': 'ORDER_FAILED', 'message': 'Your order could not be processed due to validation errors.', 'validationErrors': $states.input.validationResult.validationErrors, 'traceId': $workflowId} %}"
        Output         = "{% $merge([$states.input, {'notificationResult': $states.result, 'completedAt': $now()}]) %}"
        TimeoutSeconds = "{% $states.input.timeoutSettings.notification ? $states.input.timeoutSettings.notification : 30 %}"
        End            = true
      }

      ProcessingFailed = {
        Type           = "Task"
        Comment        = "Sends notification for general processing failures"
        Resource       = module.notification_lambda.lambda_function_arn
        QueryLanguage  = "JSONata"
        Arguments      = "{% {'orderId': $states.input.orderId, 'customerId': $states.input.customerId, 'notificationType': 'ORDER_FAILED', 'message': 'Your order could not be processed due to a system error. Please try again later.', 'traceId': $workflowId} %}"
        Output         = "{% $merge([$states.input, {'notificationResult': $states.result, 'completedAt': $now()}]) %}"
        TimeoutSeconds = "{% $states.input.timeoutSettings.notification ? $states.input.timeoutSettings.notification : 30 %}"
        End            = true
      }

      NotifyValidationFailure = {
        Type          = "Task"
        Comment       = "Notifies operations team of validation failures requiring potential redrive"
        Resource      = "arn:aws:states:::sns:publish"
        QueryLanguage = "JSONata"
        Arguments = {
          TopicArn = "arn:aws:sns:${var.aws_region}:${data.aws_caller_identity.current.account_id}:${local.function_base_name}-workflow-failures"
          Subject  = "{% 'Order Processing Workflow Failure - Validation' %}"
          Message  = "{% 'Workflow failed in validation phase. Execution: ' & $states.input.executionName & '. State: ' & $states.input.failedState & '. Error: ' & $states.input.errorDetails.Error & '. Order ID: ' & $states.input.orderId & '. Ready for redrive if needed.' %}"
        }
        Next = "FailWorkflowDueToValidationError"
      }

      NotifyProcessingFailure = {
        Type          = "Task"
        Comment       = "Notifies operations team of processing failures requiring potential redrive"
        Resource      = "arn:aws:states:::sns:publish"
        QueryLanguage = "JSONata"
        Arguments = {
          TopicArn = "arn:aws:sns:${var.aws_region}:${data.aws_caller_identity.current.account_id}:${local.function_base_name}-workflow-failures"
          Subject  = "{% 'Order Processing Workflow Failure - Processing' %}"
          Message  = "{% 'Workflow failed in processing phase. Execution: ' & $states.input.executionName & '. State: ' & $states.input.failedState & '. Error: ' & $states.input.errorDetails.Error & '. Order ID: ' & $states.input.orderId & '. Ready for redrive from failure point.' %}"
        }
        Next = "FailWorkflowDueToProcessingError"
      }

      NotifyNotificationFailure = {
        Type          = "Task"
        Comment       = "Notifies operations team of notification failures requiring potential redrive"
        Resource      = "arn:aws:states:::sns:publish"
        QueryLanguage = "JSONata"
        Arguments = {
          TopicArn = "arn:aws:sns:${var.aws_region}:${data.aws_caller_identity.current.account_id}:${local.function_base_name}-workflow-failures"
          Subject  = "{% 'Order Processing Workflow Failure - Notification' %}"
          Message  = "{% 'Workflow failed in notification phase. Execution: ' & $states.input.executionName & '. State: ' & $states.input.failedState & '. Error: ' & $states.input.errorDetails.Error & '. Order ID: ' & $states.input.orderId & '. Order processing was successful but notification failed. Ready for redrive.' %}"
        }
        Next = "FailWorkflowDueToNotificationError"
      }

      FailWorkflowDueToValidationError = {
        Type          = "Fail"
        Comment       = "Terminates workflow due to validation error - redrive candidate"
        QueryLanguage = "JSONata"
        Error         = "{% $states.input.errorDetails.Error ? $states.input.errorDetails.Error : 'States.ValidationFailure' %}"
        Cause         = "{% 'Workflow failed at validation state and is a redrive candidate. Order ID: ' & $states.input.orderId & '. Execution ARN: ' & $states.input.executionName & '. Failed State: ' & $states.input.failedState %}"
      }

      FailWorkflowDueToProcessingError = {
        Type          = "Fail"
        Comment       = "Terminates workflow due to processing error - redrive candidate"
        QueryLanguage = "JSONata"
        Error         = "{% $states.input.errorDetails.Error ? $states.input.errorDetails.Error : 'States.ProcessingFailure' %}"
        Cause         = "{% 'Workflow failed at processing state and is a redrive candidate. Order ID: ' & $states.input.orderId & '. Execution ARN: ' & $states.input.executionName & '. Failed State: ' & $states.input.failedState %}"
      }

      FailWorkflowDueToNotificationError = {
        Type          = "Fail"
        Comment       = "Terminates workflow due to notification error - redrive candidate"
        QueryLanguage = "JSONata"
        Error         = "{% $states.input.errorDetails.Error ? $states.input.errorDetails.Error : 'States.NotificationFailure' %}"
        Cause         = "{% 'Workflow failed at notification state and is a redrive candidate. Order ID: ' & $states.input.orderId & '. Execution ARN: ' & $states.input.executionName & '. Failed State: ' & $states.input.failedState %}"
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