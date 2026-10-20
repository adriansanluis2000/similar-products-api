package io.github.adriansanluis2000.similarproducts.infrastructure.adapter.out.cache;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Ticker;
import io.github.adriansanluis2000.similarproducts.application.port.out.ProductCatalog;
import io.github.adriansanluis2000.similarproducts.application.port.out.ProductCatalogUnavailableException;
import io.github.adriansanluis2000.similarproducts.domain.ProductLookup;
import io.github.adriansanluis2000.similarproducts.domain.UnavailableReason;
import io.github.adriansanluis2000.similarproducts.domain.model.Product;
import io.github.adriansanluis2000.similarproducts.domain.model.ProductId;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class CachingProductCatalogTest {

    private static final CacheSettings SETTINGS = new CacheSettings(
            100,
            new CacheSettings.Products(ofSeconds(60), ofMinutes(5), ofSeconds(5), ofSeconds(30)),
            new CacheSettings.SimilarIds(ofMinutes(10), ofMinutes(5), ofMinutes(5)));

    private static final ProductLookup SHIRT = found("1", "9.99");
    private static final ProductLookup CHEAPER_SHIRT = found("1", "7.99");
    private static final ProductLookup UNAVAILABLE = new ProductLookup.Unavailable(UnavailableReason.TIMEOUT);

    private final FakeTicker ticker = new FakeTicker();
    private final ScriptedCatalog upstream = new ScriptedCatalog();
    private final CachingProductCatalog catalog = new CachingProductCatalog(upstream, SETTINGS, ticker, Runnable::run);

    @Test
    void sharesOneUpstreamCallBetweenConcurrentLookups() throws InterruptedException {
        var answer = new CompletableFuture<ProductLookup>();
        upstream.pending = answer;
        var start = new CountDownLatch(1);
        List<Future<CompletableFuture<ProductLookup>>> lookups;
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            lookups = IntStream.range(0, 50)
                    .mapToObj(_ -> threads.submit(() -> {
                        start.await();
                        return catalog.findProduct(id("1"));
                    }))
                    .toList();
            start.countDown();
        }

        answer.complete(SHIRT);

        assertThat(lookups)
                .allSatisfy(lookup -> assertThat(lookup.resultNow().join()).isEqualTo(SHIRT));
        assertThat(upstream.productCalls.get()).isEqualTo(1);
    }

    @Test
    void cachesALookupThatCompletesAfterItsCallerStoppedWaiting() {
        var answer = new CompletableFuture<ProductLookup>();
        upstream.pending = answer;
        assertThat(catalog.findProduct(id("1"))).isNotDone();

        answer.complete(SHIRT);

        assertThat(catalog.findProduct(id("1"))).isCompletedWithValue(SHIRT);
        assertThat(upstream.productCalls.get()).isEqualTo(1);
    }

    @Test
    void keepsAFoundProductUntilItExpires() {
        upstream.products.put(id("1"), SHIRT);
        catalog.findProduct(id("1")).join();

        ticker.advance(ofSeconds(29));
        assertThat(catalog.findProduct(id("1"))).isCompletedWithValue(SHIRT);
        assertThat(upstream.productCalls.get()).isEqualTo(1);

        // Expired: the next lookup has to wait for the upstream, unlike a refresh.
        upstream.pending = new CompletableFuture<>();
        ticker.advance(ofSeconds(32));
        assertThat(catalog.findProduct(id("1"))).isNotDone();
    }

    @Test
    void servesTheCachedProductWhileRefreshingIt() {
        upstream.products.put(id("1"), SHIRT);
        catalog.findProduct(id("1")).join();
        var refresh = new CompletableFuture<ProductLookup>();
        upstream.pending = refresh;
        ticker.advance(ofSeconds(31));

        assertThat(catalog.findProduct(id("1"))).isCompletedWithValue(SHIRT);
        refresh.complete(CHEAPER_SHIRT);

        assertThat(catalog.findProduct(id("1"))).isCompletedWithValue(CHEAPER_SHIRT);
        assertThat(upstream.productCalls.get()).isEqualTo(2);
    }

    @Test
    void keepsTheCachedProductWhenItBecomesUnavailableOnRefresh() {
        upstream.products.put(id("1"), SHIRT);
        catalog.findProduct(id("1")).join();
        upstream.products.put(id("1"), UNAVAILABLE);
        ticker.advance(ofSeconds(31));

        assertThat(catalog.findProduct(id("1")).join()).isEqualTo(SHIRT);
        assertThat(catalog.findProduct(id("1")).join()).isEqualTo(SHIRT);

        ticker.advance(ofSeconds(30));
        assertThat(catalog.findProduct(id("1")).join()).isEqualTo(UNAVAILABLE);
    }

    @Test
    void keepsUnavailableAnswersForSecondsAndMissingProductsForLonger() {
        upstream.products.put(id("6"), UNAVAILABLE);
        catalog.findProduct(id("5")).join();
        catalog.findProduct(id("6")).join();

        ticker.advance(ofSeconds(4));
        catalog.findProduct(id("5")).join();
        catalog.findProduct(id("6")).join();
        assertThat(upstream.productCalls.get()).isEqualTo(2);

        ticker.advance(ofSeconds(2));
        catalog.findProduct(id("5")).join();
        catalog.findProduct(id("6")).join();
        assertThat(upstream.productCalls.get()).isEqualTo(3);
    }

    @Test
    void cachesSimilarIds() {
        upstream.similarIds.put(id("1"), Optional.of(List.of(id("2"), id("3"))));

        catalog.findSimilarIds(id("1")).join();

        assertThat(catalog.findSimilarIds(id("1")).join()).contains(List.of(id("2"), id("3")));
        assertThat(upstream.similarIdsCalls.get()).isEqualTo(1);
    }

    @Test
    void doesNotCacheFailuresToGetSimilarIds() {
        upstream.similarIdsFailure = new ProductCatalogUnavailableException(UnavailableReason.UPSTREAM_ERROR, null);
        assertThat(catalog.findSimilarIds(id("1"))).isCompletedExceptionally();

        upstream.similarIdsFailure = null;
        upstream.similarIds.put(id("1"), Optional.of(List.of(id("2"))));

        assertThat(catalog.findSimilarIds(id("1")).join()).contains(List.of(id("2")));
        assertThat(upstream.similarIdsCalls.get()).isEqualTo(2);
    }

    private static ProductId id(String value) {
        return new ProductId(value);
    }

    private static ProductLookup found(String id, String price) {
        return new ProductLookup.Found(new Product(id(id), "Shirt", new BigDecimal(price), true));
    }

    private static final class FakeTicker implements Ticker {

        private final AtomicLong nanos = new AtomicLong();

        @Override
        public long read() {
            return nanos.get();
        }

        void advance(Duration duration) {
            nanos.addAndGet(duration.toNanos());
        }
    }

    /** Answers what each test sets up, and counts the calls it receives. */
    private static final class ScriptedCatalog implements ProductCatalog {

        private final Map<ProductId, ProductLookup> products = new ConcurrentHashMap<>();
        private final Map<ProductId, Optional<List<ProductId>>> similarIds = new ConcurrentHashMap<>();
        private final AtomicInteger productCalls = new AtomicInteger();
        private final AtomicInteger similarIdsCalls = new AtomicInteger();
        private CompletableFuture<ProductLookup> pending;
        private RuntimeException similarIdsFailure;

        @Override
        public CompletableFuture<Optional<List<ProductId>>> findSimilarIds(ProductId productId) {
            similarIdsCalls.incrementAndGet();
            return similarIdsFailure != null
                    ? CompletableFuture.failedFuture(similarIdsFailure)
                    : CompletableFuture.completedFuture(similarIds.getOrDefault(productId, Optional.empty()));
        }

        @Override
        public CompletableFuture<ProductLookup> findProduct(ProductId productId) {
            productCalls.incrementAndGet();
            return pending != null
                    ? pending
                    : CompletableFuture.completedFuture(products.getOrDefault(productId, new ProductLookup.NotFound()));
        }
    }
}
