package io.github.adriansanluis2000.similarproducts.infrastructure.adapter.out.metrics;

import io.github.adriansanluis2000.similarproducts.application.port.out.OmissionListener;
import io.github.adriansanluis2000.similarproducts.domain.UnavailableReason;
import io.github.adriansanluis2000.similarproducts.domain.model.ProductId;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;

/**
 * Counts the products left out of answers in {@code similar.products.omitted}, tagged with the reason: {@code
 * not_found}, {@code budget_exceeded} or the reason the catalog was unavailable, such as {@code timeout}.
 */
public class MicrometerOmissionListener implements OmissionListener {

    private final MeterRegistry registry;

    public MicrometerOmissionListener(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void productNotFound(ProductId productId) {
        count("not_found");
    }

    @Override
    public void productUnavailable(ProductId productId, UnavailableReason reason) {
        count(reason.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public void budgetExceeded(ProductId productId) {
        count("budget_exceeded");
    }

    private void count(String reason) {
        registry.counter("similar.products.omitted", "reason", reason).increment();
    }
}
