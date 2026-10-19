package io.github.adriansanluis2000.similarproducts.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

public record Product(ProductId id, String name, BigDecimal price, boolean available) {

    public Product {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(price, "price");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }
}
