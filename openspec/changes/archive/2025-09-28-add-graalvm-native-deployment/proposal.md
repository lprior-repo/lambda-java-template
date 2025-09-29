## Why
CI/CD pipeline builds GraalVM native images but Terraform deploys them using Java runtime, missing native performance benefits.

## What Changes
- ✅ Update Terraform Lambda configuration to support native deployment mode
- ✅ Add native/JVM deployment toggle via environment variables
- ✅ Configure proper runtime settings for native images (provided.al2)
- ✅ Update build artifact paths and CI pipeline for native packages

## Impact
- Affected specs: cicd-pipeline
- Affected code: terraform/locals.tf, terraform/lambda-functions.tf, terraform/variables.tf
- Performance improvement: 2-3x faster cold starts with native images