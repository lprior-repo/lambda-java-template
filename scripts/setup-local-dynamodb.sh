#!/bin/bash
set -euo pipefail

# Setup local DynamoDB tables for development
# Run this script after starting docker-compose.local.yml

echo "🚀 Setting up local DynamoDB tables for development..."

DYNAMODB_ENDPOINT="http://localhost:8000"

# Wait for DynamoDB Local to be ready
echo "⏳ Waiting for DynamoDB Local to be ready..."
until curl -s "$DYNAMODB_ENDPOINT" > /dev/null 2>&1; do
    echo "  DynamoDB Local not ready yet, waiting..."
    sleep 2
done
echo "✅ DynamoDB Local is ready!"

# Create Products table
echo "📦 Creating products-local table..."
aws dynamodb create-table \
    --endpoint-url "$DYNAMODB_ENDPOINT" \
    --table-name products-local \
    --attribute-definitions \
        AttributeName=id,AttributeType=S \
    --key-schema \
        AttributeName=id,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST \
    --region us-east-1 || echo "Table products-local may already exist"

# Create Audit Logs table
echo "📝 Creating audit-logs-local table..."
aws dynamodb create-table \
    --endpoint-url "$DYNAMODB_ENDPOINT" \
    --table-name audit-logs-local \
    --attribute-definitions \
        AttributeName=id,AttributeType=S \
        AttributeName=timestamp,AttributeType=S \
    --key-schema \
        AttributeName=id,KeyType=HASH \
        AttributeName=timestamp,KeyType=RANGE \
    --billing-mode PAY_PER_REQUEST \
    --region us-east-1 || echo "Table audit-logs-local may already exist"

# Insert sample data
echo "🌱 Inserting sample data..."
aws dynamodb put-item \
    --endpoint-url "$DYNAMODB_ENDPOINT" \
    --table-name products-local \
    --item '{
        "id": {"S": "sample-product-1"},
        "name": {"S": "Sample Product"},
        "description": {"S": "This is a sample product for testing"},
        "price": {"N": "29.99"},
        "category": {"S": "Electronics"},
        "createdAt": {"S": "'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"},
        "updatedAt": {"S": "'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"}
    }' \
    --region us-east-1

aws dynamodb put-item \
    --endpoint-url "$DYNAMODB_ENDPOINT" \
    --table-name products-local \
    --item '{
        "id": {"S": "sample-product-2"},
        "name": {"S": "Debug Product"},
        "description": {"S": "Another product for debugging"},
        "price": {"N": "19.99"},
        "category": {"S": "Software"},
        "createdAt": {"S": "'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"},
        "updatedAt": {"S": "'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"}
    }' \
    --region us-east-1

echo ""
echo "✅ Local DynamoDB setup complete!"
echo ""
echo "🔗 Access DynamoDB Admin UI at: http://localhost:8001"
echo "🔗 DynamoDB Local endpoint: http://localhost:8000"
echo ""
echo "🚀 Start your Spring Boot app with:"
echo "   mvn spring-boot:run -Dspring-boot.run.profiles=local"
echo ""
echo "🌐 Then access your API at:"
echo "   http://localhost:8080/api/products"
echo "   http://localhost:8080/api/health"