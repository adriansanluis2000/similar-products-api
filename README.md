# Similar Products API

[![CI](https://github.com/adriansanluis2000/similar-products-api/actions/workflows/ci.yml/badge.svg)](https://github.com/adriansanluis2000/similar-products-api/actions/workflows/ci.yml)

Solution to the [backend dev technical test](docs/statement.md) from [dalogax/backendDevTest](https://github.com/dalogax/backendDevTest): a Spring Boot service that exposes `GET /product/{productId}/similar` on port 5000 and returns the details of the products similar to a given one, built on top of two existing APIs.

The focus is on a clean architecture, performance and resilience backed by measurements, and a delivery ready to deploy.

> **Status:** in development.

## Repository layout

| Path | Content |
|---|---|
| `app/` | Spring Boot application |
| `Dockerfile`, `docker-compose.override.yaml` | Container image and its service next to the test infrastructure |
| `similarProducts.yaml` | Contract of the API to implement |
| `existingApis.yaml` | Contract of the existing APIs the service consumes |
| `docker-compose.yaml`, `shared/` | Test infrastructure: mocks (simulado), k6, InfluxDB and Grafana |
| `docs/statement.md` | Original statement |
| `docs/performance.md` | [Load test results](docs/performance.md) at each stage |
| `docs/adr/` | [Architecture decision records](docs/adr/README.md) |

Files from the original statement are kept unchanged, so the load test runs against them as is.

## Running the application

### With Docker

Requires Docker with Compose. One command starts the application with the mocks, InfluxDB and Grafana:

```bash
docker compose up -d simulado influxdb grafana app
```

The image is built the first time; after changing the code, add `--build`. Every release tag also publishes the image to the GitHub Container Registry, as `ghcr.io/adriansanluis2000/similar-products-api`. [`docker-compose.override.yaml`](docker-compose.override.yaml) adds the application to the infrastructure of the statement, whose compose file is left untouched. The image is described in [ADR 0011](docs/adr/0011-container-image.md).

### Locally

Requires JDK 25 and the mocks running (see [Running the test infrastructure](#running-the-test-infrastructure)).

```bash
cd app
./mvnw spring-boot:run
```

The API listens on <http://localhost:5000>, with its health check at <http://localhost:5000/actuator/health>. To build and run the tests, use `./mvnw verify`.

## API

`GET /product/{productId}/similar` returns the detail of the products similar to the given one, ordered by similarity, as agreed in [`similarProducts.yaml`](similarProducts.yaml). Errors are [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details.

| Situation | Response |
|---|---|
| The product exists | 200 with its similar products. Those whose detail cannot be retrieved (404, error or timeout) or does not arrive within the time budget are left out, and repeated ids are returned once |
| The product does not exist | 404 |
| The product id is not 1 to 64 letters, digits, `-` or `_` | 400 |
| The existing APIs fail or cannot be reached when asked for the similar ids | 502 |
| They do not answer the similar ids within the time budget | 504 |
| They are not being called for a while, after failing too often or because too many calls are in progress | 503, with `Retry-After` |

The reasons behind these rules are in [ADR 0004](docs/adr/0004-similar-products-business-rules.md) and [ADR 0010](docs/adr/0010-map-upstream-failures-to-gateway-errors.md).

## Configuration

| Property | Default | Description |
|---|---|---|
| `spring.http.serviceclient.products.base-url` | `http://localhost:3001` | Base URL of the existing product APIs |
| `spring.http.serviceclient.products.connect-timeout` | `1s` | Time to establish a connection to them |
| `spring.http.serviceclient.products.read-timeout` | `6s` | Time to wait for each of their responses. Longer than the budget, so slow products can still reach the cache |
| `similar-products.budget` | `1s` | Maximum time to answer a request; products that take longer are left out ([ADR 0006](docs/adr/0006-time-budget-and-partial-results.md)) |
| `similar-products.cache.maximum-size` | `10000` | Entries kept per cache ([ADR 0007](docs/adr/0007-cache-strategy.md)) |
| `similar-products.cache.products.found` | `60s` | How long a product detail is kept |
| `similar-products.cache.products.not-found` | `5m` | How long a product that does not exist is remembered |
| `similar-products.cache.products.unavailable` | `5s` | How long a failed lookup is remembered before trying again |
| `similar-products.cache.products.refresh` | `30s` | Age after which a cached detail is refreshed in the background while still served |
| `similar-products.cache.similar-ids.found` | `10m` | How long the similar ids of a product are kept |
| `similar-products.cache.similar-ids.not-found` | `5m` | How long a product without similar ids is remembered |
| `similar-products.cache.similar-ids.refresh` | `5m` | Age after which similar ids are refreshed in the background |
| `similar-products.upstream.max-concurrent-calls` | `100` | Calls to the existing APIs allowed at the same time; more are rejected at once ([ADR 0008](docs/adr/0008-upstream-resilience.md)) |
| `similar-products.upstream.retry-backoff` | `100ms` | Average wait before retrying, once, a call that failed to connect ([ADR 0009](docs/adr/0009-retry-on-connection-errors.md)) |
| `similar-products.upstream.circuit-breaker.failure-rate-threshold` | `50` | Percentage of failed calls that opens the circuit |
| `similar-products.upstream.circuit-breaker.minimum-number-of-calls` | `20` | Calls needed in the window before the failure rate counts |
| `similar-products.upstream.circuit-breaker.sliding-window` | `10s` | Window over which the failure rate is computed |
| `similar-products.upstream.circuit-breaker.wait-in-open-state` | `10s` | Time the circuit stays open before letting calls through again |

Any property can be overridden with an environment variable, for example `SPRING_HTTP_SERVICECLIENT_PRODUCTS_BASEURL`.

## Tests

`./mvnw verify` (in `app/`) runs every check:

| Kind | What it covers |
|---|---|
| Unit tests | Domain invariants, the use case (order, omissions, duplicates, time budget) and the cache (expiry by outcome, refresh, coalescing), with fakes and a fake clock |
| Adapter tests | The HTTP adapter against WireMock (quirks of the mocks, timeouts, circuit breaker, retry) and the REST adapter with MockMvc |
| Architecture tests | Dependency rules between layers, with ArchUnit |
| End-to-end tests (`*IT`) | The scenarios of the load test against the real mock image and `mocks.json`, with Testcontainers. They need Docker and are skipped without it |

The build fails if tests cover less than 85 % of the lines of any `domain` or `application` package. The coverage report is written to `app/target/site/jacoco/index.html`.

## Observability

| Endpoint | Content |
|---|---|
| `/actuator/health/liveness` | Whether the process is alive, for restarts. Also used by the image's `HEALTHCHECK` |
| `/actuator/health/readiness` | Whether it can take traffic |
| `/actuator/prometheus` | Metrics in Prometheus format |

Besides the standard JVM and HTTP metrics (`http_server_requests_*` for this API, `http_client_requests_*` for the calls to the existing APIs), it publishes:

| Metric | Meaning |
|---|---|
| `similar_products_omitted_total` (`reason` = `not_found`, `budget_exceeded`, `timeout`, `upstream_error`, `circuit_open`, ...) | Similar products left out of answers, which the answers themselves do not show |
| `cache_gets_total`, `cache_puts_total`, `cache_evictions_total` (`cache` = `products`, `similar-ids`) | Hits and misses of each cache, and so its hit ratio |
| `resilience4j_circuitbreaker_state`, `resilience4j_circuitbreaker_calls_*` | State of the circuit breaker and outcome of the calls it guards |
| `resilience4j_bulkhead_available_concurrent_calls` | Room left under the concurrency limit |
| `resilience4j_retry_calls_total` | Calls that succeeded or failed with and without a retry |

The container writes its logs as JSON in the Elastic Common Schema; locally they stay human-readable. Every log line of a request carries its `traceId`, also those written while calling the existing APIs. See [ADR 0012](docs/adr/0012-observability.md).

## Running the test infrastructure

Requires Docker with Compose.

Start the mocks, InfluxDB and Grafana:

```bash
docker compose up -d simulado influxdb grafana
```

Check that the mocks respond:

```bash
curl http://localhost:3001/product/1/similarids
```

With the app running on port 5000, run the k6 load test:

```bash
docker compose run --rm k6 run scripts/test.js
```

Results are shown in Grafana at <http://localhost:3000/d/Le2Ku9NMk/k6-performance-test>.

## Troubleshooting

- **Port 5000 already in use on macOS.** Since macOS Monterey, the AirPlay Receiver listens on port 5000. Turn it off in *System Settings → General → AirDrop & Handoff → AirPlay Receiver*.
- **Mocks or dashboards do not load.** Docker must be able to mount the `shared` folder. Check *Settings → Resources → File sharing* in Docker Desktop.

## License

[Apache License 2.0](LICENSE). The statement, contracts and test infrastructure come from [dalogax/backendDevTest](https://github.com/dalogax/backendDevTest), under the same license.
