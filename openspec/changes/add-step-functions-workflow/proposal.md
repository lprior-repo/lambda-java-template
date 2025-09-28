## Why
The current template demonstrates individual Lambda functions but lacks serverless orchestration patterns. Step Functions provide a powerful way to coordinate multiple Lambda functions in complex workflows, essential for production-ready serverless applications.

## What Changes
- Add Step Functions state machine for order processing workflow
- Create additional Lambda functions: order validation, payment processing, inventory check, notification service
- Implement error handling, retry logic, and parallel execution patterns
- Add CloudWatch dashboards for workflow monitoring
- Demonstrate serverless orchestration best practices

## Impact
- Affected specs: NEW - orchestration capability
- Affected code: NEW Lambda services, terraform/step-functions.tf, terraform/locals.tf
- Architecture enhancement: Showcase enterprise-grade serverless orchestration
- Educational value: Complete example of Step Functions integration patterns