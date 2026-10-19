package io.github.adriansanluis2000.similarproducts.application.port.out;

import io.github.adriansanluis2000.similarproducts.domain.UnavailableReason;

public class ProductCatalogUnavailableException extends RuntimeException {

    private final UnavailableReason reason;

    public ProductCatalogUnavailableException(UnavailableReason reason, Throwable cause) {
        super("Product catalog unavailable: " + reason, cause);
        this.reason = reason;
    }

    public UnavailableReason reason() {
        return reason;
    }
}
