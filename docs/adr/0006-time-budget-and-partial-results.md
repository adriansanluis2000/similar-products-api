# 0006. Time budget per request and partial results

- **Status:** Accepted
- **Date:** 2026-10-20

## Context

With parallel lookups (ADR 0005), a request costs its slowest similar product. The mocks include products that take 1, 5 and 50 seconds, so a single slow product decides how long a customer waits, and read timeouts long enough to let slow products arrive make it worse. A page that shows similar products cannot wait seconds for them.

## Decision

Each request has a time budget, `similar-products.budget`, 1 second by default:

- The deadline covers the whole request, the similar ids included.
- All detail lookups start at once. When the deadline passes, the answer includes the products that have arrived, in their original order, and leaves out the rest, as ADR 0004 already does for products that fail.
- If the similar ids themselves miss the deadline, the request fails with a timeout.
- Lookups that miss the deadline are not cancelled: they keep running and can still complete in the background. The use case waits on a copy of each future, or with a timed wait that does not complete it, so the time limit of one request never completes a future that other requests may share.

The budget is a single value for the whole request, validated at startup (it must be positive).

## Alternatives

- **No budget, short read timeouts.** Simpler, but a timeout aborts the upstream call, so a slow product could never be fetched at all. The budget limits how long the customer waits; the read timeout limits how long a call may take.
- **Cancel the lookups that miss the deadline.** Frees resources sooner, but throws away work that is about to complete and, once there is a cache, would stop slow products from ever reaching it.
- **A budget per lookup instead of per request.** Lookups run in parallel, so the per-request deadline is what the customer experiences and is easier to reason about.

## Consequences

- No request waits much longer than the budget, whatever the upstream does.
- Answers can be partial, and which products make it depends on timing. Without a cache, the slow products are fetched again on every request and never shown.
- Work keeps going after the answer is sent, so concurrency towards the upstream must be limited separately.
