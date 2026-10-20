package io.github.adriansanluis2000.similarproducts.domain;

/** Why the product catalog could not answer. */
public enum UnavailableReason {
    /** The catalog answered with an error status other than 404. */
    UPSTREAM_ERROR,
    /** The catalog did not answer in time. */
    TIMEOUT,
    /** The connection to the catalog could not be established. */
    CONNECTION_ERROR,
    /** The catalog answered with a body that cannot be read. */
    INVALID_RESPONSE,
    /** The catalog has been failing, so it is not called for a while. */
    CIRCUIT_OPEN,
    /** Too many calls to the catalog are already in progress. */
    CONCURRENCY_LIMIT
}
