package io.github.adriansanluis2000.similarproducts.infrastructure.adapter.in.rest;

import io.github.adriansanluis2000.similarproducts.application.port.out.ProductCatalogUnavailableException;
import io.github.adriansanluis2000.similarproducts.domain.model.InvalidProductIdException;
import io.github.adriansanluis2000.similarproducts.domain.model.ProductNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns the errors of the use case into RFC 9457 problem details. See ADRs 0004 and 0010. */
@RestControllerAdvice
class ProblemDetailsHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsHandler.class);

    @ExceptionHandler
    ProblemDetail productNotFound(ProductNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Product not found", e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail invalidProductId(InvalidProductIdException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid product id", e.getMessage());
    }

    /** The existing APIs failed (502), were too slow (504) or are being spared for a while (503). */
    @ExceptionHandler
    ResponseEntity<ProblemDetail> catalogUnavailable(ProductCatalogUnavailableException e) {
        log.warn("Similar products could not be retrieved: {}", e.reason());
        var detail = "The similar products could not be retrieved";
        var problem = switch (e.reason()) {
            case TIMEOUT -> problem(HttpStatus.GATEWAY_TIMEOUT, "Product catalog timed out", detail);
            case CIRCUIT_OPEN, CONCURRENCY_LIMIT ->
                problem(HttpStatus.SERVICE_UNAVAILABLE, "Product catalog temporarily unavailable", detail);
            case UPSTREAM_ERROR, CONNECTION_ERROR, INVALID_RESPONSE ->
                problem(HttpStatus.BAD_GATEWAY, "Product catalog failed", detail);
        };
        var response = ResponseEntity.status(problem.getStatus());
        e.retryAfter()
                .ifPresent(wait ->
                        response.header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, wait.toSeconds()))));
        return response.body(problem);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
