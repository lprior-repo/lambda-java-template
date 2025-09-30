# Ephemeral Environment Infrastructure for Local Development
# This creates isolated AWS environments for development and testing

locals {
  # Generate unique environment identifier based on developer and branch
  developer_id       = var.developer_id != "" ? var.developer_id : "dev"
  branch_name        = var.branch_name != "" ? replace(var.branch_name, "/[^a-zA-Z0-9-]/", "-") : "main"
  environment_suffix = "${local.developer_id}-${local.branch_name}"

  # Ephemeral environment configuration
  ephemeral_environment = "${var.project_name}-ephemeral-${local.environment_suffix}"
  ephemeral_tags = merge(local.common_tags, {
    Environment  = "ephemeral"
    DeveloperID  = local.developer_id
    Branch       = local.branch_name
    EphemeralEnv = "true"
    AutoDestroy  = "true"
    CreatedBy    = "local-development"
  })
}

# Note: Variables are defined in variables.tf to avoid duplication

# Ephemeral S3 bucket for build artifacts with automatic cleanup
resource "aws_s3_bucket" "ephemeral_artifacts" {
  count  = var.ephemeral_enabled ? 1 : 0
  bucket = "${local.ephemeral_environment}-artifacts"

  tags = merge(local.ephemeral_tags, {
    Name    = "${local.ephemeral_environment}-artifacts"
    Purpose = "ephemeral-build-artifacts"
  })
}

resource "aws_s3_bucket_lifecycle_configuration" "ephemeral_artifacts_cleanup" {
  count  = var.ephemeral_enabled ? 1 : 0
  bucket = aws_s3_bucket.ephemeral_artifacts[0].id

  rule {
    id     = "ephemeral_cleanup"
    status = "Enabled"

    filter {
      prefix = ""
    }

    expiration {
      days = 1 # Auto-cleanup after 1 day
    }

    noncurrent_version_expiration {
      noncurrent_days = 1
    }
  }
}

