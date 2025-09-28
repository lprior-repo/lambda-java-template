package software.amazonaws.example.ordervalidation;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.lambda.powertools.logging.Logging;
import software.amazon.lambda.powertools.tracing.Tracing;

import java.util.HashMap;
import java.util.Map;

/**
 * Lambda function handler for order validation in Step Functions workflow.
 * Validates order data structure and business rules.
 */
public class OrderValidationHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Logging(logEvent = true, clearState = true)
    @Tracing(namespace = "OrderValidationService")
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Extract order data from Step Functions input
            Map<String, Object> orderData = extractOrderData(input);
            
            // Validate order structure and business rules
            OrderValidationResult validation = validateOrder(orderData);
            
            // Prepare response for Step Functions
            result.put("isValid", validation.isValid());
            result.put("orderId", orderData.get("orderId"));
            result.put("customerId", orderData.get("customerId"));
            result.put("totalAmount", orderData.get("totalAmount"));
            result.put("items", orderData.get("items"));
            
            if (!validation.isValid()) {
                result.put("validationErrors", validation.getErrors());
            }
            
            // Add metrics
            context.getLogger().log("Order validation completed: " + validation.isValid());
            
        } catch (Exception e) {
            context.getLogger().log("Error during order validation: " + e.getMessage());
            result.put("isValid", false);
            result.put("validationErrors", java.util.List.of("Internal validation error: " + e.getMessage()));
        }
        
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractOrderData(Map<String, Object> input) {
        // Step Functions input might be nested - extract order data
        if (input.containsKey("order")) {
            return (Map<String, Object>) input.get("order");
        }
        return input;
    }

    private OrderValidationResult validateOrder(Map<String, Object> orderData) {
        OrderValidationResult result = new OrderValidationResult();
        
        // Validate required fields
        if (!orderData.containsKey("orderId") || orderData.get("orderId") == null) {
            result.addError("Order ID is required");
        }
        
        if (!orderData.containsKey("customerId") || orderData.get("customerId") == null) {
            result.addError("Customer ID is required");
        }
        
        if (!orderData.containsKey("totalAmount")) {
            result.addError("Total amount is required");
        } else {
            // Validate amount is positive
            try {
                double amount = ((Number) orderData.get("totalAmount")).doubleValue();
                if (amount <= 0) {
                    result.addError("Total amount must be positive");
                }
                if (amount > 10000) {
                    result.addError("Total amount exceeds maximum limit of $10,000");
                }
            } catch (Exception e) {
                result.addError("Invalid total amount format");
            }
        }
        
        // Validate items array
        if (!orderData.containsKey("items") || !(orderData.get("items") instanceof java.util.List)) {
            result.addError("Items list is required");
        } else {
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> items = (java.util.List<Map<String, Object>>) orderData.get("items");
            
            if (items.isEmpty()) {
                result.addError("At least one item is required");
            }
            
            // Validate each item
            for (int i = 0; i < items.size(); i++) {
                Map<String, Object> item = items.get(i);
                validateOrderItem(item, i, result);
            }
        }
        
        return result;
    }

    private void validateOrderItem(Map<String, Object> item, int index, OrderValidationResult result) {
        String prefix = "Item " + (index + 1) + ": ";
        
        if (!item.containsKey("productId") || item.get("productId") == null) {
            result.addError(prefix + "Product ID is required");
        }
        
        if (!item.containsKey("quantity")) {
            result.addError(prefix + "Quantity is required");
        } else {
            try {
                int quantity = ((Number) item.get("quantity")).intValue();
                if (quantity <= 0) {
                    result.addError(prefix + "Quantity must be positive");
                }
            } catch (Exception e) {
                result.addError(prefix + "Invalid quantity format");
            }
        }
        
        if (!item.containsKey("price")) {
            result.addError(prefix + "Price is required");
        } else {
            try {
                double price = ((Number) item.get("price")).doubleValue();
                if (price <= 0) {
                    result.addError(prefix + "Price must be positive");
                }
            } catch (Exception e) {
                result.addError(prefix + "Invalid price format");
            }
        }
    }
}