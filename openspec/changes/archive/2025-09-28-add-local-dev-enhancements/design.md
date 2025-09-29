# Local Development Enhancement Design

## Context

The current GraalVM Lambda template prioritizes production optimization (cold start performance, cost efficiency) but lacks developer velocity tools. Developers face a choice between slow native compilation for production-like testing or JVM development with runtime environment differences.

Key constraints:
- GraalVM native compilation (~45 seconds) breaks TDD red-green-refactor cycles
- SAM local invoke/start-api works with JVM but not native images effectively
- Feature flags are hardcoded, requiring redeployment for changes
- Development/production runtime parity challenges

## Goals / Non-Goals

### Goals
- **Sub-5 second feedback loops**: Hot reloading for code changes in development
- **Production parity testing**: Easy native image testing when needed
- **Runtime feature management**: AWS AppConfig integration for dynamic configuration
- **Enhanced debugging**: Support both local JVM debugging and containerized native debugging
- **Streamlined workflows**: Task-based commands for common development patterns

### Non-Goals
- Replacing production native images with JVM (performance regression)
- Complete local AWS service emulation (use real AWS services with dev environment)
- Supporting non-GraalVM runtimes (scope limited to existing template focus)

## Decisions

### Decision: Dual Development Mode Strategy
**What**: Support both JVM (hot reload) and native (production-like) development modes
**Why**: Enables fast iteration while maintaining production parity validation
**Alternatives considered**: 
- JVM-only development: Loses production parity
- Native-only development: Too slow for TDD
- Container-based hot reload: Complex setup, slower than JVM

### Decision: AWS AppConfig for Feature Flags
**What**: Use AWS AppConfig with polling-based client for runtime feature management
**Why**: Native AWS service, supports gradual rollouts, integrates with CloudWatch
**Alternatives considered**:
- Environment variables: Static, requires redeployment
- DynamoDB feature flags: Custom implementation overhead
- LaunchDarkly: External dependency, cost

### Decision: File Watcher + Ephemeral AWS Deployment
**What**: Use file system watchers to trigger automatic deployment to ephemeral AWS environments
**Why**: Tests against real AWS services, maintains production parity, enables live debugging
**Alternatives considered**:
- SAM local: Doesn't test against real AWS services, limited debugging options
- Docker local stack: Complex setup, still not real AWS

### Decision: Spring Boot 3 Native Strategy
**What**: Use Spring Boot 3 with native compilation for development experience and production performance
**Why**: Excellent development tooling (DevTools, actuator, configuration), mature GraalVM native support, AWS Lambda optimizations
**Alternatives considered**:
- Plain Maven: No development tooling, manual configuration
- Quarkus: Less mature ecosystem, different programming model
- Micronaut: Smaller community, limited development tools

## Architecture

### Development Workflow
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Code Change   │───▶│  File Watcher   │───▶│   Auto Rebuild  │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                                                        │
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│ Live AWS Lambda │◀───│ Terraform Apply│◀───│Spring Boot JAR  │
│ (Spring Boot)   │    │  (Ephemeral)    │    │   (JVM/Native)  │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

### Feature Flag Architecture
```
┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐
│  Lambda Function │────▶│  AppConfig Agent │────▶│   AWS AppConfig  │
│                  │     │   (Polling)      │     │   (Feature Store)│
└──────────────────┘     └──────────────────┘     └──────────────────┘
                                  │
                         ┌──────────────────┐
                         │ Local Cache      │
                         │ (30s TTL)        │
                         └──────────────────┘
```

### Live Debugging Architecture
```
Ephemeral Environment (JVM):
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│    IDE      │───▶│Debug Tunnel │───▶│Lambda Layer │───▶│Live AWS     │
│ (IntelliJ)  │    │(SSH/WebSock)│    │(Debug Agent)│    │Lambda (JVM) │
└─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘

Production Testing (Native):
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│    IDE      │───▶│Debug Tunnel │───▶│Lambda Layer │───▶│Live AWS     │
│             │    │(Limited)    │    │(Log Agent) │    │Lambda(Native)│
└─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
```

## Implementation Strategy

### Phase 1: Spring Boot 3 Foundation
1. Migrate to Spring Boot 3 with native compilation support
2. Add Spring Boot configuration profiles for development and production
3. Create Terraform configuration for ephemeral environments with namespace isolation
4. Implement file watching with automatic deployment to ephemeral AWS
5. Add ephemeral environment management Task commands

### Phase 2: Feature Flag Integration
1. Create AWS AppConfig infrastructure in Terraform
2. Implement AppConfig client library integrated with Spring Boot configuration
3. Add feature flag examples using Spring Boot conditional beans
4. Create feature flag management Task commands

### Phase 3: Live Lambda Debugging
1. Create Lambda debugging layer with debug agent
2. Implement debug tunnel connection for IDE integration
3. Add debugging guides and IDE configuration examples for remote debugging
4. Implement live debugging Task commands

## Risks / Trade-offs

### Risk: Development/Production Runtime Differences
**Mitigation**: 
- Mandatory native testing before production deployment
- Automated tests running against both JVM and native builds
- Clear documentation on runtime differences

### Risk: AppConfig Polling Latency
**Mitigation**:
- 30-second default polling interval with configurable override
- Local cache to prevent service unavailability
- Circuit breaker pattern for AppConfig failures

### Risk: File Watcher Performance Impact
**Mitigation**:
- Configurable file watching patterns to exclude build artifacts
- Debouncing to prevent excessive rebuilds
- Easy enable/disable for file watching

## Migration Plan

### For Existing Projects
1. **Optional adoption**: All enhancements are opt-in via new Task commands
2. **Gradual rollout**: Developers can adopt features incrementally
3. **Backward compatibility**: Existing workflows unchanged

### For New Projects
1. **Template includes**: All local development enhancements included by default
2. **Documentation**: Getting started guide includes development workflow
3. **Examples**: Sample feature flags and debugging configurations

## Open Questions

1. **File watching scope**: Which file patterns should trigger rebuilds? (*.java, *.xml, *.yaml)
2. **AppConfig environment strategy**: Separate AppConfig environments per deployment environment?
3. **Native debugging frequency**: How often do developers need native debugging vs. JVM debugging?
4. **IDE integration depth**: Should we provide IDE-specific configurations (IntelliJ, VSCode)?