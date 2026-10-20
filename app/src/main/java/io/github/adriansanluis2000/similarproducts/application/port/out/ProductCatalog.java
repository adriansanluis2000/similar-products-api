package io.github.adriansanluis2000.similarproducts.application.port.out;

import io.github.adriansanluis2000.similarproducts.domain.ProductLookup;
import io.github.adriansanluis2000.similarproducts.domain.model.ProductId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Asynchronous, so callers can look up many products at once and stop waiting whenever they need to. */
public interface ProductCatalog {

    /**
     * Returns the ids of the products similar to the given one, ordered by similarity, or empty if the product does not
     * exist. The future fails with {@link ProductCatalogUnavailableException} if the catalog cannot answer.
     */
    CompletableFuture<Optional<List<ProductId>>> findSimilarIds(ProductId productId);

    /** The future always completes normally: failures are reported as {@link ProductLookup.Unavailable}. */
    CompletableFuture<ProductLookup> findProduct(ProductId productId);
}
