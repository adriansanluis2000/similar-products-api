package io.github.adriansanluis2000.similarproducts.application.port.out;

import io.github.adriansanluis2000.similarproducts.domain.ProductLookup;
import io.github.adriansanluis2000.similarproducts.domain.model.ProductId;
import java.util.List;
import java.util.Optional;

public interface ProductCatalog {

    /**
     * Returns the ids of the products similar to the given one, ordered by similarity, or empty if the product does not
     * exist.
     *
     * @throws ProductCatalogUnavailableException if the catalog cannot answer
     */
    Optional<List<ProductId>> findSimilarIds(ProductId productId);

    ProductLookup findProduct(ProductId productId);
}
