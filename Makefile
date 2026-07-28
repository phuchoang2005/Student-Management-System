-include .env
export

DC := docker compose
POSTGRES_USER ?= management
POSTGRES_DB ?= management
CONTAINER := management-postgres

.PHONY: help env up down restart logs ps psql clean reset

help:
	@echo "make up       - start the postgres container (creates .env from .env.example if missing)"
	@echo "make down     - stop the postgres container"
	@echo "make restart  - restart the postgres container"
	@echo "make logs     - tail postgres logs"
	@echo "make ps       - show container status"
	@echo "make psql     - open a psql shell inside the container"
	@echo "make clean    - stop the container and delete its data volume"
	@echo "make reset    - clean + up (fresh database)"

env:
	@test -f .env || cp .env.example .env

up: env
	colima start
	$(DC) up -d
	@echo "Postgres is starting on port $${POSTGRES_PORT:-5432}..."

down_temp:
	$(DC) down

down:
	$(DC) down
	colima stop

restart: down up

logs:
	$(DC) logs -f postgres

ps:
	$(DC) ps

psql: env
	docker exec -it $(CONTAINER) psql -U $(POSTGRES_USER) -d $(POSTGRES_DB)

clean:
	$(DC) down -v

reset: clean up
