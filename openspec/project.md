# Project Context

## Purpose
AWS Lambda Java template for building serverless microservices. This project provides a production-ready foundation for event-driven Java applications using AWS Well-Architected framework patterns with Terraform infrastructure management.

## Tech Stack
- **Runtime**: Java 21 (Amazon Corretto)
- **Build**: Maven (multi-module structure)
- **Task Runner**: Task (taskfile.dev)
- **Infrastructure**: Terraform with terraform-aws-modules (serverless.tf patterns)
- **AWS Services**: Lambda, API Gateway HTTP API, DynamoDB, EventBridge, X-Ray, CloudWatch
- **Architecture**: ARM64 for cost optimization
- **Quality Tools**: Checkstyle, SpotBugs, PMD, Spotless, OWASP Dependency Check
- **Testing**: JUnit 5, JaCoCo coverage, Mockito
- **CI/CD**: GitHub Actions with matrix builds

## Project Conventions

### Code Style
- **Functional Programming**: Pure functions with Effect.ts patterns where applicable
- **Naming**: PascalCase for classes, camelCase for methods/variables, kebab-case for infrastructure
- **Test-Driven Development**: Red-Green-Refactor cycle with 100% test coverage requirement
- **Code Quality**: Enforced via Checkstyle, SpotBugs, PMD with automated linting
- **File Structure**: Multi-module Maven with `src/{function}/` pattern

### Architecture Patterns
- **Multi-Module Maven**: Parent POM manages dependencies, function modules are independent
- **Lambda Handler Pattern**: All handlers implement `RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>`
- **Event-Driven**: EventBridge for audit logging and service communication
- **Infrastructure as Code**: terraform-aws-modules for consistent AWS resource patterns
- **Pre-built Artifacts**: JAR files built separately from Terraform deployment
- **Centralized Configuration**: Lambda functions defined in `terraform/locals.tf`

### Testing Strategy
- **TDD Mandatory**: No production code without failing tests first
- **100% Coverage**: JaCoCo enforced with no `--passWithNoTests` flag allowed
- **Test Types**: Unit (`task test:unit`), Integration (`task test:integration`), Watch mode (`task test:watch`)
- **Mock Strategy**: Mockito for external dependencies, AWS SDK v2 test utilities
- **Performance**: Benchmark tests for latency-critical functions

### Git Workflow
- **Main Branch**: `main` for production releases
- **Feature Branches**: Short-lived feature branches with descriptive names
- **Commit Conventions**: Conventional commits with type prefixes (feat:, fix:, refactor:)
- **CI/CD**: GitHub Actions with matrix builds for parallel function compilation
- **Quality Gates**: All commits must pass lint, test, security, and Terraform validation

## Domain Context
- **Serverless Microservices**: Each Lambda function represents a bounded context
- **Event Sourcing**: Audit events captured via EventBridge for compliance
- **API-First**: OpenAPI specifications drive development with validation
- **Cost Optimization**: ARM64 Lambda architecture for 20% cost reduction
- **Observability**: X-Ray tracing, structured logging, CloudWatch metrics
- **Security**: IAM least privilege, OWASP dependency scanning, tfsec infrastructure checks

## Important Constraints
- **AWS Well-Architected**: Must follow reliability, security, performance, cost optimization pillars
- **Java 21 Runtime**: Leverage virtual threads and modern Java features
- **ARM64 Architecture**: All Lambda functions must support ARM64 for cost efficiency
- **Memory Limits**: Default 512MB, tune based on function requirements
- **Cold Start Optimization**: Minimize initialization time and dependency size
- **Compliance**: GDPR considerations for data handling and retention policies

## External Dependencies
- **AWS Services**: Lambda, API Gateway, DynamoDB, EventBridge, X-Ray, CloudWatch Logs
- **Maven Central**: AWS Lambda Powertools, AWS SDK v2, JUnit 5, Mockito
- **Build Tools**: Task runner, Maven, Java 21 Corretto
- **Infrastructure**: terraform-aws-modules from serverless.tf framework
- **CI/CD**: GitHub Actions, Infracost for cost analysis
- **Security**: OWASP dependency check, tfsec for Terraform security scanning
