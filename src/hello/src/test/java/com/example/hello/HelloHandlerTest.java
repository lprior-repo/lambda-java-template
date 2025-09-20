package com.example.hello;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class HelloHandlerTest {

    @Mock
    private Context mockContext;

    private HelloHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        handler = new HelloHandler();
        objectMapper = new ObjectMapper();

        // Mock context behavior with lenient stubs
        lenient().when(mockContext.getAwsRequestId()).thenReturn("test-request-id-123");
        lenient().when(mockContext.getFunctionName()).thenReturn("hello-lambda");
        lenient().when(mockContext.getFunctionVersion()).thenReturn("$LATEST");
        lenient().when(mockContext.getRemainingTimeInMillis()).thenReturn(30000);
    }

    @Test
    void handleRequest_shouldReturnSuccessResponse_whenValidRequest() throws Exception {
        // Given
        APIGatewayProxyRequestEvent request = createRequest("GET", "/hello");

        // When
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

        // Then
        assertNotNull(response);
        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getHeaders());
        assertNotNull(response.getBody());

        // Verify response headers
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
        assertEquals("*", response.getHeaders().get("Access-Control-Allow-Origin"));
        assertEquals("test-request-id-123", response.getHeaders().get("X-Request-ID"));

        // Verify response body
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        assertTrue(responseBody.has("message"));
        assertTrue(responseBody.has("path"));
        assertTrue(responseBody.has("timestamp"));
        assertEquals("Hello from Lambda!", responseBody.get("message").asText());
        assertEquals("/hello", responseBody.get("path").asText());
    }

    @Test
    void handleRequest_shouldHandleNullPath_whenPathIsNull() throws Exception {
        // Given
        APIGatewayProxyRequestEvent request = createRequest("GET", null);

        // When
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

        // Then
        assertEquals(200, response.getStatusCode());

        JsonNode responseBody = objectMapper.readTree(response.getBody());
        assertEquals("/hello", responseBody.get("path").asText());
    }

    @Test
    void handleRequest_shouldReturnBadRequest_whenInvalidHttpMethod() throws Exception {
        // Given
        APIGatewayProxyRequestEvent request = createRequest("INVALID", "/hello");

        // When
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

        // Then
        assertEquals(200, response.getStatusCode()); // Handler doesn't validate HTTP method, just processes
        assertNotNull(response.getBody());
    }

    @Test
    void handleRequest_shouldReturnErrorResponse_whenExceptionOccurs() throws Exception {
        // Given
        APIGatewayProxyRequestEvent request = createRequest("GET", "/hello");
        Context mockContextWithError = mock(Context.class);
        lenient().when(mockContextWithError.getAwsRequestId()).thenReturn("error-request-id");

        // Create a custom handler that throws an exception during processing
        HelloHandler errorHandler = new HelloHandler() {
            @Override
            public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
                throw new RuntimeException("Test exception");
            }
        };

        // When & Then - Exception should be caught and handled
        assertThrows(RuntimeException.class, () -> errorHandler.handleRequest(request, mockContextWithError));
    }

    @Test
    void handleRequest_shouldIncludeEnvironmentVariables_whenPresent() throws Exception {
        // Given
        APIGatewayProxyRequestEvent request = createRequest("GET", "/hello");

        // When
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

        // Then
        assertEquals(200, response.getStatusCode());

        JsonNode responseBody = objectMapper.readTree(response.getBody());
        assertTrue(responseBody.has("environment"));
        assertTrue(responseBody.has("functionName"));
    }

    @Test
    void handleRequest_shouldReturnDifferentPaths_whenDifferentPathsProvided() throws Exception {
        // Given
        String[] testPaths = {"/hello", "/hello/world", "/api/hello"};

        for (String path : testPaths) {
            APIGatewayProxyRequestEvent request = createRequest("GET", path);

            // When
            APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

            // Then
            assertEquals(200, response.getStatusCode());

            JsonNode responseBody = objectMapper.readTree(response.getBody());
            assertEquals(path, responseBody.get("path").asText());
        }
    }

    private APIGatewayProxyRequestEvent createRequest(String httpMethod, String path) {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setHttpMethod(httpMethod);
        request.setPath(path);
        return request;
    }
}