package io.github.adriansanluis2000.similarproducts.infrastructure.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import io.github.adriansanluis2000.similarproducts.application.port.in.GetSimilarProductsUseCase;
import io.github.adriansanluis2000.similarproducts.application.port.out.ProductCatalogUnavailableException;
import io.github.adriansanluis2000.similarproducts.domain.UnavailableReason;
import io.github.adriansanluis2000.similarproducts.domain.model.Product;
import io.github.adriansanluis2000.similarproducts.domain.model.ProductId;
import io.github.adriansanluis2000.similarproducts.domain.model.ProductNotFoundException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(SimilarProductsController.class)
class SimilarProductsControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private GetSimilarProductsUseCase getSimilarProducts;

    @Test
    void returnsTheSimilarProductsInSimilarityOrder() {
        given(getSimilarProducts.getSimilarProducts(new ProductId("1")))
                .willReturn(List.of(
                        new Product(new ProductId("3"), "Blazer", new BigDecimal("29.99"), false),
                        new Product(new ProductId("2"), "Dress", new BigDecimal("19.99"), true)));

        assertThat(mvc.get().uri("/product/1/similar"))
                .hasStatusOk()
                .hasContentType(MediaType.APPLICATION_JSON)
                .bodyJson()
                .isStrictlyEqualTo("""
                        [
                          {"id": "3", "name": "Blazer", "price": 29.99, "availability": false},
                          {"id": "2", "name": "Dress", "price": 19.99, "availability": true}
                        ]
                        """);
    }

    @Test
    void answersNotFoundWithAProblemDetail() {
        given(getSimilarProducts.getSimilarProducts(new ProductId("99")))
                .willThrow(new ProductNotFoundException(new ProductId("99")));

        assertThat(mvc.get().uri("/product/99/similar"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("Product not found");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a.b", "a b", "ñ"})
    void rejectsAMalformedProductIdWithoutCallingTheUseCase(String productId) {
        assertThat(mvc.get().uri("/product/{productId}/similar", productId))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("Invalid product id");
        then(getSimilarProducts).should(never()).getSimilarProducts(any());
    }

    @ParameterizedTest
    @CsvSource({
        "UPSTREAM_ERROR, 502",
        "CONNECTION_ERROR, 502",
        "INVALID_RESPONSE, 502",
        "TIMEOUT, 504",
        "CIRCUIT_OPEN, 503",
        "CONCURRENCY_LIMIT, 503"
    })
    void answersAFailureToGetTheSimilarIdsWithAGatewayError(UnavailableReason reason, int status) {
        given(getSimilarProducts.getSimilarProducts(new ProductId("1")))
                .willThrow(new ProductCatalogUnavailableException(reason, null));

        assertThat(mvc.get().uri("/product/1/similar"))
                .hasStatus(status)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .doesNotContainHeader(HttpHeaders.RETRY_AFTER)
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo(status);
    }

    @Test
    void tellsWhenToRetryIfTheCatalogIsBeingSpared() {
        given(getSimilarProducts.getSimilarProducts(new ProductId("1")))
                .willThrow(new ProductCatalogUnavailableException(
                        UnavailableReason.CIRCUIT_OPEN, Duration.ofSeconds(10), null));

        assertThat(mvc.get().uri("/product/1/similar"))
                .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .hasHeader(HttpHeaders.RETRY_AFTER, "10");
    }
}
