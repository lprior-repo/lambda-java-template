package com.example.hello;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Hello Lambda function handler
 */
public class HelloHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        return processHelloRequest(input, context);
    }

    private APIGatewayProxyResponseEvent processHelloRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            // Business logic
            ObjectNode responseData = processBusinessLogic(input);

            // Create successful response
            Map<String, String> headers = createResponseHeaders(context.getAwsRequestId());
            APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
            response.setStatusCode(200);
            response.setHeaders(headers);
            response.setBody(objectMapper.writeValueAsString(responseData));

            return response;

        } catch (Exception e) {
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

    private ObjectNode processBusinessLogic(APIGatewayProxyRequestEvent input) {
        // Simulate business processing time
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        ObjectNode responseBody = objectMapper.createObjectNode();
        responseBody.put("message", "Hello from Lambda!");
        responseBody.put("path", input.getPath() != null ? input.getPath() : "/hello");
        responseBody.put("timestamp", Instant.now().toString());
        responseBody.put("environment", System.getenv("ENVIRONMENT"));
        responseBody.put("functionName", System.getenv("AWS_LAMBDA_FUNCTION_NAME"));

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