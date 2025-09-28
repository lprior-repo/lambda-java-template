# Monitoring Capability

## Overview
Comprehensive observability and monitoring for AWS Lambda Java applications using AWS native services and Lambda Powertools.

## Requirements

### Requirement: CloudWatch Dashboards
The system SHALL provide comprehensive dashboards for Lambda function monitoring.

#### Scenario: Lambda Performance Dashboard
- **WHEN** viewing the Lambda dashboard
- **THEN** display duration, errors, invocations, throttles, and concurrent executions metrics
- **AND** provide 5-minute resolution data with customizable time ranges

#### Scenario: Business KPI Dashboard  
- **WHEN** viewing the business KPI dashboard
- **THEN** display application-specific metrics and business operations tracking
- **AND** show success rates and error patterns

### Requirement: CloudWatch Alarms
The system SHALL provide proactive alerting for critical metrics.

#### Scenario: Error Rate Monitoring
- **WHEN** Lambda function errors exceed 5 within 10 minutes
- **THEN** trigger alarm and send SNS notification
- **AND** include function name and error details

#### Scenario: Duration Monitoring
- **WHEN** Lambda function duration exceeds 25 seconds average over 10 minutes
- **THEN** trigger alarm for potential timeout issues
- **AND** alert operations team via SNS

#### Scenario: Throttle Detection
- **WHEN** any Lambda function throttle occurs
- **THEN** immediately trigger alarm
- **AND** notify for capacity planning

### Requirement: AWS Lambda Powertools Integration
The system SHALL use Lambda Powertools for structured observability.

#### Scenario: Structured Logging
- **WHEN** Lambda function executes
- **THEN** emit structured JSON logs with correlation IDs
- **AND** include business context and request metadata

#### Scenario: X-Ray Tracing
- **WHEN** Lambda function processes requests
- **THEN** create distributed traces with custom annotations
- **AND** track performance across service boundaries

#### Scenario: Custom Metrics
- **WHEN** business operations occur
- **THEN** emit structured metrics to CloudWatch
- **AND** enable business intelligence analysis

### Requirement: Log Management
The system SHALL manage logs efficiently with proper retention.

#### Scenario: Log Retention
- **WHEN** Lambda functions generate logs
- **THEN** apply 14-day retention policy for cost optimization
- **AND** ensure compliance with data retention requirements

#### Scenario: Log Parsing
- **WHEN** application logs are generated
- **THEN** extract business metrics via CloudWatch metric filters
- **AND** enable automated analysis and alerting

## Implementation Details

### Files
- `terraform/cloudwatch.tf` - CloudWatch dashboards, alarms, and SNS topics
- `terraform/outputs.tf` - Dashboard URLs and monitoring endpoints
- Lambda Powertools integration in all handler classes

### Key Features
- Real-time monitoring with 5-minute resolution
- Proactive alerting with graduated thresholds
- Cost-optimized log retention policies
- Business intelligence through structured metrics
- Distributed tracing for performance optimization

### Metrics Tracked
- Lambda: Duration, errors, invocations, throttles, concurrent executions
- DynamoDB: Read/write throttles and capacity consumption  
- API Gateway: Request counts, 4XX/5XX errors, latency
- Business: Operations count, success rates, error patterns

### Dashboards
1. **Lambda Dashboard**: Technical metrics for operations teams
2. **Business KPI Dashboard**: Application metrics for stakeholders
3. **Cost Monitoring**: Resource utilization and spend tracking