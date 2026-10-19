package io.github.adriansanluis2000.similarproducts.application.service;

import io.github.adriansanluis2000.similarproducts.application.port.in.GetSimilarProductsUseCase;
import io.github.adriansanluis2000.similarproducts.application.port.out.ProductCatalog;
import io.github.adriansanluis2000.similarproducts.domain.ProductLookup;
import io.github.adriansanluis2000.similarproducts.domain.model.Product;
import io.github.adriansanluis2000.similarproducts.domain.model.ProductId;
import io.github.adriansanluis2000.similarproducts.domain.model.ProductNotFoundException;
import java.util.List;
import java.util.stream.Stream;

public class SimilarProductsService implements GetSimilarProductsUseCase {

    private final ProductCatalog catalog;

    public SimilarProductsService(ProductCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public List<Product> getSimilarProducts(ProductId productId) {
        var similarIds = catalog.findSimilarIds(productId).orElseThrow(() -> new ProductNotFoundException(productId));
        return similarIds.stream()
                .distinct()
                .map(catalog::findProduct)
                .flatMap(SimilarProductsService::foundProduct)
                .toList();
    }

    private static Stream<Product> foundProduct(ProductLookup lookup) {
        return switch (lookup) {
            case ProductLookup.Found(var product) -> Stream.of(product);
            case ProductLookup.NotFound _, ProductLookup.Unavailable _ -> Stream.empty();
        };
    }
}
