package io.github.adriansanluis2000.similarproducts.application.port.in;

import io.github.adriansanluis2000.similarproducts.domain.model.Product;
import io.github.adriansanluis2000.similarproducts.domain.model.ProductId;
import io.github.adriansanluis2000.similarproducts.domain.model.ProductNotFoundException;
import java.util.List;

public interface GetSimilarProductsUseCase {

    /**
     * Returns the products similar to the given one, ordered by similarity. Similar products whose detail cannot be
     * retrieved are left out.
     *
     * @throws ProductNotFoundException if the given product does not exist
     */
    List<Product> getSimilarProducts(ProductId productId);
}
