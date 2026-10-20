# 0009. Retry once on connection errors

- **Status:** Accepted
- **Date:** 2026-10-20

## Context

Some failures of the existing APIs are transient and happen before the request reaches them: a refused or reset connection, or a pooled connection that the server closed. In the load test with parallel lookups, two requests (0.2 % of a scenario) failed this way on the call for similar ids, which fails the whole request. Other failures are not transient at all: a product that takes 50 s will take 50 s again, and retrying it doubles the load on an upstream that is already struggling.

## Decision

Calls to the existing APIs are retried at most once, only when they fail to connect, after a random wait of 50 % to 150 % of `similar-products.upstream.retry-backoff` (100 ms by default):

- Timeouts are never retried, nor are HTTP errors, unreadable bodies, rejections of the concurrency limit or calls refused by an open circuit.
- Each attempt goes through the circuit breaker and the concurrency limit (ADR 0008), so retries count as calls and cannot get around them.
- All calls are `GET`s, so repeating one is safe.

## Alternatives

- **No retries.** One less moving part, but a single dropped connection fails a request that a second attempt would most likely have served.
- **Retry any failure, or more than once.** It hides slow or broken upstreams behind longer waits and multiplies the load when the upstream is struggling, which is when retries hurt the most.
- **Retry without jitter.** Clients that failed together retry together, in synchronized bursts.

## Consequences

- Isolated connection failures no longer reach the customer, at the cost of up to 150 ms more for that lookup.
- In an outage where connections are refused, each call costs two attempts until the circuit opens, which then stops both.
