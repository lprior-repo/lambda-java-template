package software.amazonaws.example.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.cloud.function.context.config.ContextFunctionCatalogAutoConfiguration;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

import java.util.function.Function;

/**
 * Spring Boot Application for Product Service Lambda.
 * 
 * This application uses Spring Cloud Function to handle AWS Lambda invocations
 * with Spring Boot dependency injection and configuration management.
 */
@SpringBootApplication
@Import({ContextFunctionCatalogAutoConfiguration.class, PowerToolsConfiguration.class})
@RegisterReflectionForBinding({
    Product.class,
    ProductResponse.class,
    CreateProductRequest.class,
    UpdateProductRequest.class,
    ErrorResponse.class
})
public class ProductApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(ProductApplication.class, args);
    }
    
    /**
     * Define the Lambda function as a Spring Bean.
     * Spring Cloud Function will automatically wire this to the AWS Lambda runtime.
     */
    @Bean
    public Function<com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent, 
                    com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse> productHandler(
            SpringBootProductHandler springBootProductHandler) {
        return springBootProductHandler;
    }
}