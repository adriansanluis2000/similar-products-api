package io.github.adriansanluis2000.similarproducts.infrastructure.adapter.out.cache;

import io.github.adriansanluis2000.similarproducts.domain.UnavailableReason;
import io.github.adriansanluis2000.similarproducts.domain.model.ProductId;

/** Fails a refresh on purpose, so the cache keeps serving the detail it already has. No stack trace: it is expected. */
class StaleProductKept extends RuntimeException {

    StaleProductKept(ProductId id, UnavailableReason reason) {
        super(
                "Product " + id.value() + " unavailable on refresh (" + reason + "), keeping the cached detail",
                null,
                false,
                false);
    }
}
