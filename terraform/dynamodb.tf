# DynamoDB Tables using terraform-aws-modules
module "products_table" {
  source  = "terraform-aws-modules/dynamodb-table/aws"
  version = "~> 4.0"

  name         = "${local.function_base_name}-products"
  billing_mode = local.dynamodb_billing_mode
  hash_key     = "id"

  # Provisioned capacity (only used when billing_mode is PROVISIONED)
  read_capacity  = local.dynamodb_read_capacity
  write_capacity = local.dynamodb_write_capacity

  attributes = [
    {
      name = "id"
      type = "S"
    },
    {
      name = "name"
      type = "S"
    }
  ]

  global_secondary_indexes = [
    {
      name            = "name-index"
      hash_key        = "name"
      projection_type = "ALL"
      # GSI capacity (only used when billing_mode is PROVISIONED)
      read_capacity  = local.dynamodb_read_capacity
      write_capacity = local.dynamodb_write_capacity
    }
  ]

  server_side_encryption_enabled = true
  point_in_time_recovery_enabled  = true

  tags = local.common_tags
}

module "audit_logs_table" {
  source  = "terraform-aws-modules/dynamodb-table/aws"
  version = "~> 4.0"

  name         = "${local.function_base_name}-audit-logs"
  billing_mode = local.dynamodb_billing_mode
  hash_key     = "event_id"
  range_key    = "timestamp"

  # Provisioned capacity (only used when billing_mode is PROVISIONED)
  read_capacity  = local.dynamodb_read_capacity
  write_capacity = local.dynamodb_write_capacity

  attributes = [
    {
      name = "event_id"
      type = "S"
    },
    {
      name = "timestamp"
      type = "S"
    }
  ]

  ttl_attribute_name = "ttl"
  ttl_enabled        = true

  server_side_encryption_enabled = true

  tags = local.common_tags
}