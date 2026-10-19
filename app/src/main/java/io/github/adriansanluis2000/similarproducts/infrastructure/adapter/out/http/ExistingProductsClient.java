package io.github.adriansanluis2000.similarproducts.infrastructure.adapter.out.http;

import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/** Client of the existing product APIs described in {@code existingApis.yaml}. */
@HttpExchange("/product/{productId}")
public interface ExistingProductsClient {

    /** The contract says strings, but the APIs answer numbers. Jackson reads both into strings. */
    @GetExchange("/similarids")
    List<String> getSimilarIds(@PathVariable String productId);

    @GetExchange
    ProductDetailResponse getProduct(@PathVariable String productId);
}
