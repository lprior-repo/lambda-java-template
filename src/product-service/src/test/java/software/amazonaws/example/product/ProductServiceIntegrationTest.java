package software.amazonaws.example.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ProductService using real AWS DynamoDB.
 * These tests require:
 * - AWS credentials configured (AWS_PROFILE or default credentials)
 * - DynamoDB table exists with name specified in PRODUCTS_TABLE_NAME environment variable
 * - Required IAM permissions for DynamoDB operations
 * 
 * Run with: mvn test -Dtest=ProductServiceIntegrationTest -DPRODUCTS_TABLE_NAME=your-table-name
 */
@EnabledIfEnvironmentVariable(named = "PRODUCTS_TABLE_NAME", matches = ".+")
class ProductServiceIntegrationTest {

    private static final String TABLE_NAME = System.getenv("PRODUCTS_TABLE_NAME");
    
    private DynamoDbClient dynamoDbClient;
    private ProductRepository productRepository;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        assumeTableExists();
        
        dynamoDbClient = DynamoDbClient.builder().build();
        productRepository = new ProductRepository(dynamoDbClient, TABLE_NAME);
        productService = new ProductService(productRepository);
    }

    @Test
    void createProduct_WithValidData_ShouldPersistToDynamoDB() {
        // Arrange
        CreateProductRequest request = new CreateProductRequest(
            "Integration Test Product " + UUID.randomUUID(), 
            new BigDecimal("99.99")
        );

        // Act
        ProductResponse response = productService.createProduct(request);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getId());
        assertEquals(request.getName(), response.getName());
        assertEquals(request.getPrice(), response.getPrice());

        // Verify in DynamoDB
        Optional<ProductResponse> retrieved = productService.getProduct(response.getId());
        assertTrue(retrieved.isPresent());
        assertEquals(response.getId(), retrieved.get().getId());
        assertEquals(response.getName(), retrieved.get().getName());
        assertEquals(response.getPrice(), retrieved.get().getPrice());
        
        // Cleanup
        cleanup(response.getId());
    }

    @Test
    void getProduct_ExistingProduct_ShouldRetrieveFromDynamoDB() {
        // Arrange - Create test product
        CreateProductRequest createRequest = new CreateProductRequest(
            "Test Retrieval Product " + UUID.randomUUID(), 
            new BigDecimal("149.99")
        );
        ProductResponse created = productService.createProduct(createRequest);

        // Act
        Optional<ProductResponse> retrieved = productService.getProduct(created.getId());

        // Assert
        assertTrue(retrieved.isPresent());
        assertEquals(created.getId(), retrieved.get().getId());
        assertEquals(created.getName(), retrieved.get().getName());
        assertEquals(created.getPrice(), retrieved.get().getPrice());
        
        // Cleanup
        cleanup(created.getId());
    }

    @Test
    void getProduct_NonExistentProduct_ShouldReturnEmpty() {
        // Arrange
        String nonExistentId = "non-existent-" + UUID.randomUUID();

        // Act
        Optional<ProductResponse> result = productService.getProduct(nonExistentId);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void updateProduct_ExistingProduct_ShouldModifyInDynamoDB() {
        // Arrange - Create test product
        CreateProductRequest createRequest = new CreateProductRequest(
            "Original Product " + UUID.randomUUID(), 
            new BigDecimal("199.99")
        );
        ProductResponse created = productService.createProduct(createRequest);
        
        UpdateProductRequest updateRequest = new UpdateProductRequest(
            "Updated Product Name", 
            new BigDecimal("299.99")
        );

        // Act
        Optional<ProductResponse> updated = productService.updateProduct(created.getId(), updateRequest);

        // Assert
        assertTrue(updated.isPresent());
        assertEquals(created.getId(), updated.get().getId());
        assertEquals(updateRequest.getName(), updated.get().getName());
        assertEquals(updateRequest.getPrice(), updated.get().getPrice());

        // Verify persistence in DynamoDB
        Optional<ProductResponse> retrieved = productService.getProduct(created.getId());
        assertTrue(retrieved.isPresent());
        assertEquals(updateRequest.getName(), retrieved.get().getName());
        assertEquals(updateRequest.getPrice(), retrieved.get().getPrice());
        
        // Cleanup
        cleanup(created.getId());
    }

    @Test
    void updateProduct_NonExistentProduct_ShouldReturnEmpty() {
        // Arrange
        String nonExistentId = "non-existent-" + UUID.randomUUID();
        UpdateProductRequest updateRequest = new UpdateProductRequest(
            "Updated Name", 
            new BigDecimal("100.00")
        );

        // Act
        Optional<ProductResponse> result = productService.updateProduct(nonExistentId, updateRequest);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void deleteProduct_ExistingProduct_ShouldRemoveFromDynamoDB() {
        // Arrange - Create test product
        CreateProductRequest createRequest = new CreateProductRequest(
            "To Be Deleted " + UUID.randomUUID(), 
            new BigDecimal("49.99")
        );
        ProductResponse created = productService.createProduct(createRequest);

        // Act
        boolean deleted = productService.deleteProduct(created.getId());

        // Assert
        assertTrue(deleted);

        // Verify removal from DynamoDB
        Optional<ProductResponse> retrieved = productService.getProduct(created.getId());
        assertTrue(retrieved.isEmpty());
    }

    @Test
    void deleteProduct_NonExistentProduct_ShouldReturnFalse() {
        // Arrange
        String nonExistentId = "non-existent-" + UUID.randomUUID();

        // Act
        boolean result = productService.deleteProduct(nonExistentId);

        // Assert
        assertFalse(result);
    }

    @Test
    void createProduct_WithInvalidData_ShouldThrowException() {
        // Arrange
        CreateProductRequest invalidRequest = new CreateProductRequest("", new BigDecimal("99.99"));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            productService.createProduct(invalidRequest);
        });
    }

    @Test
    void createProduct_WithNegativePrice_ShouldThrowException() {
        // Arrange
        CreateProductRequest invalidRequest = new CreateProductRequest(
            "Test Product", 
            new BigDecimal("-10.00")
        );

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            productService.createProduct(invalidRequest);
        });
    }

    /**
     * Verifies that the DynamoDB table exists and is accessible.
     * Skips tests if table doesn't exist or is not accessible.
     */
    private void assumeTableExists() {
        try {
            DescribeTableRequest request = DescribeTableRequest.builder()
                .tableName(TABLE_NAME)
                .build();
            
            DescribeTableResponse response = dynamoDbClient.describeTable(request);
            
            // Check if table is active
            if (response.table().tableStatus() != TableStatus.ACTIVE) {
                throw new RuntimeException("Table " + TABLE_NAME + " is not in ACTIVE status: " + 
                    response.table().tableStatus());
            }
            
        } catch (ResourceNotFoundException e) {
            throw new RuntimeException("DynamoDB table " + TABLE_NAME + " does not exist. " +
                "Please create the table or set PRODUCTS_TABLE_NAME environment variable.", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to DynamoDB table " + TABLE_NAME + 
                ". Check AWS credentials and permissions.", e);
        }
    }

    /**
     * Cleanup helper method to remove test data from DynamoDB.
     */
    private void cleanup(String productId) {
        try {
            productService.deleteProduct(productId);
        } catch (Exception e) {
            // Log but don't fail the test
            System.err.println("Failed to cleanup product " + productId + ": " + e.getMessage());
        }
    }
}