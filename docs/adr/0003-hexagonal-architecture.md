# 0003. Hexagonal architecture

- **Status:** Accepted
- **Date:** 2026-10-19

## Context

The service has one inbound channel today (REST) and one outbound dependency (the existing product APIs). Most of the work ahead is around that outbound edge: timeouts, caching, circuit breaking and partial results. The business rules (order, omissions, not found) must stay readable and testable while that edge grows, and a second inbound channel is planned.

## Decision

We follow a hexagonal architecture (ports and adapters) in three packages:

- `domain`: products, product ids and the outcome of a lookup (`ProductLookup`), with their invariants. Plain Java.
- `application`: the use case and its ports. `GetSimilarProductsUseCase` is the inbound port and `ProductCatalog` the outbound one. Plain Java.
- `infrastructure`: adapters (REST in, HTTP out, and later caching) and the Spring wiring.

`domain` and `application` do not depend on Spring, Jackson or HTTP. The use case is created in `infrastructure.config`, so the core carries no framework annotations. ArchUnit enforces these rules in the build.

## Alternatives

- **Layered by technical role (controller, service, repository).** Familiar, but the service ends up depending on HTTP client types and exceptions, and cross-cutting concerns like caching leak into it.
- **A single class per endpoint.** Fastest to write, but the resilience logic of the next phases would bury the business rules.

## Consequences

- The use case is tested with an in-memory catalog: fast, no Spring context and no HTTP.
- Caching and resilience can be added as decorators of `ProductCatalog` without touching the use case.
- A new inbound adapter reuses the use case as is.
- More types and packages than a minimal solution, which is the price of keeping the edges replaceable.
