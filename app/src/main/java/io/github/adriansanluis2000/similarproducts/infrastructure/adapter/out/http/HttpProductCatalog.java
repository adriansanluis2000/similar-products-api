package io.github.adriansanluis2000.similarproducts.infrastructure.adapter.out.http;

import io.github.adriansanluis2000.similarproducts.application.port.out.ProductCatalog;
import io.github.adriansanluis2000.similarproducts.application.port.out.ProductCatalogUnavailableException;
import io.github.adriansanluis2000.similarproducts.domain.ProductLookup;
import io.github.adriansanluis2000.similarproducts.domain.UnavailableReason;
import io.github.adriansanluis2000.similarproducts.domain.model.ProductId;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Reads products from the existing APIs and translates HTTP outcomes into the catalog vocabulary. Each call blocks a
 * virtual thread of the application task executor, so many calls can be waiting at once.
 */
@Component
class HttpProductCatalog implements ProductCatalog {

    private static final Logger log = LoggerFactory.getLogger(HttpProductCatalog.class);

    private final ExistingProductsClient client;
    private final AsyncTaskExecutor executor;

    HttpProductCatalog(ExistingProductsClient client, AsyncTaskExecutor executor) {
        this.client = client;
        this.executor = executor;
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
            return Optional.of(client.getSimilarIds(productId.value()).stream()
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
            return new ProductLookup.Found(client.getProduct(productId.value()).toProduct());
        } catch (HttpClientErrorException.NotFound e) {
            return new ProductLookup.NotFound();
        } catch (RuntimeException e) {
            var reason = reasonOf(e);
            log.debug("Product {} unavailable: {}", productId.value(), reason, e);
            return new ProductLookup.Unavailable(reason);
        }
    }

    /** Anything that is not an HTTP error or an I/O failure means the body could not be turned into products. */
    private static UnavailableReason reasonOf(RuntimeException e) {
        return switch (e) {
            case RestClientResponseException _ -> UnavailableReason.UPSTREAM_ERROR;
            case ResourceAccessException access ->
                isTimeout(access.getMostSpecificCause())
                        ? UnavailableReason.TIMEOUT
                        : UnavailableReason.CONNECTION_ERROR;
            default -> UnavailableReason.INVALID_RESPONSE;
        };
    }

    private static boolean isTimeout(Throwable cause) {
        return cause instanceof SocketTimeoutException
                || (cause instanceof HttpTimeoutException && !(cause instanceof HttpConnectTimeoutException));
    }
}
