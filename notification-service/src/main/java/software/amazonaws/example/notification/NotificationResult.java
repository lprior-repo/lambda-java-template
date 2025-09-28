package software.amazonaws.example.notification;

/**
 * Result object for notification delivery operations.
 */
public class NotificationResult {
    private final boolean success;
    private final String status;
    private final String notificationId;
    private final String deliveryMethod;
    private final String sentAt;
    private final String messagePreview;
    private final String errorMessage;
    private final boolean retryable;

    private NotificationResult(boolean success, String status, String notificationId, 
                              String deliveryMethod, String sentAt, String messagePreview,
                              String errorMessage, boolean retryable) {
        this.success = success;
        this.status = status;
        this.notificationId = notificationId;
        this.deliveryMethod = deliveryMethod;
        this.sentAt = sentAt;
        this.messagePreview = messagePreview;
        this.errorMessage = errorMessage;
        this.retryable = retryable;
    }

    public static NotificationResult success(String notificationId, String deliveryMethod, 
                                           String sentAt, String messageContent) {
        String preview = messageContent.length() > 50 ? 
            messageContent.substring(0, 47) + "..." : messageContent;
        return new NotificationResult(true, "SENT", notificationId, deliveryMethod, 
                                    sentAt, preview, null, false);
    }

    public static NotificationResult failed(String errorMessage, boolean retryable) {
        String status = retryable ? "FAILED_RETRYABLE" : "FAILED_PERMANENT";
        return new NotificationResult(false, status, null, null, null, null, 
                                    errorMessage, retryable);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getStatus() {
        return status;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public String getDeliveryMethod() {
        return deliveryMethod;
    }

    public String getSentAt() {
        return sentAt;
    }

    public String getMessagePreview() {
        return messagePreview;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isRetryable() {
        return retryable;
    }
}