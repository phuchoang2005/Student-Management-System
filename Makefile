-include .env
export

DC := docker compose
MYSQL_USER ?= management
MYSQL_DATABASE ?= management
CONTAINER := management-mysql

.PHONY: help env up down restart logs ps mysql clean reset docs docs-watch docs-clean

help:
	@echo "make up       - start the mysql container (creates .env from .env.example if missing)"
	@echo "make down     - stop the mysql container"
	@echo "make restart  - restart the mysql container"
	@echo "make logs     - tail mysql logs"
	@echo "make ps       - show container status"
	@echo "make mysql    - open a mysql shell inside the container"
	@echo "make clean    - stop the container and delete its data volume"
	@echo "make reset    - clean + up (fresh database)"
	@echo "make docs     - compile docs/**/*.md to HTML (util/md-to-html.js)"
	@echo "make docs-watch - same, rebuilding on every save"
	@echo "make docs-clean - delete the generated HTML"

env:
	@test -f .env || cp .env.example .env

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

# --- Documentation -----------------------------------------------------------------
# docs/**/*.html is generated, not committed (docs/.gitignore). Sources are the .md
# files and the PlantUML .svg assets they inline.

docs:
	@test -d util/node_modules || (cd util && npm install --silent)
	node util/md-to-html.js

docs-watch:
	@test -d util/node_modules || (cd util && npm install --silent)
	node util/md-to-html.js --watch

docs-clean:
	node util/md-to-html.js --clean
