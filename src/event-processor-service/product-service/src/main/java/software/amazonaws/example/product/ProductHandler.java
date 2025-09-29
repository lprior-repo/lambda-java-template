package software.amazonaws.example.product;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.lambda.powertools.logging.Logging;
import software.amazon.lambda.powertools.metrics.Metrics;
import software.amazon.lambda.powertools.tracing.Tracing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ProductHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductHandler.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TABLE_NAME = System.getenv("PRODUCTS_TABLE_NAME");

    private final ProductService productService;

    public ProductHandler() {
        DynamoDbClient dynamoDbClient = DynamoDbClient.builder().build();
        ProductRepository productRepository = new ProductRepository(dynamoDbClient, TABLE_NAME);
        this.productService = new ProductService(productRepository);
    }

    // Constructor for testing
    public ProductHandler(ProductService productService) {
        this.productService = productService;
    }

    @Override
    @Logging(logEvent = true)
    @Tracing
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            String httpMethod = request.getHttpMethod();
            String path = request.getPath();

            // Add correlation IDs and additional context using MDC
            MDC.put("httpMethod", httpMethod);
            MDC.put("path", path);
            MDC.put("requestId", context.getAwsRequestId());

            LOGGER.info("Processing API request");

            return switch (httpMethod) {
                case "GET" -> handleGetProduct(request, context);
                case "POST" -> handleCreateProduct(request, context);
                case "PUT" -> handleUpdateProduct(request, context);
                case "DELETE" -> handleDeleteProduct(request, context);
                default -> createErrorResponse(405, "Method Not Allowed", "HTTP method not supported");
            };

        } catch (Exception e) {
            // Add error tracking with Powertools v2
            MDC.put("error", e.getMessage());
            LOGGER.error("Error processing request", e);
            return createErrorResponse(500, "Internal Server Error", "An unexpected error occurred");
        }
    }

    @Tracing
    private APIGatewayProxyResponseEvent handleGetProduct(APIGatewayProxyRequestEvent request, Context context) {
        try {
            String productId = extractProductIdFromPath(request.getPath());
            if (productId == null) {
                LOGGER.warn("Product ID validation failed");
                return createErrorResponse(400, "Bad Request", "Product ID is required");
            }

            MDC.put("productId", productId);
            LOGGER.info("Retrieving product");

            Optional<ProductResponse> product = productService.getProduct(productId);
            if (product.isEmpty()) {
                LOGGER.warn("Product not found");
                return createErrorResponse(404, "Not Found", "Product not found");
            }

            LOGGER.info("Product retrieved successfully");
            return createSuccessResponse(200, product.get());

        } catch (IllegalArgumentException e) {
            return createErrorResponse(400, "Bad Request", e.getMessage());
        } catch (Exception e) {
            MDC.put("error", e.getMessage());
            LOGGER.error("Error retrieving product", e);
            return createErrorResponse(500, "Internal Server Error", "Failed to retrieve product");
        }
    }

    @Tracing
    private APIGatewayProxyResponseEvent handleCreateProduct(APIGatewayProxyRequestEvent request, Context context) {
        try {
            CreateProductRequest createRequest = OBJECT_MAPPER.readValue(request.getBody(), CreateProductRequest.class);

            MDC.put("productName", createRequest.getName());
            LOGGER.info("Creating product");

            ProductResponse response = productService.createProduct(createRequest);

            MDC.put("createdProductId", response.getId());
            LOGGER.info("Product created successfully");

            return createSuccessResponse(201, response);

        } catch (JsonProcessingException e) {
            LOGGER.warn("Invalid JSON format in create request", e);
            return createErrorResponse(400, "Bad Request", "Invalid JSON format");
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Validation error in create request", e);
            return createErrorResponse(400, "Bad Request", e.getMessage());
        } catch (Exception e) {
            MDC.put("error", e.getMessage());
            LOGGER.error("Error creating product", e);
            return createErrorResponse(500, "Internal Server Error", "Failed to create product");
        }
    }

    @Tracing
    private APIGatewayProxyResponseEvent handleUpdateProduct(APIGatewayProxyRequestEvent request, Context context) {
        try {
            String productId = extractProductIdFromPath(request.getPath());
            if (productId == null) {
                return createErrorResponse(400, "Bad Request", "Product ID is required");
            }

            UpdateProductRequest updateRequest = OBJECT_MAPPER.readValue(request.getBody(), UpdateProductRequest.class);
            Optional<ProductResponse> response = productService.updateProduct(productId, updateRequest);

            if (response.isEmpty()) {
                return createErrorResponse(404, "Not Found", "Product not found");
            }

            return createSuccessResponse(200, response.get());

        } catch (JsonProcessingException e) {
            return createErrorResponse(400, "Bad Request", "Invalid JSON format");
        } catch (IllegalArgumentException e) {
            return createErrorResponse(400, "Bad Request", e.getMessage());
        } catch (Exception e) {
            context.getLogger().log("Error updating product: " + e.getMessage());
            return createErrorResponse(500, "Internal Server Error", "Failed to update product");
        }
    }

    @Tracing
    private APIGatewayProxyResponseEvent handleDeleteProduct(APIGatewayProxyRequestEvent request, Context context) {
        try {
            String productId = extractProductIdFromPath(request.getPath());
            if (productId == null) {
                return createErrorResponse(400, "Bad Request", "Product ID is required");
            }

            boolean deleted = productService.deleteProduct(productId);
            if (!deleted) {
                return createErrorResponse(404, "Not Found", "Product not found");
            }

            return createSuccessResponse(204, null);

        } catch (IllegalArgumentException e) {
            return createErrorResponse(400, "Bad Request", e.getMessage());
        } catch (Exception e) {
            context.getLogger().log("Error deleting product: " + e.getMessage());
            return createErrorResponse(500, "Internal Server Error", "Failed to delete product");
        }
    }

    private String extractProductIdFromPath(String path) {
        // Expected path format: /products/{id}
        if (path == null || !path.startsWith("/products/")) {
            return null;
        }

        String[] pathParts = path.split("/");
        if (pathParts.length >= 3) {
            return pathParts[2];
        }

        return null;
    }

    private APIGatewayProxyResponseEvent createSuccessResponse(int statusCode, Object body) {
        Map<String, String> headers = createCorsHeaders();

        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(headers);

        if (body != null) {
            try {
                response.withBody(OBJECT_MAPPER.writeValueAsString(body));
            } catch (JsonProcessingException e) {
                // Fallback to error response if serialization fails
                return createErrorResponse(500, "Internal Server Error", "Failed to serialize response");
            }
        }

        return response;
    }

    private APIGatewayProxyResponseEvent createErrorResponse(int statusCode, String error, String message) {
        Map<String, String> headers = createCorsHeaders();
        ErrorResponse errorResponse = new ErrorResponse(error, message);

        try {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(statusCode)
                    .withHeaders(headers)
                    .withBody(OBJECT_MAPPER.writeValueAsString(errorResponse));
        } catch (JsonProcessingException e) {
            // Last resort fallback
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withHeaders(headers)
                    .withBody("{\"error\":\"Internal Server Error\",\"message\":\"Failed to serialize error response\"}");
        }
    }

    private Map<String, String> createCorsHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.put("Access-Control-Allow-Headers", "Content-Type, Authorization");
        return headers;
    }
}