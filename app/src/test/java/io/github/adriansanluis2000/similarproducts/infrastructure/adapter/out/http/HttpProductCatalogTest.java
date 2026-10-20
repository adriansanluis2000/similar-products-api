package io.github.adriansanluis2000.similarproducts.infrastructure.adapter.out.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.adriansanluis2000.similarproducts.application.port.out.ProductCatalogUnavailableException;
import io.github.adriansanluis2000.similarproducts.domain.ProductLookup;
import io.github.adriansanluis2000.similarproducts.domain.UnavailableReason;
import io.github.adriansanluis2000.similarproducts.domain.model.Product;
import io.github.adriansanluis2000.similarproducts.domain.model.ProductId;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Stubs reproduce how the real mocks answer, quirks included (see shared/simulado/mocks.json). */
@SpringBootTest
class HttpProductCatalogTest {

    private static final Duration WAIT_IN_OPEN_STATE = Duration.ofMillis(300);

    @RegisterExtension
    static final WireMockExtension upstream = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void upstreamProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.http.serviceclient.products.base-url", upstream::baseUrl);
        registry.add("spring.http.serviceclient.products.read-timeout", () -> "500ms");
    }

    @Autowired
    private ExistingProductsClient client;

    @Autowired
    private AsyncTaskExecutor executor;

    private HttpProductCatalog catalog;

    @BeforeEach
    void createCatalog() {
        catalog = catalogWith(100, 100);
    }

    private HttpProductCatalog catalogWith(int maxConcurrentCalls, int minimumNumberOfCalls) {
        var circuitBreaker = new ResilienceSettings.CircuitBreaker(
                50, minimumNumberOfCalls, Duration.ofSeconds(10), WAIT_IN_OPEN_STATE);
        return new HttpProductCatalog(
                client, executor, new ResilienceSettings(maxConcurrentCalls, Duration.ofMillis(10), circuitBreaker));
    }

    @Test
    void readsSimilarIdsSentAsNumbers() {
        upstream.stubFor(get("/product/1/similarids").willReturn(okJson("[2,3,4]")));

        assertThat(catalog.findSimilarIds(id("1")).join()).contains(List.of(id("2"), id("3"), id("4")));
    }

    @Test
    void reportsAnUnknownProductAsEmptySimilarIds() {
        upstream.stubFor(get("/product/99/similarids").willReturn(notFound().withBody("Not Found")));

        assertThat(catalog.findSimilarIds(id("99")).join()).isEmpty();
    }

    @Test
    void failsWhenSimilarIdsAnswerWithAServerError() {
        upstream.stubFor(get("/product/1/similarids").willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> catalog.findSimilarIds(id("1")).join())
                .cause()
                .isInstanceOfSatisfying(
                        ProductCatalogUnavailableException.class,
                        e -> assertThat(e.reason()).isEqualTo(UnavailableReason.UPSTREAM_ERROR));
    }

    @Test
    void failsWhenSimilarIdsAreMalformed() {
        upstream.stubFor(get("/product/1/similarids").willReturn(okJson("[2,3")));

        assertThatThrownBy(() -> catalog.findSimilarIds(id("1")).join())
                .cause()
                .isInstanceOfSatisfying(
                        ProductCatalogUnavailableException.class,
                        e -> assertThat(e.reason()).isEqualTo(UnavailableReason.INVALID_RESPONSE));
    }

    @Test
    void readsAProductDetail() {
        upstream.stubFor(get("/product/2")
                .willReturn(okJson("{\"id\":\"2\",\"name\":\"Dress\",\"price\":19.99,\"availability\":true}")));

        assertThat(catalog.findProduct(id("2")).join())
                .isEqualTo(new ProductLookup.Found(new Product(id("2"), "Dress", new BigDecimal("19.99"), true)));
    }

    @Test
    void reportsAProductThatDoesNotExistAsNotFound() {
        upstream.stubFor(get("/product/5").willReturn(notFound().withBody("{\"message\":\"Product not found\"}")));

        assertThat(catalog.findProduct(id("5")).join()).isEqualTo(new ProductLookup.NotFound());
    }

    @Test
    void reportsAServerErrorWithoutBodyAsUnavailable() {
        upstream.stubFor(get("/product/6")
                .willReturn(aResponse().withStatus(500).withHeader("Content-Type", "application/json")));

        assertThat(catalog.findProduct(id("6")).join())
                .isEqualTo(new ProductLookup.Unavailable(UnavailableReason.UPSTREAM_ERROR));
    }

    @Test
    void reportsAMalformedDetailAsUnavailable() {
        upstream.stubFor(get("/product/2").willReturn(okJson("{\"id\":\"2\",\"name\":")));

        assertThat(catalog.findProduct(id("2")).join())
                .isEqualTo(new ProductLookup.Unavailable(UnavailableReason.INVALID_RESPONSE));
    }

    @Test
    void reportsADetailWithMissingFieldsAsUnavailable() {
        upstream.stubFor(get("/product/2").willReturn(okJson("{\"id\":\"2\",\"name\":\"Dress\"}")));

        assertThat(catalog.findProduct(id("2")).join())
                .isEqualTo(new ProductLookup.Unavailable(UnavailableReason.INVALID_RESPONSE));
    }

    @Test
    void reportsADetailSlowerThanTheReadTimeoutAsUnavailable() {
        upstream.stubFor(get("/product/1000")
                .willReturn(okJson("{\"id\":\"1000\",\"name\":\"Coat\",\"price\":89.99,\"availability\":true}")
                        .withFixedDelay(1_500)));

        assertThat(catalog.findProduct(id("1000")).join())
                .isEqualTo(new ProductLookup.Unavailable(UnavailableReason.TIMEOUT));
    }

    @Test
    void opensTheCircuitWhenTheExistingApisKeepFailing() {
        catalog = catalogWith(100, 4);
        upstream.stubFor(
                get(urlPathMatching("/product/.*")).willReturn(aResponse().withStatus(500)));
        for (var product : List.of("1", "2", "3", "4")) {
            catalog.findProduct(id(product)).join();
        }

        assertThat(catalog.findProduct(id("5")).join())
                .isEqualTo(new ProductLookup.Unavailable(UnavailableReason.CIRCUIT_OPEN));
        assertThat(upstream.findAll(getRequestedFor(urlPathMatching("/product/.*"))))
                .hasSize(4);
    }

    @Test
    void failsToGetSimilarIdsWhileTheCircuitIsOpenAndSaysWhenToRetry() {
        catalog = catalogWith(100, 4);
        upstream.stubFor(
                get(urlPathMatching("/product/.*")).willReturn(aResponse().withStatus(500)));
        for (var product : List.of("1", "2", "3", "4")) {
            catalog.findProduct(id(product)).join();
        }

        assertThatThrownBy(() -> catalog.findSimilarIds(id("1")).join())
                .cause()
                .isInstanceOfSatisfying(ProductCatalogUnavailableException.class, e -> {
                    assertThat(e.reason()).isEqualTo(UnavailableReason.CIRCUIT_OPEN);
                    assertThat(e.retryAfter()).contains(WAIT_IN_OPEN_STATE);
                });
    }

    @Test
    void letsCallsThroughAgainWhenTheCircuitHasBeenOpenForAWhile() {
        catalog = catalogWith(100, 4);
        upstream.stubFor(
                get(urlPathMatching("/product/.*")).willReturn(aResponse().withStatus(500)));
        for (var product : List.of("1", "2", "3", "4")) {
            catalog.findProduct(id(product)).join();
        }
        upstream.stubFor(get("/product/2")
                .willReturn(okJson("{\"id\":\"2\",\"name\":\"Dress\",\"price\":19.99,\"availability\":true}")));

        await().pollDelay(WAIT_IN_OPEN_STATE)
                .untilAsserted(
                        () -> assertThat(catalog.findProduct(id("2")).join()).isInstanceOf(ProductLookup.Found.class));
    }

    @Test
    void doesNotCountProductsThatDoNotExistAsFailures() {
        catalog = catalogWith(100, 4);
        upstream.stubFor(get(urlPathMatching("/product/.*")).willReturn(notFound()));
        for (var product : List.of("1", "2", "3", "4", "5", "6")) {
            catalog.findProduct(id(product)).join();
        }

        assertThat(catalog.findProduct(id("7")).join()).isEqualTo(new ProductLookup.NotFound());
    }

    @Test
    void rejectsCallsBeyondTheConcurrencyLimit() {
        catalog = catalogWith(1, 100);
        upstream.stubFor(get("/product/1000")
                .willReturn(okJson("{\"id\":\"1000\",\"name\":\"Coat\",\"price\":89.99,\"availability\":true}")
                        .withFixedDelay(300)));
        var slow = catalog.findProduct(id("1000"));
        await().until(() ->
                !upstream.findAll(getRequestedFor(urlEqualTo("/product/1000"))).isEmpty());

        assertThat(catalog.findProduct(id("2")).join())
                .isEqualTo(new ProductLookup.Unavailable(UnavailableReason.CONCURRENCY_LIMIT));
        assertThat(slow.join()).isInstanceOf(ProductLookup.Found.class);
    }

    @Test
    void retriesOnceWhenTheConnectionFails() {
        upstream.stubFor(get("/product/2")
                .inScenario("flaky")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
                .willSetStateTo("recovered"));
        upstream.stubFor(get("/product/2")
                .inScenario("flaky")
                .whenScenarioStateIs("recovered")
                .willReturn(okJson("{\"id\":\"2\",\"name\":\"Dress\",\"price\":19.99,\"availability\":true}")));

        assertThat(catalog.findProduct(id("2")).join()).isInstanceOf(ProductLookup.Found.class);
        assertThat(upstream.findAll(getRequestedFor(urlEqualTo("/product/2")))).hasSize(2);
    }

    @Test
    void retriesAFailedConnectionOnlyOnce() {
        upstream.stubFor(get("/product/2").willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThat(catalog.findProduct(id("2")).join())
                .isEqualTo(new ProductLookup.Unavailable(UnavailableReason.CONNECTION_ERROR));
        assertThat(upstream.findAll(getRequestedFor(urlEqualTo("/product/2")))).hasSize(2);
    }

    @Test
    void neverRetriesATimeout() {
        upstream.stubFor(get("/product/1000")
                .willReturn(okJson("{\"id\":\"1000\",\"name\":\"Coat\",\"price\":89.99,\"availability\":true}")
                        .withFixedDelay(1_500)));

        assertThat(catalog.findProduct(id("1000")).join())
                .isEqualTo(new ProductLookup.Unavailable(UnavailableReason.TIMEOUT));
        assertThat(upstream.findAll(getRequestedFor(urlEqualTo("/product/1000"))))
                .hasSize(1);
    }

    private static ProductId id(String value) {
        return new ProductId(value);
    }
}
