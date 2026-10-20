# 0005. Virtual threads over WebFlux

- **Status:** Accepted
- **Date:** 2026-10-20

## Context

Each request to the service waits on the network twice: once for the similar ids and once for the details of each similar product, which can take from milliseconds to 50 seconds. The baseline fetched the details one after another on Tomcat's pool of platform threads, so a request took the sum of all upstream latencies and held a thread for all that time.

We need the detail calls to run in parallel and many requests to wait on slow upstreams at once, without running out of threads.

## Decision

We keep Spring MVC and blocking HTTP calls, on virtual threads (Java 25):

- `spring.threads.virtual.enabled=true` serves each request on a virtual thread and backs Boot's `applicationTaskExecutor` with virtual threads.
- `ProductCatalog` returns `CompletableFuture`s. The HTTP adapter runs each blocking call on the `applicationTaskExecutor`, and the use case starts every detail lookup before waiting for any of them.

Java 24 removed the pinning of virtual threads in `synchronized` blocks (JEP 491), the main caveat of virtual threads on Java 21.

## Alternatives

- **WebFlux and a reactive `WebClient`.** Same concurrency, but every layer becomes reactive: the use case, the cache, the error handling and the tests. Stack traces and debugging get harder, and the team pays that cost on every change for no gain at this scale.
- **A fixed pool of platform threads for the detail calls.** Works, but its size becomes a hidden limit that has to be tuned against upstream latency. Concurrency towards the upstream is limited explicitly with a bulkhead instead (see later ADRs).

## Consequences

- The code stays imperative and readable. The only asynchronous seam is the `ProductCatalog` port, which is also what lets callers stop waiting on a lookup without cancelling it.
- Concurrency has no natural ceiling any more: nothing stops thousands of simultaneous upstream calls, so an explicit limit is needed.
- Thread-local state does not flow into the futures by itself. Anything that needs it (such as tracing) must be propagated on purpose.
