# Documentation compiler -- docs/**/*.md -> .html via ../util/md-to-html.js. Split out of the
# former root Makefile so each toolchain (management/, bench/, docs/) owns its own targets.
# Invoke from the repo root as `make -C docs <target>`, or `cd docs && make <target>`.
#
# The .html twins are generated and gitignored (docs/.gitignore) -- never edit them, edit the
# .md. Every path below is relative to this Makefile's directory, one level under the repo root.

.PHONY: help docs docs-watch docs-clean

help:
	@echo "make docs       - compile docs/**/*.md to HTML (../util/md-to-html.js)"
	@echo "make docs-watch - same, rebuilding on every save"
	@echo "make docs-clean - delete the generated HTML"

docs:
	@test -d ../util/node_modules || (cd ../util && npm install --silent)
	node ../util/md-to-html.js

docs-watch:
	@test -d ../util/node_modules || (cd ../util && npm install --silent)
	node ../util/md-to-html.js --watch

docs-clean:
	node ../util/md-to-html.js --clean
