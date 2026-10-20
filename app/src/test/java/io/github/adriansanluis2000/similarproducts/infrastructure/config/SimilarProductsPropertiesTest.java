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
                    "similar-products.cache.similar-ids.refresh=5m");

    @Test
    void bindsTheSettings() {
        context.run(it -> {
            var properties = it.getBean(SimilarProductsProperties.class);
            assertThat(properties.budget()).isEqualTo(Duration.ofMillis(1500));
            assertThat(properties.cache().products().unavailable()).isEqualTo(Duration.ofSeconds(5));
            assertThat(properties.cache().similarIds().found()).isEqualTo(Duration.ofMinutes(10));
        });
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "similar-products.budget=0s",
                "similar-products.budget=",
                "similar-products.cache.maximum-size=0",
                "similar-products.cache.products.unavailable=-1s",
                "similar-products.cache.similar-ids.refresh="
            })
    void rejectsMissingOrNonPositiveSettings(String setting) {
        context.withPropertyValues(setting).run(it -> assertThat(it).hasFailed());
    }

    @EnableConfigurationProperties(SimilarProductsProperties.class)
    static class PropertiesOnly {}
}
