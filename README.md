# automigrate

[![CI](https://github.com/abogoyavlensky/automigrate/actions/workflows/checks.yaml/badge.svg?branch=master)](https://github.com/abogoyavlensky/automigrate/actions/workflows/checks.yaml)

Database auto-migration tool for Clojure. Define models as plain EDN data structures 
and create database schema migrations automatically based on changes to the models.


## Features

- define db schema as **models** in EDN **declaratively**;
- **automatically migrate** db schema based on model changes;
- view raw SQL for any migration;
- ability to add raw SQL migration for specific cases or data migrations;
- migrate to any migration in forward and backward *[:construction: under development]* directions;
- use with PostgreSQL *[:construction: others are under development]*;

## State

Project is in **alpha** state till the `1.0.0` version and is not yet ready for production use. 
Breaking changes are possible.


## Usage

### Installation

[![Clojars Project](https://img.shields.io/clojars/v/net.clojars.abogoyavlensky/automigrate.svg)](https://clojars.org/net.clojars.abogoyavlensky/automigrate)

#### tools.deps -X option

A config for development environment could look like the following:

*deps.edn*
```clojure
{...
 :aliases {...
           :migrations {:extra-deps {net.clojars.abogoyavlensky/automigrate {:mvn/version "<LATEST VERSION>"}
                                     org.postgresql/postgresql {:mvn/version "42.3.1"}}
                        :ns-default automigrate.core
                        :exec-args {:models-file "resources/db/models.edn"
                                    :migrations-dir "resources/db/migrations"
                                    :jdbc-url "jdbc:postgresql://localhost:5432/mydb?user=myuser&password=secret"}}}}
```

:information_source: *You can move postgres driver to project's `:deps` section.
You can choose any paths you want for `:models-file` and `:migrations-dir`.* 

Then you could use it as:

```shell
clojure -X:migrations make
```

#### tools.deps -T option

Alternatively you can use the following alias with `-T` option (*from clojure tools cli version >= `1.10.3.933`*).
The difference is that the project's `:deps` is not included for running migrations.

*deps.edn*
```clojure
{...
 :aliases {...
           :migrations {:deps {net.clojars.abogoyavlensky/automigrate {:mvn/version "<LATEST VERSION>"}
                               org.postgresql/postgresql {:mvn/version "42.3.1"}}
                        :ns-default automigrate.core
                        :exec-args {:models-file "resources/db/models.edn"
                                    :migrations-dir "resources/db/migrations"
                                    :jdbc-url "jdbc:postgresql://localhost:5432/mydb?user=myuser&password=secret"}}}}
```

You can then use it as:

```shell
clojure -T:migrations make
```

#### Leiningen

[:construction: *Leiningen support is under development.*]

### Getting started

After configuration, you are able to create `models.edn` file with first model, 
make migration for it and migrate db schema. Paths for migrations and models can be chosen as you want.
A model is the representation of a database table which is described in EDN structure.
Let's do it step by step.

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
$ clojure -X:migrations make
Created migration: resources/db/migrations/0001_auto_create_table_book.edn
Actions:
  - create table book
```

Migration can contain multiple actions. Every action is converted to a SQL query.
The migration at `resources/db/migrations/0001_auto_create_table_book.edn` will look like:

```clojure
({:action :create-table,
  :model-name :book,
  :fields
  {:id {:unique true, :primary-key true, :type :serial},
   :name {:null false, :type [:varchar 256]},
   :description {:type :text}}})
```

#### Migrate
Existing migrations will be applied one by one in order of migration number:

```shell
$ clojure -X:migrations migrate
Migrating: 0001_auto_create_table_book...
Successfully migrated: 0001_auto_create_table_book
```

That's it. In the database you can see a newly created table called `book` with defined columns 
and one entry in table `automigrate_migrations` with new migration `0001_auto_create_table_book`.

#### List and explain migrations

To view status of existing migrations you can run:
```shell
$ clojure -X:migrations list
Existing migrations:

[✓] 0001_auto_create_table_book.edn
```

To view raw SQL for existing migration you can run command `explain` with appropriate number: 

```shell
$ clojure -X:migrations explain :number 1
SQL for migration 0001_auto_create_table_book.edn:

BEGIN;
CREATE TABLE book (id SERIAL UNIQUE PRIMARY KEY, name VARCHAR(256) NOT NULL, description TEXT);
COMMIT;
```

All SQL queries of the migration are wrapped by a transaction.

:information_source: *For a slightly more complex example please check [models.edn](/examples/models.edn)
and [README.md](/examples/README.md) from the `examples` dir of this repo.* 

## Documentation

### Model definition

Models are represented as a map with the model name as a keyword key and the value describing the model itself.
A model's definition could be a vector of vectors in the simple case of just defining fields.
As we saw in the previous example:

```clojure
{:book [[:id :serial {:unique true
                      :primary-key true}]
        [:name [:varchar 256] {:null false}]
        [:description :text]]}
```

Or it could be a map with two keys `:fields` and (*optional*) `:indexes`. Each of these is also a vector of vectors. 
The same model from above could be described as a map:

```clojure
{:book {:fields [[:id :serial {:unique true
                               :primary-key true}]
                [:name [:varchar 256] {:null false}]
                [:description :text]]}}
```

#### Fields

Each field is a vector of three elements: `[:field-name :field-type {:some-option :option-value}]`. 
The third element is optional, but name and type are required.

The first element is the name of a field and must be a keyword.

##### Field types
The second element could be a keyword or a vector of keyword and integer. 
Available field types are presented in the following table:

| Field type             | Description                                                         |
|------------------------|---------------------------------------------------------------------|
| `:integer`             |                                                                     |
| `:smallint`            |                                                                     |
| `:bigint`              |                                                                     |
| `:float`               |                                                                     |
| `:real`                |                                                                     |
| `:serial`              | auto-incremented integer field                                      |
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

:information_source: *There are fixed field types because `automigrate` 
validates type of field and default value to have errors as early as possible 
before running migration against database.*

##### Field options

Options value is a map where key is the name of the option and value is the available option value.
Available options are presented in the table below:

| Field option   | Description                                                                                   | Required? | Value                                                                                                        |
|----------------|-----------------------------------------------------------------------------------------------|-----------|--------------------------------------------------------------------------------------------------------------|
| `:null`        | Set to `false` for non-nullable field. Field is nullable by default if the option is not set. | `false`   | `boolean?`                                                                                                   |
| `:primary-key` | Set to `true` for making primary key field.                                                   | `false`   | `true?`                                                                                                      |
| `:unique`      | Set to `true` to add unique constraint for a field.                                           | `false`   | `true?`                                                                                                      |
| `:default`     | Default value for a field.                                                                    | `false`   | `boolean?`, `integer?`, `float?`, `string?`, `nil?`, or fn defined as `[:keyword <integer? float? string?>]` |
| `:foreign-key` | Set to namespaced keyword to point to a primary key field from another model.                 | `false`   | `:another-model/field-name`                                                                                  |
| `:on-delete`   | Specify delete action for `:foreign-key`.                                                     | `false`   | `:cascade`, `:set-null`, `:set-default`, `:restrict`, `:no-action`                                           |
| `:on-update`   | Specify update action for `:foreign-key`.                                                     | `false`   |                                                                                                              |


#### Indexes

Each index is a vector of three elements: `[:name-of-index :type-of-index {:fields [:field-from-model-to-index] :unique boolean?}]`
Name, type and `:fields` in options are required.

The first element is the name of an index and must be a keyword.

##### Index types

The second element is an index type and must be a keyword of available index types:

| Field type |
|------------|
| `:btree`   |
| `:gin`     |
| `:gist`    |
| `:spgist`  |
| `:brin`    |
| `:hash`    |

##### Index options

The options value is a map where key is the name of the option and value is the available option value.
The option `:fields` is required, others are optional (*for now there is just `:unique` is optional*).  
Available options are presented in the table below:


| Field option | Description                                                           | Required? | Value               |
|--------------|-----------------------------------------------------------------------|-----------|---------------------|
| `:fields`    | Vector of fields as keywords. Index will be created for those fields. | `true`    | [`:field-name` ...] |
| `:unique`    | Set to `true` if index should be unique.                              | `false`   | `true?`             |


### CLI interface

Available commands are: `make`, `migrate`, `list`, `explain`. Let's see them in detail by section.

:information_source: *Assume that args `:models-file`, `:migrations-dir` and `:jdbc-url` are supposed to be set in deps.edn alias.*

Common args for all commands:

| Argument            | Description                                | Required?                              | Possible values                                                                                  | Default value              |
|---------------------|--------------------------------------------|----------------------------------------|--------------------------------------------------------------------------------------------------|----------------------------|
| `:models-file`      | Path to models file.                       | `true` (only for `make`)               | string path (example: `"path/to/models.edn"`)                                                    | *not provided*             |
| `:migrations-dir`   | Path to store migrations dir.              | `true`                                 | string path (example: `"path/to/migrations"`)                                                    | *not provided*             |
| `:jdbc-url`         | Database connection defined as JDBC-url.   | `true` (only for `migrate` and `list`) | string jdbc url (example: `"jdbc:postgresql://localhost:5432/mydb?user=myuser&password=secret"`) | *not provided*             |
| `:migrations-table` | Model name for storing applied migrations. | `false`                                | string (example: `"migrations"`)                                                                 | `"automigrate_migrations"` |

#### `make`

Create migration for new changes in models file.
It detects the creating, updating and deleting of tables, columns and indexes.
Each migration is wrapped by transaction by default.

*Specific args:*

| Argument          | Description                                              | Required?                                            | Possible values                               | Default value                                           |
|-------------------|----------------------------------------------------------|------------------------------------------------------|-----------------------------------------------|---------------------------------------------------------|
| `:type`           | Type of migration file.                                  | `false`                                              | `:empty-sql`                                  | *not provided*, migration will be created automatically |
| `:name`           | Custom name for migration file separated by underscores. | `false` *(:warning: required for `:empty-sql` type)* | string (example: `"add_custom_trigger"`)      | *generated automatically by first migration action*     |

##### Examples

Create migration automatically with auto-generated name:

```shell
$ clojure -X:migrations :make
Created migration: resources/db/migrations/0001_auto_create_table_book.edn
Actions:
  ...
```

Create migration automatically with custom name:

```shell
$ clojure -X:migrations make :name create_table_author
Created migration: resources/db/migrations/0002_create_table_author.edn
Actions:
  ...
```

Create empty sql migration with custom name:

```shell
$ clojure -X:migrations make :type :empty-sql :name add_custom_trigger
Created migration: resources/db/migrations/0003_add_custom_trigger.sql
```

Try to create migration without new changes in models:

```shell
$ clojure -X:migrations make
There are no changes in models.
```


#### `migrate`

Applies changes described in migration to database.
Applies all unapplied migrations by number order if arg `:number` is not presented in command.
Throws error for same migration number.

:warning: *Backward migration is not yet fully implemented for auto-migrations, but already works for custom SQL migrations.
For auto-migrations, it is possible to unapply migration and to delete appropriate entry from migrations table.
But database changes will not be unapplied for now.*

*Specific args:*

| Argument  | Description                                                                                                                                                                    | Required? | Possible values                                 | Default value                                    |
|-----------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|-------------------------------------------------|--------------------------------------------------|
| `:number` | Number of migration which should be a target point. In forward direction, migration by number will by applied. In backward direction, migration by number will not be applied. | `false`   | integer (example: `1` for migration `0001_...`) | *not provided*, last migration number by default |

##### Examples

Migrate forward all unapplied migrations:
```shell
$ clojure -X:migrations migrate
Migrating: 0001_auto_create_table_book...
Successfully migrated: 0001_auto_create_table_book
Migrating: 0002_create_table_author...
Successfully migrated: 0002_create_table_author
Migrating: 0003_add_custom_trigger...
Successfully migrated: 0003_add_custom_trigger
```

Migrate forward up to particular migration number (*included*):
```shell
$ clojure -X:migrations migrate :number 2
Migrating: 0001_auto_create_table_book...
Successfully migrated: 0001_auto_create_table_book
Migrating: 0002_create_table_author...
Successfully migrated: 0002_create_table_author
```

Migrate backward up to particular migration number (*excluded*):
```shell
$ clojure -X:migrations migrate :number 1
Unapplying: 0002_create_table_author...
WARNING: backward migration isn't fully implemented yet. Database schema has not been changed!
Successfully unapplied: 0002_create_table_author
```

Migrate backward to initial state of database:
```shell
$ clojure -X:migrations migrate :number 0
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
$ clojure -X:migrations migrate
Nothing to migrate.
```

Try to migrate up to not existing migration:
```shell
$ clojure -X:migrations migrate :number 10
-- ERROR -------------------------------------

Invalid target migration number.
```

#### `list`

Print out list of existing migrations with statuses displayed as
boxes before migration name:
- `[✓]` - applied;
- `[ ]` - not applied.

*No specific args.*

##### Examples:

View list of partially applied migrations:
```shell
$ clojure -X:migrations list
Existing migrations:

[✓] 0001_auto_create_table_book.edn
[ ] 0002_create_table_author.edn
[ ] 0003_add_custom_trigger.sql
```

#### `explain`

Print out actual raw SQL for particular migration by number.

*Specific args:*

| Argument      | Description                                       | Required?    | Possible values                                 | Default value  |
|---------------|---------------------------------------------------|--------------|-------------------------------------------------|----------------|
| `:number`     | Number of migration which should be explained.    | `true`       | integer (example: `1` for migration `0001_...`) | *not provided* |
| `:direction`  | Direction in which migration should be explained. | `false`      | `:forward`, `:backward`                         | `:forward`     |

##### Examples:

View raw SQL for migration in forward direction:
```shell
$ clojure -X:migrations explain :number 1
SQL for migration 0001_auto_create_table_book.edn:

BEGIN;
CREATE TABLE book (id SERIAL UNIQUE PRIMARY KEY, name VARCHAR(256) NOT NULL, description TEXT);
COMMIT;
```

View raw SQL for migration in backward direction:
```shell
$ clojure -X:migrations explain :number 1 :direction :backward
SQL for migration 0001_auto_create_table_book.edn:

WARNING: backward migration isn't fully implemented yet.
```

#### help/doc

You can print docstring for function in the default namespace of the lib by running clojure cli `help/doc` function.

Print doc for all available functions:

```shell
$ clojure -X:deps:migrations help/doc :ns automigrate.core
Public interface for lib's users.

-------------------------
...
```

Print doc for particular function:

```shell
$ clojure -X:deps:migrations help/doc :ns automigrate.core :fn make
-------------------------
automigrate.core/make
...
```


### Custom SQL migration 

There are some specific cases which are not yet supported by auto-migrations. 
There are cases when you need to add simple data migration.
You can add a custom SQL migration which contains raw SQL for forward and backward directions separately in single SQL-file.
For that you can run the following command for making empty SQL migration with custom name:

```shell
$ clojure -X:migrations make :type :empty-sql :name make_all_accounts_active
Created migration: resources/db/migrations/0003_make_all_accounts_active.sql
```

The newly created file will look like:

```sql
-- FORWARD


-- BACKWARD

```

You can fill it with two block of queries for forward and backward migration. For example:

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
$ clojure -X:migrations migrate
Migrating: 0003_make_all_accounts_active...
Successfully migrated: 0003_make_all_accounts_active
```


### Use in production

:warning: *The library is not yet ready for production use. 
But it is really appreciated if you try it out! :wink:*

For now, there is just a single way to configure db connection url as a `:jdbc-url` arg for a command.
The idea here is that you could override default dev `:jdbc-url` value from `deps.edn` by running `migrate`
command with env var using bash-script, makefile or whatever you want:

```shell
$ clojure -X:migrations migrate :jdbc-url $DATABASE_URL 
Migrating: ...
```

*The downside of that approach could be a lack of ability to use a common config for a project.
In the future there could be more convenient options for configuration if needed.* 


## Roadmap draft

- [ ] Support backward auto-migration.
- [ ] Support custom migration using Clojure.
- [ ] Support running with Leiningen.
- [ ] Support array, enum and other fields in PostgreSQL.
- [ ] Support comment for field.
- [ ] Support autogenerated custom constraints and checks.
- [ ] Support for migrating views.
- [ ] Support for SQLite and MySQL.
- [ ] Optimize autogenerated sql queries.
- [ ] Test against different versions of db and Clojure.
- [ ] Add visual representation of db schema by models. 
- [ ] Handle of model/field renaming.


### Things still in design

- Should args `:models-file` and `:migrations-dir` are set by default?
- Should it be possible to set arg `:jdbc-url` as an env var?
- How to handle common configuration conveniently?
- More consistent and helpful messages for users.
- Ability to separate models by multiple files.
- Move transformations out of conformers.
- Most likely add option to disable field types validation in order to be able to use 
the tool while not every type of database column is supported.


## Inspired by

- [Django migrations](https://docs.djangoproject.com/en/4.0/topics/migrations/)

### Thanks to projects
- [Honey SQL](https://github.com/seancorfield/honeysql)
- [Dependency](https://github.com/weavejester/dependency)
- [Differ](https://github.com/robinheghan/differ)


## Materials

- Blog post: [Announcing automigrate](https://bogoyavlensky.com/blog/announcing-automigrate/). 

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
make deploy-snapshot :patch  # build and deploy to Clojars next snapshot version from local machine
make release :patch  # bump git tag version by semver rules and push to remote repo
```

*In CI there is the [GitHub action](/.github/workflows/release.yaml) 
which publish any new git tag as new version of the lib to Clojars.*


## License

Copyright © 2021 Andrey Bogoyavlensky

Distributed under the MIT License.
