# Styling for output
YELLOW := "\e[1;33m"
NC := "\e[0m"
INFO := @sh -c '\
    printf $(YELLOW); \
    echo "=> $$1"; \
    printf $(NC)' VALUE


DIRS?=src test
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


.PHONY: build  # Build a deployable jar
build:
	@$(INFO) "Building jar..."
	@clojure -X:build


.PHONY: install  # Build and install package locally
install:
	@$(MAKE) build
	@$(INFO) "Installing jar locally..."
	@clojure -X:install


.PHONY: deploy  # Build and deploy package to Clojars
deploy:
	@$(MAKE) build
	@$(INFO) "Deploying jar to Clojars..."
	@clojure -X:deploy


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


.PHONY: check  # Check linting and apply formatting locally
check:
	@$(MAKE) lint
	@$(MAKE) fmt


.PHONY: test  # Run tests with coverage
test:
	@$(INFO) "Running tests..."
	@clojure -X:dev:test


# Docker-compose

.PHONY: up  # Run db
up:
	@$(INFO) "Running db..."
	@docker-compose up -d db adminer


.PHONY: up-test  # Run db
up-test:
	@$(INFO) "Running testing dbs..."
	@docker-compose up -d test-postgres


.PHONY: ps  # List docker containers
ps:
	@$(INFO) "List docker containers..."
	@docker-compose ps


.PHONY: stop  # Stop docker containers
stop:
	@$(INFO) "Stopping docker containers..."
	@docker-compose stop


# Testing commands

.PHONY: make-migrations  # Make testing migrations
migrations:
	@$(INFO) "Make testing migrations..."
	@clojure -A:dev -X:migrations :action :make-migrations


.PHONY: migrate  # Migrate testing migrations
migrate:
	@$(INFO) "Migrate testing migrations..."
	@clojure -A:dev -X:migrations :action :migrate


.PHONY: explain  # Print SQL for particular migration
explain:
	@$(INFO) "Explain testing migration..."
	@clojure -A:dev -X:migrations :action :explain :number $(GOALS)
