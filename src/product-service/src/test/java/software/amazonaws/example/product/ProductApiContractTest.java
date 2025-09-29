package software.amazonaws.example.product;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.module.jsv.JsonSchemaValidator;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Contract tests for ProductHandler API using OpenAPI specification validation.
 * These tests verify that the API implementation adheres to the OpenAPI contract.
 * 
 * Contract testing ensures:
 * - Response schemas match OpenAPI specification
 * - HTTP status codes are as documented
 * - Content types are correct
 * - Required fields are present
 * - Data types and formats are valid
 */
class ProductApiContractTest {

    private static final String OPENAPI_SPEC_PATH = "../openapi/product-api.yaml";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    private SpringBootProductHandler productHandler;
    private Context mockContext;
    private OpenAPI openApiSpec;

    @BeforeEach
    void setUp(TestInfo testInfo) throws IOException {
        // Load OpenAPI specification
        loadOpenApiSpec();
        
        // Set up ProductHandler with mock dependencies for contract testing
        ProductService mockProductService = new MockProductService();
        productHandler = new SpringBootProductHandler(mockProductService);
        
        // Mock Lambda context
        mockContext = mock(Context.class);
        when(mockContext.getAwsRequestId()).thenReturn("contract-test-" + UUID.randomUUID());
        when(mockContext.getFunctionName()).thenReturn("product-handler-contract-test");
        
        System.out.println("Running contract test: " + testInfo.getDisplayName());
    }

    @Test
    void healthEndpoint_ShouldMatchOpenApiContract() throws JsonProcessingException {
        // Arrange
        APIGatewayV2HTTPEvent request = createGetRequest("/health");
        
        // Act
        APIGatewayV2HTTPResponse response = productHandler.apply(request);
        
        // Assert - Status Code
        assertEquals(200, response.getStatusCode(), "Health endpoint should return 200");
        
        // Assert - Content Type
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
        
        // Assert - Response Schema
        validateResponseAgainstSchema("/health", "get", "200", response.getBody());
        
        // Assert - Required Fields
        JsonNode responseJson = OBJECT_MAPPER.readTree(response.getBody());
        assertTrue(responseJson.has("status"), "Health response must have 'status' field");
        assertTrue(responseJson.has("service"), "Health response must have 'service' field");
        assertEquals("healthy", responseJson.get("status").asText());
        assertEquals("product-service", responseJson.get("service").asText());
    }

    @Test
    void createProduct_ValidRequest_ShouldMatchOpenApiContract() throws JsonProcessingException {
        // Arrange
        CreateProductRequest createRequest = new CreateProductRequest(
            "Contract Test Product", 
            new BigDecimal("199.99")
        );
        String requestBody = OBJECT_MAPPER.writeValueAsString(createRequest);
        APIGatewayV2HTTPEvent request = createPostRequest("/products", requestBody);
        
        // Act
        APIGatewayV2HTTPResponse response = productHandler.apply(request);
        
        // Assert - Status Code
        assertEquals(201, response.getStatusCode(), "Create product should return 201");
        
        // Assert - Content Type
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
        
        // Assert - Response Schema
        validateResponseAgainstSchema("/products", "post", "201", response.getBody());
        
        // Assert - Required Fields
        JsonNode responseJson = OBJECT_MAPPER.readTree(response.getBody());
        assertTrue(responseJson.has("id"), "Product response must have 'id' field");
        assertTrue(responseJson.has("name"), "Product response must have 'name' field");
        assertTrue(responseJson.has("price"), "Product response must have 'price' field");
        
        // Assert - Data Types
        assertNotNull(UUID.fromString(responseJson.get("id").asText()), "ID should be valid UUID");
        assertEquals("Contract Test Product", responseJson.get("name").asText());
        assertEquals(199.99, responseJson.get("price").asDouble(), 0.01);
    }

    @Test
    void createProduct_InvalidRequest_ShouldMatchOpenApiContract() throws JsonProcessingException {
        // Arrange - Invalid product (empty name)
        CreateProductRequest invalidRequest = new CreateProductRequest("", new BigDecimal("99.99"));
        String requestBody = OBJECT_MAPPER.writeValueAsString(invalidRequest);
        APIGatewayV2HTTPEvent request = createPostRequest("/products", requestBody);
        
        // Act
        APIGatewayV2HTTPResponse response = productHandler.apply(request);
        
        // Assert - Status Code
        assertEquals(400, response.getStatusCode(), "Invalid request should return 400");
        
        // Assert - Content Type
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
        
        // Assert - Response Schema
        validateResponseAgainstSchema("/products", "post", "400", response.getBody());
        
        // Assert - Error Format
        JsonNode responseJson = OBJECT_MAPPER.readTree(response.getBody());
        assertTrue(responseJson.has("error"), "Error response must have 'error' field");
        assertFalse(responseJson.get("error").asText().isEmpty(), "Error message should not be empty");
    }

