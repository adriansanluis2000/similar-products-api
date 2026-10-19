package io.github.adriansanluis2000.similarproducts.infrastructure.adapter.out.http;

import io.github.adriansanluis2000.similarproducts.domain.model.Product;
import io.github.adriansanluis2000.similarproducts.domain.model.ProductId;
import java.math.BigDecimal;
import java.util.Objects;

public record ProductDetailResponse(String id, String name, BigDecimal price, Boolean availability) {

    Product toProduct() {
        return new Product(new ProductId(id), name, price, Objects.requireNonNull(availability, "availability"));
    }
}