resource "aws_s3_bucket_public_access_block" "ephemeral_artifacts" {
  count  = var.ephemeral_enabled ? 1 : 0
  bucket = aws_s3_bucket.ephemeral_artifacts[0].id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Ephemeral DynamoDB tables with reduced capacity for cost optimization
resource "aws_dynamodb_table" "ephemeral_users" {
  count            = var.ephemeral_enabled ? 1 : 0
  name             = "${local.ephemeral_environment}-users"
  billing_mode     = "PAY_PER_REQUEST" # Cost-effective for development
  hash_key         = "id"
  stream_enabled   = true
  stream_view_type = "NEW_AND_OLD_IMAGES"

  attribute {
    name = "id"
    type = "S"
  }

  attribute {
    name = "email"
    type = "S"
  }

  global_secondary_index {
    name            = "EmailIndex"
    hash_key        = "email"
    projection_type = "ALL"
  }

  tags = merge(local.ephemeral_tags, {
    Name    = "${local.ephemeral_environment}-users"
    Purpose = "ephemeral-development"
  })

  # Auto-destroy protection disabled for ephemeral environments
  deletion_protection_enabled = false
}

resource "aws_dynamodb_table" "ephemeral_products" {
  count        = var.ephemeral_enabled ? 1 : 0
  name         = "${local.ephemeral_environment}-products"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "id"

  attribute {
    name = "id"
    type = "S"
  }

  tags = merge(local.ephemeral_tags, {
    Name    = "${local.ephemeral_environment}-products"
    Purpose = "ephemeral-development"
  })

  deletion_protection_enabled = false
}


# CloudWatch Log Groups with shorter retention for cost optimization
resource "aws_cloudwatch_log_group" "ephemeral_lambda_logs" {
  for_each = var.ephemeral_enabled ? local.lambda_functions : {}

  name              = "/aws/lambda/${local.ephemeral_environment}-${each.key}"
  retention_in_days = 3 # Short retention for ephemeral env

  tags = merge(local.ephemeral_tags, {
    Name    = "${local.ephemeral_environment}-${each.key}-logs"
    Purpose = "ephemeral-development"
  })
}

# Outputs for ephemeral environment
output "ephemeral_environment_name" {
  description = "Name of the ephemeral environment"
  value       = var.ephemeral_enabled ? local.ephemeral_environment : null
}

output "ephemeral_s3_bucket" {
  description = "S3 bucket for ephemeral build artifacts"
  value       = var.ephemeral_enabled ? aws_s3_bucket.ephemeral_artifacts[0].bucket : null
}

output "ephemeral_dynamodb_tables" {
  description = "DynamoDB tables for ephemeral environment"
  value = var.ephemeral_enabled ? {
    users    = aws_dynamodb_table.ephemeral_users[0].name
    products = aws_dynamodb_table.ephemeral_products[0].name
  } : null
}


output "ephemeral_log_groups" {
  description = "CloudWatch log groups for ephemeral Lambda functions"
  value = var.ephemeral_enabled ? {
    for k, v in aws_cloudwatch_log_group.ephemeral_lambda_logs : k => v.name
  } : null
}

# Auto-destroy Lambda function for cleanup
data "archive_file" "ephemeral_cleanup_lambda" {
  count       = var.ephemeral_enabled ? 1 : 0
  type        = "zip"
  output_path = "${path.module}/../build/ephemeral-cleanup.zip"

  source {
    content = templatefile("${path.module}/scripts/ephemeral-cleanup.py", {
      environment_prefix = local.ephemeral_environment
      auto_destroy_hours = var.auto_destroy_hours
    })
    filename = "lambda_function.py"
  }
}

resource "aws_lambda_function" "ephemeral_cleanup" {
  count            = var.ephemeral_enabled ? 1 : 0
  filename         = data.archive_file.ephemeral_cleanup_lambda[0].output_path
  function_name    = "${local.ephemeral_environment}-cleanup"
  role             = aws_iam_role.ephemeral_cleanup_role[0].arn
  handler          = "lambda_function.lambda_handler"
  source_code_hash = data.archive_file.ephemeral_cleanup_lambda[0].output_base64sha256
  runtime          = "python3.12"
  timeout          = 300

  environment {
    variables = {
      ENVIRONMENT_PREFIX = local.ephemeral_environment
      AUTO_DESTROY_HOURS = var.auto_destroy_hours
    }
  }

  tags = merge(local.ephemeral_tags, {
    Name    = "${local.ephemeral_environment}-cleanup"
    Purpose = "ephemeral-auto-cleanup"
  })
}

# IAM role for cleanup function
resource "aws_iam_role" "ephemeral_cleanup_role" {
  count = var.ephemeral_enabled ? 1 : 0
  name  = "${local.ephemeral_environment}-cleanup-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })

  tags = local.ephemeral_tags
}

resource "aws_iam_role_policy" "ephemeral_cleanup_policy" {
  count = var.ephemeral_enabled ? 1 : 0
  name  = "${local.ephemeral_environment}-cleanup-policy"
  role  = aws_iam_role.ephemeral_cleanup_role[0].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:*:*:*"
      },
      {
        Effect = "Allow"
        Action = [
          "dynamodb:DeleteTable",
          "dynamodb:ListTables",
          "dynamodb:DescribeTable"
        ]
        Resource = "*"
        Condition = {
          StringLike = {
            "dynamodb:TableName" = "${local.ephemeral_environment}-*"
          }
        }
      },
      {
        Effect = "Allow"
        Action = [
          "s3:DeleteBucket",
          "s3:DeleteObject",
          "s3:ListBucket"
        ]
        Resource = [
          "arn:aws:s3:::${local.ephemeral_environment}-*",
          "arn:aws:s3:::${local.ephemeral_environment}-*/*"
        ]
      }
    ]
  })
}

# CloudWatch event to trigger cleanup
resource "aws_cloudwatch_event_rule" "ephemeral_cleanup_schedule" {
  count               = var.ephemeral_enabled ? 1 : 0
  name                = "${local.ephemeral_environment}-cleanup-schedule"
  description         = "Trigger cleanup for ephemeral environment"
  schedule_expression = "rate(${var.auto_destroy_hours} hours)"

  tags = local.ephemeral_tags
}

resource "aws_cloudwatch_event_target" "ephemeral_cleanup_target" {
  count     = var.ephemeral_enabled ? 1 : 0
  rule      = aws_cloudwatch_event_rule.ephemeral_cleanup_schedule[0].name
  target_id = "EphemeralCleanupTarget"
  arn       = aws_lambda_function.ephemeral_cleanup[0].arn
}

resource "aws_lambda_permission" "ephemeral_cleanup_permission" {
  count         = var.ephemeral_enabled ? 1 : 0
  statement_id  = "AllowExecutionFromCloudWatch"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.ephemeral_cleanup[0].function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.ephemeral_cleanup_schedule[0].arn
}