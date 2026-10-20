# Similar Products API

[![CI](https://github.com/adriansanluis2000/similar-products-api/actions/workflows/ci.yml/badge.svg)](https://github.com/adriansanluis2000/similar-products-api/actions/workflows/ci.yml)

Solution to the [backend dev technical test](docs/statement.md) from [dalogax/backendDevTest](https://github.com/dalogax/backendDevTest): a Spring Boot service that exposes `GET /product/{productId}/similar` on port 5000 and returns the details of the products similar to a given one, built on top of two existing APIs.

The focus is on a clean architecture, performance and resilience backed by measurements, and a delivery ready to deploy.

> **Status:** in development.

## Repository layout

| Path | Content |
|---|---|
| `app/` | Spring Boot application |
| `similarProducts.yaml` | Contract of the API to implement |
| `existingApis.yaml` | Contract of the existing APIs the service consumes |
| `docker-compose.yaml`, `shared/` | Test infrastructure: mocks (simulado), k6, InfluxDB and Grafana |
| `docs/statement.md` | Original statement |
| `docs/performance.md` | [Load test results](docs/performance.md) at each stage |
| `docs/adr/` | [Architecture decision records](docs/adr/README.md) |

Files from the original statement are kept unchanged, so the load test runs against them as is.

## Running the application

Requires JDK 25.

```bash
cd app
./mvnw spring-boot:run
```

The API listens on <http://localhost:5000>, with its health check at <http://localhost:5000/actuator/health>. It reads products from the existing APIs, which the mocks serve on port 3001 (see below). To build and run the tests, use `./mvnw verify`.

## API

`GET /product/{productId}/similar` returns the detail of the products similar to the given one, ordered by similarity, as agreed in [`similarProducts.yaml`](similarProducts.yaml). Errors are [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details.

| Situation | Response |
|---|---|
| The product exists | 200 with its similar products. Those whose detail cannot be retrieved (404, error or timeout) or does not arrive within the time budget are left out, and repeated ids are returned once |
| The product does not exist | 404 |
| The product id is not 1 to 64 letters, digits, `-` or `_` | 400 |
| The similar ids cannot be retrieved | 500 |

The reasons behind these rules are in [ADR 0004](docs/adr/0004-similar-products-business-rules.md).

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

Any property can be overridden with an environment variable, for example `SPRING_HTTP_SERVICECLIENT_PRODUCTS_BASEURL`.

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
