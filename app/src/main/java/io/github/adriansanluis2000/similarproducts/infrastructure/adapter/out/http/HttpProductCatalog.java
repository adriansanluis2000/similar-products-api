package io.github.adriansanluis2000.similarproducts.infrastructure.adapter.out.http;

import io.github.adriansanluis2000.similarproducts.application.port.out.ProductCatalog;
import io.github.adriansanluis2000.similarproducts.application.port.out.ProductCatalogUnavailableException;
import io.github.adriansanluis2000.similarproducts.domain.ProductLookup;
import io.github.adriansanluis2000.similarproducts.domain.UnavailableReason;
import io.github.adriansanluis2000.similarproducts.domain.model.ProductId;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Reads products from the existing APIs and translates HTTP outcomes into the catalog vocabulary. Each call blocks a
 * virtual thread of the application task executor, so many calls can be waiting at once. Calls go through a circuit
 * breaker and a concurrency limit (see ADR 0008), and are retried once if they fail to connect (see ADR 0009).
 */
public class HttpProductCatalog implements ProductCatalog {

    private static final Logger log = LoggerFactory.getLogger(HttpProductCatalog.class);

    private final ExistingProductsClient client;
    private final AsyncTaskExecutor executor;
    private final CircuitBreaker circuitBreaker;
    private final Bulkhead bulkhead;
    private final Retry retry;

    public HttpProductCatalog(ExistingProductsClient client, AsyncTaskExecutor executor, ResilienceSettings settings) {
        this.client = client;
        this.executor = executor;
        this.circuitBreaker = circuitBreaker(settings.circuitBreaker());
        this.bulkhead = Bulkhead.of(
                "products",
                BulkheadConfig.custom()
                        .maxConcurrentCalls(settings.maxConcurrentCalls())
                        .maxWaitDuration(Duration.ZERO)
                        .build());
        this.retry = Retry.of(
                "products",
                RetryConfig.custom()
                        .maxAttempts(2)
                        .intervalFunction(IntervalFunction.ofRandomized(settings.retryBackoff(), 0.5))
                        .retryOnException(HttpProductCatalog::isConnectionError)
                        .build());
    }

    @Override
    public CompletableFuture<Optional<List<ProductId>>> findSimilarIds(ProductId productId) {
        return CompletableFuture.supplyAsync(() -> similarIds(productId), executor);
    }

    @Override
    public CompletableFuture<ProductLookup> findProduct(ProductId productId) {
        return CompletableFuture.supplyAsync(() -> product(productId), executor);
    }

    private Optional<List<ProductId>> similarIds(ProductId productId) {
        try {
            return Optional.of(protect(() -> client.getSimilarIds(productId.value())).stream()
                    .map(ProductId::new)
                    .toList());
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            throw new ProductCatalogUnavailableException(reasonOf(e), e);
        }
    }

    private ProductLookup product(ProductId productId) {
        try {
            return new ProductLookup.Found(
                    protect(() -> client.getProduct(productId.value())).toProduct());
        } catch (HttpClientErrorException.NotFound e) {
            return new ProductLookup.NotFound();
        } catch (RuntimeException e) {
            var reason = reasonOf(e);
            log.debug("Product {} unavailable: {}", productId.value(), reason, e);
            return new ProductLookup.Unavailable(reason);
        }
    }

    /** Each attempt goes through the circuit breaker and the concurrency limit, so a retry is also guarded. */
    private <T> T protect(Supplier<T> call) {
        return Retry.decorateSupplier(
                        retry,
                        CircuitBreaker.decorateSupplier(circuitBreaker, Bulkhead.decorateSupplier(bulkhead, call)))
                .get();
    }

    /**
     * Only a failure to connect is retried: the request most likely never reached the upstream. A timeout is never
     * retried, as it would double the wait on an upstream that is already slow.
     */
    private static boolean isConnectionError(Throwable e) {
        return e instanceof ResourceAccessException access && !isTimeout(access.getMostSpecificCause());
    }

    /**
     * Only server errors and I/O failures mean the existing APIs are unhealthy. A 404 is a valid answer, and rejections
     * of the concurrency limit say nothing about the upstream.
     */
    private static CircuitBreaker circuitBreaker(ResilienceSettings.CircuitBreaker settings) {
        return CircuitBreaker.of(
                "products",
                CircuitBreakerConfig.custom()
                        .slidingWindow(
                                (int) settings.slidingWindow().toSeconds(),
                                settings.minimumNumberOfCalls(),
                                SlidingWindowType.TIME_BASED)
                        .failureRateThreshold(settings.failureRateThreshold())
                        .waitDurationInOpenState(settings.waitInOpenState())
                        .recordException(
                                e -> e instanceof HttpServerErrorException || e instanceof ResourceAccessException)
                        .ignoreExceptions(BulkheadFullException.class)
                        .build());
    }

    /** Anything that is not an HTTP error, an I/O failure or a rejection means the body could not be read. */
    private static UnavailableReason reasonOf(RuntimeException e) {
        return switch (e) {
            case RestClientResponseException _ -> UnavailableReason.UPSTREAM_ERROR;
            case ResourceAccessException access ->
                isConnectionError(access) ? UnavailableReason.CONNECTION_ERROR : UnavailableReason.TIMEOUT;
            case CallNotPermittedException _ -> UnavailableReason.CIRCUIT_OPEN;
            case BulkheadFullException _ -> UnavailableReason.CONCURRENCY_LIMIT;
            default -> UnavailableReason.INVALID_RESPONSE;
        };
    }

    private static boolean isTimeout(Throwable cause) {
        return cause instanceof SocketTimeoutException
                || (cause instanceof HttpTimeoutException && !(cause instanceof HttpConnectTimeoutException));
    }
}
