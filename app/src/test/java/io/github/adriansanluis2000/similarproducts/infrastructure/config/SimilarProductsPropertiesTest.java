package io.github.adriansanluis2000.similarproducts.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SimilarProductsPropertiesTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesOnly.class)
            .withPropertyValues(
                    "similar-products.budget=1500ms",
                    "similar-products.cache.maximum-size=100",
                    "similar-products.cache.products.found=60s",
                    "similar-products.cache.products.not-found=5m",
                    "similar-products.cache.products.unavailable=5s",
                    "similar-products.cache.products.refresh=30s",
                    "similar-products.cache.similar-ids.found=10m",
                    "similar-products.cache.similar-ids.not-found=5m",
                    "similar-products.cache.similar-ids.refresh=5m",
                    "similar-products.upstream.max-concurrent-calls=100",
                    "similar-products.upstream.retry-backoff=100ms",
                    "similar-products.upstream.circuit-breaker.failure-rate-threshold=50",
                    "similar-products.upstream.circuit-breaker.minimum-number-of-calls=20",
                    "similar-products.upstream.circuit-breaker.sliding-window=10s",
                    "similar-products.upstream.circuit-breaker.wait-in-open-state=10s");

    @Test
    void bindsTheSettings() {
        context.run(it -> {
            var properties = it.getBean(SimilarProductsProperties.class);
            assertThat(properties.budget()).isEqualTo(Duration.ofMillis(1500));
            assertThat(properties.cache().products().unavailable()).isEqualTo(Duration.ofSeconds(5));
            assertThat(properties.cache().similarIds().found()).isEqualTo(Duration.ofMinutes(10));
            assertThat(properties.upstream().circuitBreaker().waitInOpenState()).isEqualTo(Duration.ofSeconds(10));
        });
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "similar-products.budget=0s",
                "similar-products.budget=",
                "similar-products.cache.maximum-size=0",
                "similar-products.cache.products.unavailable=-1s",
                "similar-products.cache.similar-ids.refresh=",
                "similar-products.upstream.max-concurrent-calls=0",
                "similar-products.upstream.retry-backoff=0ms",
                "similar-products.upstream.circuit-breaker.failure-rate-threshold=101",
                "similar-products.upstream.circuit-breaker.sliding-window=500ms"
            })
    void rejectsMissingOrOutOfRangeSettings(String setting) {
        context.withPropertyValues(setting).run(it -> assertThat(it).hasFailed());
    }

    @EnableConfigurationProperties(SimilarProductsProperties.class)
    static class PropertiesOnly {}
}
