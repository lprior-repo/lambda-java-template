package software.amazonaws.example.inventory;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.lambda.powertools.logging.Logging;
import software.amazon.lambda.powertools.tracing.Tracing;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Lambda function handler for inventory management in Step Functions workflow.
 * Checks product availability, simulates stock levels, and manages inventory reservations.
 */
public class InventoryHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final Random random = new Random();

    @Override
    @Logging(logEvent = true, clearState = true)
    @Tracing(namespace = "InventoryService")
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Extract inventory request information
            String orderId = (String) input.get("orderId");
            String productId = (String) input.get("productId");
            Integer requestedQuantity = ((Number) input.getOrDefault("quantity", 1)).intValue();
            
            context.getLogger().log(String.format("Checking inventory for order %s, product %s, quantity: %d", 
                orderId, productId, requestedQuantity));
            
            // Simulate inventory check delay
            Thread.sleep(50 + random.nextInt(150));
            
            // Simulate inventory processing logic
            InventoryResult inventoryResult = checkAndReserveInventory(orderId, productId, requestedQuantity);
            
            // Prepare response
            result.put("orderId", orderId);
            result.put("productId", productId);
            result.put("requestedQuantity", requestedQuantity);
            result.put("availabilityStatus", inventoryResult.getAvailabilityStatus());
            result.put("stockLevel", inventoryResult.getStockLevel());
            
            if (inventoryResult.isAvailable()) {
                result.put("reservationId", inventoryResult.getReservationId());
                result.put("reservedQuantity", inventoryResult.getReservedQuantity());
            } else {
                result.put("unavailabilityReason", inventoryResult.getUnavailabilityReason());
            }
            
            context.getLogger().log(String.format("Inventory check %s for order %s - Status: %s", 
                inventoryResult.isAvailable() ? "succeeded" : "failed", orderId, inventoryResult.getAvailabilityStatus()));
            
        } catch (Exception e) {
            context.getLogger().log("Error during inventory check: " + e.getMessage());
            result.put("availabilityStatus", "ERROR");
            result.put("unavailabilityReason", "Internal inventory service error: " + e.getMessage());
        }
        
        return result;
    }

    private InventoryResult checkAndReserveInventory(String orderId, String productId, Integer requestedQuantity) {
        // Simulate different inventory scenarios
        
        // Generate simulated current stock level
        int currentStock = generateStockLevel(productId);
        
        // Simulate out of stock for 15% of requests
        if (random.nextDouble() < 0.15) {
            return InventoryResult.outOfStock(0, "Product temporarily out of stock");
        }
        
        // Check if requested quantity exceeds available stock
        if (requestedQuantity > currentStock) {
            return InventoryResult.insufficientStock(currentStock, 
                String.format("Insufficient stock: requested %d, available %d", requestedQuantity, currentStock));
        }
        
        // Simulate inventory system failures (2% chance)
        if (random.nextDouble() < 0.02) {
            return InventoryResult.error(currentStock, "Inventory system temporarily unavailable");
        }
        
        // Success case - reserve inventory
        String reservationId = "rsv_" + UUID.randomUUID().toString().substring(0, 8);
        int remainingStock = currentStock - requestedQuantity;
        
        return InventoryResult.available(reservationId, requestedQuantity, remainingStock);
    }

    private int generateStockLevel(String productId) {
        // Generate pseudo-random stock level based on product ID for consistency
        int seed = productId != null ? productId.hashCode() : 0;
        Random productRandom = new Random(seed + System.currentTimeMillis() / (1000 * 60 * 5)); // Changes every 5 minutes
        
        // Generate stock levels with different probability distributions
        double stockTier = productRandom.nextDouble();
        
        if (stockTier < 0.1) {
            // 10% chance of low stock (0-5 items)
            return productRandom.nextInt(6);
        } else if (stockTier < 0.3) {
            // 20% chance of medium stock (6-20 items)
            return 6 + productRandom.nextInt(15);
        } else {
            // 70% chance of high stock (21-100 items)
            return 21 + productRandom.nextInt(80);
        }
    }
}