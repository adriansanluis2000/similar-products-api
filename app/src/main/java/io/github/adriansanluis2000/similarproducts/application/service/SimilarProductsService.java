package io.github.adriansanluis2000.similarproducts.application.service;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import io.github.adriansanluis2000.similarproducts.application.port.in.GetSimilarProductsUseCase;
import io.github.adriansanluis2000.similarproducts.application.port.out.ProductCatalog;
import io.github.adriansanluis2000.similarproducts.application.port.out.ProductCatalogUnavailableException;
import io.github.adriansanluis2000.similarproducts.domain.ProductLookup;
import io.github.adriansanluis2000.similarproducts.domain.UnavailableReason;
import io.github.adriansanluis2000.similarproducts.domain.model.Product;
import io.github.adriansanluis2000.similarproducts.domain.model.ProductId;
import io.github.adriansanluis2000.similarproducts.domain.model.ProductNotFoundException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

/**
 * Answers within a time budget per request (see ADR 0006). Lookups that miss it are left out of the answer but not
 * cancelled, so they can still complete in the background.
 */
public class SimilarProductsService implements GetSimilarProductsUseCase {

    private static final ProductLookup MISSED_BUDGET = new ProductLookup.Unavailable(UnavailableReason.TIMEOUT);

    private final ProductCatalog catalog;
    private final Duration budget;

    public SimilarProductsService(ProductCatalog catalog, Duration budget) {
        this.catalog = catalog;
        this.budget = budget;
    }

    @Override
    public List<Product> getSimilarProducts(ProductId productId) {
        var deadline = System.nanoTime() + budget.toNanos();
        var similarIds = await(catalog.findSimilarIds(productId), deadline)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        // All lookups start before waiting for any of them.
        var lookups = similarIds.stream().distinct().map(catalog::findProduct).toList();
        CompletableFuture.allOf(lookups.toArray(CompletableFuture[]::new))
                .completeOnTimeout(null, remaining(deadline), NANOSECONDS)
                .join();
        return lookups.stream()
                .map(lookup -> lookup.getNow(MISSED_BUDGET))
                .flatMap(SimilarProductsService::foundProduct)
                .toList();
    }

    /** Waits on a copy, so the time limit never completes a future that others may share. */
    private static <T> T await(CompletableFuture<T> future, long deadline) {
        try {
            return future.copy().orTimeout(remaining(deadline), NANOSECONDS).join();
        } catch (CompletionException e) {
            throw switch (e.getCause()) {
                case TimeoutException timeout ->
                    new ProductCatalogUnavailableException(UnavailableReason.TIMEOUT, timeout);
                case RuntimeException cause -> cause;
                default -> e;
            };
        }
    }

    private static long remaining(long deadline) {
        return Math.max(0, deadline - System.nanoTime());
    }

    private static Stream<Product> foundProduct(ProductLookup lookup) {
        return switch (lookup) {
            case ProductLookup.Found(var product) -> Stream.of(product);
            case ProductLookup.NotFound _, ProductLookup.Unavailable _ -> Stream.empty();
        };
    }
}
