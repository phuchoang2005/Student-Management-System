# Backend infrastructure -- the MySQL container the app runs against. Split out of the former
# root Makefile so each toolchain (management/, bench/, docs-v00/) owns its own targets. Invoke
# from the repo root as `make -C management <target>`, or `cd management && make <target>`.
#
# docker-compose.yml lives here (it's backend infrastructure); .env stays at the repo root.
# --project-directory .. is still required: the compose file's bind mounts (bench/observability/*)
# are written relative to the repo root, not to this Makefile's directory.

-include ../.env
export

DC := docker compose -f docker-compose.yml --project-directory ..
# docker-compose.yml gates mysqld-exporter/prometheus/grafana behind the "benchmark" profile, with
# mysql in both -- so teardown targets activate every profile to reap whichever stack (dev-only or
# full benchmark) happens to be running; `docker compose down` with no --profile only touches
# services that declare no profile at all and would otherwise orphan a running benchmark stack.
DC_ALL_PROFILES := $(DC) --profile dev --profile benchmark
MYSQL_USER ?= management
MYSQL_DATABASE ?= management
CONTAINER := management-mysql

.PHONY: help env up up-bench down_temp down restart restart-bench logs ps mysql clean reset

help:
	@echo "make up            - start mysql only (creates .env from .env.example if missing)"
	@echo "make up-bench      - start mysql + mysqld-exporter + prometheus + grafana (see bench/observability/README.md)"
	@echo "make down          - stop everything and stop colima"
	@echo "make restart       - restart the mysql-only stack"
	@echo "make restart-bench - restart the full benchmark stack"
	@echo "make logs          - tail mysql logs"
	@echo "make ps            - show container status"
	@echo "make mysql         - open a mysql shell inside the container"
	@echo "make clean         - stop everything and delete data volumes"
	@echo "make reset         - clean + up (fresh database)"

env:
	@test -f ../.env || cp ../.env.example ../.env

up: env
	colima start --memory 4
	$(DC) --profile dev up -d
	@echo "MySQL is starting on port $${MYSQL_PORT:-3306}..."

up-bench: env
	colima start --memory 4
	$(DC) --profile benchmark up -d
	@echo "MySQL + observability stack (mysqld-exporter, prometheus, grafana) starting -- see bench/observability/README.md"

down_temp:
	$(DC_ALL_PROFILES) down

down:
	$(DC_ALL_PROFILES) down
	colima stop

restart: down up

restart-bench: down up-bench

logs:
	$(DC) logs -f mysql

ps:
	$(DC) ps

mysql: env
	docker exec -it $(CONTAINER) mysql -u$(MYSQL_USER) -p$(MYSQL_PASSWORD) $(MYSQL_DATABASE)

clean:
	$(DC_ALL_PROFILES) down -v

reset: clean up
