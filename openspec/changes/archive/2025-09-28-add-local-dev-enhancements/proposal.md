# Local Development Enhancements with Hot Reloading and Feature Flags

## Why

Current development experience for GraalVM Lambda functions has several velocity bottlenecks:
- **Slow feedback loops**: Native compilation + deployment takes 45+ seconds, breaking TDD flow
- **Limited live debugging**: No ability to debug Lambda functions running in live AWS environments
- **Manual feature management**: No runtime feature toggles for development/testing
- **Ephemeral environment friction**: Slow deployment cycles make ephemeral testing inefficient

This creates friction that slows developer velocity when working against real AWS services in ephemeral environments.

## What Changes

- **Hot Deployment to Ephemeral Environments**: Automated rapid deployment to live AWS environments on code changes
- **AWS AppConfig Integration**: Feature flag management for runtime configuration and A/B testing
- **Live AWS Lambda Debugging**: Remote debugging capabilities for Lambda functions running in AWS
- **Ephemeral Environment Management**: Streamlined creation/destruction of development AWS environments
- **Task-based Developer Workflow**: Streamlined commands for rapid AWS deployment and debugging
- **Spring Boot 3 Native with AOT Compilation**: Spring Boot Native for Lambda with Ahead-of-Time compilation to combat cold starts
- **Spring DevTools Integration**: Development-time hot reload with production AOT native compilation
- **No Long-Running JVM**: Pure native compilation for Lambda startup performance, not dynamic JVM execution

## Impact

- Affected specs: `local-development` (new capability)
- Affected code:
  - `Taskfile.yml` - New development workflow tasks
  - `template-ephemeral.yaml` (new) - SAM template for ephemeral deployment
  - `terraform/appconfig.tf` (new) - Feature flag infrastructure
  - `src/*/pom.xml` - Spring Boot 3 and native dependencies
  - Spring Boot configuration and development scripts
- Dependencies: AWS SAM CLI, AWS AppConfig service, file watchers
- **BREAKING**: None - purely additive enhancements