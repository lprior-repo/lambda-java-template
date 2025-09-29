package software.amazonaws.example.product;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class ProductService {
    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public ProductResponse createProduct(CreateProductRequest request) {
        validateCreateRequest(request);

        String productId = generateProductId();
        Product product = new Product(productId, request.getName(), request.getPrice());

        productRepository.save(product);

        return ProductResponse.from(product);
    }

    public Optional<ProductResponse> getProduct(String id) {
        validateProductId(id);

        return productRepository.findById(id)
                .map(ProductResponse::from);
    }

    public Optional<ProductResponse> updateProduct(String id, UpdateProductRequest request) {
        validateProductId(id);
        validateUpdateRequest(request);

        Optional<Product> existingProduct = productRepository.findById(id);
        if (existingProduct.isEmpty()) {
            return Optional.empty();
        }

        Product updatedProduct = new Product(id, request.getName(), request.getPrice());
        productRepository.save(updatedProduct);

        return Optional.of(ProductResponse.from(updatedProduct));
    }

    public boolean deleteProduct(String id) {
        validateProductId(id);

        Optional<Product> existingProduct = productRepository.findById(id);
        if (existingProduct.isEmpty()) {
            return false;
        }

        productRepository.deleteById(id);
        return true;
    }

    public ProductListResponse getAllProducts() {
        List<Product> products = productRepository.findAll();
        List<ProductResponse> productResponses = products.stream()
                .map(ProductResponse::from)
                .collect(Collectors.toList());
        return new ProductListResponse(productResponses);
    }

    private String generateProductId() {
        return UUID.randomUUID().toString();
    }

    private void validateCreateRequest(CreateProductRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Create product request cannot be null");
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Product name cannot be null or empty");
        }
        if (request.getPrice() == null) {
            throw new IllegalArgumentException("Product price cannot be null");
        }
        if (request.getPrice().signum() <= 0) {
            throw new IllegalArgumentException("Product price must be positive");
        }
    }

    private void validateUpdateRequest(UpdateProductRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Update product request cannot be null");
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Product name cannot be null or empty");
        }
        if (request.getPrice() == null) {
            throw new IllegalArgumentException("Product price cannot be null");
        }
        if (request.getPrice().signum() <= 0) {
            throw new IllegalArgumentException("Product price must be positive");
        }
    }

    private void validateProductId(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Product ID cannot be null or empty");
        }
    }
}