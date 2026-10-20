package io.github.adriansanluis2000.similarproducts.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.github.adriansanluis2000.similarproducts.application.port.out.ProductCatalog;
import io.github.adriansanluis2000.similarproducts.application.port.out.ProductCatalogUnavailableException;
import io.github.adriansanluis2000.similarproducts.domain.ProductLookup;
import io.github.adriansanluis2000.similarproducts.domain.UnavailableReason;
import io.github.adriansanluis2000.similarproducts.domain.model.Product;
import io.github.adriansanluis2000.similarproducts.domain.model.ProductId;
import io.github.adriansanluis2000.similarproducts.domain.model.ProductNotFoundException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class SimilarProductsServiceTest {

    private final InMemoryCatalog catalog = new InMemoryCatalog();
    private final SimilarProductsService service = new SimilarProductsService(catalog);

    @Test
    void returnsSimilarProductsInSimilarityOrder() {
        catalog.similar("1", "3", "2", "4").withProducts("2", "3", "4");

        assertThat(idsOf(service.getSimilarProducts(id("1")))).containsExactly("3", "2", "4");
    }

    @Test
    void looksUpAllSimilarProductsBeforeWaitingForAnyOfThem() {
        catalog.similar("1", "2", "3", "4").withProducts("2", "3", "4");
        catalog.holdLookups();

        var result = CompletableFuture.supplyAsync(() -> service.getSimilarProducts(id("1")));

        await().until(() -> catalog.lookedUp.size() == 3);
        catalog.releaseLookups();
        assertThat(idsOf(result.join())).containsExactly("2", "3", "4");
    }

    @Test
    void leavesOutProductsThatDoNotExist() {
        catalog.similar("4", "1", "5", "2").withProducts("1", "2");

        assertThat(idsOf(service.getSimilarProducts(id("4")))).containsExactly("1", "2");
    }

    @Test
    void leavesOutProductsThatAreUnavailable() {
        catalog.similar("5", "1", "6", "2").withProducts("1", "2");
        catalog.lookups.put(id("6"), new ProductLookup.Unavailable(UnavailableReason.UPSTREAM_ERROR));

        assertThat(idsOf(service.getSimilarProducts(id("5")))).containsExactly("1", "2");
    }

    @Test
    void removesDuplicateIdsKeepingTheFirstOccurrence() {
        catalog.similar("1", "2", "3", "2").withProducts("2", "3");

        assertThat(idsOf(service.getSimilarProducts(id("1")))).containsExactly("2", "3");
        assertThat(catalog.lookedUp).containsExactly(id("2"), id("3"));
    }

    @Test
    void returnsAnEmptyListWhenThereAreNoSimilarProducts() {
        catalog.similar("1");

        assertThat(service.getSimilarProducts(id("1"))).isEmpty();
    }

    @Test
    void failsWhenTheProductDoesNotExist() {
        assertThatThrownBy(() -> service.getSimilarProducts(id("99")))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void failsWhenTheSimilarIdsAreUnavailable() {
        catalog.similarIdsFailure = new ProductCatalogUnavailableException(UnavailableReason.UPSTREAM_ERROR, null);

        assertThatThrownBy(() -> service.getSimilarProducts(id("1"))).isSameAs(catalog.similarIdsFailure);
    }

    private static ProductId id(String value) {
        return new ProductId(value);
    }

    private static List<String> idsOf(List<Product> products) {
        return products.stream().map(product -> product.id().value()).toList();
    }

    private static final class InMemoryCatalog implements ProductCatalog {

        private final Map<ProductId, List<ProductId>> similarIds = new HashMap<>();
        private final Map<ProductId, ProductLookup> lookups = new HashMap<>();
        private final List<ProductId> lookedUp = new CopyOnWriteArrayList<>();
        private CompletableFuture<Void> release = CompletableFuture.completedFuture(null);
        private RuntimeException similarIdsFailure;

        InMemoryCatalog similar(String productId, String... similar) {
            similarIds.put(
                    id(productId), Arrays.stream(similar).map(ProductId::new).toList());
            return this;
        }

        void withProducts(String... ids) {
            for (var value : ids) {
                var product = new Product(id(value), "Product " + value, BigDecimal.TEN, true);
                lookups.put(product.id(), new ProductLookup.Found(product));
            }
        }

        /** Lookups stay pending until released. */
        void holdLookups() {
            release = new CompletableFuture<>();
        }

        void releaseLookups() {
            release.complete(null);
        }

        @Override
        public CompletableFuture<Optional<List<ProductId>>> findSimilarIds(ProductId productId) {
            return similarIdsFailure == null
                    ? CompletableFuture.completedFuture(Optional.ofNullable(similarIds.get(productId)))
                    : CompletableFuture.failedFuture(similarIdsFailure);
        }

        @Override
        public CompletableFuture<ProductLookup> findProduct(ProductId productId) {
            lookedUp.add(productId);
            return release.thenApply(_ -> lookups.getOrDefault(productId, new ProductLookup.NotFound()));
        }
    }
}
