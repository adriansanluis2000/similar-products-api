# 0012. Observability

- **Status:** Accepted
- **Date:** 2026-10-20

## Context

Most of what this service does is invisible in its answers: a cached detail served while it is refreshed, a product left out because it missed the time budget, a circuit that opened. Operators need to see that behaviour to tune it, and to tell this service's problems from the upstream's. The service also runs in containers, where logs are collected and parsed, not read in a terminal.

## Decision

- **Health probes.** `/actuator/health/liveness` and `/actuator/health/readiness`, for orchestrators and for the image's `HEALTHCHECK`.
- **Metrics in Prometheus format** at `/actuator/prometheus`:
  - the standard JVM metrics, and HTTP metrics for this API and for each call to the existing APIs;
  - hit and miss counts of both caches, from Caffeine's statistics;
  - state and outcomes of the circuit breaker, the concurrency limit and the retry, from Resilience4j;
  - `similar.products.omitted`, counted by reason (`not_found`, `budget_exceeded`, `timeout`, ...). The use case reports each omission through an outbound port, `OmissionListener`, so the core does not depend on Micrometer.
- **Structured logs.** The container image writes JSON in the Elastic Common Schema, with Spring Boot's built-in support. Running locally keeps human-readable logs.
- **Trace ids in every log line.** Micrometer Tracing with the OpenTelemetry bridge gives each request a trace id. A context-propagating task decorator carries it into the virtual threads that call the existing APIs, so their log lines share it. No traces are exported yet.

## Alternatives

- **Logs only.** Cheap, but counting omissions or cache hits from log lines is slow and fragile.
- **Counting omissions in the REST adapter.** It only sees the products returned, not why the others are missing.
- **Exporting traces now (OTLP to a collector).** Valuable in production, but it needs a collector that the test infrastructure does not have. Adding an exporter is a dependency and a few properties away.

## Consequences

- Cache hit ratio, circuit state and omissions by reason can be graphed and alerted on.
- Every log line of a request, including those written on other threads, can be found by its trace id.
- Each request creates spans that are not exported. Their cost is small, and it pays off once an exporter is added.
