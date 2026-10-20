package io.github.adriansanluis2000.similarproducts.infrastructure.adapter.out.cache;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Ticker;
import io.github.adriansanluis2000.similarproducts.application.port.out.ProductCatalog;
import io.github.adriansanluis2000.similarproducts.domain.ProductLookup;
import io.github.adriansanluis2000.similarproducts.domain.model.ProductId;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Caches the answers of another catalog. See ADR 0007.
 *
 * <ul>
 *   <li>Concurrent lookups of the same product share a single call to the delegate.
 *   <li>A lookup keeps going after its callers stop waiting, and its answer is cached when it arrives.
 *   <li>Each answer is kept for a time that depends on what it says, and refreshed in the background while it is
 *       still served.
 * </ul>
 *
 * <p>Hit, miss, load and eviction statistics are published as the {@code cache.*} metrics of each cache.
 */
public class CachingProductCatalog implements ProductCatalog, MeterBinder {

    private final AsyncLoadingCache<ProductId, Optional<List<ProductId>>> similarIds;
    private final AsyncLoadingCache<ProductId, ProductLookup> products;

    public CachingProductCatalog(ProductCatalog delegate, CacheSettings settings) {
        this(delegate, settings, Ticker.systemTicker(), ForkJoinPool.commonPool());
    }

    CachingProductCatalog(ProductCatalog delegate, CacheSettings settings, Ticker ticker, Executor executor) {
        var similarIdsSettings = settings.similarIds();
        similarIds = Caffeine.newBuilder()
                .maximumSize(settings.maximumSize())
                .expireAfter(Expiry.writing((ProductId _, Optional<List<ProductId>> ids) ->
                        ids.isPresent() ? similarIdsSettings.found() : similarIdsSettings.notFound()))
                .refreshAfterWrite(similarIdsSettings.refresh())
                .ticker(ticker)
                .executor(executor)
                .recordStats()
                .buildAsync((id, _) -> delegate.findSimilarIds(id));
        var productSettings = settings.products();
        products = Caffeine.newBuilder()
                .maximumSize(settings.maximumSize())
                .expireAfter(Expiry.writing((ProductId _, ProductLookup lookup) -> timeToLive(lookup, productSettings)))
                .refreshAfterWrite(productSettings.refresh())
                .ticker(ticker)
                .executor(executor)
                .recordStats()
                .buildAsync(new ProductLoader(delegate));
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        CaffeineCacheMetrics.monitor(registry, similarIds, "similar-ids");
        CaffeineCacheMetrics.monitor(registry, products, "products");
    }

    @Override
    public CompletableFuture<Optional<List<ProductId>>> findSimilarIds(ProductId productId) {
        return similarIds.get(productId);
    }

    @Override
    public CompletableFuture<ProductLookup> findProduct(ProductId productId) {
        return products.get(productId);
    }

    private static Duration timeToLive(ProductLookup lookup, CacheSettings.Products settings) {
        return switch (lookup) {
            case ProductLookup.Found _ -> settings.found();
            case ProductLookup.NotFound _ -> settings.notFound();
            case ProductLookup.Unavailable _ -> settings.unavailable();
        };
    }

    private record ProductLoader(ProductCatalog delegate) implements AsyncCacheLoader<ProductId, ProductLookup> {

        @Override
        public CompletableFuture<ProductLookup> asyncLoad(ProductId id, Executor executor) {
            return delegate.findProduct(id);
        }

        /**
         * A product that becomes unavailable while refreshing keeps its cached detail until it expires: a failed
         * refresh leaves the cached value in place.
         */
        @Override
        public CompletableFuture<ProductLookup> asyncReload(ProductId id, ProductLookup cached, Executor executor) {
            return delegate.findProduct(id).thenApply(fresh -> {
                if (fresh instanceof ProductLookup.Unavailable(var reason) && cached instanceof ProductLookup.Found) {
                    throw new StaleProductKept(id, reason);
                }
                return fresh;
            });
        }
    }
}
