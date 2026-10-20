package io.github.adriansanluis2000.similarproducts.application.service;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import io.github.adriansanluis2000.similarproducts.application.port.in.GetSimilarProductsUseCase;
import io.github.adriansanluis2000.similarproducts.application.port.out.OmissionListener;
import io.github.adriansanluis2000.similarproducts.application.port.out.ProductCatalog;
import io.github.adriansanluis2000.similarproducts.application.port.out.ProductCatalogUnavailableException;
import io.github.adriansanluis2000.similarproducts.domain.ProductLookup;
import io.github.adriansanluis2000.similarproducts.domain.UnavailableReason;
import io.github.adriansanluis2000.similarproducts.domain.model.Product;
import io.github.adriansanluis2000.similarproducts.domain.model.ProductId;
import io.github.adriansanluis2000.similarproducts.domain.model.ProductNotFoundException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

/**
 * Answers within a time budget per request (see ADR 0006). Lookups that miss it are left out of the answer but not
 * cancelled, so they can still complete in the background. Every product left out is reported to the listener.
 */
public class SimilarProductsService implements GetSimilarProductsUseCase {

    private final ProductCatalog catalog;
    private final Duration budget;
    private final OmissionListener omissions;

    public SimilarProductsService(ProductCatalog catalog, Duration budget, OmissionListener omissions) {
        this.catalog = catalog;
        this.budget = budget;
        this.omissions = omissions;
    }

    @Override
    public List<Product> getSimilarProducts(ProductId productId) {
        var deadline = System.nanoTime() + budget.toNanos();
        var similarIds = await(catalog.findSimilarIds(productId), deadline)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        // All lookups start before waiting for any of them. Repeated ids keep their first position.
        var lookups = new LinkedHashMap<ProductId, CompletableFuture<ProductLookup>>();
        similarIds.forEach(id -> lookups.computeIfAbsent(id, catalog::findProduct));
        CompletableFuture.allOf(lookups.values().toArray(CompletableFuture[]::new))
                .completeOnTimeout(null, remaining(deadline), NANOSECONDS)
                .join();
        var products = new ArrayList<Product>();
        lookups.forEach((id, lookup) -> {
            if (!lookup.isDone()) {
                omissions.budgetExceeded(id);
                return;
            }
            switch (lookup.join()) {
                case ProductLookup.Found(var product) -> products.add(product);
                case ProductLookup.NotFound _ -> omissions.productNotFound(id);
                case ProductLookup.Unavailable(var reason) -> omissions.productUnavailable(id, reason);
            }
        });
        return List.copyOf(products);
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
}
