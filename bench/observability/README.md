# bench/observability/

Prometheus + Grafana wiring for the six dashboards specified in
[`docs-v00/Benchmark/benchmark-strategy/06-dashboard-building.md`](../../docs-v00/Benchmark/benchmark-strategy/06-dashboard-building.md):
Overview, HTTP & Load Testing, JVM Runtime, Spring Boot Runtime, MySQL Performance, Performance
Correlation. Prometheus, Grafana, and mysqld-exporter all run as containers, defined in
`management/docker-compose.yml` alongside `mysql`, gated behind the `benchmark` docker-compose
profile so day-to-day `make -C management up` (the `dev` profile) stays mysql-only —
`make -C management up-bench`/`down` starts and stops the full stack together. Config lives here,
versioned, and is bind-mounted straight into the containers, so editing a file here and restarting
(or, for dashboards, just waiting ~10s) is the whole workflow — no host-machine state to keep in
sync.

```
observability/
├── prometheus.yml                  scrape configs: prometheus, spring-boot-app (host.docker.internal:8081), mysqld-exporter
├── mysqld-exporter-grant.sql       one-time GRANT for the read-only 'exporter' MySQL user
├── mysqld_exporter.my.cnf.example  copy to mysqld_exporter.my.cnf (gitignored) with the real password
└── grafana/
    ├── datasources/prometheus.yaml  provisions the Prometheus datasource, uid=prometheus-benchmark
    └── dashboards/
        ├── provider.yaml            dashboard provisioning provider, 10s poll
        └── *.json                   the six dashboards
```

## Why the app isn't in this compose project

`management/` runs on the host via `./mvnw spring-boot:run`, not as a container — bench/'s k6
harness and the app's own dev loop both assume that. Prometheus reaches it at
`host.docker.internal:8081` instead of a container DNS name (`extra_hosts:
host.docker.internal:host-gateway` in `docker-compose.yml` makes this resolve on Linux too, not
just Docker Desktop).

## Prerequisites

- `make -C management up-bench` — brings up `mysql`, `mysqld-exporter`, `prometheus`, and
  `grafana` together (`docker compose --profile benchmark up -d`; plain `make -C management up`
  activates the `dev` profile instead and starts mysql only).
- `bench/observability/mysqld_exporter.my.cnf` must exist before `mysqld-exporter` will start
  cleanly (see below) — copy it from `mysqld_exporter.my.cnf.example` and match the password to
  `mysqld-exporter-grant.sql`.
- The API run with `-Dspring-boot.run.profiles=benchmark` so `management.server.port=8081` opens
  `/actuator/prometheus` on its own, unauthenticated embedded connector (see
  `management/src/main/resources/application-benchmark.properties` and the comment there tying
  this back to PM-029 / `shared/security/SecurityConfig.java` — the `:8080/actuator/**`
  `SYSTEM_ADMINISTRATOR` gate is untouched; `SecurityConfig#managementPortFilterChain` permits
  actuator traffic only when it physically arrives on `:8081`, so the port split is what actually
  does the work, not just where the exposure config points).

## One-time setup

1. **mysqld-exporter's credentials** — the grant SQL creates the MySQL-side user; the `.my.cnf`
   file is what the container authenticates with:
   ```sh
   docker exec -i management-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < bench/observability/mysqld-exporter-grant.sql
   cp bench/observability/mysqld_exporter.my.cnf.example bench/observability/mysqld_exporter.my.cnf
   ```
   (Only needed once per machine — `mysqld_exporter.my.cnf` is gitignored and persists across
   `docker compose` restarts.)

2. **Bring everything up**:
   ```sh
   make -C management up-bench
   ```
   Grafana provisions the Prometheus datasource and all six dashboards (under a "Benchmark"
   folder) automatically on first start.

## Why containers instead of the earlier Homebrew setup

This used to run Prometheus and Grafana as `brew services` on the host. Moved to docker-compose so
the whole stack (`mysql`, `mysqld-exporter`, `prometheus`, `grafana`) starts and stops as one unit
with `make -C management up-bench`/`down`, with no local machine config (`grafana.ini`,
`prometheus.args`) to hand-edit or lose on a reinstall. mysqld-exporter was already a container (no
Homebrew formula exists for it); this just brought the other two in line with it. If you still have
the old brew services running, stop them (`brew services stop prometheus grafana`) — otherwise both
setups will fight over `:9090`/`:3000`.

## Verifying

- `docker compose ps` — `mysql`, `mysqld-exporter`, `prometheus`, `grafana` all `Up` (`mysql`
  `healthy`).
- `http://localhost:9090/targets` — `prometheus`, `spring-boot-app`, and `mysqld-exporter` all `UP`.
- `curl localhost:8081/actuator/prometheus` — metrics, no auth. `curl -i
  localhost:8080/actuator/prometheus` — still 401/403, unchanged.
- `http://localhost:3000` (admin/admin) — the "Benchmark" folder has all six dashboards.

## k6 → Prometheus

k6 itself still runs on the host (`brew install k6`, per `bench/README.md`), pushing to Prometheus
over its published host port. `bench/Makefile`'s `k6 run` invocations add `--out
experimental-prometheus-rw` (toggle with `PROMETHEUS_RW=0` if Prometheus isn't running), pushing to
`http://localhost:9090/api/v1/write`. This is additive to the existing `--summary-export` JSON
files — `make -C bench bench-report` is unaffected.

The HTTP & Load Testing dashboard's queries were written against k6 v2.2.0's actual
`experimental-prometheus-rw` output, confirmed empirically (not guessed) against a real run:
`k6_vus`, `k6_http_reqs_total` (with a `status` label), and `k6_http_req_{duration,sending,
receiving,waiting}_p50/p95/p99` gauges — the trend stats list is set via
`K6_PROMETHEUS_RW_TREND_STATS=p(50),p(95),p(99)` in `bench/Makefile` (k6's own default is p99
only). If a k6 upgrade changes this mapping, check
`http://localhost:9090/api/v1/label/__name__/values` during a run and adjust
`grafana/dashboards/http-load-testing.json` accordingly — Grafana re-reads it within 10s of a save,
no restart needed.
