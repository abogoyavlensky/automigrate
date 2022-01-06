# automigrate

[![CI](https://github.com/abogoyavlensky/automigrate/actions/workflows/checks.yaml/badge.svg?branch=master)](https://github.com/abogoyavlensky/automigrate/actions/workflows/checks.yaml)

Database auto-migration tool for Clojure. Define models as plain edn data structures, 
and create database schema migrations based on changes of the models automatically.

## Features

- define db schema as **models** in edn **declaratively**;
- **automatically** update db schema **migrations** based on models' changes;
- create and update tables, columns and indexes without touching SQL;
- ability to add raw SQL migration for specific cases;
- view raw SQL for any migration;
- migrate to any migration in forward and backward *[:construction: under development]* directions.

## Usage

### Installation

### Getting started

### CLI interface

### Model definition



## Development

### Run locally

```bash
make up  # run docker-compose with databases for development
make repl  # run builtin repl with dev aliases; also you could use any repl you want
make test  # run whole tests locally against testing database started by docker-compose
make fmt  # run formatting in action mode
make lint  # run linting
make check-deps  # run checking new versions of deps in force mode
```

### Release new version

```bash
make install-snapshot :patch  # build and install locally a new version of lib based on latest git tag and using semver
make release :patch  # bump git tag version by semver rules and push to remote repo
```

*In CI there is the github action which publish any new git tag as new version of the lib to Clojars.*

## License

Copyright © 2021 Andrey Bogoyavlensky

Distributed under the MIT License.
