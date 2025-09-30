# S3 bucket for Lambda deployment artifacts using terraform-aws-modules
module "lambda_artifacts_bucket" {
  source  = "terraform-aws-modules/s3-bucket/aws"
  version = "~> 4.0"

  bucket = "${local.project_name}-${local.environment}-lambda-artifacts-${random_id.bucket_suffix.hex}"

  # Versioning
  versioning = {
    enabled = true
  }

  # Server-side encryption
  server_side_encryption_configuration = {
    rule = {
      apply_server_side_encryption_by_default = {
        sse_algorithm = "AES256"
      }
    }
  }

  # Public access block
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true

  tags = local.common_tags
}

resource "random_id" "bucket_suffix" {
  byte_length = 4
}

# Upload Lambda ZIP packages to S3
resource "aws_s3_object" "lambda_packages" {
  for_each = local.lambda_functions

  bucket = module.lambda_artifacts_bucket.s3_bucket_id
  key    = "${each.key}/${basename(each.value.source_dir)}"
  source = each.value.source_dir

  # Force upload on every apply to ensure latest code is deployed
  source_hash = filemd5(each.value.source_dir)

  tags = local.common_tags
}