package software.amazonaws.example.payment;

/**
 * Result object for payment processing operations.
 */
public class PaymentResult {
    private final boolean success;
    private final String status;
    private final String transactionId;
    private final String paymentMethod;
    private final String errorMessage;

    private PaymentResult(boolean success, String status, String transactionId, 
                         String paymentMethod, String errorMessage) {
        this.success = success;
        this.status = status;
        this.transactionId = transactionId;
        this.paymentMethod = paymentMethod;
        this.errorMessage = errorMessage;
    }

    public static PaymentResult success(String transactionId, String paymentMethod) {
        return new PaymentResult(true, "APPROVED", transactionId, paymentMethod, null);
    }

    public static PaymentResult failed(String errorMessage, String errorCode) {
        return new PaymentResult(false, "DECLINED", null, null, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getStatus() {
        return status;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}