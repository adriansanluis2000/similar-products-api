package io.github.adriansanluis2000.similarproducts.infrastructure.adapter.in.rest;

import io.github.adriansanluis2000.similarproducts.application.port.in.GetSimilarProductsUseCase;
import io.github.adriansanluis2000.similarproducts.domain.model.Product;
import io.github.adriansanluis2000.similarproducts.domain.model.ProductId;
import io.github.adriansanluis2000.similarproducts.infrastructure.adapter.in.rest.api.ProductApi;
import io.github.adriansanluis2000.similarproducts.infrastructure.adapter.in.rest.model.ProductDetail;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SimilarProductsController implements ProductApi {

    private final GetSimilarProductsUseCase getSimilarProducts;

    SimilarProductsController(GetSimilarProductsUseCase getSimilarProducts) {
        this.getSimilarProducts = getSimilarProducts;
    }

    @Override
    public ResponseEntity<Set<ProductDetail>> getProductSimilar(String productId) {
        var products = getSimilarProducts.getSimilarProducts(new ProductId(productId));
        return ResponseEntity.ok(products.stream()
                .map(SimilarProductsController::toResponse)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    private static ProductDetail toResponse(Product product) {
        return new ProductDetail(product.id().value(), product.name(), product.price(), product.available());
    }
}
