package software.amazonaws.example.product;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// AWS Lambda Powertools v2 - Manual approach for Spring Native compatibility
import software.amazon.lambda.powertools.tracing.TracingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Spring Boot implementation of Product Lambda Handler.
 * 
 * This handler integrates with Spring Boot's dependency injection and configuration
 * while maintaining compatibility with AWS Lambda runtime and AWS Lambda Powertools
 * for observability (tracing, logging, metrics).
 */
@Component
public class SpringBootProductHandler implements Function<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
    
    private final ProductService productService;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public SpringBootProductHandler(ProductService productService) {
        this.productService = productService;
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public APIGatewayV2HTTPResponse apply(APIGatewayV2HTTPEvent request) {
        Logger logger = LoggerFactory.getLogger(SpringBootProductHandler.class);
        
        // Handle null request
        if (request == null) {
            logger.error("Received null request");
            return createErrorResponse(400, "Bad Request");
        }
        
        // Add correlation ID for request tracing
        String correlationId = request.getHeaders() != null 
            ? request.getHeaders().getOrDefault("x-correlation-id", UUID.randomUUID().toString())
            : UUID.randomUUID().toString();
        
        try {
            String httpMethod = request.getRequestContext().getHttp().getMethod();
            String path = request.getRequestContext().getHttp().getPath();
            
            logger.info("Processing request: {} {} with correlationId: {}", httpMethod, path, correlationId);
            
            APIGatewayV2HTTPResponse response;
            switch (httpMethod.toUpperCase()) {
                case "GET":
                    response = handleGetRequest(request, path);
                    break;
                case "POST":
                    response = handlePostRequest(request, path);
                    break;
                case "PUT":
                    response = handlePutRequest(request, path);
                    break;
                case "DELETE":
                    response = handleDeleteRequest(request, path);
                    break;
                default:
                    response = createErrorResponse(405, "Method not allowed");
            }
            
            // Add correlation ID to response headers
            if (response.getHeaders() == null) {
                response.setHeaders(new HashMap<>());
            }
            response.getHeaders().put("x-correlation-id", correlationId);
            
            logger.info("Request processed successfully with status: {}", response.getStatusCode());
            return response;
            
        } catch (JsonProcessingException e) {
            logger.error("JSON processing error with correlationId: {}", correlationId, e);
            return createErrorResponse(400, "Invalid JSON format");
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument with correlationId: {}", correlationId, e);
            return createErrorResponse(400, e.getMessage());
        } catch (Exception e) {
            logger.error("Error processing request with correlationId: {}", correlationId, e);
            return createErrorResponse(500, "Internal Server Error");
        }
    }
    
    private APIGatewayV2HTTPResponse handleGetRequest(APIGatewayV2HTTPEvent request, String path) throws JsonProcessingException {
        if (path.equals("/health")) {
            return createSuccessResponse(Map.of("status", "healthy", "service", "product-service"));
        }
        
        if (path.startsWith("/products/")) {
            String productId = extractProductId(path);
            if (productId != null && !productId.trim().isEmpty()) {
                var product = productService.getProduct(productId);
                if (product.isPresent()) {
                    return createSuccessResponse(product.get());
                } else {
                    return createErrorResponse(404, "Product not found");
                }
            } else {
                return createErrorResponse(400, "Invalid product ID");
            }
        }
        
        if (path.equals("/products")) {
            var products = productService.getAllProducts();
            return createSuccessResponse(products);
        }
        
        return createErrorResponse(404, "Not found");
    }
    
    private APIGatewayV2HTTPResponse handlePostRequest(APIGatewayV2HTTPEvent request, String path) throws JsonProcessingException {
        if (path.equals("/products")) {
            CreateProductRequest createRequest = objectMapper.readValue(request.getBody(), CreateProductRequest.class);
            ProductResponse response = productService.createProduct(createRequest);
            return createSuccessResponse(response, 201);
        }
        
        return createErrorResponse(404, "Not found");
    }
    
    private APIGatewayV2HTTPResponse handlePutRequest(APIGatewayV2HTTPEvent request, String path) throws JsonProcessingException {
        if (path.startsWith("/products/")) {
            String productId = extractProductId(path);
            if (productId != null && !productId.trim().isEmpty()) {
                UpdateProductRequest updateRequest = objectMapper.readValue(request.getBody(), UpdateProductRequest.class);
                var product = productService.updateProduct(productId, updateRequest);
                if (product.isPresent()) {
                    return createSuccessResponse(product.get());
                } else {
                    return createErrorResponse(404, "Product not found");
                }
            } else {
                return createErrorResponse(400, "Invalid product ID");
            }
        }
        
        return createErrorResponse(404, "Not found");
    }
    
    private APIGatewayV2HTTPResponse handleDeleteRequest(APIGatewayV2HTTPEvent request, String path) throws JsonProcessingException {
        if (path.startsWith("/products/")) {
            String productId = extractProductId(path);
            if (productId != null && !productId.trim().isEmpty()) {
                boolean deleted = productService.deleteProduct(productId);
                if (deleted) {
                    return createNoContentResponse();
                } else {
                    return createErrorResponse(404, "Product not found");
                }
            } else {
                return createErrorResponse(400, "Invalid product ID");
            }
        }
        
        return createErrorResponse(404, "Not found");
    }
    
    private String extractProductId(String path) {
        String[] parts = path.split("/");
        if (parts.length >= 3 && "products".equals(parts[1])) {
            return parts[2];
        }
        return null;
    }
    
    private APIGatewayV2HTTPResponse createSuccessResponse(Object body) throws JsonProcessingException {
        return createSuccessResponse(body, 200);
    }
    
    private APIGatewayV2HTTPResponse createSuccessResponse(Object body, int statusCode) throws JsonProcessingException {
        APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
        response.setStatusCode(statusCode);
        response.setBody(objectMapper.writeValueAsString(body));
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.put("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.setHeaders(headers);
        
        return response;
    }
    
    private APIGatewayV2HTTPResponse createNoContentResponse() {
        APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
        response.setStatusCode(204);
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.put("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.setHeaders(headers);
        
        return response;
    }
    
    private APIGatewayV2HTTPResponse createErrorResponse(int statusCode, String message) {
        APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
        response.setStatusCode(statusCode);
        
        try {
            ErrorResponse errorResponse = new ErrorResponse(message, statusCode);
            response.setBody(objectMapper.writeValueAsString(errorResponse));
        } catch (JsonProcessingException e) {
            response.setBody("{\"error\":\"" + message + "\",\"statusCode\":" + statusCode + "}");
        }
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        response.setHeaders(headers);
        
        return response;
    }
}