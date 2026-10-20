package io.github.adriansanluis2000.similarproducts.infrastructure.adapter.out.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.adriansanluis2000.similarproducts.domain.UnavailableReason;
import io.github.adriansanluis2000.similarproducts.domain.model.ProductId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class MicrometerOmissionListenerTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerOmissionListener listener = new MicrometerOmissionListener(registry);

    @Test
    void countsOmittedProductsByReason() {
        listener.productNotFound(new ProductId("5"));
        listener.productNotFound(new ProductId("7"));
        listener.productUnavailable(new ProductId("6"), UnavailableReason.UPSTREAM_ERROR);
        listener.budgetExceeded(new ProductId("10000"));

        assertThat(omitted("not_found")).isEqualTo(2);
        assertThat(omitted("upstream_error")).isEqualTo(1);
        assertThat(omitted("budget_exceeded")).isEqualTo(1);
    }

    private double omitted(String reason) {
        return registry.get("similar.products.omitted")
                .tag("reason", reason)
                .counter()
                .count();
    }
}
