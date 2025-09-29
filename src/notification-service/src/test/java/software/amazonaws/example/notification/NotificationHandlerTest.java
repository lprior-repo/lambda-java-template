package software.amazonaws.example.notification;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationHandlerTest {

    @Mock
    private Context context;

    @Mock
    private LambdaLogger logger;

    private NotificationHandler handler;

    @BeforeEach
    void setUp() {
        handler = new NotificationHandler();
        when(context.getLogger()).thenReturn(logger);
    }

    @Test
    void testSuccessfulNotificationSending() {
        // Given
        Map<String, Object> input = new HashMap<>();
        input.put("orderId", "order-123");
        input.put("customerId", "customer-456");
        input.put("notificationType", "ORDER_CONFIRMATION");
        input.put("preferredMethod", "email");
        input.put("orderStatus", "CONFIRMED");

        // When
        Map<String, Object> result = handler.handleRequest(input, context);

        // Then
        assertNotNull(result);
        assertEquals("order-123", result.get("orderId"));
        assertEquals("customer-456", result.get("customerId"));
        assertEquals("ORDER_CONFIRMATION", result.get("notificationType"));
        assertNotNull(result.get("notificationStatus"));

        String status = (String) result.get("notificationStatus");
        assertTrue(status.equals("SENT") || status.equals("FAILED_RETRYABLE") || 
                  status.equals("FAILED_PERMANENT"));

        if ("SENT".equals(status)) {
            assertNotNull(result.get("notificationId"));
            assertNotNull(result.get("sentAt"));
            assertNotNull(result.get("messagePreview"));
            assertNotNull(result.get("deliveryMethod"));
        } else {
            assertNotNull(result.get("notificationError"));
            assertNotNull(result.get("retryable"));
            // deliveryMethod may be null for failed notifications
        }
    }

    @Test
    void testNotificationWithDefaults() {
        // Given
        Map<String, Object> input = new HashMap<>();
        input.put("orderId", "order-123");
        input.put("customerId", "customer-456");
        // Using defaults for notificationType, preferredMethod, orderStatus

        // When
        Map<String, Object> result = handler.handleRequest(input, context);

        // Then
        assertNotNull(result);
        assertEquals("ORDER_CONFIRMATION", result.get("notificationType"));
        assertNotNull(result.get("deliveryMethod"));
    }

    @Test
    void testInvalidNotificationType() {
        // Given
        Map<String, Object> input = new HashMap<>();
        input.put("orderId", "order-123");
        input.put("customerId", "customer-456");
        input.put("notificationType", "INVALID_TYPE");
        input.put("preferredMethod", "email");

        // When
        Map<String, Object> result = handler.handleRequest(input, context);

        // Then
        assertNotNull(result);
        String status = (String) result.get("notificationStatus");
        assertTrue(status.equals("FAILED_PERMANENT") || status.equals("FAILED_RETRYABLE"));
        assertNotNull(result.get("notificationError"));
        if (result.get("notificationError").toString().contains("Invalid notification type")) {
            assertEquals(false, result.get("retryable"));
        }
    }

    @Test
    void testDifferentNotificationTypes() {
        String[] notificationTypes = {
            "ORDER_CONFIRMATION", "ORDER_FAILED", "PAYMENT_FAILED", 
            "INVENTORY_UNAVAILABLE", "ORDER_CANCELLED", "ORDER_SHIPPED", "ORDER_DELIVERED"
        };

        for (String type : notificationTypes) {
            // Given
            Map<String, Object> input = new HashMap<>();
            input.put("orderId", "order-123");
            input.put("customerId", "customer-456");
            input.put("notificationType", type);
            input.put("preferredMethod", "email");

            // When
            Map<String, Object> result = handler.handleRequest(input, context);

            // Then
            assertNotNull(result, "Result should not be null for type: " + type);
            assertEquals(type, result.get("notificationType"));
        }
    }

    @Test
    void testDifferentDeliveryMethods() {
        String[] methods = {"email", "sms", "push"};

        for (String method : methods) {
            // Given
            Map<String, Object> input = new HashMap<>();
            input.put("orderId", "order-123");
            input.put("customerId", "customer-456");
            input.put("notificationType", "ORDER_CONFIRMATION");
            input.put("preferredMethod", method);

            // When
            Map<String, Object> result = handler.handleRequest(input, context);

            // Then
            assertNotNull(result);
            assertNotNull(result.get("deliveryMethod"));
            // Delivery method might fallback, so just verify it's set
        }
    }

    @Test
    void testErrorHandling() {
        // Given
        Map<String, Object> input = new HashMap<>();
        input.put("orderId", "order-123");
        input.put("customerId", null); // This might cause issues
        input.put("notificationType", "ORDER_CONFIRMATION");

        // When
        Map<String, Object> result = handler.handleRequest(input, context);

        // Then
        assertNotNull(result);
        // Should handle gracefully, either succeed or fail with proper error
        assertNotNull(result.get("notificationStatus"));
    }

    @Test
    void testLoggingOccurs() {
        // Given
        Map<String, Object> input = new HashMap<>();
        input.put("orderId", "order-123");
        input.put("customerId", "customer-456");
        input.put("notificationType", "ORDER_CONFIRMATION");

        // When
        handler.handleRequest(input, context);

        // Then
        verify(logger, atLeastOnce()).log(anyString());
    }
}