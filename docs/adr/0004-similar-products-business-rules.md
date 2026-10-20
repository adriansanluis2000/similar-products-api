# 0004. Business rules for similar products

- **Status:** Accepted. The status codes for failures to get the similar ids are set by [ADR 0010](0010-map-upstream-failures-to-gateway-errors.md).
- **Date:** 2026-10-19

## Context

The contract (`similarProducts.yaml`) defines only two responses: 200 with the list of similar products, ordered by similarity, and 404 when the product is not found. It says nothing about similar products whose detail cannot be retrieved, duplicated ids, malformed ids or failures of the existing APIs. The mocks exercise several of those cases: a similar product that returns 404, one that returns 500 and others that take up to 50 seconds.

## Decision

- **Unknown product.** If the similar ids of the requested product return 404, we answer 404 with a problem detail (RFC 9457).
- **Similar product without detail.** If the detail of a similar product returns 404, an error or does not arrive in time, we leave that product out and answer 200 with the rest, in the original order. One broken product must not hide the others from the customer.
- **Duplicated ids.** The contract marks the list as `uniqueItems`. We drop repeated ids and keep the first occurrence, so the order is preserved and each detail is requested once.
- **Product id format.** A product id must be 1 to 64 letters, digits, hyphens or underscores. Anything else is answered with 400 and a problem detail, before calling the existing APIs. This keeps unexpected input, such as `..` or encoded slashes, out of the upstream URLs.
- **Similar ids unavailable.** If the similar ids cannot be retrieved for any other reason, we cannot build a meaningful answer and respond with an error. The status codes for those failures are refined in a later ADR.

## Alternatives

- **Fail the whole request when one detail fails.** Simpler, but a single slow or broken product would take down every page that shows it as similar.
- **Answer 404 to a malformed product id.** It stays within the contract, since such a product cannot exist, but it hides a client error behind a "not found" and makes debugging harder for the consumers.
- **Keep duplicates.** It would break the `uniqueItems` constraint of the contract.

## Consequences

- The response can be partial without any signal in the body, as the contract has no room for it. Omissions must be visible in logs and metrics instead.
- 400 is an addition to the contract that the consumers need to know about.
