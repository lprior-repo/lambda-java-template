package software.amazonaws.example.product;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test class for SpringBootProductHandler following TDD principles.
 * Tests cover all endpoints, error scenarios, and Powertools integration.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SpringBootProductHandler")
class SpringBootProductHandlerTest {

    @Mock
    private ProductService productService;
    
    private SpringBootProductHandler handler;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        handler = new SpringBootProductHandler(productService);
        objectMapper = new ObjectMapper();
    }
    
    private APIGatewayV2HTTPEvent createRequest(String method, String path, String body, Map<String, String> headers) {
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
        
        if (headers != null) {
            event.setHeaders(headers);
        }
        
        return event;
    }
    
    @Nested
    @DisplayName("Health endpoint")
    class HealthEndpoint {
        
        @Test
        @DisplayName("should return healthy status")
        void shouldReturnHealthyStatus() throws Exception {
            // Given
            APIGatewayV2HTTPEvent request = createRequest("GET", "/health", null, null);
            
            // When
            APIGatewayV2HTTPResponse response = handler.apply(request);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(200);
            assertThat(response.getHeaders()).containsEntry("Content-Type", "application/json");
            assertThat(response.getHeaders()).containsKey("x-correlation-id");
            
            Map<String, Object> body = objectMapper.readValue(response.getBody(), new TypeReference<Map<String, Object>>() {});
            assertThat(body).containsEntry("status", "healthy");
            assertThat(body).containsEntry("service", "product-service");
        }
        
        @Test
        @DisplayName("should include correlation ID in response headers")
        void shouldIncludeCorrelationIdInResponseHeaders() {
            // Given
            Map<String, String> headers = Map.of("x-correlation-id", "test-correlation-123");
            APIGatewayV2HTTPEvent request = createRequest("GET", "/health", null, headers);
            
            // When
            APIGatewayV2HTTPResponse response = handler.apply(request);
            
            // Then
            assertThat(response.getHeaders()).containsEntry("x-correlation-id", "test-correlation-123");
        }
    }
    
    @Nested
    @DisplayName("GET /products/{id}")
    class GetProductById {
        
        @Test
        @DisplayName("should return product when found")
        void shouldReturnProductWhenFound() throws Exception {
            // Given
            String productId = "product-123";
            ProductResponse productResponse = new ProductResponse(productId, "Test Product", new BigDecimal("99.99"));
            when(productService.getProduct(productId)).thenReturn(Optional.of(productResponse));
            
            APIGatewayV2HTTPEvent request = createRequest("GET", "/products/" + productId, null, null);
            
            // When
            APIGatewayV2HTTPResponse response = handler.apply(request);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(200);
            assertThat(response.getHeaders()).containsEntry("Content-Type", "application/json");
            
            ProductResponse actualProduct = objectMapper.readValue(response.getBody(), ProductResponse.class);
            assertThat(actualProduct.getId()).isEqualTo(productId);
            assertThat(actualProduct.getName()).isEqualTo("Test Product");
            
            verify(productService).getProduct(productId);
        }
        
        @Test
        @DisplayName("should return 404 when product not found")
        void shouldReturn404WhenProductNotFound() throws Exception {
            // Given
            String productId = "non-existent";
            when(productService.getProduct(productId)).thenReturn(Optional.empty());
            
            APIGatewayV2HTTPEvent request = createRequest("GET", "/products/" + productId, null, null);
            
            // When
            APIGatewayV2HTTPResponse response = handler.apply(request);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(404);
            
            ErrorResponse errorResponse = objectMapper.readValue(response.getBody(), ErrorResponse.class);
            assertThat(errorResponse.getMessage()).isEqualTo("Product not found");
            assertThat(errorResponse.getStatusCode()).isEqualTo(404);
            
            verify(productService).getProduct(productId);
        }
        
        @Test
        @DisplayName("should handle malformed product ID path")
        void shouldHandleMalformedProductIdPath() {
            // Given
            APIGatewayV2HTTPEvent request = createRequest("GET", "/products/", null, null);
            
            // When
            APIGatewayV2HTTPResponse response = handler.apply(request);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(404);
        }
    }
    
    @Nested
    @DisplayName("GET /products")
    class GetAllProducts {
        
        @Test
        @DisplayName("should return all products")
        void shouldReturnAllProducts() throws Exception {
            // Given
            List<ProductResponse> products = List.of(
                new ProductResponse("1", "Product 1", new BigDecimal("10.00")),
                new ProductResponse("2", "Product 2", new BigDecimal("20.00"))
            );
            ProductListResponse productListResponse = new ProductListResponse(products);
            when(productService.getAllProducts()).thenReturn(productListResponse);
            
            APIGatewayV2HTTPEvent request = createRequest("GET", "/products", null, null);
            
            // When
            APIGatewayV2HTTPResponse response = handler.apply(request);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(200);
            
            ProductListResponse actualResponse = objectMapper.readValue(response.getBody(), ProductListResponse.class);
            assertThat(actualResponse.getProducts()).hasSize(2);
            assertThat(actualResponse.getProducts().get(0).getName()).isEqualTo("Product 1");
            
            verify(productService).getAllProducts();
        }
        
        @Test
        @DisplayName("should return empty list when no products exist")
        void shouldReturnEmptyListWhenNoProductsExist() throws Exception {
            // Given
            when(productService.getAllProducts()).thenReturn(new ProductListResponse(List.of()));
            
            APIGatewayV2HTTPEvent request = createRequest("GET", "/products", null, null);
            
            // When
            APIGatewayV2HTTPResponse response = handler.apply(request);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(200);
            
            ProductListResponse productListResponse = objectMapper.readValue(response.getBody(), ProductListResponse.class);
            assertThat(productListResponse.getProducts()).isEmpty();
            
            verify(productService).getAllProducts();
        }
    }
    
    @Nested
    @DisplayName("POST /products")
    class CreateProduct {
        
        @Test
        @DisplayName("should create product successfully")
        void shouldCreateProductSuccessfully() throws Exception {
            // Given
            CreateProductRequest createRequest = new CreateProductRequest("New Product", new BigDecimal("50.00"));
            ProductResponse createdProduct = new ProductResponse("created-123", "New Product", new BigDecimal("50.00"));
            when(productService.createProduct(any(CreateProductRequest.class))).thenReturn(createdProduct);
            
            String requestBody = objectMapper.writeValueAsString(createRequest);
            APIGatewayV2HTTPEvent request = createRequest("POST", "/products", requestBody, null);
            
            // When
            APIGatewayV2HTTPResponse response = handler.apply(request);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(201);
            
            ProductResponse productResponse = objectMapper.readValue(response.getBody(), ProductResponse.class);
            assertThat(productResponse.getId()).isEqualTo("created-123");
            assertThat(productResponse.getName()).isEqualTo("New Product");
            
            verify(productService).createProduct(any(CreateProductRequest.class));
        }
        
        @Test
        @DisplayName("should handle invalid JSON in request body")
        void shouldHandleInvalidJsonInRequestBody() {
            // Given
            String invalidJson = "{ invalid json }";
            APIGatewayV2HTTPEvent request = createRequest("POST", "/products", invalidJson, null);
            
            // When
            APIGatewayV2HTTPResponse response = handler.apply(request);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(500);
            
            try {
                ErrorResponse errorResponse = objectMapper.readValue(response.getBody(), ErrorResponse.class);
                assertThat(errorResponse.getMessage()).isEqualTo("Internal server error");
            } catch (Exception e) {
                // Fallback assertion for malformed error response
                assertThat(response.getBody()).contains("Internal server error");
            }
        }
    }
    
    @Nested
    @DisplayName("PUT /products/{id}")
    class UpdateProduct {
        
        @Test
        @DisplayName("should update product successfully")
        void shouldUpdateProductSuccessfully() throws Exception {
            // Given
            String productId = "update-123";
            UpdateProductRequest updateRequest = new UpdateProductRequest("Updated Product", new BigDecimal("75.00"));
            ProductResponse updatedProduct = new ProductResponse(productId, "Updated Product", new BigDecimal("75.00"));
            when(productService.updateProduct(eq(productId), any(UpdateProductRequest.class))).thenReturn(Optional.of(updatedProduct));
            
            String requestBody = objectMapper.writeValueAsString(updateRequest);
            APIGatewayV2HTTPEvent request = createRequest("PUT", "/products/" + productId, requestBody, null);
            
            // When
            APIGatewayV2HTTPResponse response = handler.apply(request);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(200);
            
            ProductResponse productResponse = objectMapper.readValue(response.getBody(), ProductResponse.class);
            assertThat(productResponse.getId()).isEqualTo(productId);
            assertThat(productResponse.getName()).isEqualTo("Updated Product");
            
            verify(productService).updateProduct(eq(productId), any(UpdateProductRequest.class));
        }
        
        @Test
        @DisplayName("should return 404 when updating non-existent product")
        void shouldReturn404WhenUpdatingNonExistentProduct() throws Exception {
            // Given
            String productId = "non-existent";
            UpdateProductRequest updateRequest = new UpdateProductRequest("Updated Product", new BigDecimal("75.00"));
            when(productService.updateProduct(eq(productId), any(UpdateProductRequest.class))).thenReturn(Optional.empty());
            
            String requestBody = objectMapper.writeValueAsString(updateRequest);
            APIGatewayV2HTTPEvent request = createRequest("PUT", "/products/" + productId, requestBody, null);
            
            // When
            APIGatewayV2HTTPResponse response = handler.apply(request);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(404);
            
            ErrorResponse errorResponse = objectMapper.readValue(response.getBody(), ErrorResponse.class);
            assertThat(errorResponse.getMessage()).isEqualTo("Product not found");
            
            verify(productService).updateProduct(eq(productId), any(UpdateProductRequest.class));
        }
    }
    
    @Nested
    @DisplayName("DELETE /products/{id}")
    class DeleteProduct {
        
        @Test
        @DisplayName("should delete product successfully")
        void shouldDeleteProductSuccessfully() throws Exception {
            // Given
            String productId = "delete-123";
            when(productService.deleteProduct(productId)).thenReturn(true);
            
            APIGatewayV2HTTPEvent request = createRequest("DELETE", "/products/" + productId, null, null);
            
            // When
            APIGatewayV2HTTPResponse response = handler.apply(request);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(204);
            assertThat(response.getBody()).isNullOrEmpty();
            
            verify(productService).deleteProduct(productId);
        }
        
        @Test
        @DisplayName("should return 404 when deleting non-existent product")
        void shouldReturn404WhenDeletingNonExistentProduct() throws Exception {
            // Given
            String productId = "non-existent";
            when(productService.deleteProduct(productId)).thenReturn(false);
            
            APIGatewayV2HTTPEvent request = createRequest("DELETE", "/products/" + productId, null, null);
            
            // When
            APIGatewayV2HTTPResponse response = handler.apply(request);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(404);
            
            ErrorResponse errorResponse = objectMapper.readValue(response.getBody(), ErrorResponse.class);
            assertThat(errorResponse.getMessage()).isEqualTo("Product not found");
            
            verify(productService).deleteProduct(productId);
        }
    }
    
    @Nested
    @DisplayName("HTTP Method handling")
    class HttpMethodHandling {
        
        @Test
        @DisplayName("should return 405 for unsupported HTTP methods")
        void shouldReturn405ForUnsupportedHttpMethods() throws Exception {
            // Given
            APIGatewayV2HTTPEvent request = createRequest("PATCH", "/products", null, null);
            
            // When
            APIGatewayV2HTTPResponse response = handler.apply(request);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(405);
            
            ErrorResponse errorResponse = objectMapper.readValue(response.getBody(), ErrorResponse.class);
            assertThat(errorResponse.getMessage()).isEqualTo("Method not allowed");
        }
        
        @Test
        @DisplayName("should return 404 for unknown paths")
        void shouldReturn404ForUnknownPaths() throws Exception {
            // Given
            APIGatewayV2HTTPEvent request = createRequest("GET", "/unknown", null, null);
            
            // When
            APIGatewayV2HTTPResponse response = handler.apply(request);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(404);
            
            ErrorResponse errorResponse = objectMapper.readValue(response.getBody(), ErrorResponse.class);
            assertThat(errorResponse.getMessage()).isEqualTo("Not found");
        }
    }
    
    @Nested
    @DisplayName("CORS headers")
    class CorsHeaders {
        
        @Test
        @DisplayName("should include CORS headers in successful responses")
        void shouldIncludeCorsHeadersInSuccessfulResponses() {
            // Given
            APIGatewayV2HTTPEvent request = createRequest("GET", "/health", null, null);
            
            // When
            APIGatewayV2HTTPResponse response = handler.apply(request);
            
            // Then
            assertThat(response.getHeaders()).containsEntry("Access-Control-Allow-Origin", "*");
            assertThat(response.getHeaders()).containsEntry("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            assertThat(response.getHeaders()).containsEntry("Access-Control-Allow-Headers", "Content-Type, Authorization");
        }
        
        @Test
        @DisplayName("should include CORS headers in error responses")
        void shouldIncludeCorsHeadersInErrorResponses() {
            // Given
            APIGatewayV2HTTPEvent request = createRequest("GET", "/unknown", null, null);
            
            // When
            APIGatewayV2HTTPResponse response = handler.apply(request);
            
            // Then
            assertThat(response.getHeaders()).containsEntry("Access-Control-Allow-Origin", "*");
            assertThat(response.getHeaders()).containsEntry("Content-Type", "application/json");
        }
    }
    
    @Nested
    @DisplayName("Exception handling")
    class ExceptionHandling {
        
        @Test
        @DisplayName("should handle service layer exceptions gracefully")
        void shouldHandleServiceLayerExceptionsGracefully() throws Exception {
            // Given
            when(productService.getAllProducts()).thenThrow(new RuntimeException("Database connection failed"));
            
            APIGatewayV2HTTPEvent request = createRequest("GET", "/products", null, null);
            
            // When
            APIGatewayV2HTTPResponse response = handler.apply(request);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(500);
            
            ErrorResponse errorResponse = objectMapper.readValue(response.getBody(), ErrorResponse.class);
            assertThat(errorResponse.getMessage()).isEqualTo("Internal server error");
            assertThat(errorResponse.getStatusCode()).isEqualTo(500);
        }
    }
}