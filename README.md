# AWS Lambda Java Template - Serverless Spring Boot

A production-ready AWS Lambda Java template with Spring Boot 3, simplified architecture, and single-script deployment.

## 🎯 Overview

- **Spring Boot 3** with Java 21 for modern Lambda development
- **Terraform Infrastructure** using serverless.tf patterns  
- **Single Deploy Script** - build + package + deploy in one command
- **Clean Architecture** - Product API + Custom Authorizer only
- **100% Test Coverage** with JUnit 5, Mockito, and Terratest
- **GitHub Actions CI/CD** with quality gates and security scanning

## 📁 Structure

```
lambda-java-template/
├── src/
│   ├── product-service/         # Main REST API (CRUD operations)
│   └── authorizer-service/      # API Gateway custom authorizer
├── terraform/                  # Infrastructure as Code
├── deploy.sh                   # Single deployment script
├── scripts/                    # Development utilities  
└── .github/workflows/          # CI/CD automation
```

## 🚀 Quick Start

### Prerequisites
- **Java 21** (Corretto recommended)
- **Maven 3.9+**
- **Terraform 1.13+** 
- **AWS CLI** configured

### Deploy

```bash
# Build and deploy to development
./deploy.sh dev apply

# Plan changes first
./deploy.sh dev plan

# Deploy to production
./deploy.sh prod apply

# Destroy infrastructure
./deploy.sh dev destroy
```

## 🏗️ Architecture

**Core Components:**
- **API Gateway HTTP API** - Cost-optimized REST endpoints
- **2 Lambda Functions** - Product service + Authorizer (Java 21, ARM64)
- **DynamoDB Tables** - Products and audit logs with encryption
- **CloudWatch** - Dashboards, alarms, and structured logging

**API Endpoints:**
```
GET  /health              # Health check (no auth)
GET  /products            # List products (auth required)
POST /products            # Create product (auth required) 
GET  /products/{id}       # Get product (auth required)
PUT  /products/{id}       # Update product (auth required)
DELETE /products/{id}     # Delete product (auth required)
```

## 🔧 Development

### Build & Test
```bash
# Build all services
mvn clean package

# Run tests with coverage
mvn test
# ✅ 80%+ coverage enforced

# Security scan
mvn verify -P security
```

### Code Quality
```bash
# Lint and format
mvn checkstyle:check
mvn spotless:apply

# Security analysis  
mvn org.owasp:dependency-check:check
```

### Local Testing
```bash
# Unit tests
mvn test -Dtest=ProductHandlerTest

# Integration tests (requires AWS creds)
export PRODUCTS_TABLE_NAME=test-products
export AUDIT_TABLE_NAME=test-audit
mvn test -Dtest="*IntegrationTest"

# End-to-end tests
./scripts/run-e2e-tests.sh
```

## 📊 Infrastructure Testing

Comprehensive Terratest validation (15 test cases):

```bash
cd infra-tests
go test -v -timeout 15m -run TestLambdaIntegration
# ✅ Lambda functions deployment
# ✅ API Gateway configuration  
# ✅ DynamoDB tables setup
# ✅ IAM permissions validation
# ✅ CloudWatch monitoring
```

## 🚀 CI/CD Pipeline

**GitHub Actions** with quality gates:
- ✅ Maven build and test (80%+ coverage)
- ✅ Security scanning (OWASP, SpotBugs)
- ✅ Terraform validation and security (tfsec)
- ✅ Infrastructure testing (Terratest)

**Environment Management:**
```bash
# Development
./deploy.sh dev apply

# Staging  
./deploy.sh staging apply

# Production
./deploy.sh prod apply
```

## 🛡️ Security & Compliance

**Automated Security:**
- OWASP dependency scanning (CVSS 7+ fails build)
- Static code analysis (SpotBugs, Checkstyle)
- Infrastructure security scanning (tfsec)
- Encryption at rest (DynamoDB, CloudWatch logs)

**IAM Best Practices:**
- Function-specific execution roles
- Least privilege permissions
- No hardcoded credentials

## ⚡ Performance & Cost

**Optimizations:**
- **ARM64 Lambda** (20% cost reduction)
- **Java 21** with improved startup times
- **HTTP API Gateway** (cheaper than REST)
- **On-demand DynamoDB** (pay-per-use)

