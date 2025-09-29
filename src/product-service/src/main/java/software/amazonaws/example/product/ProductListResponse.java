package software.amazonaws.example.product;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Response class for product list operations.
 * Contains a list of products returned by the getAllProducts API.
 */
public class ProductListResponse {
    
    @JsonProperty("products")
    private final List<ProductResponse> products;

    public ProductListResponse(List<ProductResponse> products) {
        this.products = Objects.requireNonNull(products, "Products list cannot be null");
    }

    public List<ProductResponse> getProducts() {
        return products;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProductListResponse that = (ProductListResponse) o;
        return Objects.equals(products, that.products);
    }

    @Override
    public int hashCode() {
        return Objects.hash(products);
    }

    @Override
    public String toString() {
        return "ProductListResponse{" +
                "products=" + products +
                '}';
    }
}