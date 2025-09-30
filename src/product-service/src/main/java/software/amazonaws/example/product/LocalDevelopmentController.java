package software.amazonaws.example.product;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import java.util.List;

/**
 * Local development controller for debugging Spring Boot Lambda functions
 * Only active when running with 'local' profile
 * Provides REST endpoints that mirror the Lambda function behavior
 */
@RestController
@RequestMapping("/api")
@ConditionalOnProperty(name = "spring.profiles.active", havingValue = "local")
public class LocalDevelopmentController {

    @Autowired
    private ProductService productService;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Service is running locally for debugging");
    }

    @GetMapping("/products")
    public ResponseEntity<ProductListResponse> getProducts() {
        try {
            List<Product> products = productService.getAllProducts();
            return ResponseEntity.ok(new ProductListResponse(products, products.size()));
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @PostMapping("/products")
    public ResponseEntity<ProductResponse> createProduct(@RequestBody CreateProductRequest request) {
        try {
            Product product = productService.createProduct(request);
            return ResponseEntity.ok(new ProductResponse(product));
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable String id) {
        try {
            Product product = productService.getProduct(id);
            if (product != null) {
                return ResponseEntity.ok(new ProductResponse(product));
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable String id, 
            @RequestBody UpdateProductRequest request) {
        try {
            Product product = productService.updateProduct(id, request);
            if (product != null) {
                return ResponseEntity.ok(new ProductResponse(product));
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable String id) {
        try {
            boolean deleted = productService.deleteProduct(id);
            if (deleted) {
                return ResponseEntity.noContent().build();
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
}