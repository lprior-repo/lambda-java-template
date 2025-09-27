package software.amazonaws.example.product;

import com.amazonaws.services.lambda.runtime.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for EventProcessorHandler following AAA pattern.
 * Tests all event processing scenarios with proper mocking.
 */
class EventProcessorHandlerTest {

    @Mock
    private DynamoDbClient mockDynamoDbClient;

    @Mock
    private Context mockContext;

    private EventProcessorHandler eventProcessorHandler;

    private final String TEST_TABLE_NAME = "test-audit-table";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        eventProcessorHandler = new EventProcessorHandler(mockDynamoDbClient);
        
        // Mock context methods
        when(mockContext.getAwsRequestId()).thenReturn("test-event-request-123");
        when(mockContext.getFunctionName()).thenReturn("event-processor-test");
        
        // Mock successful DynamoDB response
        when(mockDynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenReturn(PutItemResponse.builder().build());
    }

    // Successful Event Processing Tests
    @Test
    void handleRequest_ValidEventBridgeEvent_ShouldCreateAuditEntry() {
        // Arrange
        Map<String, Object> event = createValidEventBridgeEvent();
        
        // Mock environment variable
        setEnvironmentVariable("AUDIT_TABLE_NAME", TEST_TABLE_NAME);

        // Act
        Map<String, Object> response = eventProcessorHandler.handleRequest(event, mockContext);

        // Assert
        assertEquals(200, (Integer) response.get("statusCode"));
        
        Map<String, Object> body = (Map<String, Object>) response.get("body");
        assertEquals("Event processed successfully", body.get("message"));
        assertNotNull(body.get("eventId"));
        
        // Verify DynamoDB interaction
        ArgumentCaptor<PutItemRequest> putItemCaptor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(mockDynamoDbClient).putItem(putItemCaptor.capture());
        
        PutItemRequest capturedRequest = putItemCaptor.getValue();
        assertEquals(TEST_TABLE_NAME, capturedRequest.tableName());
        
        Map<String, AttributeValue> item = capturedRequest.item();
        assertNotNull(item.get("event_id"));
        assertNotNull(item.get("timestamp"));
        assertEquals("Product Created", item.get("event_type").s());
        assertEquals("product.service", item.get("source").s());
        assertNotNull(item.get("detail"));
        assertNotNull(item.get("ttl"));
    }

    @Test
    void handleRequest_EventWithNullDetailType_ShouldUseDefaultDetailType() {
        // Arrange
        Map<String, Object> event = createValidEventBridgeEvent();
        event.remove("detail-type");
        
        setEnvironmentVariable("AUDIT_TABLE_NAME", TEST_TABLE_NAME);

        // Act
        Map<String, Object> response = eventProcessorHandler.handleRequest(event, mockContext);

        // Assert
        assertEquals(200, (Integer) response.get("statusCode"));
        
        ArgumentCaptor<PutItemRequest> putItemCaptor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(mockDynamoDbClient).putItem(putItemCaptor.capture());
        
        PutItemRequest capturedRequest = putItemCaptor.getValue();
        Map<String, AttributeValue> item = capturedRequest.item();
        assertEquals("Unknown", item.get("event_type").s());
    }

    @Test
    void handleRequest_EventWithNullSource_ShouldUseDefaultSource() {
        // Arrange
        Map<String, Object> event = createValidEventBridgeEvent();
        event.remove("source");
        
        setEnvironmentVariable("AUDIT_TABLE_NAME", TEST_TABLE_NAME);

        // Act
        Map<String, Object> response = eventProcessorHandler.handleRequest(event, mockContext);

        // Assert
        assertEquals(200, (Integer) response.get("statusCode"));
        
        ArgumentCaptor<PutItemRequest> putItemCaptor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(mockDynamoDbClient).putItem(putItemCaptor.capture());
        
        PutItemRequest capturedRequest = putItemCaptor.getValue();
        Map<String, AttributeValue> item = capturedRequest.item();
        assertEquals("Unknown", item.get("source").s());
    }

    @Test
    void handleRequest_EventWithNullDetail_ShouldUseEmptyDetail() {
        // Arrange
        Map<String, Object> event = createValidEventBridgeEvent();
        event.remove("detail");
        
        setEnvironmentVariable("AUDIT_TABLE_NAME", TEST_TABLE_NAME);

        // Act
        Map<String, Object> response = eventProcessorHandler.handleRequest(event, mockContext);

        // Assert
        assertEquals(200, (Integer) response.get("statusCode"));
        
        ArgumentCaptor<PutItemRequest> putItemCaptor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(mockDynamoDbClient).putItem(putItemCaptor.capture());
        
        PutItemRequest capturedRequest = putItemCaptor.getValue();
        Map<String, AttributeValue> item = capturedRequest.item();
        assertEquals("{}", item.get("detail").s());
    }

