package software.amazonaws.example.notification;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.lambda.powertools.logging.Logging;
import software.amazon.lambda.powertools.tracing.Tracing;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Lambda function handler for sending notifications in Step Functions workflow.
 * Supports multiple notification types (email, sms, push) for order confirmations and failures.
 */
public class NotificationHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final Random random = new Random();

    @Override
    @Logging(logEvent = true, clearState = true)
    @Tracing(namespace = "NotificationService")
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Extract notification request information
            String orderId = (String) input.get("orderId");
            String customerId = (String) input.get("customerId");
            String notificationType = (String) input.getOrDefault("notificationType", "ORDER_CONFIRMATION");
            String preferredMethod = (String) input.getOrDefault("preferredMethod", "email");
            
            // Extract order context for notification content
            String orderStatus = (String) input.getOrDefault("orderStatus", "CONFIRMED");
            Object orderData = input.get("orderData");
            
            context.getLogger().log(String.format("Sending %s notification for order %s via %s", 
                notificationType, orderId, preferredMethod));
            
            // Simulate notification processing delay
            Thread.sleep(30 + random.nextInt(100));
            
            // Process notification
            NotificationResult notificationResult = sendNotification(
                orderId, customerId, notificationType, preferredMethod, orderStatus, orderData);
            
            // Prepare response
            result.put("orderId", orderId);
            result.put("customerId", customerId);
            result.put("notificationType", notificationType);
            result.put("notificationStatus", notificationResult.getStatus());
            result.put("notificationId", notificationResult.getNotificationId());
            result.put("deliveryMethod", notificationResult.getDeliveryMethod());
            
            if (notificationResult.isSuccess()) {
                result.put("sentAt", notificationResult.getSentAt());
                result.put("messagePreview", notificationResult.getMessagePreview());
            } else {
                result.put("notificationError", notificationResult.getErrorMessage());
                result.put("retryable", notificationResult.isRetryable());
            }
            
            context.getLogger().log(String.format("Notification %s for order %s - Status: %s", 
                notificationResult.isSuccess() ? "sent successfully" : "failed", 
                orderId, notificationResult.getStatus()));
            
        } catch (Exception e) {
            context.getLogger().log("Error during notification processing: " + e.getMessage());
            result.put("notificationStatus", "FAILED");
            result.put("notificationError", "Internal notification service error: " + e.getMessage());
            result.put("retryable", true);
        }
        
        return result;
    }

    private NotificationResult sendNotification(String orderId, String customerId, String notificationType, 
                                              String preferredMethod, String orderStatus, Object orderData) {
        
        // Validate notification type
        if (!isValidNotificationType(notificationType)) {
            return NotificationResult.failed("Invalid notification type: " + notificationType, false);
        }
        
        // Simulate notification delivery failures (8% chance)
        if (random.nextDouble() < 0.08) {
            String errorMessage = selectRandomError();
            boolean retryable = !errorMessage.contains("invalid") && !errorMessage.contains("blocked");
            return NotificationResult.failed(errorMessage, retryable);
        }
        
        // Determine actual delivery method (may fallback from preferred)
        String actualMethod = determineDeliveryMethod(preferredMethod, customerId);
        
        // Generate notification content
        String messageContent = generateNotificationContent(notificationType, orderId, orderStatus, orderData);
        
        // Simulate successful delivery
        String notificationId = "ntf_" + UUID.randomUUID().toString().substring(0, 8);
        String sentAt = java.time.Instant.now().toString();
        
        return NotificationResult.success(notificationId, actualMethod, sentAt, messageContent);
    }

    private boolean isValidNotificationType(String type) {
        return type != null && (
            type.equals("ORDER_CONFIRMATION") ||
            type.equals("ORDER_FAILED") ||
            type.equals("PAYMENT_FAILED") ||
            type.equals("INVENTORY_UNAVAILABLE") ||
            type.equals("ORDER_CANCELLED") ||
            type.equals("ORDER_SHIPPED") ||
            type.equals("ORDER_DELIVERED")
        );
    }

    private String determineDeliveryMethod(String preferredMethod, String customerId) {
        // Simulate method availability and fallback logic
        if ("email".equals(preferredMethod)) {
            // Email is highly available
            if (random.nextDouble() < 0.95) {
                return "email";
            } else {
                return "sms"; // Fallback to SMS
            }
        } else if ("sms".equals(preferredMethod)) {
            // SMS has some limitations
            if (random.nextDouble() < 0.85) {
                return "sms";
            } else {
                return "push"; // Fallback to push notification
            }
        } else if ("push".equals(preferredMethod)) {
            // Push notifications depend on app installation
            if (random.nextDouble() < 0.70) {
                return "push";
            } else {
                return "email"; // Fallback to email
            }
        } else {
            // Default to email for unknown methods
            return "email";
        }
    }

    private String generateNotificationContent(String notificationType, String orderId, 
                                             String orderStatus, Object orderData) {
        switch (notificationType) {
            case "ORDER_CONFIRMATION":
                return String.format("Your order %s has been confirmed and is being processed.", orderId);
            case "ORDER_FAILED":
                return String.format("We encountered an issue processing your order %s. Please contact support.", orderId);
            case "PAYMENT_FAILED":
                return String.format("Payment for order %s failed. Please update your payment method.", orderId);
            case "INVENTORY_UNAVAILABLE":
                return String.format("Some items in order %s are temporarily out of stock. We'll notify you when available.", orderId);
            case "ORDER_CANCELLED":
                return String.format("Your order %s has been cancelled as requested.", orderId);
            case "ORDER_SHIPPED":
                return String.format("Your order %s has shipped and is on its way to you!", orderId);
            case "ORDER_DELIVERED":
                return String.format("Your order %s has been delivered. Thank you for your business!", orderId);
            default:
                return String.format("Update regarding your order %s: Status is now %s", orderId, orderStatus);
        }
    }

    private String selectRandomError() {
        String[] errors = {
            "Temporary delivery service unavailable",
            "Rate limit exceeded, please retry later",
            "Customer email address invalid",
            "Customer phone number blocked",
            "Push notification service timeout",
            "Messaging service maintenance in progress"
        };
        return errors[random.nextInt(errors.length)];
    }
}