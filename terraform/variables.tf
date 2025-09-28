variable "aws_region" {
  description = "AWS region for resources"
  type        = string
  default     = "us-east-1"
}

variable "function_name" {
  description = "Base name for Lambda functions"
  type        = string
  default     = "java-lambda"
}

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "dev"
}

variable "namespace" {
  description = "Namespace for resource naming (enables ephemeral infrastructure)"
  type        = string
  default     = ""
  validation {
    condition     = can(regex("^[a-z0-9-]*$", var.namespace))
    error_message = "Namespace must contain only lowercase letters, numbers, and hyphens."
  }
}

variable "project_name" {
  description = "Name of the project"
  type        = string
  default     = "lambda-java-template"
}

variable "is_ephemeral" {
  description = "Whether this is an ephemeral environment"
  type        = bool
  default     = false
}

# Environment-specific Lambda configuration
variable "function_memory" {
  description = "Memory allocation for Lambda functions"
  type        = number
  default     = 512
  validation {
    condition     = var.function_memory >= 128 && var.function_memory <= 10240
    error_message = "Function memory must be between 128 and 10240 MB."
  }
}

variable "function_timeout" {
  description = "Timeout for Lambda functions in seconds"
  type        = number
  default     = 30
  validation {
    condition     = var.function_timeout >= 1 && var.function_timeout <= 900
    error_message = "Function timeout must be between 1 and 900 seconds."
  }
}

variable "enable_xray_tracing" {
  description = "Enable AWS X-Ray tracing for Lambda functions"
  type        = bool
  default     = true
}

variable "log_retention_days" {
  description = "CloudWatch log retention in days"
  type        = number
  default     = 14
  validation {
    condition     = contains([1, 3, 5, 7, 14, 30, 60, 90, 120, 150, 180, 365, 400, 545, 731, 1827, 3653], var.log_retention_days)
    error_message = "Log retention days must be a valid CloudWatch retention period."
  }
}

# DynamoDB configuration
variable "billing_mode" {
  description = "DynamoDB billing mode"
  type        = string
  default     = "PAY_PER_REQUEST"
  validation {
    condition     = contains(["PAY_PER_REQUEST", "PROVISIONED"], var.billing_mode)
    error_message = "Billing mode must be either PAY_PER_REQUEST or PROVISIONED."
  }
}

variable "read_capacity" {
  description = "DynamoDB read capacity (for PROVISIONED billing)"
  type        = number
  default     = 5
}

variable "write_capacity" {
  description = "DynamoDB write capacity (for PROVISIONED billing)"
  type        = number
  default     = 5
}

# Native deployment configuration
variable "enable_native_deployment" {
  description = "Enable GraalVM native deployment (provided.al2 runtime)"
  type        = bool
  default     = false
}

# Additional environment-specific tags
variable "additional_tags" {
  description = "Additional tags for resources"
  type        = map(string)
  default     = {}
}

