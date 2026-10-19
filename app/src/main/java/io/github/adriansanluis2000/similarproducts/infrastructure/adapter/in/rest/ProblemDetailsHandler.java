package io.github.adriansanluis2000.similarproducts.infrastructure.adapter.in.rest;

import io.github.adriansanluis2000.similarproducts.application.port.out.ProductCatalogUnavailableException;
import io.github.adriansanluis2000.similarproducts.domain.model.InvalidProductIdException;
import io.github.adriansanluis2000.similarproducts.domain.model.ProductNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns the errors of the use case into RFC 9457 problem details. See ADR 0004. */
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

    @ExceptionHandler
    ProblemDetail catalogUnavailable(ProductCatalogUnavailableException e) {
        log.warn("Similar products could not be retrieved: {}", e.reason());
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Product catalog unavailable",
                "The similar products could not be retrieved");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
