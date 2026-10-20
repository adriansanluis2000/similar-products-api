package io.github.adriansanluis2000.similarproducts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureMetrics
class SimilarProductsApiApplicationTests {

    @Autowired
    private MockMvcTester mvc;

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"})
    void reportsHealthUp(String endpoint) {
        assertThat(mvc.get().uri(endpoint))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("UP");
    }

    @Test
    void keepsTheMcpServerOffByDefault() {
        assertThat(mvc.get().uri("/mcp")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void publishesCacheAndResilienceMetricsForPrometheus() {
        assertThat(mvc.get().uri("/actuator/prometheus"))
                .hasStatusOk()
                .bodyText()
                .contains(
                        "cache_gets_total{cache=\"products\"",
                        "cache_gets_total{cache=\"similar-ids\"",
                        "resilience4j_circuitbreaker_state{",
                        "resilience4j_bulkhead_available_concurrent_calls{",
                        "resilience4j_retry_calls_total{");
    }
}
