package io.github.adriansanluis2000.similarproducts.domain.model;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(ProductId productId) {
        super("Product " + productId.value() + " not found");
    }
}
