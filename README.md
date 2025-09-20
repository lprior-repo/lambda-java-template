# Lambda Java Template

This template provides a complete setup for AWS Lambda functions written in Java, using Terraform for infrastructure management.

## Structure

```
.
├── src/
│   ├── hello/          # Hello Lambda function
│   │   ├── pom.xml
│   │   └── src/main/java/com/example/hello/
│   │       └── HelloHandler.java
│   └── users/          # Users Lambda function
│       ├── pom.xml
│       └── src/main/java/com/example/users/
│           └── UsersHandler.java
├── terraform/          # Terraform infrastructure
│   ├── main.tf
│   ├── variables.tf
│   └── outputs.tf
├── .github/
│   └── workflows/
│       └── build.yml   # GitHub Actions CI/CD
├── pom.xml             # Parent POM
├── Makefile
└── README.md
```

## Prerequisites

- Java 17+
- Maven 3.8+
- Terraform >= 1.0
- AWS CLI configured
- Make (optional, for using Makefile commands)

## Getting Started

1. **Clone this template**
   ```bash
   git clone <this-repo>
   cd lambda-java-template
   ```

2. **Install dependencies**
   ```bash
   mvn dependency:resolve
   # or
   make deps
   ```

3. **Build Lambda functions**
   ```bash
   mvn clean package
   # or
   make package
   ```

4. **Deploy infrastructure**
   ```bash
   cd terraform
   terraform init
   terraform plan
   terraform apply
   # or
   make deploy
   ```

## Development

### Adding a New Function

1. Create a new directory under `src/` (e.g., `src/orders/`)
2. Add your `pom.xml` and Java handler class
3. Add the module to the parent `pom.xml`
4. Add the function to `terraform/main.tf`
5. The build process will automatically build the new function

### Building

```bash
# Build all functions
mvn clean package
# or
make package

# The build process will:
# - Compile Java sources
# - Run tests
# - Create shaded JAR files in each target/ directory
# - Copy JARs to build/ directory for Terraform
```

### Testing

```bash
# Run tests for all functions
mvn test
# or
make test

# Run tests for a specific function
cd src/hello
mvn test
```

### Code Quality

```bash
# Run code quality checks
make lint

# Individual tools
mvn checkstyle:check    # Style checking
mvn spotbugs:check     # Bug detection
```

### Security

```bash
# Run security scan
make security
# or
mvn org.owasp:dependency-check-maven:check
```

## CI/CD

The GitHub Actions workflow automatically:
- Detects changed functions
- Builds each function in parallel using Maven
- Runs tests and code quality checks
- Performs security scans
- Creates deployment JAR files
- Uploads build artifacts

## Terraform Configuration

The infrastructure uses:
- **terraform-aws-modules/lambda/aws** for Lambda functions
- **terraform-aws-modules/apigateway-v2/aws** for API Gateway
- Pre-built JAR packages (no building in Terraform)

### Customization

Edit `terraform/variables.tf` to customize:
- AWS region
- Function names
- Environment settings

## API Endpoints

After deployment, you'll get:
- `GET /hello` - Hello function
- `GET /users` - Users function

## Java Lambda Best Practices

### Handler Implementation
- Implement `RequestHandler<I, O>` interface
- Use specific event types (e.g., `APIGatewayProxyRequestEvent`)
- Handle exceptions gracefully
- Use structured logging

### Dependencies
- Use AWS Lambda Java Core library
- Include only necessary dependencies
- Use Maven Shade plugin for fat JARs
- Consider GraalVM for native compilation (advanced)

### Performance
- Minimize cold start time
- Use dependency injection sparingly
- Initialize expensive resources outside handler method
- Consider provisioned concurrency for critical functions

### Memory and Timeout
- Java functions typically need more memory (512MB+)
- Set appropriate timeouts (30s+ for complex operations)
- Monitor memory usage and adjust accordingly

## Cost Optimization

- Functions use Java 17 runtime
- CloudWatch logs have 14-day retention
- API Gateway uses HTTP API (cheaper than REST API)
- Consider ARM64 architecture for cost savings

## Security

- IAM roles follow least privilege principle
- CloudWatch logs enabled for monitoring
- OWASP dependency checking in CI/CD
- Checkstyle and SpotBugs for code quality

## Maven Modules

This project uses a multi-module Maven structure:
- Parent POM manages dependencies and plugins
- Each function is a separate module
- Shared dependencies are managed centrally
- Consistent build configuration across functions