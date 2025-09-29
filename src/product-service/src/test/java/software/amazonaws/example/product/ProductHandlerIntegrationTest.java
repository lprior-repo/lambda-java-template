package software.amazonaws.example.product;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for ProductHandler using real AWS DynamoDB.
 * These tests verify the complete Lambda handler flow with real AWS services.
 * 
 * Requirements:
 * - AWS credentials configured (AWS_PROFILE or default credentials)
 * - DynamoDB table exists with name specified in PRODUCTS_TABLE_NAME environment variable
 * - Required IAM permissions for DynamoDB operations
 * 
 * Run with: mvn test -Dtest=ProductHandlerIntegrationTest -DPRODUCTS_TABLE_NAME=your-table-name
 */
@EnabledIfEnvironmentVariable(named = "PRODUCTS_TABLE_NAME", matches = ".+")
class ProductHandlerIntegrationTest {

    private static final String TABLE_NAME = System.getenv("PRODUCTS_TABLE_NAME");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    private SpringBootProductHandler productHandler;
    private Context mockContext;

    @BeforeEach
    void setUp() {
        // Set environment variable for the handler
        setEnvironmentVariable("PRODUCTS_TABLE_NAME", TABLE_NAME);
        
        // Create handler with real AWS services
        DynamoDbClient dynamoDbClient = DynamoDbClient.builder().build();
        ProductRepository productRepository = new ProductRepository(dynamoDbClient, TABLE_NAME);
        ProductService productService = new ProductService(productRepository);
        productHandler = new SpringBootProductHandler(productService);
        
        // Mock Lambda context
        mockContext = mock(Context.class);
        when(mockContext.getAwsRequestId()).thenReturn("integration-test-" + UUID.randomUUID());
        when(mockContext.getFunctionName()).thenReturn("product-handler-integration-test");
    }

