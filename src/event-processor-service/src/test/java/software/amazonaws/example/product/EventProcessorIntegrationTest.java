package software.amazonaws.example.product;

import com.amazonaws.services.lambda.runtime.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for EventProcessorHandler using real AWS DynamoDB.
 * These tests require:
 * - AWS credentials configured (AWS_PROFILE or default credentials)
 * - DynamoDB table exists with name specified in AUDIT_TABLE_NAME environment variable
 * - Required IAM permissions for DynamoDB operations
 * 
 * Run with: mvn test -Dtest=EventProcessorIntegrationTest -DAUDIT_TABLE_NAME=your-audit-table-name
 */
@EnabledIfEnvironmentVariable(named = "AUDIT_TABLE_NAME", matches = ".+")
class EventProcessorIntegrationTest {

    private static final String TABLE_NAME = System.getenv("AUDIT_TABLE_NAME");
    
    private DynamoDbClient dynamoDbClient;
    private EventProcessorHandler eventProcessorHandler;
    private Context mockContext;

    @BeforeEach
    void setUp() {
        assumeTableExists();
        
        dynamoDbClient = DynamoDbClient.builder().build();
        
        // Set environment variable for the handler
        setEnvironmentVariable("AUDIT_TABLE_NAME", TABLE_NAME);
        
        eventProcessorHandler = new EventProcessorHandler(dynamoDbClient);
        
        // Mock Lambda context
        mockContext = mock(Context.class);
        when(mockContext.getAwsRequestId()).thenReturn("integration-test-" + UUID.randomUUID());
        when(mockContext.getFunctionName()).thenReturn("event-processor-integration-test");
    }

    @Test
    void handleRequest_ValidEventBridgeEvent_ShouldPersistAuditEntry() {
        // Arrange
        Map<String, Object> eventBridgeEvent = createValidEventBridgeEvent();
        
        // Act
        Map<String, Object> response = eventProcessorHandler.handleRequest(eventBridgeEvent, mockContext);
        
        // Assert
        assertEquals(200, (Integer) response.get("statusCode"));
        
        Map<String, Object> body = (Map<String, Object>) response.get("body");
        assertEquals("Event processed successfully", body.get("message"));
        String eventId = (String) body.get("eventId");
        assertNotNull(eventId);
        
        // Verify audit entry exists in DynamoDB
        verifyAuditEntryExists(eventId, "Product Created", "product.service");
        
        // Cleanup
        cleanup(eventId);
    }

    @Test
    void handleRequest_EventWithComplexDetail_ShouldSerializeAndPersist() {
        // Arrange
        Map<String, Object> event = createValidEventBridgeEvent();
        Map<String, Object> complexDetail = new HashMap<>();
        complexDetail.put("productId", "test-123");
        complexDetail.put("productName", "Integration Test Product");
        complexDetail.put("price", 199.99);
        complexDetail.put("metadata", Map.of(
            "category", "electronics",
            "tags", new String[]{"new", "featured", "integration-test"}
        ));
        event.put("detail", complexDetail);
        
        // Act
        Map<String, Object> response = eventProcessorHandler.handleRequest(event, mockContext);
        
        // Assert
        assertEquals(200, (Integer) response.get("statusCode"));
        
        Map<String, Object> body = (Map<String, Object>) response.get("body");
        String eventId = (String) body.get("eventId");
        assertNotNull(eventId);
        
        // Verify complex detail is properly serialized in DynamoDB
        verifyComplexDetailSerialization(eventId, "test-123", "Integration Test Product", 199.99);
        
        // Cleanup
        cleanup(eventId);
    }

    @Test
    void handleRequest_EventWithNullValues_ShouldHandleGracefully() {
        // Arrange
        Map<String, Object> event = new HashMap<>();
        event.put("detail-type", null);
        event.put("source", null);
        event.put("detail", null);
        
        // Act
        Map<String, Object> response = eventProcessorHandler.handleRequest(event, mockContext);
        
        // Assert
        assertEquals(200, (Integer) response.get("statusCode"));
        
        Map<String, Object> body = (Map<String, Object>) response.get("body");
        String eventId = (String) body.get("eventId");
        assertNotNull(eventId);
        
        // Verify defaults are used in DynamoDB
        verifyAuditEntryExists(eventId, "Unknown", "Unknown");
        
        // Cleanup
        cleanup(eventId);
    }

    @Test
    void handleRequest_MultipleEvents_ShouldPersistAllEntries() {
        // Arrange
        Map<String, Object> event1 = createEventWithType("User Created", "user.service");
        Map<String, Object> event2 = createEventWithType("Order Placed", "order.service");
        Map<String, Object> event3 = createEventWithType("Payment Processed", "payment.service");
        
        // Act
        Map<String, Object> response1 = eventProcessorHandler.handleRequest(event1, mockContext);
        Map<String, Object> response2 = eventProcessorHandler.handleRequest(event2, mockContext);
        Map<String, Object> response3 = eventProcessorHandler.handleRequest(event3, mockContext);
        
        // Assert
        assertEquals(200, (Integer) response1.get("statusCode"));
        assertEquals(200, (Integer) response2.get("statusCode"));
        assertEquals(200, (Integer) response3.get("statusCode"));
        
        String eventId1 = (String) ((Map<String, Object>) response1.get("body")).get("eventId");
        String eventId2 = (String) ((Map<String, Object>) response2.get("body")).get("eventId");
        String eventId3 = (String) ((Map<String, Object>) response3.get("body")).get("eventId");
        
        // Verify all entries exist in DynamoDB
        verifyAuditEntryExists(eventId1, "User Created", "user.service");
        verifyAuditEntryExists(eventId2, "Order Placed", "order.service");
        verifyAuditEntryExists(eventId3, "Payment Processed", "payment.service");
        
        // Cleanup
        cleanup(eventId1);
        cleanup(eventId2);
        cleanup(eventId3);
    }

