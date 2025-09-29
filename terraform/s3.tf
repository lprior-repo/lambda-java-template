# S3 bucket for Lambda deployment artifacts
resource "aws_s3_bucket" "lambda_artifacts" {
  bucket = "${local.project_name}-${local.environment}-lambda-artifacts-${random_id.bucket_suffix.hex}"

  tags = local.common_tags
}

resource "random_id" "bucket_suffix" {
  byte_length = 4
}

resource "aws_s3_bucket_versioning" "lambda_artifacts_versioning" {
  bucket = aws_s3_bucket.lambda_artifacts.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "lambda_artifacts_encryption" {
  bucket = aws_s3_bucket.lambda_artifacts.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "lambda_artifacts_pab" {
  bucket = aws_s3_bucket.lambda_artifacts.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Upload Lambda ZIP packages to S3
resource "aws_s3_object" "lambda_packages" {
  for_each = local.lambda_functions

  bucket = aws_s3_bucket.lambda_artifacts.bucket
  key    = "${each.key}/${basename(each.value.source_dir)}"
  source = each.value.source_dir

  # Force upload on every apply to ensure latest code is deployed
  source_hash = filemd5(each.value.source_dir)

  tags = local.common_tags
}