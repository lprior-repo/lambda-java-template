package software.amazonaws.example.product;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Optional;

/**
 * Configuration and utilities for integration tests.
 * Provides helper methods for setting up and tearing down test resources.
 */
public class IntegrationTestConfig {

    /**
     * Gets the AWS region to use for testing.
     * Falls back to us-east-1 if no region is configured.
     */
    public static Region getTestRegion() {
        String region = System.getenv("AWS_REGION");
        if (region == null) {
            region = System.getProperty("aws.region", "us-east-1");
        }
        return Region.of(region);
    }

    /**
     * Gets the DynamoDB table name for products from environment variables.
     */
    public static Optional<String> getProductsTableName() {
        String tableName = System.getenv("PRODUCTS_TABLE_NAME");
        if (tableName == null) {
            tableName = System.getProperty("PRODUCTS_TABLE_NAME");
        }
        return Optional.ofNullable(tableName);
    }

    /**
     * Gets the DynamoDB table name for audit logs from environment variables.
     */
    public static Optional<String> getAuditTableName() {
        String tableName = System.getenv("AUDIT_TABLE_NAME");
        if (tableName == null) {
            tableName = System.getProperty("AUDIT_TABLE_NAME");
        }
        return Optional.ofNullable(tableName);
    }

    /**
     * Creates a DynamoDB client for testing with the configured region.
     */
    public static DynamoDbClient createDynamoDbClient() {
        return DynamoDbClient.builder()
            .region(getTestRegion())
            .build();
    }

    /**
     * Verifies that a DynamoDB table exists and is in ACTIVE status.
     * 
     * @param client DynamoDB client
     * @param tableName Name of the table to check
     * @throws RuntimeException if table doesn't exist or is not active
     */
    public static void verifyTableExists(DynamoDbClient client, String tableName) {
        try {
            DescribeTableRequest request = DescribeTableRequest.builder()
                .tableName(tableName)
                .build();
            
            DescribeTableResponse response = client.describeTable(request);
            
            if (response.table().tableStatus() != TableStatus.ACTIVE) {
                throw new RuntimeException("Table " + tableName + " is not in ACTIVE status: " + 
                    response.table().tableStatus());
            }
            
        } catch (ResourceNotFoundException e) {
            throw new RuntimeException("DynamoDB table " + tableName + " does not exist. " +
                "Please create the table or set the appropriate environment variable.", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to DynamoDB table " + tableName + 
                ". Check AWS credentials and permissions.", e);
        }
    }

    /**
     * Waits for a DynamoDB table to become active.
     * Useful when creating tables programmatically for testing.
     * 
     * @param client DynamoDB client
     * @param tableName Name of the table to wait for
     * @param maxWaitTimeSeconds Maximum time to wait in seconds
     */
    public static void waitForTableActive(DynamoDbClient client, String tableName, int maxWaitTimeSeconds) {
        long startTime = System.currentTimeMillis();
        long maxWaitTime = maxWaitTimeSeconds * 1000L;
        
        while (System.currentTimeMillis() - startTime < maxWaitTime) {
            try {
                DescribeTableRequest request = DescribeTableRequest.builder()
                    .tableName(tableName)
                    .build();
                
                DescribeTableResponse response = client.describeTable(request);
                
                if (response.table().tableStatus() == TableStatus.ACTIVE) {
                    return;
                }
                
                Thread.sleep(2000); // Wait 2 seconds before checking again
                
            } catch (ResourceNotFoundException e) {
                // Table doesn't exist yet, continue waiting
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for table to become active", e);
            } catch (Exception e) {
                throw new RuntimeException("Error checking table status", e);
            }
        }
        
        throw new RuntimeException("Table " + tableName + " did not become active within " + 
            maxWaitTimeSeconds + " seconds");
    }

    /**
     * Utility method to set environment variables for testing.
     * Note: This uses reflection and may not work in all environments.
     * 
     * @param name Environment variable name
     * @param value Environment variable value
     */
    public static void setEnvironmentVariable(String name, String value) {
        try {
            // Try to set system property as fallback
            if (value != null) {
                System.setProperty(name, value);
            } else {
                System.clearProperty(name);
            }
        } catch (Exception e) {
            System.err.println("Failed to set environment variable " + name + ": " + e.getMessage());
        }
    }

    /**
     * Cleanup utility to remove test data from DynamoDB.
     * 
     * @param client DynamoDB client
     * @param tableName Table to clean
     * @param key Key to delete
     */
    public static void cleanupDynamoDbItem(DynamoDbClient client, String tableName, 
                                         java.util.Map<String, AttributeValue> key) {
        try {
            DeleteItemRequest deleteRequest = DeleteItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .build();
            
            client.deleteItem(deleteRequest);
        } catch (Exception e) {
            System.err.println("Failed to cleanup DynamoDB item: " + e.getMessage());
        }
    }

    /**
     * Prints configuration information for debugging integration tests.
     */
    public static void printTestConfiguration() {
        System.out.println("=== Integration Test Configuration ===");
        System.out.println("AWS Region: " + getTestRegion());
        System.out.println("Products Table: " + getProductsTableName().orElse("NOT SET"));
        System.out.println("Audit Table: " + getAuditTableName().orElse("NOT SET"));
        System.out.println("AWS Profile: " + System.getenv("AWS_PROFILE"));
        System.out.println("=====================================");
    }
}