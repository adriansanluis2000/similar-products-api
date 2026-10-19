# Performance

How the service behaves under the load test of the statement ([`shared/k6/test.js`](../shared/k6/test.js)), measured at each stage of its evolution.

## How it is measured

- **Load.** The k6 test of the statement, unchanged. Five scenarios run one after another, each with 200 virtual users for 10 seconds. Every user requests the same product over and over, sleeping 0.5 s between requests.
- **Metrics.** k6 0.28 writes every request to InfluxDB with a `scenario` tag. The tables below are queried per scenario from InfluxDB: requests, throughput over the 10 s of the scenario, latency percentiles and responses other than 200.
- **Products returned.** What the endpoint answers for each scenario right after the run.

### Environment

| | |
|---|---|
| Machine | Intel Core i7-13700KF (16 cores, 24 threads), 32 GB of RAM |
| OS | Windows 11 Pro 10.0.26300, balanced power plan |
| Application | Temurin JDK 25.0.4.1, `java -jar` on the host with default JVM options |
| Mocks, InfluxDB and k6 | Docker Desktop 29.8.2 |

The application, the mocks and k6 share the machine, so the numbers are meant to be compared between stages, not read as absolute capacity.

### Reproducing a run

Start the infrastructure and the application:

```bash
docker compose up -d simulado influxdb grafana
cd app && ./mvnw -q package -DskipTests && java -jar target/similar-products-api-0.0.1-SNAPSHOT.jar
```

From another terminal at the repository root, run the test:

```bash
docker compose run --rm k6 run scripts/test.js
```

Right after the run, query the results of the last few minutes per scenario:

```bash
docker compose exec influxdb influx -database k6 -execute "SELECT count(value), percentile(value,50), percentile(value,95), percentile(value,99), max(value) FROM http_req_duration WHERE time > now() - 5m GROUP BY scenario"
```

## Baseline (v0.1.0)

Sequential implementation: the details of the similar products are requested one after another, with a 1 s connection timeout, a 2 s read timeout and no cache. Run on 2026-10-19.

| Scenario | Requests | Throughput | p50 | p95 | p99 | Max | Non-200 | Products returned |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| normal | 3,023 | 302 req/s | 127 ms | 586 ms | 695 ms | 733 ms | 0 | 2, 3, 4 |
| notFound | 3,729 | 373 req/s | 23 ms | 221 ms | 332 ms | 363 ms | 0 | 1, 2 |
| error | 3,667 | 367 req/s | 24 ms | 264 ms | 368 ms | 397 ms | 0 | 1, 2 |
| slow | 600 | 60 req/s | 3,224 ms | 3,311 ms | 3,321 ms | 3,324 ms | 0 | 3, 100 |
| verySlow | 400 | 40 req/s | 5,070 ms | 5,239 ms | 5,245 ms | 5,247 ms | 0 | 100 |

What the numbers show:

- **Correct, but slow when one product is slow.** Every answer is a 200 with the products the business rules expect, but latencies add up. In `slow`, product 100 costs 1 s and product 1000 the full 2 s read timeout. In `verySlow`, products 1000 and 10000 hit the timeout one after the other. Products 1000 and 10000 are never returned.
- **Slow answers mean fewer requests.** Each virtual user waits for its response before sleeping, so throughput falls from about 300 req/s to 40-60 req/s in the slow scenarios. The load generator measures the latency of the service, not its capacity, once responses take seconds.
- **`normal` is slower than `notFound` and `error`** although it does similar work: it runs first, while the JVM is still warming up, and product 3 takes 100 ms upstream.
- **One thread per request for its whole duration.** Tomcat's default pool of 200 threads matches the 200 users of the test, so nothing queues here. A slower upstream or more concurrent users would exhaust it.
