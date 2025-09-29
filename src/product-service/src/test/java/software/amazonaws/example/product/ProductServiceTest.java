package software.amazonaws.example.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ProductServiceTest {

    private TestProductRepository productRepository;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productRepository = new TestProductRepository();
        productService = new ProductService(productRepository);
    }

    @Test
    void createProduct_WithValidRequest_ShouldReturnProductResponse() {
        // Given
        CreateProductRequest request = new CreateProductRequest("Test Product", new BigDecimal("99.99"));

        // When
        ProductResponse response = productService.createProduct(request);

        // Then
        assertNotNull(response);
        assertNotNull(response.getId());
        assertEquals("Test Product", response.getName());
        assertEquals(new BigDecimal("99.99"), response.getPrice());
        assertTrue(productRepository.hasBeenSaved(response.getId()));
    }

    @Test
    void createProduct_WithNullRequest_ShouldThrowException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> productService.createProduct(null));
        assertEquals("Create product request cannot be null", exception.getMessage());
    }

    @Test
    void createProduct_WithNullName_ShouldThrowException() {
        // Given
        CreateProductRequest request = new CreateProductRequest(null, new BigDecimal("99.99"));

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> productService.createProduct(request));
        assertEquals("Product name cannot be null or empty", exception.getMessage());
    }

    @Test
    void createProduct_WithEmptyName_ShouldThrowException() {
        // Given
        CreateProductRequest request = new CreateProductRequest("  ", new BigDecimal("99.99"));

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> productService.createProduct(request));
        assertEquals("Product name cannot be null or empty", exception.getMessage());
    }

    @Test
    void createProduct_WithNullPrice_ShouldThrowException() {
        // Given
        CreateProductRequest request = new CreateProductRequest("Test Product", null);

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> productService.createProduct(request));
        assertEquals("Product price cannot be null", exception.getMessage());
    }

    @Test
    void createProduct_WithZeroPrice_ShouldThrowException() {
        // Given
        CreateProductRequest request = new CreateProductRequest("Test Product", BigDecimal.ZERO);

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> productService.createProduct(request));
        assertEquals("Product price must be positive", exception.getMessage());
    }

    @Test
    void createProduct_WithNegativePrice_ShouldThrowException() {
        // Given
        CreateProductRequest request = new CreateProductRequest("Test Product", new BigDecimal("-10.00"));

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> productService.createProduct(request));
        assertEquals("Product price must be positive", exception.getMessage());
    }

    @Test
    void getProduct_WithExistingId_ShouldReturnProduct() {
        // Given
        String productId = "123";
        Product product = new Product(productId, "Test Product", new BigDecimal("99.99"));
        productRepository.save(product);

        // When
        Optional<ProductResponse> response = productService.getProduct(productId);

        // Then
        assertTrue(response.isPresent());
        assertEquals(productId, response.get().getId());
        assertEquals("Test Product", response.get().getName());
        assertEquals(new BigDecimal("99.99"), response.get().getPrice());
    }

    @Test
    void getProduct_WithNonExistingId_ShouldReturnEmpty() {
        // Given
        String productId = "123";

        // When
        Optional<ProductResponse> response = productService.getProduct(productId);

        // Then
        assertTrue(response.isEmpty());
    }

    @Test
    void getProduct_WithNullId_ShouldThrowException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> productService.getProduct(null));
        assertEquals("Product ID cannot be null or empty", exception.getMessage());
    }

    @Test
    void getProduct_WithEmptyId_ShouldThrowException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> productService.getProduct("  "));
        assertEquals("Product ID cannot be null or empty", exception.getMessage());
    }

    @Test
    void updateProduct_WithExistingProduct_ShouldReturnUpdatedProduct() {
        // Given
        String productId = "123";
        Product existingProduct = new Product(productId, "Old Product", new BigDecimal("50.00"));
        productRepository.save(existingProduct);
        UpdateProductRequest request = new UpdateProductRequest("New Product", new BigDecimal("75.00"));

        // When
        Optional<ProductResponse> response = productService.updateProduct(productId, request);

        // Then
        assertTrue(response.isPresent());
        assertEquals(productId, response.get().getId());
        assertEquals("New Product", response.get().getName());
        assertEquals(new BigDecimal("75.00"), response.get().getPrice());
    }

    @Test
    void updateProduct_WithNonExistingProduct_ShouldReturnEmpty() {
        // Given
        String productId = "123";
        UpdateProductRequest request = new UpdateProductRequest("New Product", new BigDecimal("75.00"));

        // When
        Optional<ProductResponse> response = productService.updateProduct(productId, request);

        // Then
        assertTrue(response.isEmpty());
    }

    @Test
    void deleteProduct_WithExistingProduct_ShouldReturnTrue() {
        // Given
        String productId = "123";
        Product existingProduct = new Product(productId, "Test Product", new BigDecimal("99.99"));
        productRepository.save(existingProduct);

        // When
        boolean result = productService.deleteProduct(productId);

        // Then
        assertTrue(result);
        assertTrue(productRepository.hasBeenDeleted(productId));
    }

    @Test
    void deleteProduct_WithNonExistingProduct_ShouldReturnFalse() {
        // Given
        String productId = "123";

        // When
        boolean result = productService.deleteProduct(productId);

        // Then
        assertFalse(result);
    }

    // Test implementation of ProductRepository for testing purposes
    private static class TestProductRepository extends ProductRepository {
        private final Map<String, Product> products = new HashMap<>();
        private final Map<String, Boolean> deletedProducts = new HashMap<>();

        public TestProductRepository() {
            super(null, null); // We're not using the real DynamoDB client in tests
        }

        @Override
        public void save(Product product) {
            products.put(product.getId(), product);
        }

        @Override
        public Optional<Product> findById(String id) {
            return Optional.ofNullable(products.get(id));
        }

        @Override
        public void deleteById(String id) {
            products.remove(id);
            deletedProducts.put(id, true);
        }

        public boolean hasBeenSaved(String id) {
            return products.containsKey(id);
        }

        public boolean hasBeenDeleted(String id) {
            return deletedProducts.getOrDefault(id, false);
        }
    }
}