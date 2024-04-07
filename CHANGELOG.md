# Changelog

All notable changes to this project will be documented in this file.

*The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)*

## 0.3.3 - UNRELEASED

### Added

- Read database URL from env variable by default.
- Add ability to set a name for the env variable of database URL. 
- Add ability to migrate from jar.

### Changed

- Make migrations-dir and models-file optional with default value. 

## 0.3.2 - 2024-02-27

### Added

- Format SQL output from explain command.

### Changed

- Add an example minimal setup with empty models.
- Update doc: remove unique option from id fields in examples.

### Fixed

- Fix ordering actions for dropping table with fk reference.


## 0.3.1 - 2024-01-22

### Added

- Add ability to manage column level CHECK constraints.

### Changed

- Add fixed name for constraints.

### Fixed

- Fix validation of referenced field for foreign key constraint.

## 0.3.0 - 2024-01-12

### Added

- Add auto-generated backward migrations.


## 0.2.1 - 2023-12-01

### Added

- Add ability to manage a comment on field.
- Add ability to create a partial indexes.

## 0.2.0 - 2023-11-24

### Added

- Add all data types without parameters for PostgreSQL.
- Add bit data types.
- Add interval data type.
- Add parameterised time and timestamp data types with time zone.
- Add char and varchar data types without parameters.
- Add ability to create a field with any type as an array.

### Fixed

- Fix validation of parameter for float data type.
- Fix alter-column action in case column type changing.

### Changed

- Update check mark for applied migrations in list command output.

## 0.1.3 - 2023-10-26

### Fixed

- Fix cljdoc discovery by git tag.

## 0.1.2 - 2023-10-26

### Added

- Add Enum field type.
- Add ability to explain migration in human-readable format.
- Add help command.

### Changed

- Add model name to the auto-generated migration name.

## 0.1.1 - 2023-09-25

### Added

- Add numeric/decimal field type.

### Fixed

- Do not capitalize string value of option `:default`. Fixed by upgrading honeysql.
- Do not drop fk constraint if fk option for field was empty. 

### Changed

- Upgrade dependencies: honeysql=2.4.1066, next.jdbc=1.3.883.

## 0.1.0 - 2022-01-20

### Added

- Add ability to make migrations and migrate for changing table and column.
- Add ability to drop table and column.
- Add ability to create, alter and drop index.
- Add ability to migrate to specific migration number in any direction.
- Add ability to print list of migrations with statuses.
- Add ability to print raw SQL for migration.
- Add ability to create, migrate and explain sql migration type.
- Add ability to show error messages for users.
