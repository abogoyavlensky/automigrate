# Styling for output
YELLOW := "\e[1;33m"
NC := "\e[0m"
INFO := @sh -c '\
    printf $(YELLOW); \
    echo "=> $$1"; \
    printf $(NC)' VALUE


DIRS?=src test build.clj
GOALS = $(filter-out $@,$(MAKECMDGOALS))

.SILENT:  # Ignore output of make `echo` command


.PHONY: help  # Show list of targets with descriptions
help:
	@$(INFO) "Commands:"
	@grep '^.PHONY: .* #' Makefile | sed 's/\.PHONY: \(.*\) # \(.*\)/\1 > \2/' | column -tx -s ">"


.PHONY: deps  # Install deps
deps:
	@$(INFO) "Install deps..."
	@clojure -P -X:test:dev


.PHONY: fmt-check  # Checking code formatting
fmt-check:
	@$(INFO) "Checking code formatting..."
	@cljstyle check --report $(DIRS)


.PHONY: fmt  # Fixing code formatting
fmt:
	@$(INFO) "Fixing code formatting..."
	@cljstyle fix --report $(DIRS)


.PHONY: lint  # Linting code
lint:
	@$(INFO) "Linting project..."
	@clj-kondo --config .clj-kondo/config-ci.edn --lint $(DIRS)


.PHONY: lint-init  # Linting code with libraries
lint-init:
	@$(INFO) "Linting project's classpath..."
	@clj-kondo --config .clj-kondo/config-ci.edn --lint $(shell clj -Spath)


.PHONY: check-deps  # Check deps versions
check-deps:
	@$(INFO) "Checking deps versions..."
	@clojure -M:check-deps


.PHONY: check  # Check linting and apply formatting locally
check:
	@$(MAKE) fmt
	@$(MAKE) lint


.PHONY: test  # Run tests with coverage
test:
	@$(INFO) "Running tests..."
	@clojure -X:dev:test


# Docker-compose

.PHONY: up  # Run db, testing db and db admin web UI locally for development
up:
	@$(INFO) "Running db..."
	@docker-compose up -d db adminer test-postgres


.PHONY: ps  # List docker containers
ps:
	@$(INFO) "List docker containers..."
	@docker-compose ps


.PHONY: stop  # Stop docker containers
stop:
	@$(INFO) "Stopping docker containers..."
	@docker-compose stop


# Testing commands

.PHONY: migrations  # Making migrations
migrations:
	@$(INFO) "Making migrations..."
	@clojure -A:dev -X:migrations :cmd :make-migration $(GOALS)


.PHONY: migrate  # Migrating migrations
migrate:
	@$(INFO) "Migrating..."
	@clojure -A:dev -X:migrations :cmd :migrate $(GOALS)


.PHONY: explain  # Print SQL for particular migration
explain:
	@$(INFO) "Explaining migration..."
	@clojure -A:dev -X:migrations :cmd :explain :number $(GOALS)


.PHONY: list  # Print migration's list
list:
	@$(INFO) "Migrations found..."
	@clojure -A:dev -X:migrations :cmd :list-migrations $(GOALS)


# Build and release

.PHONY: build  # Build a deployable jar
build:
	@$(INFO) "Building a jar..."
	@clojure -T:build build $(GOALS)


.PHONY: install  # Build and install package locally
install:
	@$(INFO) "Building a jar..."
	@clojure -T:build build $(GOALS)
	@$(INFO) "Installing a jar locally..."
	@clojure -T:build install $(GOALS)


# TODO: update!
#.PHONY: deploy  # Build and deploy package to Clojars
#deploy:
#	@$(MAKE) build
#	@$(INFO) "Deploying jar to Clojars..."
#	@clojure -X:deploy