    @Test
    void handleRequest_HealthCheck_ShouldReturnHealthStatus() {
        // Arrange
        APIGatewayV2HTTPEvent request = createGetRequest("/health");
        
        // Act
        APIGatewayV2HTTPResponse response = productHandler.apply(request);
        
        // Assert
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("\"status\":\"healthy\""));
        assertTrue(response.getBody().contains("\"service\":\"product-service\""));
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
    }

    @Test
    void handleRequest_CreateAndRetrieveProduct_ShouldPersistAndRetrieve() throws JsonProcessingException {
        // Arrange
        CreateProductRequest createRequest = new CreateProductRequest(
            "Integration Test Product " + UUID.randomUUID(), 
            new BigDecimal("199.99")
        );
        String requestBody = OBJECT_MAPPER.writeValueAsString(createRequest);
        APIGatewayV2HTTPEvent createEvent = createPostRequest("/products", requestBody);
        
        // Act - Create Product
        APIGatewayV2HTTPResponse createResponse = productHandler.apply(createEvent);
        
        // Assert - Create Response
        assertEquals(201, createResponse.getStatusCode());
        ProductResponse createdProduct = OBJECT_MAPPER.readValue(createResponse.getBody(), ProductResponse.class);
        assertNotNull(createdProduct.getId());
        assertEquals(createRequest.getName(), createdProduct.getName());
        assertEquals(createRequest.getPrice(), createdProduct.getPrice());
        
        try {
            // Act - Retrieve Product
            APIGatewayV2HTTPEvent getEvent = createGetRequest("/products/" + createdProduct.getId());
            APIGatewayV2HTTPResponse getResponse = productHandler.apply(getEvent);
            
            // Assert - Retrieve Response
            assertEquals(200, getResponse.getStatusCode());
            ProductResponse retrievedProduct = OBJECT_MAPPER.readValue(getResponse.getBody(), ProductResponse.class);
            assertEquals(createdProduct.getId(), retrievedProduct.getId());
            assertEquals(createdProduct.getName(), retrievedProduct.getName());
            assertEquals(createdProduct.getPrice(), retrievedProduct.getPrice());
            
        } finally {
            // Cleanup
            cleanup(createdProduct.getId());
        }
    }

    @Test
    void handleRequest_CreateUpdateAndRetrieveProduct_ShouldPersistChanges() throws JsonProcessingException {
        // Arrange - Create Product
        CreateProductRequest createRequest = new CreateProductRequest(
            "Original Product " + UUID.randomUUID(), 
            new BigDecimal("99.99")
        );
        String createBody = OBJECT_MAPPER.writeValueAsString(createRequest);
        APIGatewayV2HTTPEvent createEvent = createPostRequest("/products", createBody);
        
        // Act - Create
        APIGatewayV2HTTPResponse createResponse = productHandler.apply(createEvent);
        ProductResponse createdProduct = OBJECT_MAPPER.readValue(createResponse.getBody(), ProductResponse.class);
        
        try {
            // Arrange - Update Product
            UpdateProductRequest updateRequest = new UpdateProductRequest(
                "Updated Product Name", 
                new BigDecimal("299.99")
            );
            String updateBody = OBJECT_MAPPER.writeValueAsString(updateRequest);
            APIGatewayV2HTTPEvent updateEvent = createPutRequest("/products/" + createdProduct.getId(), updateBody);
            
            // Act - Update
            APIGatewayV2HTTPResponse updateResponse = productHandler.apply(updateEvent);
            
            // Assert - Update Response
            assertEquals(200, updateResponse.getStatusCode());
            ProductResponse updatedProduct = OBJECT_MAPPER.readValue(updateResponse.getBody(), ProductResponse.class);
            assertEquals(createdProduct.getId(), updatedProduct.getId());
            assertEquals(updateRequest.getName(), updatedProduct.getName());
            assertEquals(updateRequest.getPrice(), updatedProduct.getPrice());
            
            // Act - Verify Persistence
            APIGatewayV2HTTPEvent getEvent = createGetRequest("/products/" + createdProduct.getId());
            APIGatewayV2HTTPResponse getResponse = productHandler.apply(getEvent);
            
            // Assert - Persistence
            assertEquals(200, getResponse.getStatusCode());
            ProductResponse persistedProduct = OBJECT_MAPPER.readValue(getResponse.getBody(), ProductResponse.class);
            assertEquals(updateRequest.getName(), persistedProduct.getName());
            assertEquals(updateRequest.getPrice(), persistedProduct.getPrice());
            
        } finally {
            // Cleanup
            cleanup(createdProduct.getId());
        }
    }

    @Test
    void handleRequest_CreateAndDeleteProduct_ShouldRemoveFromStorage() throws JsonProcessingException {
        // Arrange - Create Product
        CreateProductRequest createRequest = new CreateProductRequest(
            "To Be Deleted " + UUID.randomUUID(), 
            new BigDecimal("149.99")
        );
        String createBody = OBJECT_MAPPER.writeValueAsString(createRequest);
        APIGatewayV2HTTPEvent createEvent = createPostRequest("/products", createBody);
        
        // Act - Create
        APIGatewayV2HTTPResponse createResponse = productHandler.apply(createEvent);
        ProductResponse createdProduct = OBJECT_MAPPER.readValue(createResponse.getBody(), ProductResponse.class);
        
        // Act - Delete
        APIGatewayV2HTTPEvent deleteEvent = createDeleteRequest("/products/" + createdProduct.getId());
        APIGatewayV2HTTPResponse deleteResponse = productHandler.apply(deleteEvent);
        
        // Assert - Delete Response
        assertEquals(204, deleteResponse.getStatusCode());
        
        // Act - Verify Removal
        APIGatewayV2HTTPEvent getEvent = createGetRequest("/products/" + createdProduct.getId());
        APIGatewayV2HTTPResponse getResponse = productHandler.apply(getEvent);
        
        // Assert - Product No Longer Exists
        assertEquals(404, getResponse.getStatusCode());
        assertTrue(getResponse.getBody().contains("Product not found"));
    }

    @Test
    void handleRequest_GetNonExistentProduct_ShouldReturn404() {
        // Arrange
        String nonExistentId = "non-existent-" + UUID.randomUUID();
        APIGatewayV2HTTPEvent request = createGetRequest("/products/" + nonExistentId);
        
        // Act
        APIGatewayV2HTTPResponse response = productHandler.apply(request);
        
        // Assert
        assertEquals(404, response.getStatusCode());
        assertTrue(response.getBody().contains("Product not found"));
    }

    @Test
    void handleRequest_CreateProductWithInvalidData_ShouldReturn400() throws JsonProcessingException {
        // Arrange - Invalid product (empty name)
        CreateProductRequest invalidRequest = new CreateProductRequest("", new BigDecimal("99.99"));
        String requestBody = OBJECT_MAPPER.writeValueAsString(invalidRequest);
        APIGatewayV2HTTPEvent request = createPostRequest("/products", requestBody);
        
        // Act
        APIGatewayV2HTTPResponse response = productHandler.apply(request);
        
        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Product name cannot be null or empty"));
    }

    @Test
    void handleRequest_CreateProductWithNegativePrice_ShouldReturn400() throws JsonProcessingException {
        // Arrange - Invalid product (negative price)
        CreateProductRequest invalidRequest = new CreateProductRequest(
            "Test Product", 
            new BigDecimal("-10.00")
        );
        String requestBody = OBJECT_MAPPER.writeValueAsString(invalidRequest);
        APIGatewayV2HTTPEvent request = createPostRequest("/products", requestBody);
        
        // Act
        APIGatewayV2HTTPResponse response = productHandler.apply(request);
        
        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Product price must be positive"));
    }

    @Test
    void handleRequest_ListProducts_ShouldReturnEmptyList() {
        // Arrange
        APIGatewayV2HTTPEvent request = createGetRequest("/products");
        
        // Act
        APIGatewayV2HTTPResponse response = productHandler.apply(request);
        
        // Assert
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("\"products\":[]"));
    }

    @Test
    void handleRequest_UnsupportedMethod_ShouldReturn405() {
        // Arrange
        APIGatewayV2HTTPEvent request = createRequest("PATCH", "/products", null);
        
        // Act
        APIGatewayV2HTTPResponse response = productHandler.apply(request);
        
        // Assert
        assertEquals(405, response.getStatusCode());
        assertTrue(response.getBody().contains("Method Not Allowed"));
    }

    @Test
    void handleRequest_InvalidEndpoint_ShouldReturn404() {
        // Arrange
        APIGatewayV2HTTPEvent request = createGetRequest("/invalid-endpoint");
        
        // Act
        APIGatewayV2HTTPResponse response = productHandler.apply(request);
        
        // Assert
        assertEquals(404, response.getStatusCode());
        assertTrue(response.getBody().contains("Endpoint not found"));
    }

    // Helper methods for creating test requests

    private APIGatewayV2HTTPEvent createGetRequest(String path) {
        return createRequest("GET", path, null);
    }

    private APIGatewayV2HTTPEvent createPostRequest(String path, String body) {
        return createRequest("POST", path, body);
    }

    private APIGatewayV2HTTPEvent createPutRequest(String path, String body) {
        return createRequest("PUT", path, body);
    }

    private APIGatewayV2HTTPEvent createDeleteRequest(String path) {
        return createRequest("DELETE", path, null);
    }

    private APIGatewayV2HTTPEvent createRequest(String method, String path, String body) {
        APIGatewayV2HTTPEvent event = new APIGatewayV2HTTPEvent();
        
        APIGatewayV2HTTPEvent.RequestContext requestContext = new APIGatewayV2HTTPEvent.RequestContext();
        APIGatewayV2HTTPEvent.RequestContext.Http http = new APIGatewayV2HTTPEvent.RequestContext.Http();
        http.setMethod(method);
        http.setPath(path);
        requestContext.setHttp(http);
        event.setRequestContext(requestContext);
        
        if (body != null) {
            event.setBody(body);
        }
        
        return event;
    }

    private void cleanup(String productId) {
        try {
            APIGatewayV2HTTPEvent deleteEvent = createDeleteRequest("/products/" + productId);
            productHandler.apply(deleteEvent);
        } catch (Exception e) {
            System.err.println("Failed to cleanup product " + productId + ": " + e.getMessage());
        }
    }

    private void setEnvironmentVariable(String name, String value) {
        // This is a test helper - in real scenarios this would be set externally
        System.setProperty(name, value);
    }
}