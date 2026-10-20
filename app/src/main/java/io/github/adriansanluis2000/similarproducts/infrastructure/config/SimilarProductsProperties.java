package io.github.adriansanluis2000.similarproducts.infrastructure.config;

import io.github.adriansanluis2000.similarproducts.infrastructure.adapter.out.cache.CacheSettings;
import io.github.adriansanluis2000.similarproducts.infrastructure.adapter.out.http.ResilienceSettings;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of the service, validated at startup. Defaults live in {@code application.yaml}.
 *
 * @param budget maximum time to answer a request; products that take longer are left out
 * @param cache expiry and refresh of the cached answers of the existing APIs
 * @param upstream protection of the existing APIs
 */
@Validated
@ConfigurationProperties("similar-products")
record SimilarProductsProperties(
        @NotNull @DurationMin(millis = 1) Duration budget,
        @NotNull @Valid CacheSettings cache,
        @NotNull @Valid ResilienceSettings upstream) {}
