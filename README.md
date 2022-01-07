# automigrate

[![CI](https://github.com/abogoyavlensky/automigrate/actions/workflows/checks.yaml/badge.svg?branch=master)](https://github.com/abogoyavlensky/automigrate/actions/workflows/checks.yaml)

Database auto-migration tool for Clojure. Define models as plain edn data structures, 
and create database schema migrations based on changes of the models automatically.


## Features

- define db schema as **models** in edn **declaratively**;
- **automatically migrate** db schema based on models' changes;
- create and update tables, columns and indexes without touching SQL;
- ability to add raw SQL migration for specific cases;
- view raw SQL for any migration;
- migrate to any migration in forward and backward *[:construction: under development]* directions;
- support for PostgreSQL *[:construction: others are under development]*;


## Usage

### Installation

**TODO:** add release label from Clojars! 

A config for development environment could look like following example.

#### tools.deps

*deps.edn*

```clojure
{:deps {org.clojure/clojure {:mvn/version "1.10.3"}
        org.postgresql/postgresql {:mvn/version "42.3.1"}}
 
 :aliases {:migrations {:extra-deps {net.clojars.abogoyavlensky/automigrate {:mvn/version "RELEASE"}}
                        :exec-fn automigrate.core/run
                        :exec-args {:models-file "src/resources/db/models.edn"
                                    :migrations-dir "src/resources/db/migrations"
                                    :jdbc-url "jdbc:postgresql://localhost:5432/mydb?user=myuser&password=secret"}}
           ...}
 ...}
```

Then you could use it as:

```bash
clojure -X:migrations :cmd :make-migration
```

### Getting started

After configuration you could create models.edn file with first model, 
make migration for it and migrate db schema.

#### Add model

*resources/db/models.edn*
```clojure
{:book [[:id :serial {:unique true
                      :primary-key true}]
        [:name [:varchar 256] {:null false}]
        [:description :text]]}
```

#### Make migration
```bash
$ clojure -X:migrations :cmd :make-migration
Created migration: resources/db/migrations/0001_auto_create_table_book.edn
Actions:
  - create table book
```

And the migration at `resources/db/migrations/0001_auto_create_table_book.edn` looks like:
```clojure
({:action :create-table,
  :model-name :book,
  :fields
  {:id {:unique true, :primary-key true, :type :serial},
   :name {:null false, :type [:varchar 256]},
   :description {:type :text}}})

```

#### Migrate
```bash
$ clojure -X:migrations :cmd :migrate
Migrating: 0001_auto_create_table_book...
Successfully migrated: 0001_auto_create_table_book
```

That's it, in db you could see newly created table called `book` with defined columns 
and one entry in model `automigrated_migrations` with new migration `0001_auto_create_table_book`.

#### Observation commands

To view status of existing migrations you could run:
```bash
$ clojure -X:migrations :cmd :list-migrations
[✓] 0001_auto_create_table_book.edn
```

To view raw SQL for existing migration you could run command `explain` with appropriate number: 
```bash
$ clojure -X:migrations :cmd :explain :number 1
SQL for migration 0001_auto_create_table_book.edn:

BEGIN;
CREATE TABLE book (id SERIAL UNIQUE PRIMARY KEY, name VARCHAR(256) NOT NULL, description TEXT);
COMMIT;
```

### CLI interface

### Model definition

### Raw SQL migration 


## Roadmap draft

- [ ] Support backward auto-migration.
- [ ] Support custom migration using Clojure.
- [ ] Support for SQLite and MySQL.
- [ ] Support running with Leiningen.
- [ ] Handle project and resources paths properly.
- [ ] Test against different versions of db and Clojure.
- [ ] Use spec conformers more idiomatically without transformations.
- [ ] Improve error messages. 


### Things still in designing

- Should args `:models-file` and `:migrations-dir` be set by default?
- Should it be possible to set arg `:jdbc-url` as an env var?
- Should commands be separated by different functions instead of `:cmd` arg?
- How to handle configuration of model and migration paths with `-T` option of `tools.deps`?
- More consistent and proper way for printing messages for users.


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
