package software.amazonaws.example.product;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayCustomAuthorizerEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Comprehensive unit tests for AuthorizerHandler following AAA pattern.
 * Tests all authorization scenarios with proper validation.
 */
class AuthorizerHandlerTest {

    @Mock
    private Context mockContext;

    private AuthorizerHandler authorizerHandler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        authorizerHandler = new AuthorizerHandler();
        
        // Mock context methods
        when(mockContext.getAwsRequestId()).thenReturn("test-auth-request-123");
        when(mockContext.getFunctionName()).thenReturn("authorizer-service-test");
    }

    // Valid API Key Tests
    @Test
    void handleRequest_ValidApiKeyInXApiKeyHeader_ShouldAuthorize() {
        // Arrange
        APIGatewayCustomAuthorizerEvent event = createAuthorizerEvent();
        Map<String, String> headers = new HashMap<>();
        headers.put("x-api-key", "valid-api-key-123");
        event.setHeaders(headers);

        // Act
        Map<String, Object> response = authorizerHandler.handleRequest(event, mockContext);

        // Assert
        assertTrue((Boolean) response.get("isAuthorized"));
        Map<String, Object> context = (Map<String, Object>) response.get("context");
        assertEquals("valid-api-key-123", context.get("apiKey"));
    }

    @Test
    void handleRequest_ValidApiKeyInXApiKeyHeaderUppercase_ShouldAuthorize() {
        // Arrange
        APIGatewayCustomAuthorizerEvent event = createAuthorizerEvent();
        Map<String, String> headers = new HashMap<>();
        headers.put("X-API-Key", "valid-api-key-456");
        event.setHeaders(headers);

        // Act
        Map<String, Object> response = authorizerHandler.handleRequest(event, mockContext);

        // Assert
        assertTrue((Boolean) response.get("isAuthorized"));
        Map<String, Object> context = (Map<String, Object>) response.get("context");
        assertEquals("valid-api-key-456", context.get("apiKey"));
    }

    @Test
    void handleRequest_ValidApiKeyWithWhitespace_ShouldAuthorize() {
        // Arrange
        APIGatewayCustomAuthorizerEvent event = createAuthorizerEvent();
        Map<String, String> headers = new HashMap<>();
        headers.put("x-api-key", "  valid-api-key-789  ");
        event.setHeaders(headers);

        // Act
        Map<String, Object> response = authorizerHandler.handleRequest(event, mockContext);

        // Assert
        assertTrue((Boolean) response.get("isAuthorized"));
        Map<String, Object> context = (Map<String, Object>) response.get("context");
        assertEquals("  valid-api-key-789  ", context.get("apiKey"));
    }

    // Invalid API Key Tests
    @Test
    void handleRequest_MissingApiKey_ShouldDenyAuthorization() {
        // Arrange
        APIGatewayCustomAuthorizerEvent event = createAuthorizerEvent();
        Map<String, String> headers = new HashMap<>();
        event.setHeaders(headers);

        // Act
        Map<String, Object> response = authorizerHandler.handleRequest(event, mockContext);

        // Assert
        assertFalse((Boolean) response.get("isAuthorized"));
        Map<String, Object> context = (Map<String, Object>) response.get("context");
        assertEquals("invalid", context.get("apiKey"));
    }

    @Test
    void handleRequest_EmptyApiKey_ShouldDenyAuthorization() {
        // Arrange
        APIGatewayCustomAuthorizerEvent event = createAuthorizerEvent();
        Map<String, String> headers = new HashMap<>();
        headers.put("x-api-key", "");
        event.setHeaders(headers);

        // Act
        Map<String, Object> response = authorizerHandler.handleRequest(event, mockContext);

        // Assert
        assertFalse((Boolean) response.get("isAuthorized"));
        Map<String, Object> context = (Map<String, Object>) response.get("context");
        assertEquals("invalid", context.get("apiKey"));
    }

    @Test
    void handleRequest_WhitespaceOnlyApiKey_ShouldDenyAuthorization() {
        // Arrange
        APIGatewayCustomAuthorizerEvent event = createAuthorizerEvent();
        Map<String, String> headers = new HashMap<>();
        headers.put("x-api-key", "   ");
        event.setHeaders(headers);

        // Act
        Map<String, Object> response = authorizerHandler.handleRequest(event, mockContext);

        // Assert
        assertFalse((Boolean) response.get("isAuthorized"));
        Map<String, Object> context = (Map<String, Object>) response.get("context");
        assertEquals("invalid", context.get("apiKey"));
    }

    @Test
    void handleRequest_NullHeaders_ShouldDenyAuthorization() {
        // Arrange
        APIGatewayCustomAuthorizerEvent event = createAuthorizerEvent();
        event.setHeaders(null);

        // Act
        Map<String, Object> response = authorizerHandler.handleRequest(event, mockContext);

        // Assert
        assertFalse((Boolean) response.get("isAuthorized"));
        Map<String, Object> context = (Map<String, Object>) response.get("context");
        assertEquals("invalid", context.get("apiKey"));
    }

    // Header Case Sensitivity Tests
    @Test
    void handleRequest_BothApiKeyHeaders_ShouldPreferLowercase() {
        // Arrange
        APIGatewayCustomAuthorizerEvent event = createAuthorizerEvent();
        Map<String, String> headers = new HashMap<>();
        headers.put("x-api-key", "lowercase-key");
        headers.put("X-API-Key", "uppercase-key");
        event.setHeaders(headers);

        // Act
        Map<String, Object> response = authorizerHandler.handleRequest(event, mockContext);

        // Assert
        assertTrue((Boolean) response.get("isAuthorized"));
        Map<String, Object> context = (Map<String, Object>) response.get("context");
        assertEquals("lowercase-key", context.get("apiKey"));
    }

    @Test
    void handleRequest_OnlyUppercaseHeader_ShouldUseUppercase() {
        // Arrange
        APIGatewayCustomAuthorizerEvent event = createAuthorizerEvent();
        Map<String, String> headers = new HashMap<>();
        headers.put("X-API-Key", "uppercase-only-key");
        headers.put("authorization", "Bearer token");
        event.setHeaders(headers);

        // Act
        Map<String, Object> response = authorizerHandler.handleRequest(event, mockContext);

        // Assert
        assertTrue((Boolean) response.get("isAuthorized"));
        Map<String, Object> context = (Map<String, Object>) response.get("context");
        assertEquals("uppercase-only-key", context.get("apiKey"));
    }

    // Error Handling Tests
    @Test
    void handleRequest_NullEvent_ShouldHandleGracefully() {
        // Arrange
        APIGatewayCustomAuthorizerEvent event = null;

        // Act
        Map<String, Object> response = authorizerHandler.handleRequest(event, mockContext);

        // Assert
        assertFalse((Boolean) response.get("isAuthorized"));
        Map<String, Object> context = (Map<String, Object>) response.get("context");
        assertEquals("Authorization failed", context.get("error"));
    }

    // Response Structure Tests
    @Test
    void handleRequest_ValidRequest_ShouldReturnCorrectResponseStructure() {
        // Arrange
        APIGatewayCustomAuthorizerEvent event = createAuthorizerEvent();
        Map<String, String> headers = new HashMap<>();
        headers.put("x-api-key", "test-key");
        event.setHeaders(headers);

        // Act
        Map<String, Object> response = authorizerHandler.handleRequest(event, mockContext);

        // Assert
        assertNotNull(response);
        assertTrue(response.containsKey("isAuthorized"));
        assertTrue(response.containsKey("context"));
        
        Object isAuthorized = response.get("isAuthorized");
        assertTrue(isAuthorized instanceof Boolean);
        
        Object context = response.get("context");
        assertTrue(context instanceof Map);
        
        Map<String, Object> contextMap = (Map<String, Object>) context;
        assertTrue(contextMap.containsKey("apiKey"));
    }

    @Test
    void handleRequest_ErrorResponse_ShouldReturnCorrectErrorStructure() {
        // Arrange
        APIGatewayCustomAuthorizerEvent event = createAuthorizerEvent();
        event.setHeaders(null);

        // Act
        Map<String, Object> response = authorizerHandler.handleRequest(event, mockContext);

        // Assert
        assertNotNull(response);
        assertFalse((Boolean) response.get("isAuthorized"));
        assertTrue(response.containsKey("context"));
        
        Map<String, Object> context = (Map<String, Object>) response.get("context");
        assertEquals("invalid", context.get("apiKey"));
    }

    // Additional Scenario Tests
    @Test
    void handleRequest_OtherHeaders_ShouldIgnoreUnrelatedHeaders() {
        // Arrange
        APIGatewayCustomAuthorizerEvent event = createAuthorizerEvent();
        Map<String, String> headers = new HashMap<>();
        headers.put("authorization", "Bearer some-token");
        headers.put("content-type", "application/json");
        headers.put("user-agent", "test-client");
        headers.put("x-api-key", "valid-key");
        event.setHeaders(headers);

        // Act
        Map<String, Object> response = authorizerHandler.handleRequest(event, mockContext);

        // Assert
        assertTrue((Boolean) response.get("isAuthorized"));
        Map<String, Object> context = (Map<String, Object>) response.get("context");
        assertEquals("valid-key", context.get("apiKey"));
    }

    @Test
    void handleRequest_SpecialCharactersInApiKey_ShouldAuthorize() {
        // Arrange
        String specialKey = "key-with_special.chars@123";
        APIGatewayCustomAuthorizerEvent event = createAuthorizerEvent();
        Map<String, String> headers = new HashMap<>();
        headers.put("x-api-key", specialKey);
        event.setHeaders(headers);

        // Act
        Map<String, Object> response = authorizerHandler.handleRequest(event, mockContext);

        // Assert
        assertTrue((Boolean) response.get("isAuthorized"));
        Map<String, Object> context = (Map<String, Object>) response.get("context");
        assertEquals(specialKey, context.get("apiKey"));
    }

    // Helper method to create a basic authorizer event
    private APIGatewayCustomAuthorizerEvent createAuthorizerEvent() {
        APIGatewayCustomAuthorizerEvent event = new APIGatewayCustomAuthorizerEvent();
        event.setType("REQUEST");
        event.setMethodArn("arn:aws:execute-api:us-east-1:123456789012:abcdef123/test/GET/products");
        return event;
    }
}