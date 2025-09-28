# Environment-Specific Configurations

This directory contains Terraform variable files for different deployment environments.

## Available Environments

- **`dev.tfvars`**: Development environment with basic settings for local testing
- **`staging.tfvars`**: Staging environment with production-like configuration for testing
- **`prod.tfvars`**: Production environment with optimized settings for performance and reliability

## Usage

### Deploy to Development
```bash
cd terraform
./deploy.sh dev plan    # Review changes
./deploy.sh dev apply   # Deploy changes
```

### Deploy to Staging  
```bash
cd terraform
./deploy.sh staging plan    # Review changes
./deploy.sh staging apply   # Deploy changes
```

### Deploy to Production
```bash
cd terraform
./deploy.sh prod plan    # Review changes
./deploy.sh prod apply   # Deploy changes
```

### Destroy Environment
```bash
cd terraform
./deploy.sh [env] destroy  # Destroy all resources
```

## Environment Differences

| Configuration | Dev | Staging | Production |
|---------------|-----|---------|------------|
| Lambda Memory | 512 MB | 1024 MB | 1024 MB |
| Log Retention | 7 days | 14 days | 30 days |
| DynamoDB Billing | Pay-per-request | Pay-per-request | Provisioned |
| X-Ray Tracing | Enabled | Enabled | Enabled |

## Ephemeral Environments

For feature branches and CI/CD, you can create ephemeral environments:

```bash
# Using namespace for unique resource naming
terraform plan -var-file="environments/dev.tfvars" \
               -var="namespace=feature-123" \
               -var="is_ephemeral=true"
```

This creates resources prefixed with the namespace, allowing multiple environments to coexist.

## Customization

To customize an environment:

1. Copy an existing `.tfvars` file
2. Modify the values as needed
3. Use with the deployment script: `./deploy.sh [env-name] plan`

## Variables

See `../variables.tf` for all available configuration options and their validation rules.