    @Test
    void handleRequest_ValidEvent_ShouldSetCorrectTTL() {
        // Arrange
        Map<String, Object> event = createValidEventBridgeEvent();
        long beforeTest = Instant.now().getEpochSecond();
        
        // Act
        Map<String, Object> response = eventProcessorHandler.handleRequest(event, mockContext);
        
        // Assert
        assertEquals(200, (Integer) response.get("statusCode"));
        
        String eventId = (String) ((Map<String, Object>) response.get("body")).get("eventId");
        
        // Verify TTL is approximately 90 days from now
        verifyTTLIsCorrect(eventId, beforeTest);
        
        // Cleanup
        cleanup(eventId);
    }

    // Helper methods

    private Map<String, Object> createValidEventBridgeEvent() {
        return createEventWithType("Product Created", "product.service");
    }

    private Map<String, Object> createEventWithType(String detailType, String source) {
        Map<String, Object> event = new HashMap<>();
        event.put("detail-type", detailType);
        event.put("source", source);
        
        Map<String, Object> detail = new HashMap<>();
        detail.put("id", UUID.randomUUID().toString());
        detail.put("action", "integration-test");
        detail.put("timestamp", Instant.now().toString());
        event.put("detail", detail);
        
        return event;
    }

    private void verifyAuditEntryExists(String eventId, String expectedEventType, String expectedSource) {
        GetItemRequest getRequest = GetItemRequest.builder()
            .tableName(TABLE_NAME)
            .key(Map.of("event_id", AttributeValue.builder().s(eventId).build()))
            .build();
        
        GetItemResponse getResponse = dynamoDbClient.getItem(getRequest);
        
        assertTrue(getResponse.hasItem(), "Audit entry should exist in DynamoDB for eventId: " + eventId);
        
        Map<String, AttributeValue> item = getResponse.item();
        assertEquals(eventId, item.get("event_id").s());
        assertEquals(expectedEventType, item.get("event_type").s());
        assertEquals(expectedSource, item.get("source").s());
        assertNotNull(item.get("timestamp"));
        assertNotNull(item.get("detail"));
        assertNotNull(item.get("ttl"));
    }

    private void verifyComplexDetailSerialization(String eventId, String expectedProductId, 
                                                String expectedProductName, double expectedPrice) {
        GetItemRequest getRequest = GetItemRequest.builder()
            .tableName(TABLE_NAME)
            .key(Map.of("event_id", AttributeValue.builder().s(eventId).build()))
            .build();
        
        GetItemResponse getResponse = dynamoDbClient.getItem(getRequest);
        
        assertTrue(getResponse.hasItem());
        
        String detailJson = getResponse.item().get("detail").s();
        assertTrue(detailJson.contains("\"productId\":\"" + expectedProductId + "\""));
        assertTrue(detailJson.contains("\"productName\":\"" + expectedProductName + "\""));
        assertTrue(detailJson.contains("\"price\":" + expectedPrice));
        assertTrue(detailJson.contains("\"category\":\"electronics\""));
    }

    private void verifyTTLIsCorrect(String eventId, long beforeTest) {
        GetItemRequest getRequest = GetItemRequest.builder()
            .tableName(TABLE_NAME)
            .key(Map.of("event_id", AttributeValue.builder().s(eventId).build()))
            .build();
        
        GetItemResponse getResponse = dynamoDbClient.getItem(getRequest);
        
        assertTrue(getResponse.hasItem());
        
        long ttl = Long.parseLong(getResponse.item().get("ttl").n());
        long expectedMinTTL = beforeTest + (90 * 24 * 60 * 60) - 10; // Allow 10 second variance
        long expectedMaxTTL = beforeTest + (90 * 24 * 60 * 60) + 10;
        
        assertTrue(ttl >= expectedMinTTL && ttl <= expectedMaxTTL,
            "TTL should be approximately 90 days from test start. Expected: " + 
            expectedMinTTL + "-" + expectedMaxTTL + ", Actual: " + ttl);
    }

    private void assumeTableExists() {
        try {
            DescribeTableRequest request = DescribeTableRequest.builder()
                .tableName(TABLE_NAME)
                .build();
            
            DescribeTableResponse response = dynamoDbClient.describeTable(request);
            
            if (response.table().tableStatus() != TableStatus.ACTIVE) {
                throw new RuntimeException("Table " + TABLE_NAME + " is not in ACTIVE status: " + 
                    response.table().tableStatus());
            }
            
        } catch (ResourceNotFoundException e) {
            throw new RuntimeException("DynamoDB table " + TABLE_NAME + " does not exist. " +
                "Please create the table or set AUDIT_TABLE_NAME environment variable.", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to DynamoDB table " + TABLE_NAME + 
                ". Check AWS credentials and permissions.", e);
        }
    }

    private void cleanup(String eventId) {
        try {
            DeleteItemRequest deleteRequest = DeleteItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of("event_id", AttributeValue.builder().s(eventId).build()))
                .build();
            
            dynamoDbClient.deleteItem(deleteRequest);
        } catch (Exception e) {
            System.err.println("Failed to cleanup audit entry " + eventId + ": " + e.getMessage());
        }
    }

    private void setEnvironmentVariable(String name, String value) {
        // This is a test helper - in real scenarios this would be set externally
        try {
            java.lang.reflect.Field field = EventProcessorHandler.class.getDeclaredField("TABLE_NAME");
            field.setAccessible(true);
            field.set(null, value);
        } catch (Exception e) {
            // Use system property as fallback
            System.setProperty(name, value);
        }
    }
}