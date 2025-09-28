package software.amazonaws.example.payment;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.lambda.powertools.logging.Logging;
import software.amazon.lambda.powertools.tracing.Tracing;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Lambda function handler for payment processing in Step Functions workflow.
 * Simulates payment processing with random success/failure scenarios.
 */
public class PaymentHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final Random random = new Random();

    @Override
    @Logging(logEvent = true, clearState = true)
    @Tracing(namespace = "PaymentService")
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Extract payment information
            String orderId = (String) input.get("orderId");
            String customerId = (String) input.get("customerId");
            Double totalAmount = ((Number) input.get("totalAmount")).doubleValue();
            
            context.getLogger().log(String.format("Processing payment for order %s, amount: $%.2f", orderId, totalAmount));
            
            // Simulate payment processing delay
            Thread.sleep(100 + random.nextInt(200));
            
            // Simulate payment processing logic
            PaymentResult paymentResult = processPayment(orderId, customerId, totalAmount);
            
            // Prepare response
            result.put("orderId", orderId);
            result.put("customerId", customerId);
            result.put("totalAmount", totalAmount);
            result.put("paymentStatus", paymentResult.getStatus());
            result.put("transactionId", paymentResult.getTransactionId());
            result.put("paymentMethod", paymentResult.getPaymentMethod());
            
            if (!paymentResult.isSuccess()) {
                result.put("paymentError", paymentResult.getErrorMessage());
            }
            
            context.getLogger().log(String.format("Payment %s for order %s", 
                paymentResult.getStatus(), orderId));
            
        } catch (Exception e) {
            context.getLogger().log("Error during payment processing: " + e.getMessage());
            result.put("paymentStatus", "FAILED");
            result.put("paymentError", "Internal payment processing error: " + e.getMessage());
        }
        
        return result;
    }

    private PaymentResult processPayment(String orderId, String customerId, Double amount) {
        // Simulate different payment outcomes based on amount and random factors
        
        // Reject very large amounts
        if (amount > 5000) {
            return PaymentResult.failed("Amount exceeds daily limit", "DECLINED_LIMIT_EXCEEDED");
        }
        
        // Simulate network/processing failures (5% chance)
        if (random.nextDouble() < 0.05) {
            return PaymentResult.failed("Payment gateway timeout", "GATEWAY_TIMEOUT");
        }
        
        // Simulate insufficient funds (10% chance for amounts > $100)
        if (amount > 100 && random.nextDouble() < 0.10) {
            return PaymentResult.failed("Insufficient funds", "INSUFFICIENT_FUNDS");
        }
        
        // Simulate card declined (5% chance)
        if (random.nextDouble() < 0.05) {
            return PaymentResult.failed("Card declined by issuer", "CARD_DECLINED");
        }
        
        // Success case
        String transactionId = "txn_" + UUID.randomUUID().toString().substring(0, 8);
        String paymentMethod = selectPaymentMethod(amount);
        
        return PaymentResult.success(transactionId, paymentMethod);
    }

    private String selectPaymentMethod(Double amount) {
        // Simulate different payment methods based on amount
        if (amount > 1000) {
            return "BANK_TRANSFER";
        } else if (amount > 100) {
            return "CREDIT_CARD";
        } else {
            return "DEBIT_CARD";
        }
    }
}