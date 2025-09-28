# Orchestration Capability

## Overview
Serverless workflow orchestration using AWS Step Functions for complex business processes with parallel execution, error handling, and retry logic.

## Requirements

### Requirement: Step Functions Workflow Orchestration
The system SHALL provide serverless workflow orchestration using AWS Step Functions.

#### Scenario: Order Processing Workflow
- **WHEN** an order is submitted via API Gateway
- **THEN** execute Step Functions state machine for order processing
- **AND** coordinate multiple Lambda functions in defined sequence

#### Scenario: Parallel Execution
- **WHEN** order validation passes
- **THEN** execute inventory check and payment processing in parallel
- **AND** proceed only when both parallel branches succeed

#### Scenario: Error Handling and Retries
- **WHEN** any step in the workflow fails
- **THEN** execute appropriate error handling states
- **AND** apply retry logic with exponential backoff where configured

### Requirement: Workflow Lambda Functions
The system SHALL provide specialized Lambda functions for workflow steps.

#### Scenario: Order Validation Service
- **WHEN** workflow begins
- **THEN** validate order format, required fields, and business rules
- **AND** return validation status with detailed error messages

#### Scenario: Payment Processing Service
- **WHEN** payment step executes
- **THEN** simulate payment processing with random success/failure
- **AND** return payment confirmation or decline reason

#### Scenario: Inventory Service
- **WHEN** inventory check executes
- **THEN** verify product availability and reserved quantity
- **AND** return stock status and reservation details

#### Scenario: Notification Service
- **WHEN** workflow completes (success or failure)
- **THEN** send appropriate customer notification
- **AND** log notification delivery status

### Requirement: Workflow Monitoring
The system SHALL provide comprehensive monitoring for Step Functions executions.

#### Scenario: Execution Tracking
- **WHEN** workflow executes
- **THEN** track state transitions and execution duration
- **AND** provide detailed execution history and logs

#### Scenario: Performance Metrics
- **WHEN** workflows complete
- **THEN** capture success rates, duration metrics, and error patterns
- **AND** display metrics in CloudWatch dashboards

#### Scenario: Error Analysis
- **WHEN** workflow failures occur
- **THEN** capture detailed error context and failed state information
- **AND** enable rapid troubleshooting and resolution

## Implementation Details

### Workflow Architecture
```
Order Submission (API Gateway)
        ↓
    ValidateOrder (Lambda)
        ↓
  CheckValidation (Choice)
        ↓
 ParallelProcessing (Parallel)
    ├── CheckInventory (Lambda)
    └── ProcessPayment (Lambda)
        ↓
   EvaluateResults (Choice)
        ↓
    OrderSuccess/Failure (Lambda)
```

### State Machine Features
- **Parallel Execution**: Inventory and payment processing run concurrently
- **Error Handling**: Comprehensive catch and retry policies
- **Retry Logic**: Exponential backoff for transient failures
- **Choice States**: Dynamic routing based on validation and processing results
- **CloudWatch Integration**: Full execution logging and monitoring

### Lambda Functions
- **order-validation-service**: Validates order structure and business rules
- **payment-service**: Simulates payment processing with realistic failure scenarios
- **inventory-service**: Checks product availability and reserves stock
- **notification-service**: Sends customer notifications for all workflow outcomes

### API Integration
- **POST /orders**: Trigger order processing workflow via API Gateway
- **Authentication**: API key-based authorization
- **Response**: Step Functions execution ARN for tracking

### Native Deployment Support
All workflow Lambda functions support both JVM and GraalVM native deployment modes:
- **JVM Mode**: Traditional Java 21 runtime
- **Native Mode**: GraalVM compiled with provided.al2 runtime for 2-3x faster cold starts