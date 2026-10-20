package io.github.adriansanluis2000.similarproducts.application.port.out;

import io.github.adriansanluis2000.similarproducts.domain.UnavailableReason;
import java.time.Duration;
import java.util.Optional;

public class ProductCatalogUnavailableException extends RuntimeException {

    private final UnavailableReason reason;
    private final Duration retryAfter;

    public ProductCatalogUnavailableException(UnavailableReason reason, Throwable cause) {
        this(reason, null, cause);
    }

    /** @param retryAfter how long the catalog is expected to stay unavailable, if known */
    public ProductCatalogUnavailableException(UnavailableReason reason, Duration retryAfter, Throwable cause) {
        super("Product catalog unavailable: " + reason, cause);
        this.reason = reason;
        this.retryAfter = retryAfter;
    }

    public UnavailableReason reason() {
        return reason;
    }

    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
