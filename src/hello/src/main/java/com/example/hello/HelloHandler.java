package com.example.hello;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import software.amazon.lambda.powertools.logging.Logging;
import software.amazon.lambda.powertools.tracing.Tracing;
import software.amazon.lambda.powertools.metrics.Metrics;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Hello Lambda function handler with AWS Powertools integration
 */
@Logging(logEvent = true, samplingRate = 0.1)
@Tracing(captureMode = Tracing.CaptureMode.RESPONSE_AND_ERROR)
@Metrics(namespace = "LambdaTemplate/Hello", service = "hello-service")
public class HelloHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Tracing
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        return processHelloRequest(input, context);
    }

    @Tracing
    private APIGatewayProxyResponseEvent processHelloRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            // Add tracing annotations
            software.amazon.lambda.powertools.tracing.Tracing.putAnnotation("path", input.getPath() != null ? input.getPath() : "/hello");
            software.amazon.lambda.powertools.tracing.Tracing.putAnnotation("httpMethod", input.getHttpMethod());

            // Add custom metrics
            software.amazon.lambda.powertools.metrics.Metrics.addMetric("RequestCount", 1.0);

            // Log structured information
            software.amazon.lambda.powertools.logging.LoggingUtils.appendKey("requestId", context.getAwsRequestId());
            software.amazon.lambda.powertools.logging.LoggingUtils.appendKey("functionName", context.getFunctionName());
            software.amazon.lambda.powertools.logging.LoggingUtils.appendKey("functionVersion", context.getFunctionVersion());
            software.amazon.lambda.powertools.logging.LoggingUtils.appendKey("remainingTime", context.getRemainingTimeInMillis());

            // Business logic
            ObjectNode responseData = processBusinessLogic(input);

            // Create successful response
            Map<String, String> headers = createResponseHeaders(context.getAwsRequestId());
            APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
            response.setStatusCode(200);
            response.setHeaders(headers);
            response.setBody(objectMapper.writeValueAsString(responseData));

            // Add success metrics
            software.amazon.lambda.powertools.metrics.Metrics.addMetric("SuccessCount", 1.0);

            return response;

        } catch (Exception e) {
            // Log error with context
            software.amazon.lambda.powertools.logging.LoggingUtils.appendKey("error", e.getMessage());

            // Add error metrics
            software.amazon.lambda.powertools.metrics.Metrics.addMetric("ErrorCount", 1.0);

            // Create error response
            Map<String, String> headers = createResponseHeaders(context.getAwsRequestId());
            APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
            response.setStatusCode(500);
            response.setHeaders(headers);

            ObjectNode errorBody = objectMapper.createObjectNode();
            errorBody.put("message", "Internal server error");
            errorBody.put("requestId", context.getAwsRequestId());
            errorBody.put("timestamp", Instant.now().toString());

            try {
                response.setBody(objectMapper.writeValueAsString(errorBody));
            } catch (Exception jsonException) {
                response.setBody("{\"error\":\"Internal server error\"}");
            }

            return response;
        }
    }

    @Tracing
    private ObjectNode processBusinessLogic(APIGatewayProxyRequestEvent input) {
        // Simulate business processing time
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        ObjectNode responseBody = objectMapper.createObjectNode();
        responseBody.put("message", "Hello from Lambda with Powertools!");
        responseBody.put("path", input.getPath() != null ? input.getPath() : "/hello");
        responseBody.put("timestamp", Instant.now().toString());
        responseBody.put("environment", System.getenv("ENVIRONMENT"));
        responseBody.put("functionName", System.getenv("AWS_LAMBDA_FUNCTION_NAME"));

        // Add tracing metadata
        software.amazon.lambda.powertools.tracing.Tracing.putMetadata("response", responseBody);

        return responseBody;
    }

    private Map<String, String> createResponseHeaders(String requestId) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Headers", "Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token");
        headers.put("Access-Control-Allow-Methods", "OPTIONS,POST,GET");
        headers.put("X-Request-ID", requestId);
        headers.put("Cache-Control", "max-age=300");
        return headers;
    }
}