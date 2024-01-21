# automigrate

[![CI](https://github.com/abogoyavlensky/automigrate/actions/workflows/checks.yaml/badge.svg?branch=master)](https://github.com/abogoyavlensky/automigrate/actions/workflows/checks.yaml)
[![cljdoc badge](https://cljdoc.org/badge/net.clojars.abogoyavlensky/automigrate)](https://cljdoc.org/jump/release/net.clojars.abogoyavlensky/automigrate)

Auto-generated database schema migrations for Clojure. Define models as plain EDN data 
and create database schema migrations automatically based on changes to the models.


## Features

- **declaratively** define db schema as **models** in EDN;
- create migrations **automatically** based on model changes;
- **migrate** db schema in forward and backward directions;
- manage migrations for: tables, indexes, constraints, enum types;
- view actual SQL or human-readable description for a migration;
- optionally add a custom SQL migration for specific cases;
- use with PostgreSQL :information_source: [*other databases are planned*] .

### Quick overview

https://github.com/abogoyavlensky/automigrate/assets/1375411/880db134-f2ed-46b4-9e77-72e326b6bf56

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

:construction: *Leiningen support is planned.*

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
Applying 0001_auto_create_table_book...
0001_auto_create_table_book successfully applied.
```

That's it. In the database you can see a newly created table called `book` with defined columns 
and one entry in table `automigrate_migrations` with new migration `0001_auto_create_table_book`.

#### List and explain migrations

To view status of existing migrations you can run:
```shell
$ clojure -X:migrations list
Existing migrations:

[x] 0001_auto_create_table_book.edn
```

To view raw SQL for existing migration you can run command `explain` with appropriate number: 

```shell
$ clojure -X:migrations explain :number 1
SQL for forward migration 0001_auto_create_table_book.edn:

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

Or it could be a map with keys `:fields`, `:indexes` (*optional*) and `:types` (*optional*). Each of these is also a vector of vectors. 
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
The second element could be a keyword or a vector of keyword and params. 
Available field types are matched with PostgreSQL built-in data types
and presented in the following table:

| Field type                                  | Description                                                                                                                                                                 |
|---------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `:integer`                                  |                                                                                                                                                                             |
| `:smallint`                                 |                                                                                                                                                                             |
| `:bigint`                                   |                                                                                                                                                                             |
| `:float`                                    |                                                                                                                                                                             |
| `:real`                                     |                                                                                                                                                                             |
| `:serial`                                   | Auto-incremented pg integer field.                                                                                                                                          |
| `:bigserial`                                | Auto-incremented pg bigint field.                                                                                                                                           |
| `:smallserial`                              | Auto-incremented pg serial2 field.                                                                                                                                          |
| `:numeric` or `[:numeric <pos-int>? <int>]` | Numeric type with optional precision and scale params. Default value could be set as numeric string, bigdec, float, int and nil: `"10.22"`, `10.22M`, `10`, `10.22`, `nil`. |
| `:decimal` or `[:decimal <pos-int>? <int>]` | Numeric type with optional precision and scale params. Same as `:numeric`.                                                                                                  |
| `:uuid`                                     |                                                                                                                                                                             |
| `:boolean`                                  |                                                                                                                                                                             |
| `:text`                                     |                                                                                                                                                                             |
| `:time` or `[:time <int>]`                  |                                                                                                                                                                             |
| `:timetz` or `[:timetz <int>]`              |                                                                                                                                                                             |
| `:timestamp` or `[:timestamp <int>]`        |                                                                                                                                                                             |
| `:timestamptz`  or `[:timestamptz <int>]`   |                                                                                                                                                                             |
| `:interval` or `[:interval <int>]`          |                                                                                                                                                                             |
| `:date`                                     |                                                                                                                                                                             |
| `:point`                                    |                                                                                                                                                                             |
| `:json`                                     |                                                                                                                                                                             |
| `:jsonb`                                    |                                                                                                                                                                             |
| `:varchar` or `[:varchar <pos-int>]`        | Second element is the length of value.                                                                                                                                      |
| `:char` or `[:char <pos-int>]`              | Second element is the length of value.                                                                                                                                      |
| `:float` or `[:float <pos-int>]`            | Second element is the minimum acceptable precision in binary digits.                                                                                                        |
| `[:enum <enum-type-name>]`                  | To use enum type you should define it in `:types` section in model.                                                                                                         |
| `:box`                                      |                                                                                                                                                                             |
| `:bytea`                                    |                                                                                                                                                                             |
| `:cidr`                                     |                                                                                                                                                                             |
| `:circle`                                   |                                                                                                                                                                             |
| `:double-precision`                         |                                                                                                                                                                             |
| `:inet`                                     |                                                                                                                                                                             |
| `:line`                                     |                                                                                                                                                                             |
| `:lseg`                                     |                                                                                                                                                                             |
| `:macaddr`                                  |                                                                                                                                                                             |
| `:macaddr8`                                 |                                                                                                                                                                             |
| `:money`                                    |                                                                                                                                                                             | 
| `:path`                                     |                                                                                                                                                                             |
| `:pg_lsn`                                   |                                                                                                                                                                             |
| `:pg_snapshot`                              |                                                                                                                                                                             |
| `:polygon`                                  |                                                                                                                                                                             |
| `:tsquery`                                  |                                                                                                                                                                             |
| `:tsvector`                                 |                                                                                                                                                                             |
| `:txid_snapshot`                            |                                                                                                                                                                             |
| `:xml`                                      |                                                                                                                                                                             |
| `:bit` or `[:bit <pos-int>]`                |                                                                                                                                                                             | 
| `:varbit` or `[:varbit <pos-int>]`          |                                                                                                                                                                             |

Doc reference to the PostgreSQL built-in general-purpose data types: 
[https://www.postgresql.org/docs/current/datatype.html#DATATYPE-TABLE](https://www.postgresql.org/docs/current/datatype.html#DATATYPE-TABLE) 

###### Notes

- _`<...>?` - param is optional._
- _`or` - an alternative definition of type._

:information_source: *There are fixed field types because `automigrate` 
validates type of field and default value to have errors as early as possible 
before running migration against database.*

##### Field options

Options value is a map where key is the name of the option and value is the available option value.
Available options are presented in the table below:

| Field option   | Description                                                                                   | Required? | Value                                                                                                                          |
|----------------|-----------------------------------------------------------------------------------------------|-----------|--------------------------------------------------------------------------------------------------------------------------------|
| `:null`        | Set to `false` for non-nullable field. Field is nullable by default if the option is not set. | `false`   | `boolean?`                                                                                                                     |
| `:primary-key` | Set to `true` for making primary key field.                                                   | `false`   | `true?`                                                                                                                        |
| `:unique`      | Set to `true` to add unique constraint for a field.                                           | `false`   | `true?`                                                                                                                        |
| `:default`     | Default value for a field.                                                                    | `false`   | `boolean?`, `integer?`, `float?`, `decimal?`, `string?`, `nil?`, or fn defined as `[:keyword <integer? or float? or string?>]` |
| `:foreign-key` | Set to namespaced keyword to point to a primary key field from another model.                 | `false`   | `:another-model/field-name`                                                                                                    |
| `:on-delete`   | Specify delete action for `:foreign-key`.                                                     | `false`   | `:cascade`, `:set-null`, `:set-default`, `:restrict`, `:no-action`                                                             |
| `:on-update`   | Specify update action for `:foreign-key`.                                                     | `false`   | `:cascade`, `:set-null`, `:set-default`, `:restrict`, `:no-action`                                                             |
| `:check`       | Set condition in Honeysql format to create custom CHECK for a column.                         | `false`   | Example: `[:and [:> :month 0] [:<= :month 12]]`                                                                                |
| `:array`       | Can be added to any field type to make it array.                                              | `false`   | `string?`, examples: `"[]"`, `"[][]"`, `[][10][3]`                                                                             |
| `:comment`     | Add a comment on the field.                                                                   | `false`   | `string?`                                                                                                                      |


#### Indexes

Each index is a vector of three elements: 
`[:name-of-index :type-of-index {:fields [:field-from-model-to-index] :unique boolean? :where [...]}]`
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
The option `:fields` is required, others are optional.  
Available options are presented in the table below:


| Field option | Description                                                           | Required? | Value                      |
|--------------|-----------------------------------------------------------------------|-----------|----------------------------|
| `:fields`    | Vector of fields as keywords. Index will be created for those fields. | `true`    | [`:field-name` ...]        |
| `:unique`    | Set to `true` if index should be unique.                              | `false`   | `true?`                    |
| `:where`     | Set condition in Honeysql format to create partial index.             | `false`   | Example: `[:> amount 10]`  |

#### Types

:information_source: _At the moment only Enum type is supported._

Each type is a vector of three elements: `[:name-of-type :type-of-type {...}]`
Name, type-of-type and options are required.

The first element is the name of a type and must be a keyword.

##### Type of type

The second element is a type of type and must be a keyword of available types:


| Field type |
|------------|
| `:enum`    |


##### Enum type
Each enum type is a vector of three elements: `[:name-of-type :enum {:choices [<str>]}]`

Options for enum type must contain the `:choices` value with vector of strings. 
`:choices` represent enum values for the type.

An example of model definition with enum type:
```clojure
{:account {:fields [[:id :serial]
                    [:role [:enum :account-role]]]
           :types [[:account-role :enum {:choices ["admin" "customer"]}]]}}
```

Limitations:

- `:choices` can't be empty;
- values in `:choices` must be unique for the particular type;
- **removing** a value from `:choices` of existing type is not supported;
- **re-ordering** values in `:choices` of existing type is not supported;
        
### CLI interface

Available commands are: `make`, `migrate`, `list`, `explain`, `help`. Let's see them in detail by section.

:information_source: *Assume that args `:models-file`, `:migrations-dir` and `:jdbc-url` are supposed to be set in deps.edn alias.*

Common args for all commands:

| Argument            | Description                                | Required?                              | Possible values                                                                                  | Default value              |
|---------------------|--------------------------------------------|----------------------------------------|--------------------------------------------------------------------------------------------------|----------------------------|
| `:models-file`      | Path to models file.                       | `true` (only for `make`)               | string path (example: `"path/to/models.edn"`)                                                    | *not provided*             |
| `:migrations-dir`   | Path to store migrations dir.              | `true`                                 | string path (example: `"path/to/migrations"`)                                                    | *not provided*             |
| `:jdbc-url`         | Database connection defined as JDBC-url.   | `true` (only for `migrate` and `list`) | string jdbc url (example: `"jdbc:postgresql://localhost:5432/mydb?user=myuser&password=secret"`) | *not provided*             |
| `:migrations-table` | Model name for storing applied migrations. | `false`                                | string (example: `"migrations"`)                                                                 | `"automigrate_migrations"` |

### `make`

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

Create empty SQL migration with custom name:

```shell
$ clojure -X:migrations make :type :empty-sql :name add_custom_trigger
Created migration: resources/db/migrations/0003_add_custom_trigger.sql
```

Try to create migration without new changes in models:

```shell
$ clojure -X:migrations make
There are no changes in models.
```


### `migrate`

Applies change described in migration to database.
Applies all unapplied migrations by number order if arg `:number` is not presented in command.
Throws error for same migration number.

Backward migration is fully implemented. For auto-generated and SQL migrations, it is possible to revert migration and to delete appropriate entry from migrations table.
Database changes will be reverted. 

In forward direction if specified migration `:number` is **included**, meaning if, for example, `:number 3` the migration with number 3 **will be applied**.
In backward migration the `:number` is **excluded**, so all migrations until the specified number will be reverted but not the target one. 
For instance if we have 3 migrations as applied, and want to revert just the 3d and 2d ones, we can run `migrate` command with `:number 1`. 
3d and 3d migrations will be reverted, but the first one will stay applied.     

*Specific args:*

| Argument  | Description                                                                                                                                                                 | Required? | Possible values                                 | Default value                                    |
|-----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|-------------------------------------------------|--------------------------------------------------|
| `:number` | Number of migration which should be a target point. In forward direction, migration by number will by applied. In backward direction, migration by number will be reverted. | `false`   | integer (example: `1` for migration `0001_...`) | *not provided*, last migration number by default |

##### Examples

Migrate forward all unapplied migrations:
```shell
$ clojure -X:migrations migrate
Appyling 0001_auto_create_table_book...
0001_auto_create_table_book successfully applied.
Appyling 0002_create_table_author...
0002_create_table_author successfully applied.
Appyling 0003_add_custom_trigger...
0003_add_custom_trigger successfully applied.
```

Migrate forward up to particular migration number (*included*):
```shell
$ clojure -X:migrations migrate :number 2
Appyling 0001_auto_create_table_book...
0001_auto_create_table_book successfully applied.
Appyling 0002_create_table_author...
0002_create_table_author successfully applied.
```

Migrate backward down to particular migration number (*excluded*):
```shell
$ clojure -X:migrations migrate :number 1
Reverting 0002_create_table_author...
0002_create_table_author successfully reverted.
```

Migrate backward to initial state of database:
```shell
$ clojure -X:migrations migrate :number 0
Reverting 0003_add_custom_trigger...
0003_add_custom_trigger successfully reverted.
Reverting 0002_create_table_author...
0002_create_table_author successfully reverted.
Reverting 0001_auto_create_table_book...
0001_auto_create_table_book successfully reverted.
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

### `list`

Print out list of existing migrations with statuses displayed as
boxes before migration name:
- `[x]` - applied;
- `[ ]` - not applied.

*No specific args.*

##### Examples:

View list of partially applied migrations:
```shell
$ clojure -X:migrations list
Existing migrations:

[x] 0001_auto_create_table_book.edn
[ ] 0002_create_table_author.edn
[ ] 0003_add_custom_trigger.sql
```

### `explain`

Print out actual raw SQL for particular migration by number.

*Specific args:*

| Argument     | Description                                       | Required? | Possible values                                 | Default value  |
|--------------|---------------------------------------------------|-----------|-------------------------------------------------|----------------|
| `:number`    | Number of migration which should be explained.    | `true`    | integer (example: `1` for migration `0001_...`) | *not provided* |
| `:direction` | Direction in which migration should be explained. | `false`   | `:forward`, `:backward`                         | `:forward`     |
| `:format`    | Format of explanation.                            | `false`   | `:sql`, `:human`                                | `:sql`         |

##### Examples:

View raw SQL for migration in forward direction:
```shell
$ clojure -X:migrations explain :number 1
SQL for forward migration 0001_auto_create_table_book.edn:

BEGIN;
CREATE TABLE book (id SERIAL UNIQUE PRIMARY KEY, name VARCHAR(256) NOT NULL, description TEXT);
COMMIT;
```

View raw SQL for migration in backward direction:
```shell
$ clojure -X:migrations explain :number 1 :direction backward
SQL for backward migration 0001_auto_create_table_book.edn:

BEGIN;
DROP TABLE IF EXISTS book;
COMMIT;
```

### `help`

You can print short doc info for a particular command or the tool itself by running `help` command.

*Args:*

| Argument | Description   | Required? | Possible values                               | Default value                                          |
|----------|---------------|-----------|-----------------------------------------------|--------------------------------------------------------|
| `:cmd`   | Command name. | `false`   | `make`, `migrate`, `list`, `explain`, `help`  | *not provided*, by default prints doc for all commands |


##### Examples 

Print doc for all available commands:

```shell
$ clojure -X:migrations help
Auto-generated database migrations for Clojure.

Available commands:
...
```

Print doc for a particular command:

```shell
$ clojure -X:migrations help :cmd make
Create a new migration based on changes to the models.

Available options:
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

You can fill it with two block of queries for forward and backward migration. 
Backward migration block is not mandatory and can be empty. 
For example:

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
Appyling 0003_make_all_accounts_active...
0003_make_all_accounts_active successfully applied.
```


### Use in production

:warning: *The library is not yet ready for production use. 
But it is really appreciated if you try it out! :wink:*

For now, there is just a single way to configure db connection url as a `:jdbc-url` arg for a command.
The idea here is that you could override default dev `:jdbc-url` value from `deps.edn` by running `migrate`
command with env var using bash-script, makefile or whatever you want:

```shell
$ clojure -X:migrations migrate :jdbc-url $DATABASE_URL 
Appyling ...
```

*The downside of that approach could be a lack of ability to use a common config for a project.
In the future there could be more convenient options for configuration if needed.* 


## Roadmap

- [x] Enum type of fields.
- [x] All built-in data types.
- [x] Array data types.
- [x] Comment on field.
- [x] Partial indexes.
- [x] Auto-generated backward migration.
- [x] Field level CHECK constraints.
- [ ] Data-migration using Clojure.
- [ ] Support for SQLite.
- [ ] Optimized auto-generated SQL queries.
- [ ] Model level constraints.
- [ ] Support for MySQL.
- [ ] Visual representation of DB schema.


### Things still in design

- How to handle common configuration conveniently?
- Should args `:models-file` and `:migrations-dir` are set by default?
- Should it be possible to set arg `:jdbc-url` as an env var?
- More consistent and helpful messages for users, maybe using `fipp` library.
- Ability to separate models by multiple files.
- Move transformations out of clojure spec conformers.
- Try to replace `spec` with `malli`.
- Simplify model definition just as map with key `:type` instead of vector of 3 items. 
- Disable field types validation at all, or add ability to set arbitrary custom type.
- Handle of model/field renaming.


## Inspired by

- [Django migrations](https://docs.djangoproject.com/en/4.0/topics/migrations/)
- [Prisma Migrate](https://www.prisma.io/migrate)

### Thanks to projects
- [Honey SQL](https://github.com/seancorfield/honeysql)
- [Dependency](https://github.com/weavejester/dependency)
- [Differ](https://github.com/robinheghan/differ)


## Resources

- [Announcing automigrate](https://bogoyavlensky.com/blog/announcing-automigrate/) (blog post) 

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


## License

Copyright © 2021 Andrey Bogoyavlenskiy

Distributed under the MIT License.
