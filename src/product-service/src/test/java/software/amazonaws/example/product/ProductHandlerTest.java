package software.amazonaws.example.product;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for ProductHandler following AAA pattern.
 * Tests all Lambda handler methods with proper mocking and validation.
 */
class ProductHandlerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private ProductService mockProductService;

    @Mock
    private Context mockContext;

    private SpringBootProductHandler productHandler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        productHandler = new SpringBootProductHandler(mockProductService);
        
        // Mock context methods
        when(mockContext.getAwsRequestId()).thenReturn("test-request-id-123");
        when(mockContext.getFunctionName()).thenReturn("product-service-test");
    }

    // Health Check Tests
    @Test
    void handleRequest_HealthCheckEndpoint_ShouldReturnHealthyStatus() {
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

    // GET Product Tests
    @Test
    void handleRequest_GetExistingProduct_ShouldReturnProduct() throws JsonProcessingException {
        // Arrange
        String productId = "123";
        ProductResponse productResponse = new ProductResponse(productId, "Test Product", new BigDecimal("99.99"));
        when(mockProductService.getProduct(productId)).thenReturn(Optional.of(productResponse));
        
        APIGatewayV2HTTPEvent request = createGetRequest("/products/" + productId);

        // Act
        APIGatewayV2HTTPResponse response = productHandler.apply(request);

        // Assert
        assertEquals(200, response.getStatusCode());
        ProductResponse actualProduct = OBJECT_MAPPER.readValue(response.getBody(), ProductResponse.class);
        assertEquals(productId, actualProduct.getId());
        assertEquals("Test Product", actualProduct.getName());
        assertEquals(new BigDecimal("99.99"), actualProduct.getPrice());
        verify(mockProductService).getProduct(productId);
    }

    @Test
    void handleRequest_GetNonExistingProduct_ShouldReturn404() {
        // Arrange
        String productId = "999";
        when(mockProductService.getProduct(productId)).thenReturn(Optional.empty());
        
        APIGatewayV2HTTPEvent request = createGetRequest("/products/" + productId);

        // Act
        APIGatewayV2HTTPResponse response = productHandler.apply(request);

        // Assert
        assertEquals(404, response.getStatusCode());
        assertTrue(response.getBody().contains("Product not found"));
        verify(mockProductService).getProduct(productId);
    }

    @Test
    void handleRequest_GetProductWithInvalidPath_ShouldReturn400() {
        // Arrange
        APIGatewayV2HTTPEvent request = createGetRequest("/products/");

        // Act
        APIGatewayV2HTTPResponse response = productHandler.apply(request);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Invalid product ID"));
        verify(mockProductService, never()).getProduct(anyString());
    }

    // POST Product Tests
    @Test
    void handleRequest_CreateValidProduct_ShouldReturnCreatedProduct() throws JsonProcessingException {
        // Arrange
        CreateProductRequest createRequest = new CreateProductRequest("New Product", new BigDecimal("149.99"));
        ProductResponse createdProduct = new ProductResponse("456", "New Product", new BigDecimal("149.99"));
        when(mockProductService.createProduct(any(CreateProductRequest.class))).thenReturn(createdProduct);
        
        String requestBody = OBJECT_MAPPER.writeValueAsString(createRequest);
        APIGatewayV2HTTPEvent request = createPostRequest("/products", requestBody);

        // Act
        APIGatewayV2HTTPResponse response = productHandler.apply(request);

        // Assert
        assertEquals(201, response.getStatusCode());
        ProductResponse actualProduct = OBJECT_MAPPER.readValue(response.getBody(), ProductResponse.class);
        assertEquals("456", actualProduct.getId());
        assertEquals("New Product", actualProduct.getName());
        assertEquals(new BigDecimal("149.99"), actualProduct.getPrice());
        verify(mockProductService).createProduct(any(CreateProductRequest.class));
    }

    @Test
    void handleRequest_CreateProductWithInvalidJson_ShouldReturn400() {
        // Arrange
        String invalidJson = "{\"name\": \"Product\", \"price\": invalid}";
        APIGatewayV2HTTPEvent request = createPostRequest("/products", invalidJson);

        // Act
        APIGatewayV2HTTPResponse response = productHandler.apply(request);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Invalid JSON format"));
        verify(mockProductService, never()).createProduct(any(CreateProductRequest.class));
    }

    @Test
    void handleRequest_CreateProductWithValidationError_ShouldReturn400() throws JsonProcessingException {
        // Arrange
        CreateProductRequest createRequest = new CreateProductRequest("", new BigDecimal("149.99"));
        when(mockProductService.createProduct(any(CreateProductRequest.class)))
            .thenThrow(new IllegalArgumentException("Product name cannot be null or empty"));
        
        String requestBody = OBJECT_MAPPER.writeValueAsString(createRequest);
        APIGatewayV2HTTPEvent request = createPostRequest("/products", requestBody);

        // Act
        APIGatewayV2HTTPResponse response = productHandler.apply(request);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Product name cannot be null or empty"));
        verify(mockProductService).createProduct(any(CreateProductRequest.class));
    }

    // PUT Product Tests
    @Test
    void handleRequest_UpdateExistingProduct_ShouldReturnUpdatedProduct() throws JsonProcessingException {
        // Arrange
        String productId = "123";
        UpdateProductRequest updateRequest = new UpdateProductRequest("Updated Product", new BigDecimal("199.99"));
        ProductResponse updatedProduct = new ProductResponse(productId, "Updated Product", new BigDecimal("199.99"));
        when(mockProductService.updateProduct(eq(productId), any(UpdateProductRequest.class)))
            .thenReturn(Optional.of(updatedProduct));
        
        String requestBody = OBJECT_MAPPER.writeValueAsString(updateRequest);
        APIGatewayV2HTTPEvent request = createPutRequest("/products/" + productId, requestBody);

        // Act
        APIGatewayV2HTTPResponse response = productHandler.apply(request);

        // Assert
        assertEquals(200, response.getStatusCode());
        ProductResponse actualProduct = OBJECT_MAPPER.readValue(response.getBody(), ProductResponse.class);
        assertEquals(productId, actualProduct.getId());
        assertEquals("Updated Product", actualProduct.getName());
        assertEquals(new BigDecimal("199.99"), actualProduct.getPrice());
        verify(mockProductService).updateProduct(eq(productId), any(UpdateProductRequest.class));
    }

    @Test
    void handleRequest_UpdateNonExistingProduct_ShouldReturn404() throws JsonProcessingException {
        // Arrange
        String productId = "999";
        UpdateProductRequest updateRequest = new UpdateProductRequest("Updated Product", new BigDecimal("199.99"));
        when(mockProductService.updateProduct(eq(productId), any(UpdateProductRequest.class)))
            .thenReturn(Optional.empty());
        
        String requestBody = OBJECT_MAPPER.writeValueAsString(updateRequest);
        APIGatewayV2HTTPEvent request = createPutRequest("/products/" + productId, requestBody);

        // Act
        APIGatewayV2HTTPResponse response = productHandler.apply(request);

        // Assert
        assertEquals(404, response.getStatusCode());
        assertTrue(response.getBody().contains("Product not found"));
        verify(mockProductService).updateProduct(eq(productId), any(UpdateProductRequest.class));
    }

    // DELETE Product Tests
    @Test
    void handleRequest_DeleteExistingProduct_ShouldReturn204() {
        // Arrange
        String productId = "123";
        when(mockProductService.deleteProduct(productId)).thenReturn(true);
        
        APIGatewayV2HTTPEvent request = createDeleteRequest("/products/" + productId);

        // Act
        APIGatewayV2HTTPResponse response = productHandler.apply(request);

        // Assert
        assertEquals(204, response.getStatusCode());
        verify(mockProductService).deleteProduct(productId);
    }

    @Test
    void handleRequest_DeleteNonExistingProduct_ShouldReturn404() {
        // Arrange
        String productId = "999";
        when(mockProductService.deleteProduct(productId)).thenReturn(false);
        
        APIGatewayV2HTTPEvent request = createDeleteRequest("/products/" + productId);

        // Act
        APIGatewayV2HTTPResponse response = productHandler.apply(request);

        // Assert
        assertEquals(404, response.getStatusCode());
        assertTrue(response.getBody().contains("Product not found"));
        verify(mockProductService).deleteProduct(productId);
    }

    // Error Handling Tests
    @Test
    void handleRequest_UnsupportedHttpMethod_ShouldReturn405() {
        // Arrange
        APIGatewayV2HTTPEvent request = createRequest("PATCH", "/products", null);

        // Act
        APIGatewayV2HTTPResponse response = productHandler.apply(request);

        // Assert
        assertEquals(405, response.getStatusCode());
        assertTrue(response.getBody().contains("Method not allowed"));
        verifyNoInteractions(mockProductService);
    }

    @Test
    void handleRequest_InvalidEndpoint_ShouldReturn404() {
        // Arrange
        APIGatewayV2HTTPEvent request = createGetRequest("/invalid-endpoint");

        // Act
        APIGatewayV2HTTPResponse response = productHandler.apply(request);

        // Assert
        assertEquals(404, response.getStatusCode());
        assertTrue(response.getBody().contains("Not found"));
        verifyNoInteractions(mockProductService);
    }

    @Test
    void handleRequest_NullRequest_ShouldHandleGracefully() {
        // Arrange
        APIGatewayV2HTTPEvent request = null;

        // Act
        APIGatewayV2HTTPResponse response = productHandler.apply(request);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Bad Request"));
        verifyNoInteractions(mockProductService);
    }

    @Test
    void handleRequest_ServiceThrowsException_ShouldReturn500() {
        // Arrange
        String productId = "123";
        when(mockProductService.getProduct(productId)).thenThrow(new RuntimeException("Database connection failed"));
        
        APIGatewayV2HTTPEvent request = createGetRequest("/products/" + productId);

        // Act
        APIGatewayV2HTTPResponse response = productHandler.apply(request);

        // Assert
        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("Internal Server Error"));
        verify(mockProductService).getProduct(productId);
    }

    // CORS Headers Tests
    @Test
    void handleRequest_AllResponses_ShouldIncludeCorsHeaders() {
        // Arrange
        APIGatewayV2HTTPEvent request = createGetRequest("/health");

        // Act
        APIGatewayV2HTTPResponse response = productHandler.apply(request);

        // Assert
        Map<String, String> headers = response.getHeaders();
        assertEquals("application/json", headers.get("Content-Type"));
        assertEquals("*", headers.get("Access-Control-Allow-Origin"));
        assertEquals("GET, POST, PUT, DELETE, OPTIONS", headers.get("Access-Control-Allow-Methods"));
        assertEquals("Content-Type, Authorization", headers.get("Access-Control-Allow-Headers"));
    }

    // List Products Test
    @Test
    void handleRequest_ListProducts_ShouldReturnEmptyList() {
        // Arrange
        APIGatewayV2HTTPEvent request = createGetRequest("/products");
        ProductListResponse emptyList = new ProductListResponse(List.of());
        when(mockProductService.getAllProducts()).thenReturn(emptyList);

        // Act
        APIGatewayV2HTTPResponse response = productHandler.apply(request);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("\"products\":[]"));
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
}