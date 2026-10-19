# Similar Products API

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
| `docs/adr/` | [Architecture decision records](docs/adr/README.md) |

Files from the original statement are kept unchanged, so the load test runs against them as is.

## Running the application

Requires JDK 25.

```bash
cd app
./mvnw spring-boot:run
```

The API listens on <http://localhost:5000>, with its health check at <http://localhost:5000/actuator/health>. To build and run the tests, use `./mvnw verify`.

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
