package software.amazonaws.example.inventory;

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
class InventoryHandlerTest {

    @Mock
    private Context context;

    @Mock
    private LambdaLogger logger;

    private InventoryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new InventoryHandler();
        when(context.getLogger()).thenReturn(logger);
    }

    @Test
    void testSuccessfulInventoryCheck() {
        // Given
        Map<String, Object> input = new HashMap<>();
        input.put("orderId", "order-123");
        input.put("productId", "product-456");
        input.put("quantity", 2);

        // When
        Map<String, Object> result = handler.handleRequest(input, context);

        // Then
        assertNotNull(result);
        assertEquals("order-123", result.get("orderId"));
        assertEquals("product-456", result.get("productId"));
        assertEquals(2, result.get("requestedQuantity"));
        assertNotNull(result.get("availabilityStatus"));
        assertNotNull(result.get("stockLevel"));

        String status = (String) result.get("availabilityStatus");
        assertTrue(status.equals("AVAILABLE") || status.equals("OUT_OF_STOCK") || 
                  status.equals("INSUFFICIENT_STOCK") || status.equals("ERROR"));

        if ("AVAILABLE".equals(status)) {
            assertNotNull(result.get("reservationId"));
            assertNotNull(result.get("reservedQuantity"));
        } else {
            assertNotNull(result.get("unavailabilityReason"));
        }
    }

    @Test
    void testInventoryCheckWithDefaultQuantity() {
        // Given
        Map<String, Object> input = new HashMap<>();
        input.put("orderId", "order-123");
        input.put("productId", "product-456");
        // No quantity specified - should default to 1

        // When
        Map<String, Object> result = handler.handleRequest(input, context);

        // Then
        assertNotNull(result);
        assertEquals(1, result.get("requestedQuantity"));
    }

    @Test
    void testInventoryCheckWithMissingProductId() {
        // Given
        Map<String, Object> input = new HashMap<>();
        input.put("orderId", "order-123");
        input.put("quantity", 2);
        // Missing productId

        // When
        Map<String, Object> result = handler.handleRequest(input, context);

        // Then
        assertNotNull(result);
        assertEquals("order-123", result.get("orderId"));
        assertEquals(2, result.get("requestedQuantity"));
        // Should still process even with null productId
    }

    @Test
    void testErrorHandling() {
        // Given
        Map<String, Object> input = new HashMap<>();
        input.put("orderId", "order-123");
        input.put("productId", "product-456");
        input.put("quantity", "invalid-quantity"); // This will cause NumberFormatException

        // When
        Map<String, Object> result = handler.handleRequest(input, context);

        // Then
        assertNotNull(result);
        assertEquals("ERROR", result.get("availabilityStatus"));
        assertNotNull(result.get("unavailabilityReason"));
        assertTrue(((String) result.get("unavailabilityReason")).contains("Internal inventory service error"));
    }

    @Test
    void testLoggingOccurs() {
        // Given
        Map<String, Object> input = new HashMap<>();
        input.put("orderId", "order-123");
        input.put("productId", "product-456");
        input.put("quantity", 5);

        // When
        handler.handleRequest(input, context);

        // Then
        verify(logger, atLeastOnce()).log(anyString());
    }
}