**Monitoring:**
- CloudWatch dashboards for all services
- X-Ray distributed tracing
- Custom business metrics
- Cost alerts and optimization

## 🔄 Adding New Services

1. **Create service module:**
   ```bash
   mkdir src/new-service
   # Add pom.xml with parent reference
   ```

2. **Update parent POM:**
   ```xml
   <modules>
     <module>src/new-service</module>
   </modules>
   ```

3. **Update Terraform:**
   ```hcl
   # terraform/locals.tf
   lambda_functions = {
     new_service = {
       name       = "${local.function_base_name}-new-service"
       source_dir = "../build/new-service.jar"
       runtime    = "java21"
       handler    = "com.example.NewServiceHandler::handleRequest"
       routes     = [{ path = "/new", method = "GET", auth = true }]
     }
   }
   ```

## 📝 Configuration

**Environment Variables:**
```bash
# Required for Lambda functions
PRODUCTS_TABLE_NAME=products-${environment}
AUDIT_TABLE_NAME=audit-logs-${environment}
LOG_LEVEL=INFO

# For integration tests
AWS_REGION=us-east-1
AWS_PROFILE=your-profile
```

**Terraform Variables:**
```hcl
# environments/dev.tfvars
project_name = "lambda-java-template"
environment = "dev"
aws_region = "us-east-1"
log_retention_days = 7
```

## 🎯 Testing Strategy

**Unit Tests (Java):**
- JUnit 5 + Mockito for service layer
- 80% coverage minimum (enforced)
- Fast feedback loop

**Integration Tests (Java):**
- Real AWS services (DynamoDB, EventBridge)  
- TestContainers for local development
- Contract testing with REST Assured

**Infrastructure Tests (Go):**
- Terratest for AWS resource validation
- End-to-end workflow testing
- Performance and compliance checks

## 🔗 Key Files

| File | Purpose |
|------|---------|
| `deploy.sh` | Single deployment script |
| `terraform/locals.tf` | Lambda function configuration |
| `terraform/lambda-functions.tf` | Infrastructure definitions |
| `src/product-service/` | Main REST API implementation |
| `src/authorizer-service/` | Custom API Gateway authorizer |
| `.github/workflows/ci.yml` | CI/CD pipeline |
| `infra-tests/` | Infrastructure testing (Terratest) |

## 💡 Best Practices

**Code Quality:**
- Follow TDD practices (Red-Green-Refactor)
- Maintain 100% test coverage for critical paths
- Use functional programming patterns
- Keep functions ≤ 25 lines, complexity ≤ 5

**Infrastructure:**
- All infrastructure as code (Terraform)
- Environment-specific configurations
- Least privilege IAM policies
- Comprehensive monitoring and alerting

**Security:**
- Regular dependency updates
- Automated vulnerability scanning
- Secrets managed via AWS Parameter Store/KMS
- Input validation and sanitization

## 🆘 Troubleshooting

**Common Issues:**

1. **Deployment Fails**
   ```bash
   # Check AWS credentials
   aws sts get-caller-identity
   
   # Validate Terraform
   cd terraform && terraform validate
   ```

2. **Tests Fail**
   ```bash
   # Check environment variables
   echo $PRODUCTS_TABLE_NAME
   
   # Verify DynamoDB tables exist
   aws dynamodb list-tables
   ```

3. **Lambda Errors**
   ```bash
   # View logs
   aws logs tail /aws/lambda/lambda-java-template-dev-product-service --follow
   
   # Check function configuration
   aws lambda get-function --function-name lambda-java-template-dev-product-service
   ```

## 🤝 Contributing

1. **Fork and clone** the repository
2. **Create feature branch** from main
3. **Write tests first** (TDD approach)
4. **Implement feature** with comprehensive tests
5. **Run validation:** `./deploy.sh dev plan && mvn verify`
6. **Submit pull request** with clear description

## 📞 Support

- **Documentation:** Check this README and code comments
- **Issues:** GitHub Issues for bugs and feature requests  
- **AWS Resources:** [Lambda Java Guide](https://docs.aws.amazon.com/lambda/latest/dg/lambda-java.html)
- **Framework:** [Spring Boot on Lambda](https://docs.spring.io/spring-boot/docs/current/reference/html/native-image.html)

---

🚀 **Ready to build production-ready serverless applications with enterprise patterns and comprehensive testing!**