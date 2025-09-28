#!/bin/bash

# Terraform Deployment Script for Multiple Environments
# Usage: ./deploy.sh [environment] [action]
# Environment: dev, staging, prod
# Action: plan, apply, destroy

set -e

# Default values
ENVIRONMENT=${1:-dev}
ACTION=${2:-plan}

# Validate environment
case $ENVIRONMENT in
  dev|staging|prod)
    echo "✅ Deploying to $ENVIRONMENT environment"
    ;;
  *)
    echo "❌ Error: Invalid environment. Use: dev, staging, or prod"
    exit 1
    ;;
esac

# Validate action
case $ACTION in
  plan|apply|destroy)
    echo "✅ Running terraform $ACTION"
    ;;
  *)
    echo "❌ Error: Invalid action. Use: plan, apply, or destroy"
    exit 1
    ;;
esac

# Set variables file
VARS_FILE="environments/${ENVIRONMENT}.tfvars"

if [ ! -f "$VARS_FILE" ]; then
  echo "❌ Error: Variables file $VARS_FILE not found"
  exit 1
fi

# Initialize Terraform if needed
if [ ! -d ".terraform" ]; then
  echo "🔧 Initializing Terraform..."
  terraform init
fi

# Run Terraform command
echo "🚀 Running terraform $ACTION for $ENVIRONMENT environment..."
echo "📋 Using variables from: $VARS_FILE"

case $ACTION in
  plan)
    terraform plan -var-file="$VARS_FILE" -out="${ENVIRONMENT}.tfplan"
    echo "✅ Plan complete. Review the changes above."
    echo "💡 To apply: ./deploy.sh $ENVIRONMENT apply"
    ;;
  apply)
    if [ -f "${ENVIRONMENT}.tfplan" ]; then
      terraform apply "${ENVIRONMENT}.tfplan"
      rm -f "${ENVIRONMENT}.tfplan"
    else
      terraform apply -var-file="$VARS_FILE"
    fi
    echo "✅ Deployment to $ENVIRONMENT complete!"
    ;;
  destroy)
    echo "⚠️  WARNING: This will destroy all resources in $ENVIRONMENT!"
    echo "⚠️  Type 'yes' to confirm destruction:"
    read -r confirmation
    if [ "$confirmation" = "yes" ]; then
      terraform destroy -var-file="$VARS_FILE"
      echo "💥 Resources in $ENVIRONMENT destroyed"
    else
      echo "❌ Destruction cancelled"
      exit 1
    fi
    ;;
esac