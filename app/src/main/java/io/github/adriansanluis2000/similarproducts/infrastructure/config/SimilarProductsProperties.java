package io.github.adriansanluis2000.similarproducts.infrastructure.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of the service, validated at startup. Defaults live in {@code application.yaml}.
 *
 * @param budget maximum time to answer a request; products that take longer are left out
 */
@Validated
@ConfigurationProperties("similar-products")
record SimilarProductsProperties(
        @NotNull @DurationMin(millis = 1) Duration budget) {}
