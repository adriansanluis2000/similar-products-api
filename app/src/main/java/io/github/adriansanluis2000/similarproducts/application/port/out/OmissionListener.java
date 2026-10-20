package io.github.adriansanluis2000.similarproducts.application.port.out;

import io.github.adriansanluis2000.similarproducts.domain.UnavailableReason;
import io.github.adriansanluis2000.similarproducts.domain.model.ProductId;

/**
 * Learns about each similar product left out of an answer, and why. Answers carry no trace of the products they leave
 * out (see ADR 0004), so this is where omissions become visible.
 */
public interface OmissionListener {

    void productNotFound(ProductId productId);

    void productUnavailable(ProductId productId, UnavailableReason reason);

    /** The lookup was still running when the time budget of the request ran out. */
    void budgetExceeded(ProductId productId);
}
