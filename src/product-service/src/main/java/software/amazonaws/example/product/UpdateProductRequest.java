package software.amazonaws.example.product;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Objects;

public class UpdateProductRequest {
    @JsonProperty("name")
    private String name;

    @JsonProperty("price")
    private BigDecimal price;

    public UpdateProductRequest() {
        // Default constructor for Jackson
    }

    public UpdateProductRequest(String name, BigDecimal price) {
        this.name = name;
        this.price = price;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UpdateProductRequest that = (UpdateProductRequest) o;
        return Objects.equals(name, that.name) &&
               Objects.equals(price, that.price);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, price);
    }

    @Override
    public String toString() {
        return "UpdateProductRequest{" +
                "name='" + name + '\'' +
                ", price=" + price +
                '}';
    }
}