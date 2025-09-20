package com.example.users;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Users Lambda function handler
 */
public class UsersHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        return processUsersRequest(input, context);
    }

    private APIGatewayProxyResponseEvent processUsersRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            // Fetch users data
            ArrayNode users = getUsersFromDatabase();

            // Create response data
            ObjectNode responseData = createResponseData(users, context.getAwsRequestId());

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

    private ArrayNode getUsersFromDatabase() {
        // Simulate database latency
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Mock users data - in real implementation, you'd fetch from DynamoDB
        ArrayNode users = objectMapper.createArrayNode();

        ObjectNode user1 = objectMapper.createObjectNode();
        user1.put("id", "1");
        user1.put("name", "John Doe");
        user1.put("email", "john@example.com");
        user1.put("createdAt", "2024-01-15T10:30:00Z");
        users.add(user1);

        ObjectNode user2 = objectMapper.createObjectNode();
        user2.put("id", "2");
        user2.put("name", "Jane Smith");
        user2.put("email", "jane@example.com");
        user2.put("createdAt", "2024-01-16T14:45:00Z");
        users.add(user2);

        ObjectNode user3 = objectMapper.createObjectNode();
        user3.put("id", "3");
        user3.put("name", "Alice Johnson");
        user3.put("email", "alice@example.com");
        user3.put("createdAt", "2024-01-17T09:15:00Z");
        users.add(user3);

        // Users fetched successfully

        return users;
    }

    private ObjectNode createResponseData(ArrayNode users, String requestId) {
        ObjectNode responseBody = objectMapper.createObjectNode();
        responseBody.set("users", users);
        responseBody.put("count", users.size());
        responseBody.put("timestamp", Instant.now().toString());
        responseBody.put("requestId", requestId);

        // Response data prepared

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