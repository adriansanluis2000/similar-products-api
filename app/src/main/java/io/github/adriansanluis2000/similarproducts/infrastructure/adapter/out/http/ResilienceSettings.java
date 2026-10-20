package io.github.adriansanluis2000.similarproducts.infrastructure.adapter.out.http;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;

/**
 * Protection of the existing APIs. See ADR 0008.
 *
 * @param maxConcurrentCalls calls to the existing APIs allowed at the same time; more are rejected at once
 * @param retryBackoff average wait before retrying a call that failed to connect, with random jitter of half of it
 */
public record ResilienceSettings(
        @Positive int maxConcurrentCalls,
        @NotNull @DurationMin(millis = 1) Duration retryBackoff,
        @NotNull @Valid CircuitBreaker circuitBreaker) {

    /**
     * Opens when the existing APIs fail too often, and lets a few calls through after a while to check if they are
     * back. Only errors and timeouts count as failures, not 404s.
     *
     * @param failureRateThreshold percentage of failed calls in the window that opens the circuit
     * @param minimumNumberOfCalls calls needed in the window before the failure rate counts
     * @param slidingWindow time window over which the failure rate is computed
     * @param waitInOpenState time the circuit stays open before letting calls through again
     */
    public record CircuitBreaker(
            @Min(1) @Max(100) float failureRateThreshold,
            @Positive int minimumNumberOfCalls,
            @NotNull @DurationMin(seconds = 1) Duration slidingWindow,
            @NotNull @DurationMin(millis = 1) Duration waitInOpenState) {}
}
