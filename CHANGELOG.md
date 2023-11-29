# Changelog

All notable changes to this project will be documented in this file.

*The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)*

## 0.2.0 - UNRELEASED

### Added

- Add ability to manage a comment on field.
- Add ability to create partial indexes.

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
