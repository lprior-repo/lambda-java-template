# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an AWS Lambda Java template using Terraform for infrastructure management. The project follows a multi-module Maven structure with serverless patterns from AWS Well-Architected framework, utilizing terraform-aws-modules from the serverless.tf framework.

## Build System

### Task Runner (Primary)
Use **Task** (taskfile.dev) for all development operations:
- `task` - Show all available tasks
- `task build` - Build all Lambda functions
- `task package` - Package functions for deployment
- `task test` - Run tests with coverage
- `task deploy:dev` - Deploy to development environment

### Maven Commands (Alternative)
- `mvn clean package` - Build all functions
- `mvn test` - Run all tests
- `mvn dependency:resolve` - Download dependencies

## Development Workflow

### Adding New Lambda Functions
1. Create new directory under `src/` (e.g., `src/orders/`)
2. Add function-specific `pom.xml` with parent reference
3. Implement handler class in `src/main/java/com/example/{function}/`
4. Add module to parent `pom.xml` modules section
5. Add function configuration to `terraform/locals.tf` in `lambda_functions` map
6. Update Terraform if new routes or permissions needed

### Testing
- `task test` - Run all tests with JaCoCo coverage
- `task test:unit` - Unit tests only
- `task test:integration` - Integration tests only
- `task test:watch` - TDD watch mode (requires watchexec)
- `task test:coverage` - Generate detailed coverage report

### Code Quality
- `task lint` - Run Checkstyle, SpotBugs, PMD
- `task lint:fix` - Auto-fix formatting with Spotless
- `task security` - OWASP dependency check and security scans
- `task validate` - Run all validation checks (lint + test + security + terraform)

## Architecture

### Multi-Module Maven Structure
- **Parent POM**: `pom.xml` - Manages dependencies and plugins centrally
- **Function Modules**: `src/{function}/pom.xml` - Individual Lambda functions
- **Build Output**: `build/` directory contains deployment JARs

### Terraform Infrastructure (serverless.tf patterns)
- **terraform-aws-modules/lambda/aws** - Lambda function management
- **terraform-aws-modules/apigateway-v2/aws** - API Gateway HTTP API
- **Pre-built JAR packages** - No building in Terraform, uses artifacts from build/
- **DynamoDB tables** - Users, posts, audit logs with proper IAM permissions
- **EventBridge** - Event-driven architecture for audit logging
- **ARM64 architecture** - Cost-optimized Lambda functions

### Lambda Function Configuration
Functions are defined in `terraform/locals.tf`:
```hcl
lambda_functions = {
  function_name = {
    name       = "${local.function_base_name}-{name}"
    source_dir = "../build/{name}.zip"
    runtime    = "java21"
    handler    = "com.example.Handler::handleRequest"
    routes     = [{ path = "/path", method = "GET", auth = true }]
  }
}
```

## Deployment

### Infrastructure as Code
- `task tf:init` - Initialize Terraform
- `task tf:plan` - Plan infrastructure changes
- `task tf:apply` - Apply changes (requires built artifacts)
- `task tf:validate` - Validate Terraform configuration
- `task tf:security` - Run tfsec security checks

### Environment Deployment
- `task deploy:dev` - Full development deployment
- `task deploy:staging` - Staging environment
- `task deploy:prod` - Production environment

Each deployment runs validation before applying changes.

## Monitoring & Observability

### Logs
- `task logs:hello` - Hello function logs with JSON formatting
- `task logs:users` - Users function logs
- `task xray` - View X-Ray traces and service map
- `task metrics` - CloudWatch metrics dashboard

### Performance
- `task profile` - JVM profiling guidance
- `task benchmark` - Run performance benchmarks

## Development Environment

### Setup
- `task dev:setup` - Initialize Java development environment
- `task dev:tools` - List available development tools
- `task dev:invoke` - Invoke Lambda locally with SAM
- `task dev:debug` - Start Lambda in debug mode

### API Development
- `task api:docs` - Generate API documentation from OpenAPI spec
- `task api:validate` - Validate OpenAPI specification

## Java-Specific Details

### Runtime & Dependencies
- **Java 21** runtime with Corretto distribution
- **AWS Lambda Powertools** for structured logging, tracing, metrics
- **Maven Shade Plugin** creates fat JARs for deployment
- **ARM64 architecture** for cost optimization

### Memory & Performance
- Default: 512MB memory, 30s timeout
- Java functions typically need more memory than other runtimes
- X-Ray tracing enabled for performance monitoring
- CloudWatch logs with 14-day retention

### Handler Pattern
All handlers implement `RequestHandler<I, O>` interface:
```java
public class Handler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>
```

## CI/CD Pipeline

GitHub Actions workflow:
- **Matrix builds** - Parallel building of detected functions
- **Artifact uploads** - JAR files for deployment
- **Quality checks** - Checkstyle, tests, security scans
- **Infrastructure validation** - Terraform fmt, validate, tflint
- **Cost analysis** - Infracost integration for cost estimates

Functions are auto-detected from `src/` directory structure.