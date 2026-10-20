# 0010. Map upstream failures to 502, 503 and 504

- **Status:** Accepted
- **Date:** 2026-10-20

## Context

When the similar ids of a product cannot be retrieved, there is nothing meaningful to answer (ADR 0004), and so far the service answered 500. The contract defines only 200 and 404, but a 500 tells consumers that this service is broken, when the problem is a dependency, and gives them no hint about whether or when to retry. The failure can now have several causes: the existing APIs failed or answered something unreadable, they did not answer within the time budget (ADR 0006), or the service is deliberately not calling them because the circuit is open or too many calls are in progress (ADR 0008).

## Decision

Failures to get the similar ids are answered with the gateway status codes of RFC 9110, as RFC 9457 problem details:

| Cause | Status |
|---|---|
| The existing APIs answered an error, an unreadable body, or could not be reached | 502 Bad Gateway |
| They did not answer within the time budget | 504 Gateway Timeout |
| The circuit is open, or the concurrency limit is full | 503 Service Unavailable, with `Retry-After` |

`Retry-After` is the time the circuit stays open (10 s by default), an upper bound since it may close sooner, or 1 s for the concurrency limit. The HTTP adapter provides it with the failure, so the REST adapter does not depend on it.

Failures of a single similar product are still left out of a 200 (ADR 0004).

## Alternatives

- **500 for every failure.** Simpler, but it hides which side failed and whether retrying makes sense.
- **503 for every failure.** Correct in spirit, but 502 and 504 tell operators at once whether the dependency is broken or slow.
- **200 with an empty list.** Stays within the contract, but lies: consumers would show "no similar products" for a product that has them.

## Consequences

- Consumers and load balancers can tell this service's errors from its dependency's, and back off when told to.
- 502, 503 and 504 are additions to the contract that consumers need to know about, like the 400 of ADR 0004.
