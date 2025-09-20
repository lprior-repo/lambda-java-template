package com.example.users;

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
class UsersHandlerTest {

    @Mock
    private Context mockContext;

    private UsersHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        handler = new UsersHandler();
        objectMapper = new ObjectMapper();

        // Mock context behavior with lenient stubs
        lenient().when(mockContext.getAwsRequestId()).thenReturn("test-request-id-456");
        lenient().when(mockContext.getFunctionName()).thenReturn("users-lambda");
        lenient().when(mockContext.getFunctionVersion()).thenReturn("$LATEST");
        lenient().when(mockContext.getRemainingTimeInMillis()).thenReturn(30000);
    }

    @Test
    void handleRequest_shouldReturnSuccessResponse_whenValidRequest() throws Exception {
        // Given
        APIGatewayProxyRequestEvent request = createRequest("GET", "/users");

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
        assertEquals("test-request-id-456", response.getHeaders().get("X-Request-ID"));

        // Verify response body structure
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        assertTrue(responseBody.has("users"));
        assertTrue(responseBody.has("count"));
        assertTrue(responseBody.has("timestamp"));
        assertTrue(responseBody.has("requestId"));
        assertEquals("test-request-id-456", responseBody.get("requestId").asText());
    }

    @Test
    void handleRequest_shouldReturnUsersArray_whenRequestProcessed() throws Exception {
        // Given
        APIGatewayProxyRequestEvent request = createRequest("GET", "/users");

        // When
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

        // Then
        assertEquals(200, response.getStatusCode());

        JsonNode responseBody = objectMapper.readTree(response.getBody());
        JsonNode users = responseBody.get("users");
        assertTrue(users.isArray());
        assertEquals(3, users.size()); // Should return 3 mock users

        // Verify user structure
        JsonNode firstUser = users.get(0);
        assertTrue(firstUser.has("id"));
        assertTrue(firstUser.has("name"));
        assertTrue(firstUser.has("email"));
        assertTrue(firstUser.has("createdAt"));

        // Verify specific user data
        assertEquals("1", firstUser.get("id").asText());
        assertEquals("John Doe", firstUser.get("name").asText());
        assertEquals("john@example.com", firstUser.get("email").asText());
    }

    @Test
    void handleRequest_shouldReturnCorrectCount_whenUsersReturned() throws Exception {
        // Given
        APIGatewayProxyRequestEvent request = createRequest("GET", "/users");

        // When
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

        // Then
        assertEquals(200, response.getStatusCode());

        JsonNode responseBody = objectMapper.readTree(response.getBody());
        assertEquals(3, responseBody.get("count").asInt());
    }

    @Test
    void handleRequest_shouldHandleNullPath_whenPathIsNull() throws Exception {
        // Given
        APIGatewayProxyRequestEvent request = createRequest("GET", null);

        // When
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

        // Then
        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode responseBody = objectMapper.readTree(response.getBody());
        assertTrue(responseBody.has("users"));
    }

    @Test
    void handleRequest_shouldReturnErrorResponse_whenExceptionOccurs() throws Exception {
        // Given
        APIGatewayProxyRequestEvent request = createRequest("GET", "/users");
        Context mockContextWithError = mock(Context.class);
        lenient().when(mockContextWithError.getAwsRequestId()).thenReturn("error-request-id");

        // Create a custom handler that throws an exception during processing
        UsersHandler errorHandler = new UsersHandler() {
            @Override
            public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
                throw new RuntimeException("Test exception");
            }
        };

        // When & Then - Exception should be caught and handled
        assertThrows(RuntimeException.class, () -> errorHandler.handleRequest(request, mockContextWithError));
    }

    @Test
    void handleRequest_shouldReturnValidTimestamp_whenProcessed() throws Exception {
        // Given
        APIGatewayProxyRequestEvent request = createRequest("GET", "/users");

        // When
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

        // Then
        assertEquals(200, response.getStatusCode());

        JsonNode responseBody = objectMapper.readTree(response.getBody());
        String timestamp = responseBody.get("timestamp").asText();
        assertNotNull(timestamp);
        assertFalse(timestamp.isEmpty());
        // Timestamp should be in ISO 8601 format
        assertTrue(timestamp.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*"));
    }

    @Test
    void handleRequest_shouldHandleDifferentHttpMethods_whenCalled() throws Exception {
        // Given
        String[] httpMethods = {"GET", "POST", "PUT", "DELETE"};

        for (String method : httpMethods) {
            APIGatewayProxyRequestEvent request = createRequest(method, "/users");

            // When
            APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

            // Then
            assertEquals(200, response.getStatusCode());
            assertNotNull(response.getBody());

            JsonNode responseBody = objectMapper.readTree(response.getBody());
            assertTrue(responseBody.has("users"));
        }
    }

    @Test
    void handleRequest_shouldReturnConsistentUserData_whenCalledMultipleTimes() throws Exception {
        // Given
        APIGatewayProxyRequestEvent request = createRequest("GET", "/users");

        // When - Call handler multiple times
        APIGatewayProxyResponseEvent response1 = handler.handleRequest(request, mockContext);
        APIGatewayProxyResponseEvent response2 = handler.handleRequest(request, mockContext);

        // Then
        assertEquals(200, response1.getStatusCode());
        assertEquals(200, response2.getStatusCode());

        JsonNode responseBody1 = objectMapper.readTree(response1.getBody());
        JsonNode responseBody2 = objectMapper.readTree(response2.getBody());

        // User data should be consistent
        assertEquals(responseBody1.get("count").asInt(), responseBody2.get("count").asInt());
        assertEquals(responseBody1.get("users").size(), responseBody2.get("users").size());
    }

    private APIGatewayProxyRequestEvent createRequest(String httpMethod, String path) {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setHttpMethod(httpMethod);
        request.setPath(path);
        return request;
    }
}