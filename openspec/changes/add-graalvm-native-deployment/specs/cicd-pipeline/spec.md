## ADDED Requirements

### Requirement: Native Deployment Configuration
The system SHALL support deploying Lambda functions as native images with provided.al2 runtime.

#### Scenario: Native Runtime Selection
- **WHEN** native deployment mode is enabled via environment configuration
- **THEN** use provided.al2 runtime instead of java21
- **AND** deploy native ZIP packages containing bootstrap scripts and native binaries

#### Scenario: Conditional Deployment Mode
- **WHEN** deployment is configured
- **THEN** support both native and JVM deployment modes via toggle
- **AND** maintain backward compatibility with existing JVM deployments