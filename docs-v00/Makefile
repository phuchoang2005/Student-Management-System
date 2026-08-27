# Documentation compiler -- docs-v00/**/*.md -> .html via ../util/md-to-html.js. Split out of the
# former root Makefile so each toolchain (management/, bench/, docs-v00/) owns its own targets.
# Invoke from the repo root as `make -C docs-v00 <target>`, or `cd docs-v00 && make <target>`.
#
# The .html twins are generated and gitignored (docs-v00/.gitignore) -- never edit them, edit the
# .md. Every path below is relative to this Makefile's directory, one level under the repo root.

.PHONY: help docs docs-watch docs-clean

help:
	@echo "make docs       - compile docs-v00/**/*.md to HTML (../util/md-to-html.js)"
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
