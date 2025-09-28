# DynamoDB Tables
resource "aws_dynamodb_table" "products" {
  name         = "${local.function_base_name}-products"
  billing_mode = local.dynamodb_billing_mode
  hash_key     = "id"

  # Provisioned capacity (only used when billing_mode is PROVISIONED)
  read_capacity  = local.dynamodb_read_capacity
  write_capacity = local.dynamodb_write_capacity

  attribute {
    name = "id"
    type = "S"
  }

  attribute {
    name = "name"
    type = "S"
  }

  global_secondary_index {
    name            = "name-index"
    hash_key        = "name"
    projection_type = "ALL"
    # GSI capacity (only used when billing_mode is PROVISIONED)
    read_capacity  = local.dynamodb_read_capacity
    write_capacity = local.dynamodb_write_capacity
  }

  server_side_encryption {
    enabled = true
  }

  point_in_time_recovery {
    enabled = true
  }

  tags = local.common_tags
}

resource "aws_dynamodb_table" "audit_logs" {
  name         = "${local.function_base_name}-audit-logs"
  billing_mode = local.dynamodb_billing_mode
  hash_key     = "event_id"
  range_key    = "timestamp"

  # Provisioned capacity (only used when billing_mode is PROVISIONED)
  read_capacity  = local.dynamodb_read_capacity
  write_capacity = local.dynamodb_write_capacity

  attribute {
    name = "event_id"
    type = "S"
  }

  attribute {
    name = "timestamp"
    type = "S"
  }

  ttl {
    attribute_name = "ttl"
    enabled        = true
  }

  server_side_encryption {
    enabled = true
  }

  tags = local.common_tags
}