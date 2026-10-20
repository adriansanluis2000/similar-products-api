# 0008. Circuit breaker and concurrency limit towards the existing APIs

- **Status:** Accepted
- **Date:** 2026-10-20

## Context

The cache (ADR 0007) protects the existing APIs from repeated lookups, but not from an outage: if the APIs go down, every cache miss still waits for a timeout, and each one holds a connection for up to 6 s. With virtual threads (ADR 0005) nothing caps how many of those calls run at once. At the same time, the mocks show that a single product can fail every time (product 6 answers 500, product 10000 never answers in time) while the rest of the catalog is healthy, and that must not cut off every product.

## Decision

Calls to the existing APIs go through two Resilience4j guards, used directly from code in the HTTP adapter:

- **Circuit breaker**, one for the existing APIs. It opens when at least half of the calls fail within a 10 s window, with a minimum of 20 calls in that window, and stays open for 10 s before letting a few calls through to check for recovery. Only server errors and I/O failures (timeouts, refused connections) count as failures. A 404 is a valid answer.
- **Concurrency limit** (bulkhead): at most 100 calls at the same time, and calls beyond that are rejected at once instead of queuing.

While the circuit is open or the limit is full, a detail lookup is reported as unavailable (`CIRCUIT_OPEN` or `CONCURRENCY_LIMIT`) and the product is left out, and a failure to get similar ids fails the request.

A broken product does not open the circuit, because the negative cache only lets it reach the upstream once every few seconds: product 6 adds about two failures per 10 s window, far from the minimum of 20 calls. The circuit is meant for an upstream that is down as a whole, not for one bad product.

All thresholds are configurable under `similar-products.upstream`.

## Alternatives

- **The Resilience4j Spring Boot starter and its annotations.** Configuration and metrics come for free, but the annotations rely on proxies and fit poorly with the asynchronous port. The core modules keep the policy visible in the adapter, and metrics can be bound explicitly.
- **Spring Framework 7's `@ConcurrencyLimit`.** Covers the bulkhead only. There is no circuit breaker in Spring Framework.
- **A count-based window.** With a cache in front, calls are rare and bursty, so a fixed number of calls can span minutes and keep failures from a long-gone outage. A time window reflects the current state of the upstream.

## Consequences

- When the existing APIs are down, requests fail fast instead of each waiting for a timeout, and the upstream gets room to recover.
- The concurrency limit puts a ceiling on resources spent on the upstream, but under a burst of cache misses some products may be left out even though the upstream is healthy.
- The thresholds are a judgment call for the mock's traffic, and should be revisited with production data.
