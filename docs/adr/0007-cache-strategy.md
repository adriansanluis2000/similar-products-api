# 0007. Cache strategy

- **Status:** Accepted
- **Date:** 2026-10-20

## Context

With a time budget (ADR 0006), products that take longer than the budget are never shown, and every request asks the upstream for them again. Many requests ask for the same few products at the same time, the mock already uses a whole CPU core under the fast scenarios of the load test, and some products fail every time (404 or 500). Prices and availability change, so details cannot be kept for long.

## Decision

`CachingProductCatalog` decorates the HTTP catalog with two Caffeine `AsyncLoadingCache`s, one for similar ids and one for product details. The use case does not know it exists.

- **Coalescing.** The cache holds futures, so concurrent lookups of the same product share one upstream call.
- **Completion in the background.** A lookup keeps going after the request that started it has answered. When it arrives, it is cached and the next requests include the product. For that to be useful, the read timeout grows from 2 s to 6 s: product 1000 (5 s) can arrive in the background, while product 10000 (50 s) is not waited for, since each attempt would hold a connection for almost a minute.
- **Expiry by outcome** (defaults, all configurable):

  | Answer | Product detail | Similar ids |
  |---|---|---|
  | Found | 60 s, refreshed after 30 s | 10 min, refreshed after 5 min |
  | Not found (404) | 5 min | 5 min |
  | Unavailable (error or timeout) | 5 s | not cached |

- **Stale while revalidate.** After the refresh age, the cached value keeps being served while a new one is fetched in the background, so hot products never make a request wait.
- **Stale if error.** If a product that was found becomes unavailable on refresh, the cached detail is kept until it expires. A product that disappears (404) is replaced.

Failures to get similar ids are not cached: they fail the request, and the circuit breaker handles an unhealthy upstream. Caffeine's warnings for failed loads are silenced, as those failures are expected and handled by the service.

## Alternatives

- **Spring's `@Cacheable`.** Simple, but it cannot coalesce concurrent loads, expire entries depending on their value, or refresh in the background.
- **A distributed cache (Redis).** Shares entries between replicas, at the cost of a network hop and an extra component. With one replica and a 60 s TTL it adds little; it is the natural next step when scaling out.
- **Caching only successful answers.** Products that always fail (such as 6) would hit the upstream on every request.

## Consequences

- A product detail can be up to 60 s old, and a little older while refreshing. That is the price of answering in milliseconds; the TTLs are configurable if freshness matters more.
- Product 10000 is never shown with the mock, which takes 50 s to answer it.
- Each replica has its own cache, so hit rates drop as replicas are added.
- Memory is bounded by the maximum size of each cache.
