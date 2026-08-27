# Backend infrastructure -- the MySQL container the app runs against. Split out of the former
# root Makefile so each toolchain (management/, bench/, docs-v00/) owns its own targets. Invoke
# from the repo root as `make -C management <target>`, or `cd management && make <target>`.
#
# .env and docker-compose.yml stay at the repo root (docker-compose.yml has no bind mounts, so
# nothing else needs to move with them); every path below is relative to this Makefile's
# directory, one level under the repo root.

-include ../.env
export

DC := docker compose -f ../docker-compose.yml --project-directory ..
MYSQL_USER ?= management
MYSQL_DATABASE ?= management
CONTAINER := management-mysql

.PHONY: help env up down_temp down restart logs ps mysql clean reset

help:
	@echo "make up       - start the mysql container (creates .env from .env.example if missing)"
	@echo "make down     - stop the mysql container"
	@echo "make restart  - restart the mysql container"
	@echo "make logs     - tail mysql logs"
	@echo "make ps       - show container status"
	@echo "make mysql    - open a mysql shell inside the container"
	@echo "make clean    - stop the container and delete its data volume"
	@echo "make reset    - clean + up (fresh database)"

env:
	@test -f ../.env || cp ../.env.example ../.env

up: env
	colima start
	$(DC) up -d
	@echo "MySQL is starting on port $${MYSQL_PORT:-3306}..."

down_temp:
	$(DC) down

down:
	$(DC) down
	colima stop

restart: down up

logs:
	$(DC) logs -f mysql

ps:
	$(DC) ps

mysql: env
	docker exec -it $(CONTAINER) mysql -u$(MYSQL_USER) -p$(MYSQL_PASSWORD) $(MYSQL_DATABASE)

clean:
	$(DC) down -v

reset: clean up
