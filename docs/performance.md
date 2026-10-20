# Performance

How the service behaves under the load test of the statement ([`shared/k6/test.js`](../shared/k6/test.js)), measured at each stage of its evolution.

## How it is measured

- **Load.** The k6 test of the statement, unchanged. Five scenarios run one after another, each with 200 virtual users for 10 seconds. Every user requests the same product over and over, sleeping 0.5 s between requests.
- **Metrics.** [`scripts/perf-report.sh`](../scripts/perf-report.sh) runs the test and queries InfluxDB, where k6 0.28 writes every request with a `scenario` tag. Per scenario it reports requests, throughput over the 10 s of the scenario, latency percentiles and the share of responses other than 200.
- **Products returned.** What the endpoint answers for each scenario right after the run.
- **Dashboard.** A screenshot of the Grafana dashboard of each run is kept in [`performance/`](performance/).

### Environment

| | |
|---|---|
| Machine | Intel Core i7-13700KF (16 cores, 24 threads), 32 GB of RAM |
| OS | Windows 11 Pro 10.0.26300, balanced power plan |
| Application | Temurin JDK 25.0.4.1, `java -jar` on the host with default JVM options |
| Mocks, InfluxDB and k6 | Docker Desktop 29.8.2 |

The mock slows down as it serves requests: three runs in a row of the same code against the same mock took the p95 of `notFound` from 611 ms to 1,882 ms, and the last one failed 17 % of the requests in `normal`. The script therefore restarts simulado before every run, and every table below was measured that way. Even so, the application, the mocks and k6 share the machine, so the numbers are meant to be compared between stages, not read as absolute capacity, and differences below about 2x in the fast scenarios are noise.

### Reproducing a run

Start the infrastructure and the application:

```bash
docker compose up -d simulado influxdb grafana
cd app && ./mvnw -q package -DskipTests && java -jar target/similar-products-api-0.0.1-SNAPSHOT.jar
```

From another terminal at the repository root, run the test and print the table. With a file name, the script also saves the Grafana screenshot, using the browser in `CHROME`:

```bash
scripts/perf-report.sh docs/performance/run.png
```

## Baseline (v0.1.0)

Sequential implementation: the details of the similar products are requested one after another, with a 1 s connection timeout, a 2 s read timeout and no cache.

| Scenario | Requests | Throughput | p50 | p95 | p99 | Max | Errors | Products returned |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| normal | 2907 | 291 req/s | 154 ms | 539 ms | 630 ms | 679 ms | 0.0 % | 2, 3, 4 |
| notFound | 3704 | 370 req/s | 27 ms | 260 ms | 371 ms | 393 ms | 0.0 % | 1, 2 |
| error | 3664 | 366 req/s | 24 ms | 267 ms | 359 ms | 387 ms | 0.0 % | 1, 2 |
| slow | 600 | 60 req/s | 3214 ms | 3363 ms | 3369 ms | 3373 ms | 0.0 % | 3, 100 |
| verySlow | 400 | 40 req/s | 5100 ms | 5229 ms | 5251 ms | 5252 ms | 0.0 % | 100 |

![Grafana dashboard of the baseline run](performance/0-baseline.png)

What the numbers show:

- **Correct, but slow when one product is slow.** Every answer is a 200 with the products the business rules expect, but latencies add up. In `slow`, product 100 costs 1 s and product 1000 the full 2 s read timeout. In `verySlow`, products 1000 and 10000 hit the timeout one after the other. Products 1000 and 10000 are never returned.
- **Slow answers mean fewer requests.** Each virtual user waits for its response before sleeping, so throughput falls from about 300 req/s to 40-60 req/s in the slow scenarios. The load generator measures the latency of the service, not its capacity, once responses take seconds.
- **`normal` is the slowest of the fast scenarios** although it does similar work: it runs first, while the JVM is still warming up, and product 3 takes 100 ms upstream.
- **One thread per request for its whole duration.** Tomcat's default pool of 200 threads matches the 200 users of the test, so nothing queues here. A slower upstream or more concurrent users would exhaust it.

## 1. Parallel lookups on virtual threads

The details of the similar products are requested at once, each blocking a virtual thread, and the request waits for all of them. Timeouts as in the baseline. See [ADR 0005](adr/0005-virtual-threads-over-webflux.md).

| Scenario | Requests | Throughput | p50 | p95 | p99 | Max | Errors | Products returned |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| normal | 3048 | 305 req/s | 118 ms | 608 ms | 675 ms | 732 ms | 0.0 % | 2, 3, 4 |
| notFound | 3858 | 386 req/s | 16 ms | 113 ms | 304 ms | 344 ms | 0.0 % | 1, 2 |
| error | 3849 | 385 req/s | 15 ms | 149 ms | 324 ms | 370 ms | 0.0 % | 1, 2 |
| slow | 800 | 80 req/s | 2053 ms | 2142 ms | 2159 ms | 2161 ms | 0.0 % | 3, 100 |
| verySlow | 806 | 81 req/s | 2037 ms | 2225 ms | 2244 ms | 2255 ms | 0.2 % | 100 |

![Grafana dashboard of the parallel run](performance/1-parallel.png)

- **A request now costs its slowest product, not the sum.** The p50 drops from 3.2 s to 2.1 s in `slow` and from 5.1 s to 2.0 s in `verySlow`, both bounded by the 2 s read timeout that products 1000 and 10000 hit. Throughput grows from 60 and 40 req/s to 80 req/s.
- **The fast scenarios stay within the noise or improve** (`notFound` p95 from 260 ms to 113 ms). In `normal`, product 3 takes 100 ms upstream, so that is the floor.
- **First errors.** Two requests of `verySlow` (0.2 %) answered 500 because the call for the similar ids failed with a connection error, not a timeout. Many more calls are now waiting on the mock at the same time.
- **Still far from the goal.** Requests still wait up to the full read timeout, and products 1000 and 10000 never make it into an answer.

## 2. Time budget per request

Requests answer within a budget of 1 s with the products that have arrived. Lookups that miss it keep running but their result is not used yet. See [ADR 0006](adr/0006-time-budget-and-partial-results.md).

| Scenario | Requests | Throughput | p50 | p95 | p99 | Max | Errors | Products returned |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| normal | 3041 | 304 req/s | 121 ms | 549 ms | 637 ms | 661 ms | 0.0 % | 2, 3, 4 |
| notFound | 3848 | 385 req/s | 15 ms | 130 ms | 306 ms | 338 ms | 0.0 % | 1, 2 |
| error | 3839 | 384 req/s | 16 ms | 167 ms | 342 ms | 389 ms | 0.0 % | 1, 2 |
| slow | 1400 | 140 req/s | 1008 ms | 1045 ms | 1046 ms | 1046 ms | 0.0 % | 3 |
| verySlow | 1400 | 140 req/s | 1017 ms | 1039 ms | 1051 ms | 1051 ms | 0.0 % | none |

![Grafana dashboard of the time budget run](performance/2-budget.png)

- **No request waits much longer than the budget.** The p99 of `slow` and `verySlow` drops from about 2.2 s to 1.05 s, and their throughput grows from 80 to 140 req/s.
- **But the slow products are gone.** Product 100 takes about as long as the budget, so it misses it, and `verySlow` now answers an empty list. Every request starts the slow lookups again and nothing keeps their results, so they are never shown. That is what the cache is for.
- The fast scenarios do not change: they never get close to the budget.
