package software.amazonaws.example.product;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for AWS Lambda Powertools v2 with Spring Native compatibility.
 * 
 * This configuration sets up environment variables for PowerTools
 * without requiring AspectJ weaving or complex functional APIs.
 */
@Configuration
public class PowerToolsConfiguration {
    
    /**
     * Initialize PowerTools environment variables
     */
    @Bean
    public String initializePowerTools() {
        // Initialize logging with service name
        System.setProperty("POWERTOOLS_SERVICE_NAME", "product-service");
        System.setProperty("POWERTOOLS_LOG_LEVEL", "INFO");
        System.setProperty("POWERTOOLS_LOGGER_SAMPLE_RATE", "0.1");
        System.setProperty("POWERTOOLS_LOGGER_LOG_EVENT", "true");
        
        // Initialize metrics with namespace
        System.setProperty("POWERTOOLS_METRICS_NAMESPACE", "ProductService");
        System.setProperty("POWERTOOLS_METRICS_CAPTURE_COLD_START", "true");
        
        // Initialize tracing
        System.setProperty("POWERTOOLS_TRACE_DISABLED", "false");
        System.setProperty("POWERTOOLS_TRACER_CAPTURE_RESPONSE", "true");
        System.setProperty("POWERTOOLS_TRACER_CAPTURE_ERROR", "true");
        
        return "powertools-initialized";
    }
}