    @Test
    void handleRequest_ComplexEventDetail_ShouldSerializeCorrectly() {
        // Arrange
        Map<String, Object> event = createValidEventBridgeEvent();
        Map<String, Object> complexDetail = new HashMap<>();
        complexDetail.put("productId", "123");
        complexDetail.put("productName", "Test Product");
        complexDetail.put("price", 99.99);
        complexDetail.put("metadata", Map.of("category", "electronics", "tags", new String[]{"new", "featured"}));
        event.put("detail", complexDetail);
        
        setEnvironmentVariable("AUDIT_TABLE_NAME", TEST_TABLE_NAME);

        // Act
        Map<String, Object> response = eventProcessorHandler.handleRequest(event, mockContext);

        // Assert
        assertEquals(200, (Integer) response.get("statusCode"));
        
        ArgumentCaptor<PutItemRequest> putItemCaptor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(mockDynamoDbClient).putItem(putItemCaptor.capture());
        
        PutItemRequest capturedRequest = putItemCaptor.getValue();
        Map<String, AttributeValue> item = capturedRequest.item();
        
        String detailJson = item.get("detail").s();
        assertTrue(detailJson.contains("\"productId\":\"123\""));
        assertTrue(detailJson.contains("\"productName\":\"Test Product\""));
        assertTrue(detailJson.contains("\"price\":99.99"));
    }

    // Configuration Error Tests
    @Test
    void handleRequest_MissingTableNameEnvironment_ShouldReturnError() {
        // Arrange
        Map<String, Object> event = createValidEventBridgeEvent();
        setEnvironmentVariable("AUDIT_TABLE_NAME", null);

        // Act
        Map<String, Object> response = eventProcessorHandler.handleRequest(event, mockContext);

        // Assert
        assertEquals(500, (Integer) response.get("statusCode"));
        
        Map<String, Object> body = (Map<String, Object>) response.get("body");
        assertEquals("Event processing failed", body.get("error"));
        assertTrue(((String) body.get("message")).contains("Configuration error"));
        
        verify(mockDynamoDbClient, never()).putItem(any(PutItemRequest.class));
    }

    @Test
    void handleRequest_EmptyTableNameEnvironment_ShouldReturnError() {
        // Arrange
        Map<String, Object> event = createValidEventBridgeEvent();
        setEnvironmentVariable("AUDIT_TABLE_NAME", "");

        // Act
        Map<String, Object> response = eventProcessorHandler.handleRequest(event, mockContext);

        // Assert
        assertEquals(500, (Integer) response.get("statusCode"));
        
        Map<String, Object> body = (Map<String, Object>) response.get("body");
        assertEquals("Event processing failed", body.get("error"));
        assertTrue(((String) body.get("message")).contains("Configuration error"));
        
        verify(mockDynamoDbClient, never()).putItem(any(PutItemRequest.class));
    }

