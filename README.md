# automigrate

[![CI](https://github.com/abogoyavlensky/automigrate/actions/workflows/checks.yaml/badge.svg?branch=master)](https://github.com/abogoyavlensky/automigrate/actions/workflows/checks.yaml)

Database auto-migration tool for Clojure. Define models as plain edn data structures, 
and create database schema migrations based on changes of the models automatically.


## Features

- define db schema as **models** in edn **declaratively**;
- **automatically migrate** db schema based on models' changes;
- ability to add raw SQL migration for specific cases or data migrations;
- view raw SQL for any migration;
- migrate to any migration in forward and backward *[:construction: under development]* directions;
- support for PostgreSQL *[:construction: others are under development]*;

## State

Project is in alpha state till the `1.0.0` version, and it is not intended for production use for now. 
Breaking changes are possible.


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

```shell
clojure -X:migrations :cmd :make-migration
```

### Getting started

After configuration, you could create models.edn file with first model, 
make migration for it and migrate db schema. Let's do ti step by step.

#### Add model

*resources/db/models.edn*
```clojure
{:book [[:id :serial {:unique true
                      :primary-key true}]
        [:name [:varchar 256] {:null false}]
        [:description :text]]}
```

#### Make migration
```shell
$ clojure -X:migrations :cmd :make-migration
Created migration: resources/db/migrations/0001_auto_create_table_book.edn
Actions:
  - create table book
```

And the migration at `resources/db/migrations/0001_auto_create_table_book.edn` will look like:
```clojure
({:action :create-table,
  :model-name :book,
  :fields
  {:id {:unique true, :primary-key true, :type :serial},
   :name {:null false, :type [:varchar 256]},
   :description {:type :text}}})

```

#### Migrate
```shell
$ clojure -X:migrations :cmd :migrate
Migrating: 0001_auto_create_table_book...
Successfully migrated: 0001_auto_create_table_book
```

That's it, in db you could see newly created table called `book` with defined columns 
and one entry in model `automigrated_migrations` with new migration `0001_auto_create_table_book`.

#### List and explain migrations

To view status of existing migrations you could run:
```shell
$ clojure -X:migrations :cmd :list-migrations
[✓] 0001_auto_create_table_book.edn
```

To view raw SQL for existing migration you could run command `explain` with appropriate number: 
```shell
$ clojure -X:migrations :cmd :explain :number 1
SQL for migration 0001_auto_create_table_book.edn:

BEGIN;
CREATE TABLE book (id SERIAL UNIQUE PRIMARY KEY, name VARCHAR(256) NOT NULL, description TEXT);
COMMIT;
```

## Documentation

### CLI interface

Available commands as `:cmd` argument: `make-migration`, `migrate`, `list-migrations`, `explain`. Let's see it in detail by section.

:information_source: *For examples assume that args `:models-file`, `:migrations-dir` and `:jdbc-url` are set in deps.edn alias.*

Common args for all commands:

| Argument            | Description                                | Required?                                    | Possible values                               | Default value               |
|---------------------|--------------------------------------------|----------------------------------------------|-----------------------------------------------|-----------------------------|
| `:models-file`      | Path to models' file.                      | `true` (for `make-migration`)                | string path (example: `"path/to/models.edn"`) | *not provided*              |
| `:migrations-dir`   | Path to store migrations' files.           | `true`                                       | string path (example: `"path/to/migrations"`) | *not provided*              |
| `:jdbc-url`         | Database connection defined as JDBC-url.   | `true` (for `migrate` and `list-migrations`) |                                               |                             |
| `:migrations-table` | Model name for storing applied migrations. | `false`                                      | string (example: `"migrations"`)              | `"automigrated_migrations"` |

#### make-migration

Create migration for new changes in models' file.

*Specific args:*

| Argument          | Description                                              | Required?                                            | Possible values                               | Default value                                           |
|-------------------|----------------------------------------------------------|------------------------------------------------------|-----------------------------------------------|---------------------------------------------------------|
| `:type`           | Type of migration file.                                  | `false`                                              | `:empty-sql`                                  | *not provided*, migration will be created automatically |
| `:name`           | Custom name for migration file separated by underscores. | `false` *(:warning: required for `:empty-sql` type)* | string (example: `"add_custom_trigger"`)      | *generated automatically by first migration action*     |

##### Examples

Create migration automatically with auto-generated name:
```shell
$ clojure -X:migrations :cmd :make-migration
Created migration: resources/db/migrations/0001_auto_create_table_book.edn
Actions:
  ...
```

Create migration automatically with custom name:
```shell
$ clojure -X:migrations :cmd :make-migration :name create_table_author
Created migration: resources/db/migrations/0002_create_table_author.edn
Actions:
  ...
```

Create empty sql migration with custom name:
```shell
$ clojure -X:migrations :cmd :make-migration :type :empty-sql :name add_custom_trigger
Created migration: resources/db/migrations/0003_add_custom_trigger.sql
```

Try to create migration without new changes in models:
```shell
$ clojure -X:migrations :cmd :make-migration
There are no changes in models.
```


#### migrate

Apply changes described in migration to database.

:warning: *Backward migration is not fully implemented yet for auto-migrations, but already works for custom SQL migrations.
For auto-migrations it is possible to unapply migration, and appropriate entry will be deleted from migrations table. 
But database changes will not be unapplied for now.*

*Specific args:*

| Argument  | Description                                                                                                                                                                  | Required? | Possible values                                 | Default value                                 |
|-----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|-------------------------------------------------|-----------------------------------------------|
| `:number` | Number of migration which should be a target point. In forward direction migration by number will by applied. In backward direction migration by number will not be applied. | `false`   | integer (example: `1` for migration `0001_...`) | *not provided*, last migration + 1 by default |

##### Examples

Migrate forward all unapplied migrations:
```shell
$ clojure -X:migrations :cmd :migrate
Migrating: 0001_auto_create_table_book...
Successfully migrated: 0001_auto_create_table_book
Migrating: 0002_create_table_author...
Successfully migrated: 0002_create_table_author
Migrating: 0003_add_custom_trigger...
Successfully migrated: 0003_add_custom_trigger
```

Migrate forward up to particular migration number:
```shell
$ clojure -X:migrations :cmd :migrate :number 2
Migrating: 0001_auto_create_table_book...
Successfully migrated: 0001_auto_create_table_book
Migrating: 0002_create_table_author...
Successfully migrated: 0002_create_table_author
```

Migrate backward up to particular migration number:
```shell
$ clojure -X:migrations :cmd :migrate :number 1
Unapplying: 0002_create_table_author...
WARNING: backward migration isn't fully implemented yet. Database schema has not been changed!
Successfully unapplied: 0002_create_table_author
```

Migrate backward to initial state of database:
```shell
$ clojure -X:migrations :cmd :migrate :number 0
Unapplying: 0003_add_custom_trigger...
Successfully unapplied: 0003_add_custom_trigger
Unapplying: 0002_create_table_author...
WARNING: backward migration isn't fully implemented yet. Database schema has not been changed!
Successfully unapplied: 0002_create_table_author
Unapplying: 0001_auto_create_table_book...
WARNING: backward migration isn't fully implemented yet. Database schema has not been changed!
Successfully unapplied: 0001_auto_create_table_book
```

Try to migrate already migrated migrations:
```shell
$ clojure -X:migrations :cmd :migrate
Nothing to migrate.
```

Try to migrate up to not existing migration:
```shell
$ clojure -X:migrations :cmd :migrate :number 10
-- ERROR -------------------------------------

Invalid target migration number.
```

#### list-migrations

Print out list of existing migrations with statuses.

*No specific args:*

##### Examples:

View list of partially applied migrations:
```shell
$ clojure -X:migrations :cmd :list-migrations
[✓] 0001_auto_create_table_book.edn
[ ] 0002_create_table_author.edn
[ ] 0003_add_custom_trigger.sql
```

#### explain

Print out raw SQL for particular migration by number.

*Specific args:*

| Argument      | Description                                      | Required?    | Possible values                                 | Default value  |
|---------------|--------------------------------------------------|--------------|-------------------------------------------------|----------------|
| `:number`     | Number of migration which should be explained.   | `true`       | integer (example: `1` for migration `0001_...`) | *not provided* |
| `:direction`  | Direction in which migration should be explained | `false`      | `:forward`, `:backward`                         | `:forward`     |

##### Examples:

View raw SQL for migration in forward direction:
```shell
$ clojure -X:migrations :cmd :explain :number 1
SQL for migration 0001_auto_create_table_book.edn:

BEGIN;
CREATE TABLE book (id SERIAL UNIQUE PRIMARY KEY, name VARCHAR(256) NOT NULL, description TEXT);
COMMIT;
```

View raw SQL for migration in backward direction:
```shell
$ clojure -X:migrations :cmd :explain :number 1 :direction :backward
SQL for migration 0001_auto_create_table_book.edn:

WARNING: backward migration isn't fully implemented yet.
```


### Model definition

Models it is a map with model name as keyword key. Model's definition could be a vector of vectors in simple case to define just fields.
As we saw in previous example:

```clojure
{:book [[:id :serial {:unique true
                      :primary-key true}]
        [:name [:varchar 256] {:null false}]
        [:description :text]]}
```

Or it could be a map with two keys `:fields` and optional `:indexes`. Each is a vector of vectors too. 
Same model could be described as a map:
```clojure
{:book {:fields [[:id :serial {:unique true
                      :primary-key true}]
                [:name [:varchar 256] {:null false}]
                [:description :text]]}}
```

#### Fields

Each field is a vector of three elements: `[:field-name :field-type {:some-optoin :option-value}]`. 
Third element is optional, but name and type are required.

First element is a name and must be a keyword.

##### Field types
Second element could be a keyword or a vector of keyword and integer. 
Available field types are presented in following table:

| Field type             | Description                                                         |
|------------------------|---------------------------------------------------------------------|
| `:integer`             |                                                                     |
| `:smallint`            |                                                                     |
| `:bigint`              |                                                                     |
| `:float`               |                                                                     |
| `:real`                |                                                                     |
| `:serial`              |                                                                     |
| `:uuid`                |                                                                     |
| `:boolean`             |                                                                     |
| `:text`                |                                                                     |
| `:timestamp`           |                                                                     |
| `:date`                |                                                                     |
| `:time`                |                                                                     |
| `:point`               |                                                                     |
| `:json`                |                                                                     |
| `:jsonb`               |                                                                     |
| `[:varchar <pos-int>]` | second element is the length of value                               |
| `[:char <pos-int>]`    | second element is the length of value                               |
| `[:float <pos-int>]`   | second element is the minimum acceptable precision in binary digits |

##### Field options

Options value is a map where key is name of the option and value is available option value. All options are optional.  
Available options are presented in table:

| Field option   | Description                                                                        | Value                                                                                                        |
|----------------|------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|
| `:null`        | Set to `false` for not nullable field. Field nullable by default If it is not set. | `boolean?`                                                                                                   |
| `:primary-key` | Set to `true` for making primary key field.                                        | `true?`                                                                                                      |
| `:unique`      | Set to `true` to add unique constraint for a field.                                | `true?`                                                                                                      |
| `:default`     | Default value for a field.                                                         | `boolean?`, `integer?`, `float?`, `string?`, `nil?`, or fn defined as `[:keyword <integer? float? string?>]` |
| `:foreign-key` | Set to namespaced keyword to point a primary key field from another model.         | `:another-model/field-name`                                                                                  |
| `:on-delete`   | Specify delete action for `:foreign-key`.                                          | `:cascade`, `:set-null`, `:set-default`, `:restrict`, `:no-action`                                           |
| `:on-update`   | Specify update action for `:foreign-key`.                                          |                                                                                                              |


#### Indexes

Each index is a vector of three elements: `[:name-of-index :type-of-index {:fields [:field-from-model-to-index] :unique boolean?}]`

First element is a name and must be a keyword.

##### Index types

Second element must be a keyword of available index types are presented in following table:

| Field type | Description |
|------------|-------------|
| `:btree`   |             |
| `:gin`     |             |
| `:gist`    |             |
| `:spgist`  |             |
| `:brin`    |             |
| `:hash`    |             |

##### Index options

Options value is a map where key is name of the option and value is available option value. 
Option `:fields` is required others are optional (for now there is just `:unique`).  
Available options are presented in table:


| Field option | Description                                                          | Value               |
|--------------|----------------------------------------------------------------------|---------------------|
| `:fields`    | Vector of fields as keywords. Index will be created for that fields. | [`:field-name` ...] |
| `:unique`    | Set to `true` if index should be unique.                             | `true?`             |


### Custom SQL migration 

There are some specific cases which is not supported by auto-migrations for a while. 
Or there are cases when you need to add simple data migration.
You can add a custom SQL migration which contains a raw SQL for forward and backward direction separately in single sql-file.
For that you could run command for making empty sql migration with custom name:

```shell
$ clojure -X:migrations :cmd :make-migration :type :empty-sql :name make_all_accounts_active
Created migration: resources/db/migrations/0003_make_all_accounts_active.sql
```

Newly created file will look like:
```sql
-- FORWARD


-- BACKWARD

```

You could fill it with two block of queries for forward and backward migration. For example:
```sql
-- FORWARD

UPDATE account
SET is_active = true;

-- BACKWARD

UPDATE account
SET is_active = false;

```

Then migrate it as usual:
```shell
$ clojure -X:migrations :cmd :migrate
Migrating: 0003_make_all_accounts_active...
Successfully migrated: 0003_make_all_accounts_active
```

### Other details

- for now auto-migration is supported for: tables, columns, indexes;
- each migration is wrapped by transaction; 

## Roadmap draft

- [ ] Support backward auto-migration.
- [ ] Support custom migration using Clojure.
- [ ] Support running with Leiningen.
- [ ] Expand fields support.
- [ ] Support array fields in PostgreSQL.
- [ ] Support enum field type.
- [ ] Support comment for field.
- [ ] Handle project and resources paths properly.
- [ ] Test against different versions of db and Clojure.
- [ ] Improve error messages.
- [ ] Support for SQLite and MySQL.
- [ ] Use spec conformers more idiomatically without transformations.


### Things still in design

- Should args `:models-file` and `:migrations-dir` be set by default?
- Should it be possible to set arg `:jdbc-url` as an env var?
- Should commands be separated by different functions instead of `:cmd` arg?
- How to handle common configuration conveniently?
- More consistent and proper way for printing messages for users.


## Development

### Run locally

```shell
make up  # run docker-compose with databases for development
make repl  # run builtin repl with dev aliases; also you could use any repl you want
make test  # run whole tests locally against testing database started by docker-compose
make fmt  # run formatting in action mode
make lint  # run linting
make check-deps  # run checking new versions of deps in force mode
```

### Release new version

```shell
make install-snapshot :patch  # build and install locally a new version of lib based on latest git tag and using semver
make release :patch  # bump git tag version by semver rules and push to remote repo
```

*In CI there is the GitHub action which publish any new git tag as new version of the lib to Clojars.*


## License

Copyright © 2021 Andrey Bogoyavlensky

Distributed under the MIT License.
