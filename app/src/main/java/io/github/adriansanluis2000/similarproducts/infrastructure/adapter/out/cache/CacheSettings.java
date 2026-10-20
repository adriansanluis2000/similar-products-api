package io.github.adriansanluis2000.similarproducts.infrastructure.adapter.out.cache;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;

/**
 * How long each kind of answer is kept, and when it is refreshed in the background. See ADR 0007.
 *
 * @param maximumSize entries kept per cache before the least used ones are evicted
 */
public record CacheSettings(
        @Positive long maximumSize,
        @NotNull @Valid Products products,
        @NotNull @Valid SimilarIds similarIds) {

    /** Product details: short-lived when found, since price and availability change. */
    public record Products(
            @NotNull @DurationMin(millis = 1) Duration found,
            @NotNull @DurationMin(millis = 1) Duration notFound,
            @NotNull @DurationMin(millis = 1) Duration unavailable,
            @NotNull @DurationMin(millis = 1) Duration refresh) {}

    /** Similar ids: they change rarely. Failures are not cached. */
    public record SimilarIds(
            @NotNull @DurationMin(millis = 1) Duration found,
            @NotNull @DurationMin(millis = 1) Duration notFound,
            @NotNull @DurationMin(millis = 1) Duration refresh) {}
}