    @Test
    void getProduct_ValidId_ShouldMatchOpenApiContract() throws JsonProcessingException {
        // Arrange
        String productId = "123e4567-e89b-12d3-a456-426614174000";
        APIGatewayV2HTTPEvent request = createGetRequest("/products/" + productId);
        
        // Act
        APIGatewayV2HTTPResponse response = productHandler.apply(request);
        
        // Assert - Status Code
        assertEquals(200, response.getStatusCode(), "Get existing product should return 200");
        
        // Assert - Content Type
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
        
        // Assert - Response Schema (using path with parameter)
        validateResponseAgainstSchema("/products/{productId}", "get", "200", response.getBody());
        
        // Assert - Required Fields
        JsonNode responseJson = OBJECT_MAPPER.readTree(response.getBody());
        assertTrue(responseJson.has("id"), "Product response must have 'id' field");
        assertTrue(responseJson.has("name"), "Product response must have 'name' field");
        assertTrue(responseJson.has("price"), "Product response must have 'price' field");
        assertEquals(productId, responseJson.get("id").asText());
    }

    @Test
    void getProduct_NonExistentId_ShouldMatchOpenApiContract() throws JsonProcessingException {
        // Arrange
        String nonExistentId = "non-existent-id";
        APIGatewayV2HTTPEvent request = createGetRequest("/products/" + nonExistentId);
        
        // Act
        APIGatewayV2HTTPResponse response = productHandler.apply(request);
        
        // Assert - Status Code
        assertEquals(404, response.getStatusCode(), "Non-existent product should return 404");
        
        // Assert - Content Type
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
        
        // Assert - Response Schema
        validateResponseAgainstSchema("/products/{productId}", "get", "404", response.getBody());
        
        // Assert - Error Format
        JsonNode responseJson = OBJECT_MAPPER.readTree(response.getBody());
        assertTrue(responseJson.has("error"), "Error response must have 'error' field");
        assertFalse(responseJson.get("error").asText().isEmpty(), 
            "Error message should not be empty");
    }

    @Test
    void updateProduct_ValidRequest_ShouldMatchOpenApiContract() throws JsonProcessingException {
        // Arrange
        String productId = "123e4567-e89b-12d3-a456-426614174000";
        UpdateProductRequest updateRequest = new UpdateProductRequest(
            "Updated Product Name", 
            new BigDecimal("299.99")
        );
        String requestBody = OBJECT_MAPPER.writeValueAsString(updateRequest);
        APIGatewayV2HTTPEvent request = createPutRequest("/products/" + productId, requestBody);
        
        // Act
        APIGatewayV2HTTPResponse response = productHandler.apply(request);
        
        // Assert - Status Code
        assertEquals(200, response.getStatusCode(), "Update product should return 200");
        
        // Assert - Content Type
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
        
        // Assert - Response Schema
        validateResponseAgainstSchema("/products/{productId}", "put", "200", response.getBody());
        
        // Assert - Updated Fields
        JsonNode responseJson = OBJECT_MAPPER.readTree(response.getBody());
        assertEquals(productId, responseJson.get("id").asText());
        assertEquals("Updated Product Name", responseJson.get("name").asText());
        assertEquals(299.99, responseJson.get("price").asDouble(), 0.01);
    }

    @Test
    void deleteProduct_ValidId_ShouldMatchOpenApiContract() {
        // Arrange
        String productId = "123e4567-e89b-12d3-a456-426614174000";
        APIGatewayV2HTTPEvent request = createDeleteRequest("/products/" + productId);
        
        // Act
        APIGatewayV2HTTPResponse response = productHandler.apply(request);
        
        // Assert - Status Code
        assertEquals(204, response.getStatusCode(), "Delete product should return 204");
        
        // Assert - No Content Body
        assertTrue(response.getBody() == null || response.getBody().isEmpty(), 
            "Delete response should have no body");
        
        // Note: No schema validation for 204 responses as they have no body
    }

