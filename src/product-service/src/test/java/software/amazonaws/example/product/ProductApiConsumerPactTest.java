package software.amazonaws.example.product;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pact Consumer Tests for Product API
 * 
 * These tests represent the consumer's expectations of the Product API provider.
 * They generate pact files that can be used to verify the provider implementation.
 * 
 * Consumer-driven contract testing with Pact ensures:
 * - API compatibility between consumer and provider
 * - Provider changes don't break consumer expectations
 * - Clear documentation of API usage patterns
 * - Fast feedback on contract violations
 */
@ExtendWith(PactConsumerTestExt.class)
class ProductApiConsumerPactTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    /**
     * Pact: Health endpoint should return service status
     */
    @Pact(consumer = "product-api-consumer", provider = "product-api-provider")
    V4Pact healthEndpointPact(PactDslWithProvider builder) {
        return builder
            .given("service is healthy")
            .uponReceiving("a request for health status")
            .path("/health")
            .method("GET")
            .willRespondWith()
            .status(200)
            .headers(Map.of("Content-Type", "application/json"))
            .body(new PactDslJsonBody()
                .stringType("status", "healthy")
                .stringType("service", "product-service"))
            .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "healthEndpointPact")
    void testHealthEndpoint(MockServer mockServer) {
        RestAssured.baseURI = mockServer.getUrl();
        
        given()
            .when()
            .get("/health")
            .then()
            .statusCode(200)
            .contentType("application/json")
            .body("status", equalTo("healthy"))
            .body("service", equalTo("product-service"));
    }

    /**
     * Pact: Create product with valid data should return created product
     */
    @Pact(consumer = "product-api-consumer", provider = "product-api-provider")
    V4Pact createProductSuccessPact(PactDslWithProvider builder) {
        return builder
            .given("valid product data is provided")
            .uponReceiving("a request to create a product")
            .path("/products")
            .method("POST")
            .headers(Map.of("Content-Type", "application/json"))
            .body(new PactDslJsonBody()
                .stringType("name", "Test Product")
                .numberType("price", 99.99))
            .willRespondWith()
            .status(201)
            .headers(Map.of("Content-Type", "application/json"))
            .body(new PactDslJsonBody()
                .uuid("id")
                .stringType("name", "Test Product")
                .numberType("price", 99.99))
            .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "createProductSuccessPact")
    void testCreateProductSuccess(MockServer mockServer) throws JsonProcessingException {
        RestAssured.baseURI = mockServer.getUrl();
        
        Map<String, Object> productData = new HashMap<>();
        productData.put("name", "Test Product");
        productData.put("price", 99.99);
        
        given()
            .contentType("application/json")
            .body(OBJECT_MAPPER.writeValueAsString(productData))
            .when()
            .post("/products")
            .then()
            .statusCode(201)
            .contentType("application/json")
            .body("id", notNullValue())
            .body("name", equalTo("Test Product"))
            .body("price", equalTo(99.99f));
    }

    /**
     * Pact: Create product with invalid data should return validation error
     */
    @Pact(consumer = "product-api-consumer", provider = "product-api-provider")
    V4Pact createProductValidationErrorPact(PactDslWithProvider builder) {
        return builder
            .given("invalid product data is provided")
            .uponReceiving("a request to create a product with empty name")
            .path("/products")
            .method("POST")
            .headers(Map.of("Content-Type", "application/json"))
            .body(new PactDslJsonBody()
                .stringType("name", "")
                .numberType("price", 99.99))
            .willRespondWith()
            .status(400)
            .headers(Map.of("Content-Type", "application/json"))
            .body(new PactDslJsonBody()
                .stringType("error")
                .numberType("statusCode", 400))
            .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "createProductValidationErrorPact")
    void testCreateProductValidationError(MockServer mockServer) throws JsonProcessingException {
        RestAssured.baseURI = mockServer.getUrl();
        
        Map<String, Object> invalidProductData = new HashMap<>();
        invalidProductData.put("name", "");
        invalidProductData.put("price", 99.99);
        
        given()
            .contentType("application/json")
            .body(OBJECT_MAPPER.writeValueAsString(invalidProductData))
            .when()
            .post("/products")
            .then()
            .statusCode(400)
            .contentType("application/json")
            .body("error", notNullValue())
            .body("statusCode", equalTo(400));
    }

    /**
     * Pact: Get existing product should return product details
     */
    @Pact(consumer = "product-api-consumer", provider = "product-api-provider")
    V4Pact getProductSuccessPact(PactDslWithProvider builder) {
        return builder
            .given("product with ID 123e4567-e89b-12d3-a456-426614174000 exists")
            .uponReceiving("a request to get product by ID")
            .path("/products/123e4567-e89b-12d3-a456-426614174000")
            .method("GET")
            .willRespondWith()
            .status(200)
            .headers(Map.of("Content-Type", "application/json"))
            .body(new PactDslJsonBody()
                .stringValue("id", "123e4567-e89b-12d3-a456-426614174000")
                .stringType("name", "Existing Product")
                .numberType("price", 149.99))
            .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "getProductSuccessPact")
    void testGetProductSuccess(MockServer mockServer) {
        RestAssured.baseURI = mockServer.getUrl();
        
        given()
            .when()
            .get("/products/123e4567-e89b-12d3-a456-426614174000")
            .then()
            .statusCode(200)
            .contentType("application/json")
            .body("id", equalTo("123e4567-e89b-12d3-a456-426614174000"))
            .body("name", equalTo("Existing Product"))
            .body("price", equalTo(149.99f));
    }

    /**
     * Pact: Get non-existent product should return 404
     */
    @Pact(consumer = "product-api-consumer", provider = "product-api-provider")
    V4Pact getProductNotFoundPact(PactDslWithProvider builder) {
        return builder
            .given("product with ID non-existent-id does not exist")
            .uponReceiving("a request to get non-existent product")
            .path("/products/non-existent-id")
            .method("GET")
            .willRespondWith()
            .status(404)
            .headers(Map.of("Content-Type", "application/json"))
            .body(new PactDslJsonBody()
                .stringType("error", "Product not found")
                .numberType("statusCode", 404))
            .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "getProductNotFoundPact")
    void testGetProductNotFound(MockServer mockServer) {
        RestAssured.baseURI = mockServer.getUrl();
        
        given()
            .when()
            .get("/products/non-existent-id")
            .then()
            .statusCode(404)
            .contentType("application/json")
            .body("error", equalTo("Product not found"))
            .body("statusCode", equalTo(404));
    }

    /**
     * Pact: Update existing product should return updated product
     */
    @Pact(consumer = "product-api-consumer", provider = "product-api-provider")
    V4Pact updateProductSuccessPact(PactDslWithProvider builder) {
        return builder
            .given("product with ID 123e4567-e89b-12d3-a456-426614174000 exists")
            .uponReceiving("a request to update product")
            .path("/products/123e4567-e89b-12d3-a456-426614174000")
            .method("PUT")
            .headers(Map.of("Content-Type", "application/json"))
            .body(new PactDslJsonBody()
                .stringType("name", "Updated Product")
                .numberType("price", 199.99))
            .willRespondWith()
            .status(200)
            .headers(Map.of("Content-Type", "application/json"))
            .body(new PactDslJsonBody()
                .stringValue("id", "123e4567-e89b-12d3-a456-426614174000")
                .stringType("name", "Updated Product")
                .numberType("price", 199.99))
            .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "updateProductSuccessPact")
    void testUpdateProductSuccess(MockServer mockServer) throws JsonProcessingException {
        RestAssured.baseURI = mockServer.getUrl();
        
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("name", "Updated Product");
        updateData.put("price", 199.99);
        
        given()
            .contentType("application/json")
            .body(OBJECT_MAPPER.writeValueAsString(updateData))
            .when()
            .put("/products/123e4567-e89b-12d3-a456-426614174000")
            .then()
            .statusCode(200)
            .contentType("application/json")
            .body("id", equalTo("123e4567-e89b-12d3-a456-426614174000"))
            .body("name", equalTo("Updated Product"))
            .body("price", equalTo(199.99f));
    }

    /**
     * Pact: Delete existing product should return no content
     */
    @Pact(consumer = "product-api-consumer", provider = "product-api-provider")
    V4Pact deleteProductSuccessPact(PactDslWithProvider builder) {
        return builder
            .given("product with ID 123e4567-e89b-12d3-a456-426614174000 exists")
            .uponReceiving("a request to delete product")
            .path("/products/123e4567-e89b-12d3-a456-426614174000")
            .method("DELETE")
            .willRespondWith()
            .status(204)
            .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "deleteProductSuccessPact")
    void testDeleteProductSuccess(MockServer mockServer) {
        RestAssured.baseURI = mockServer.getUrl();
        
        given()
            .when()
            .delete("/products/123e4567-e89b-12d3-a456-426614174000")
            .then()
            .statusCode(204);
    }

    /**
     * Pact: List all products should return product array
     */
    @Pact(consumer = "product-api-consumer", provider = "product-api-provider")
    V4Pact listProductsPact(PactDslWithProvider builder) {
        return builder
            .given("products exist in the system")
            .uponReceiving("a request to list all products")
            .path("/products")
            .method("GET")
            .willRespondWith()
            .status(200)
            .headers(Map.of("Content-Type", "application/json"))
            .body(new PactDslJsonBody()
                .array("products")
                .closeArray())
            .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "listProductsPact")
    void testListProducts(MockServer mockServer) {
        RestAssured.baseURI = mockServer.getUrl();
        
        given()
            .when()
            .get("/products")
            .then()
            .statusCode(200)
            .contentType("application/json")
            .body("products", notNullValue())
            .body("products", isA(java.util.List.class));
    }
}