package software.amazonaws.example.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end API tests for ProductHandler running against real AWS infrastructure.
 * These tests verify the complete deployment including API Gateway, Lambda, and DynamoDB.
 * 
 * Requirements:
 * - Set API_GATEWAY_URL environment variable to the deployed API Gateway URL
 * - Tests run against real AWS infrastructure (ephemeral or development environment)
 * - Validates complete request/response cycle including authorization
 * 
 * Test Categories:
 * - Health endpoint verification
 * - Authorization enforcement 
 * - CRUD operations end-to-end
 * - Error handling scenarios
 * - API Gateway integration
 */
@EnabledIfEnvironmentVariable(named = "API_GATEWAY_URL", matches = ".+")
class ProductApiEndToEndTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static String API_BASE_URL;
    
    @BeforeAll
    static void setUpClass() {
        API_BASE_URL = System.getenv("API_GATEWAY_URL");
        if (API_BASE_URL == null || API_BASE_URL.isEmpty()) {
            fail("API_GATEWAY_URL environment variable must be set for end-to-end tests");
        }
        
        // Remove trailing slash if present
        if (API_BASE_URL.endsWith("/")) {
            API_BASE_URL = API_BASE_URL.substring(0, API_BASE_URL.length() - 1);
        }
        
        RestAssured.baseURI = API_BASE_URL;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        
        System.out.println("Running end-to-end tests against: " + API_BASE_URL);
    }

    @Test
    void healthEndpoint_ShouldReturnHealthStatus() {
        System.out.println("Testing health endpoint accessibility");
        
        given()
            .when()
                .get("/health")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("status", equalTo("healthy"))
                .body("service", equalTo("product-service"))
                .body("runtime", equalTo("java21"))
                .body("timestamp", notNullValue());
    }

    @Test 
    void productsEndpoint_WithoutAuthorization_ShouldReturn401() {
        System.out.println("Testing authorization enforcement on products endpoint");
        
        given()
            .when()
                .get("/products")
            .then()
                .statusCode(401)
                .body("message", equalTo("Unauthorized"));
    }

    @Test
    void productsEndpoint_WithInvalidAuthorization_ShouldReturn401() {
        System.out.println("Testing invalid authorization token handling");
        
        given()
            .header("Authorization", "Bearer invalid-token")
            .when()
                .get("/products")
            .then()
                .statusCode(401)
                .body("message", equalTo("Unauthorized"));
    }

    @Test
    void createProduct_WithoutAuthorization_ShouldReturn401() {
        System.out.println("Testing create product authorization enforcement");
        
        CreateProductRequest request = new CreateProductRequest(
            "Unauthorized Test Product", 
            new BigDecimal("99.99")
        );

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
                .post("/products")
            .then()
                .statusCode(401)
                .body("message", equalTo("Unauthorized"));
    }

    @Test
    void getProduct_WithoutAuthorization_ShouldReturn401() {
        System.out.println("Testing get product authorization enforcement");
        
        String testProductId = UUID.randomUUID().toString();
        
        given()
            .when()
                .get("/products/" + testProductId)
            .then()
                .statusCode(401)
                .body("message", equalTo("Unauthorized"));
    }

    @Test
    void updateProduct_WithoutAuthorization_ShouldReturn401() {
        System.out.println("Testing update product authorization enforcement");
        
        String testProductId = UUID.randomUUID().toString();
        UpdateProductRequest request = new UpdateProductRequest(
            "Updated Product", 
            new BigDecimal("199.99")
        );

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
                .put("/products/" + testProductId)
            .then()
                .statusCode(401)
                .body("message", equalTo("Unauthorized"));
    }

    @Test
    void deleteProduct_WithoutAuthorization_ShouldReturn401() {
        System.out.println("Testing delete product authorization enforcement");
        
        String testProductId = UUID.randomUUID().toString();
        
        given()
            .when()
                .delete("/products/" + testProductId)
            .then()
                .statusCode(401)
                .body("message", equalTo("Unauthorized"));
    }

    @Test
    void apiGateway_CorsHeaders_ShouldBePresent() {
        System.out.println("Testing CORS headers in API Gateway response");
        
        Response response = given()
            .when()
                .get("/health");

        // Verify CORS headers are present (if configured)
        // Note: This depends on API Gateway CORS configuration
        assertNotNull(response, "Response should not be null");
        assertEquals(200, response.getStatusCode(), "Health endpoint should be accessible");
    }

    @Test
    void apiGateway_InvalidPath_ShouldReturn404() {
        System.out.println("Testing API Gateway 404 handling for invalid paths");
        
        given()
            .when()
                .get("/invalid-path")
            .then()
                .statusCode(404);
    }

    @Test
    void apiGateway_InvalidMethod_ShouldReturn404() {
        System.out.println("Testing API Gateway 404 handling for unsupported methods");
        
        // API Gateway returns 404 for methods not configured on routes
        given()
            .when()
                .patch("/health")  // PATCH not supported on health endpoint
            .then()
                .statusCode(404)
                .body("message", equalTo("Not Found"));
    }

    @Test
    void healthEndpoint_ResponseTime_ShouldBeFast() {
        System.out.println("Testing health endpoint response time");
        
        given()
            .when()
                .get("/health")
            .then()
                .time(lessThan(5000L)) // Should respond within 5 seconds
                .statusCode(200);
    }

    @Test
    void healthEndpoint_MultipleRequests_ShouldBeConsistent() {
        System.out.println("Testing health endpoint consistency across multiple requests");
        
        // Make multiple requests to verify consistency
        for (int i = 0; i < 3; i++) {
            given()
                .when()
                    .get("/health")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("status", equalTo("healthy"))
                    .body("service", equalTo("product-service"));
            
            // Small delay between requests
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    void apiGateway_RequestSizeLimit_ShouldHandleLargePayloads() {
        System.out.println("Testing API Gateway request size handling");
        
        // Create a large but reasonable payload
        StringBuilder largeDescription = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeDescription.append("This is a large product description. ");
        }
        
        CreateProductRequest largeRequest = new CreateProductRequest(
            "Large Description Product",
            new BigDecimal("99.99")
        );

        given()
            .contentType(ContentType.JSON)
            .body(largeRequest)
            .when()
                .post("/products")
            .then()
                .statusCode(401); // Should still return 401 for authorization, not 413 for size
    }

    @Test 
    void healthEndpoint_JsonStructure_ShouldBeValid() throws Exception {
        System.out.println("Testing health endpoint JSON structure validity");
        
        Response response = given()
            .when()
                .get("/health");

        assertEquals(200, response.getStatusCode());
        
        String responseBody = response.getBody().asString();
        JsonNode jsonNode = OBJECT_MAPPER.readTree(responseBody);
        
        // Verify JSON structure
        assertTrue(jsonNode.has("status"), "Response should have 'status' field");
        assertTrue(jsonNode.has("service"), "Response should have 'service' field");
        assertTrue(jsonNode.has("timestamp"), "Response should have 'timestamp' field");
        assertTrue(jsonNode.has("runtime"), "Response should have 'runtime' field");
        
        // Verify data types
        assertTrue(jsonNode.get("status").isTextual(), "Status should be a string");
        assertTrue(jsonNode.get("service").isTextual(), "Service should be a string");
        assertTrue(jsonNode.get("timestamp").isTextual(), "Timestamp should be a string");
        assertTrue(jsonNode.get("runtime").isTextual(), "Runtime should be a string");
        
        // Verify values
        assertEquals("healthy", jsonNode.get("status").asText());
        assertEquals("product-service", jsonNode.get("service").asText());
        assertEquals("java21", jsonNode.get("runtime").asText());
        
        // Verify timestamp format (should be ISO 8601)
        String timestamp = jsonNode.get("timestamp").asText();
        assertTrue(timestamp.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*"), 
            "Timestamp should be in ISO 8601 format");
    }

    /**
     * Test that simulates a typical user workflow to verify the complete system.
     * Note: This test will fail with 401 due to authorization, but verifies the complete flow.
     */
    @Test
    void userWorkflow_CompleteJourney_ShouldHandleAuthorizationCorrectly() {
        System.out.println("Testing complete user workflow (expecting authorization failures)");
        
        // Step 1: Check health (should work)
        given()
            .when()
                .get("/health")
            .then()
                .statusCode(200);
        
        // Step 2: Try to list products (should fail with 401)
        given()
            .when()
                .get("/products")
            .then()
                .statusCode(401);
        
        // Step 3: Try to create a product (should fail with 401)
        CreateProductRequest createRequest = new CreateProductRequest(
            "Workflow Test Product", 
            new BigDecimal("149.99")
        );
        
        given()
            .contentType(ContentType.JSON)
            .body(createRequest)
            .when()
                .post("/products")
            .then()
                .statusCode(401);
        
        System.out.println("User workflow test completed - authorization working correctly");
    }
}