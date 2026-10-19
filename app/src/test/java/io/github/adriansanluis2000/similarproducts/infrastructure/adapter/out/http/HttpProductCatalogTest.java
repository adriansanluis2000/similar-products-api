package io.github.adriansanluis2000.similarproducts.infrastructure.adapter.out.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.adriansanluis2000.similarproducts.application.port.out.ProductCatalogUnavailableException;
import io.github.adriansanluis2000.similarproducts.domain.ProductLookup;
import io.github.adriansanluis2000.similarproducts.domain.UnavailableReason;
import io.github.adriansanluis2000.similarproducts.domain.model.Product;
import io.github.adriansanluis2000.similarproducts.domain.model.ProductId;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Stubs reproduce how the real mocks answer, quirks included (see shared/simulado/mocks.json). */
@SpringBootTest
class HttpProductCatalogTest {

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
    private HttpProductCatalog catalog;

    @Test
    void readsSimilarIdsSentAsNumbers() {
        upstream.stubFor(get("/product/1/similarids").willReturn(okJson("[2,3,4]")));

        assertThat(catalog.findSimilarIds(id("1"))).contains(List.of(id("2"), id("3"), id("4")));
    }

    @Test
    void reportsAnUnknownProductAsEmptySimilarIds() {
        upstream.stubFor(get("/product/99/similarids").willReturn(notFound().withBody("Not Found")));

        assertThat(catalog.findSimilarIds(id("99"))).isEmpty();
    }

    @Test
    void failsWhenSimilarIdsAnswerWithAServerError() {
        upstream.stubFor(get("/product/1/similarids").willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> catalog.findSimilarIds(id("1")))
                .isInstanceOfSatisfying(
                        ProductCatalogUnavailableException.class,
                        e -> assertThat(e.reason()).isEqualTo(UnavailableReason.UPSTREAM_ERROR));
    }

    @Test
    void failsWhenSimilarIdsAreMalformed() {
        upstream.stubFor(get("/product/1/similarids").willReturn(okJson("[2,3")));

        assertThatThrownBy(() -> catalog.findSimilarIds(id("1")))
                .isInstanceOfSatisfying(
                        ProductCatalogUnavailableException.class,
                        e -> assertThat(e.reason()).isEqualTo(UnavailableReason.INVALID_RESPONSE));
    }

    @Test
    void readsAProductDetail() {
        upstream.stubFor(get("/product/2")
                .willReturn(okJson("{\"id\":\"2\",\"name\":\"Dress\",\"price\":19.99,\"availability\":true}")));

        assertThat(catalog.findProduct(id("2")))
                .isEqualTo(new ProductLookup.Found(new Product(id("2"), "Dress", new BigDecimal("19.99"), true)));
    }

    @Test
    void reportsAProductThatDoesNotExistAsNotFound() {
        upstream.stubFor(get("/product/5").willReturn(notFound().withBody("{\"message\":\"Product not found\"}")));

        assertThat(catalog.findProduct(id("5"))).isEqualTo(new ProductLookup.NotFound());
    }

    @Test
    void reportsAServerErrorWithoutBodyAsUnavailable() {
        upstream.stubFor(get("/product/6")
                .willReturn(aResponse().withStatus(500).withHeader("Content-Type", "application/json")));

        assertThat(catalog.findProduct(id("6")))
                .isEqualTo(new ProductLookup.Unavailable(UnavailableReason.UPSTREAM_ERROR));
    }

    @Test
    void reportsAMalformedDetailAsUnavailable() {
        upstream.stubFor(get("/product/2").willReturn(okJson("{\"id\":\"2\",\"name\":")));

        assertThat(catalog.findProduct(id("2")))
                .isEqualTo(new ProductLookup.Unavailable(UnavailableReason.INVALID_RESPONSE));
    }

    @Test
    void reportsADetailWithMissingFieldsAsUnavailable() {
        upstream.stubFor(get("/product/2").willReturn(okJson("{\"id\":\"2\",\"name\":\"Dress\"}")));

        assertThat(catalog.findProduct(id("2")))
                .isEqualTo(new ProductLookup.Unavailable(UnavailableReason.INVALID_RESPONSE));
    }

    @Test
    void reportsADetailSlowerThanTheReadTimeoutAsUnavailable() {
        upstream.stubFor(get("/product/1000")
                .willReturn(okJson("{\"id\":\"1000\",\"name\":\"Coat\",\"price\":89.99,\"availability\":true}")
                        .withFixedDelay(1_500)));

        assertThat(catalog.findProduct(id("1000"))).isEqualTo(new ProductLookup.Unavailable(UnavailableReason.TIMEOUT));
    }

    private static ProductId id(String value) {
        return new ProductId(value);
    }
}