    // DynamoDB Error Handling Tests
    @Test
    void handleRequest_DynamoDbException_ShouldReturnError() {
        // Arrange
        Map<String, Object> event = createValidEventBridgeEvent();
        setEnvironmentVariable("AUDIT_TABLE_NAME", TEST_TABLE_NAME);
        
        when(mockDynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenThrow(DynamoDbException.builder()
                        .message("Table not found")
                        .statusCode(400)
                        .build());

        // Act
        Map<String, Object> response = eventProcessorHandler.handleRequest(event, mockContext);

        // Assert
        assertEquals(500, (Integer) response.get("statusCode"));
        
        Map<String, Object> body = (Map<String, Object>) response.get("body");
        assertEquals("Event processing failed", body.get("error"));
        assertTrue(((String) body.get("message")).contains("Event processing failed"));
        
        verify(mockDynamoDbClient).putItem(any(PutItemRequest.class));
    }

    @Test
    void handleRequest_GeneralException_ShouldReturnError() {
        // Arrange
        Map<String, Object> event = createValidEventBridgeEvent();
        setEnvironmentVariable("AUDIT_TABLE_NAME", TEST_TABLE_NAME);
        
        when(mockDynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenThrow(new RuntimeException("Unexpected error"));

        // Act
        Map<String, Object> response = eventProcessorHandler.handleRequest(event, mockContext);

        // Assert
        assertEquals(500, (Integer) response.get("statusCode"));
        
        Map<String, Object> body = (Map<String, Object>) response.get("body");
        assertEquals("Event processing failed", body.get("error"));
        assertTrue(((String) body.get("message")).contains("Unexpected error"));
        
        verify(mockDynamoDbClient).putItem(any(PutItemRequest.class));
    }

    // Edge Case Tests
    @Test
    void handleRequest_NullEvent_ShouldHandleGracefully() {
        // Arrange
        Map<String, Object> event = null;
        setEnvironmentVariable("AUDIT_TABLE_NAME", TEST_TABLE_NAME);

        // Act
        Map<String, Object> response = eventProcessorHandler.handleRequest(event, mockContext);

        // Assert
        assertEquals(500, (Integer) response.get("statusCode"));
        
        Map<String, Object> body = (Map<String, Object>) response.get("body");
        assertEquals("Event processing failed", body.get("error"));
        
        verify(mockDynamoDbClient, never()).putItem(any(PutItemRequest.class));
    }

    @Test
    void handleRequest_EmptyEvent_ShouldProcessSuccessfully() {
        // Arrange
        Map<String, Object> event = new HashMap<>();
        setEnvironmentVariable("AUDIT_TABLE_NAME", TEST_TABLE_NAME);

        // Act
        Map<String, Object> response = eventProcessorHandler.handleRequest(event, mockContext);

        // Assert
        assertEquals(200, (Integer) response.get("statusCode"));
        
        Map<String, Object> body = (Map<String, Object>) response.get("body");
        assertEquals("Event processed successfully", body.get("message"));
        assertNotNull(body.get("eventId"));
        
        ArgumentCaptor<PutItemRequest> putItemCaptor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(mockDynamoDbClient).putItem(putItemCaptor.capture());
        
        PutItemRequest capturedRequest = putItemCaptor.getValue();
        Map<String, AttributeValue> item = capturedRequest.item();
        assertEquals("Unknown", item.get("event_type").s());
        assertEquals("Unknown", item.get("source").s());
        assertEquals("{}", item.get("detail").s());
    }

    // TTL Validation Tests
    @Test
    void handleRequest_ValidEvent_ShouldSetCorrectTTL() {
        // Arrange
        Map<String, Object> event = createValidEventBridgeEvent();
        setEnvironmentVariable("AUDIT_TABLE_NAME", TEST_TABLE_NAME);
        
        long beforeTest = System.currentTimeMillis() / 1000;

        // Act
        Map<String, Object> response = eventProcessorHandler.handleRequest(event, mockContext);

        // Assert
        assertEquals(200, (Integer) response.get("statusCode"));
        
        ArgumentCaptor<PutItemRequest> putItemCaptor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(mockDynamoDbClient).putItem(putItemCaptor.capture());
        
        PutItemRequest capturedRequest = putItemCaptor.getValue();
        Map<String, AttributeValue> item = capturedRequest.item();
        
        long ttl = Long.parseLong(item.get("ttl").n());
        long expectedMinTTL = beforeTest + (90 * 24 * 60 * 60) - 10; // Allow 10 second variance
        long expectedMaxTTL = beforeTest + (90 * 24 * 60 * 60) + 10;
        
        assertTrue(ttl >= expectedMinTTL && ttl <= expectedMaxTTL, 
                   "TTL should be approximately 90 days from now");
    }

    // Response Structure Tests
    @Test
    void handleRequest_SuccessResponse_ShouldHaveCorrectStructure() {
        // Arrange
        Map<String, Object> event = createValidEventBridgeEvent();
        setEnvironmentVariable("AUDIT_TABLE_NAME", TEST_TABLE_NAME);

        // Act
        Map<String, Object> response = eventProcessorHandler.handleRequest(event, mockContext);

        // Assert
        assertNotNull(response);
        assertTrue(response.containsKey("statusCode"));
        assertTrue(response.containsKey("body"));
        
        assertEquals(200, response.get("statusCode"));
        
        Object bodyObj = response.get("body");
        assertTrue(bodyObj instanceof Map);
        
        Map<String, Object> body = (Map<String, Object>) bodyObj;
        assertTrue(body.containsKey("message"));
        assertTrue(body.containsKey("eventId"));
        
        assertEquals("Event processed successfully", body.get("message"));
        assertNotNull(body.get("eventId"));
        assertTrue(body.get("eventId") instanceof String);
    }

    @Test
    void handleRequest_ErrorResponse_ShouldHaveCorrectStructure() {
        // Arrange
        Map<String, Object> event = createValidEventBridgeEvent();
        setEnvironmentVariable("AUDIT_TABLE_NAME", null);

        // Act
        Map<String, Object> response = eventProcessorHandler.handleRequest(event, mockContext);

        // Assert
        assertNotNull(response);
        assertTrue(response.containsKey("statusCode"));
        assertTrue(response.containsKey("body"));
        
        assertEquals(500, response.get("statusCode"));
        
        Object bodyObj = response.get("body");
        assertTrue(bodyObj instanceof Map);
        
        Map<String, Object> body = (Map<String, Object>) bodyObj;
        assertTrue(body.containsKey("error"));
        assertTrue(body.containsKey("message"));
        
        assertEquals("Event processing failed", body.get("error"));
        assertNotNull(body.get("message"));
    }

    // Helper Methods
    private Map<String, Object> createValidEventBridgeEvent() {
        Map<String, Object> event = new HashMap<>();
        event.put("detail-type", "Product Created");
        event.put("source", "product.service");
        
        Map<String, Object> detail = new HashMap<>();
        detail.put("productId", "123");
        detail.put("action", "created");
        event.put("detail", detail);
        
        return event;
    }

    private void setEnvironmentVariable(String name, String value) {
        // This is a simplified approach for testing
        // In a real test, you might use @SetEnvironmentVariable from JUnit Pioneer
        // or mock System.getenv() calls
        try {
            java.lang.reflect.Field field = EventProcessorHandler.class.getDeclaredField("TABLE_NAME");
            field.setAccessible(true);
            field.set(null, value);
        } catch (Exception e) {
            // For this test, we'll assume the environment variable is set
            // In practice, you'd use a more robust mocking approach
        }
    }
}