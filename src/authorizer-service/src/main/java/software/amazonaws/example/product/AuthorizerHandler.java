package software.amazonaws.example.product;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayCustomAuthorizerEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.lambda.powertools.logging.Logging;
import software.amazon.lambda.powertools.tracing.Tracing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;

public class AuthorizerHandler implements RequestHandler<APIGatewayCustomAuthorizerEvent, Map<String, Object>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizerHandler.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    @Logging(logEvent = true)
    @Tracing
    public Map<String, Object> handleRequest(APIGatewayCustomAuthorizerEvent request, Context context) {
        try {
            MDC.put("requestId", context.getAwsRequestId());
            LOGGER.info("Processing authorization request");

            // Extract API key from headers
            Map<String, String> headers = request.getHeaders();
            String apiKey = null;

            if (headers != null) {
                apiKey = headers.get("x-api-key");
                if (apiKey == null) {
                    apiKey = headers.get("X-API-Key");
                }
            }

            // Simple validation - in production, validate against stored keys
            boolean isAuthorized = apiKey != null && !apiKey.trim().isEmpty();

            MDC.put("isAuthorized", String.valueOf(isAuthorized));
            LOGGER.info("Authorization result determined");

            // Return authorization response in the format expected by API Gateway v2
            Map<String, Object> response = new HashMap<>();
            response.put("isAuthorized", isAuthorized);

            Map<String, Object> context_data = new HashMap<>();
            context_data.put("apiKey", isAuthorized ? apiKey : "invalid");
            response.put("context", context_data);

            return response;

        } catch (Exception e) {
            MDC.put("error", e.getMessage());
            LOGGER.error("Authorization error", e);

            Map<String, Object> response = new HashMap<>();
            response.put("isAuthorized", false);

            Map<String, Object> context_data = new HashMap<>();
            context_data.put("error", "Authorization failed");
            response.put("context", context_data);

            return response;
        }
    }
}