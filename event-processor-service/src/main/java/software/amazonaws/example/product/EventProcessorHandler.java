package software.amazonaws.example.product;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.CloudWatchLogsEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.lambda.powertools.logging.Logging;
import software.amazon.lambda.powertools.tracing.Tracing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EventProcessorHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventProcessorHandler.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TABLE_NAME = System.getenv("AUDIT_TABLE_NAME");

    private final DynamoDbClient dynamoDbClient;

    public EventProcessorHandler() {
        this.dynamoDbClient = DynamoDbClient.builder().build();
    }

    // Constructor for testing
    public EventProcessorHandler(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    @Logging(logEvent = true)
    @Tracing
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            MDC.put("requestId", context.getAwsRequestId());
            LOGGER.info("Processing EventBridge event");

            if (TABLE_NAME == null || TABLE_NAME.isEmpty()) {
                LOGGER.error("AUDIT_TABLE_NAME environment variable not set");
                return createErrorResponse("Configuration error");
            }

            // Extract event details
            Object detailObj = event.get("detail");
            String detailType = (String) event.get("detail-type");
            String source = (String) event.get("source");

            if (detailType == null) {
                detailType = "Unknown";
            }
            if (source == null) {
                source = "Unknown";
            }

            MDC.put("eventType", detailType);
            MDC.put("eventSource", source);

            // Create audit log entry
            String eventId = UUID.randomUUID().toString();
            String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
            String detailJson = OBJECT_MAPPER.writeValueAsString(detailObj != null ? detailObj : new HashMap<>());

            // Calculate TTL (90 days from now)
            long ttl = Instant.now().plusSeconds(90 * 24 * 60 * 60).getEpochSecond();

            // Prepare DynamoDB item
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("event_id", AttributeValue.builder().s(eventId).build());
            item.put("timestamp", AttributeValue.builder().s(timestamp).build());
            item.put("event_type", AttributeValue.builder().s(detailType).build());
            item.put("source", AttributeValue.builder().s(source).build());
            item.put("detail", AttributeValue.builder().s(detailJson).build());
            item.put("ttl", AttributeValue.builder().n(String.valueOf(ttl)).build());

            // Write to DynamoDB
            PutItemRequest putRequest = PutItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .item(item)
                    .build();

            dynamoDbClient.putItem(putRequest);

            MDC.put("auditEventId", eventId);
            LOGGER.info("Audit entry created successfully");

            return createSuccessResponse(eventId);

        } catch (Exception e) {
            MDC.put("error", e.getMessage());
            LOGGER.error("Event processing error", e);
            return createErrorResponse("Event processing failed: " + e.getMessage());
        }
    }

    private Map<String, Object> createSuccessResponse(String eventId) {
        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", 200);

        Map<String, Object> body = new HashMap<>();
        body.put("message", "Event processed successfully");
        body.put("eventId", eventId);
        response.put("body", body);

        return response;
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", 500);

        Map<String, Object> body = new HashMap<>();
        body.put("error", "Event processing failed");
        body.put("message", message);
        response.put("body", body);

        return response;
    }
}