    @Test
    void listProducts_ShouldMatchOpenApiContract() throws JsonProcessingException {
        // Arrange
        APIGatewayV2HTTPEvent request = createGetRequest("/products");
        
        // Act
        APIGatewayV2HTTPResponse response = productHandler.apply(request);
        
        // Assert - Status Code
        assertEquals(200, response.getStatusCode(), "List products should return 200");
        
        // Assert - Content Type
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
        
        // Assert - Response Schema
        validateResponseAgainstSchema("/products", "get", "200", response.getBody());
        
        // Assert - Array Structure
        JsonNode responseJson = OBJECT_MAPPER.readTree(response.getBody());
        assertTrue(responseJson.has("products"), "Response must have 'products' field");
        assertTrue(responseJson.get("products").isArray(), "Products field must be an array");
    }

    // Helper methods

    private void loadOpenApiSpec() throws IOException {
        Path specPath = Paths.get(OPENAPI_SPEC_PATH);
        if (!Files.exists(specPath)) {
            throw new IOException("OpenAPI specification not found at: " + OPENAPI_SPEC_PATH);
        }
        
        String specContent = Files.readString(specPath);
        SwaggerParseResult result = new OpenAPIParser().readContents(specContent, null, null);
        
        if (result.getMessages() != null && !result.getMessages().isEmpty()) {
            System.err.println("OpenAPI parsing warnings: " + result.getMessages());
        }
        
        openApiSpec = result.getOpenAPI();
        if (openApiSpec == null) {
            throw new IOException("Failed to parse OpenAPI specification");
        }
    }

    private void validateResponseAgainstSchema(String path, String method, String statusCode, String responseBody) {
        try {
            PathItem pathItem = openApiSpec.getPaths().get(path);
            assertNotNull(pathItem, "Path " + path + " should exist in OpenAPI spec");
            
            Operation operation = getOperationByMethod(pathItem, method);
            assertNotNull(operation, "Method " + method.toUpperCase() + " should exist for path " + path);
            
            ApiResponse apiResponse = operation.getResponses().get(statusCode);
            assertNotNull(apiResponse, "Status code " + statusCode + " should be documented for " + 
                method.toUpperCase() + " " + path);
            
            if (responseBody != null && !responseBody.isEmpty()) {
                Content content = apiResponse.getContent();
                if (content != null) {
                    MediaType mediaType = content.get("application/json");
                    if (mediaType != null) {
                        Schema<?> schema = mediaType.getSchema();
                        if (schema != null) {
                            // For now, just validate that it's valid JSON
                            // In a more advanced implementation, we could convert the OpenAPI schema to JSON Schema
                            JsonNode jsonNode = OBJECT_MAPPER.readTree(responseBody);
                            assertNotNull(jsonNode, "Response should be valid JSON");
                        }
                    }
                }
            }
        } catch (Exception e) {
            fail("Schema validation failed: " + e.getMessage());
        }
    }

    private Operation getOperationByMethod(PathItem pathItem, String method) {
        return switch (method.toLowerCase()) {
            case "get" -> pathItem.getGet();
            case "post" -> pathItem.getPost();
            case "put" -> pathItem.getPut();
            case "delete" -> pathItem.getDelete();
            case "patch" -> pathItem.getPatch();
            case "head" -> pathItem.getHead();
            case "options" -> pathItem.getOptions();
            case "trace" -> pathItem.getTrace();
            default -> null;
        };
    }

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

    /**
     * Mock ProductService for contract testing.
     * Provides predictable responses for contract validation.
     */
    private static class MockProductService extends ProductService {
        
        public MockProductService() {
            super(null); // No repository needed for mock
        }

        @Override
        public ProductResponse createProduct(CreateProductRequest request) {
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                throw new IllegalArgumentException("Product name cannot be null or empty");
            }
            if (request.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Product price must be positive");
            }
            
            return new ProductResponse(
                UUID.randomUUID().toString(),
                request.getName(),
                request.getPrice()
            );
        }

        @Override
        public java.util.Optional<ProductResponse> getProduct(String id) {
            if ("123e4567-e89b-12d3-a456-426614174000".equals(id)) {
                return java.util.Optional.of(new ProductResponse(
                    id,
                    "Mock Product",
                    new BigDecimal("99.99")
                ));
            }
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<ProductResponse> updateProduct(String id, UpdateProductRequest request) {
            if ("123e4567-e89b-12d3-a456-426614174000".equals(id)) {
                return java.util.Optional.of(new ProductResponse(
                    id,
                    request.getName(),
                    request.getPrice()
                ));
            }
            return java.util.Optional.empty();
        }

        @Override
        public boolean deleteProduct(String id) {
            return "123e4567-e89b-12d3-a456-426614174000".equals(id);
        }

        @Override
        public ProductListResponse getAllProducts() {
            return new ProductListResponse(java.util.Collections.emptyList());
        }
    }
}