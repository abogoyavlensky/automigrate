(ns automigrate.core
  "Public interface for lib's users."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [slingshot.slingshot :refer [try+]]
            [automigrate.migrations :as migrations]
            [automigrate.util.spec :as spec-util]
            [automigrate.util.file :as file-util]
            [automigrate.errors :as errors]
            [automigrate.help :as automigrate-help])
  (:refer-clojure :exclude [list]))


(s/def ::models-file string?)
(s/def ::migrations-dir string?)
(s/def ::jdbc-url (s/conformer str))
(s/def ::number int?)


(s/def ::cmd (set automigrate-help/HELP-CMDS-ORDER))


(s/def ::format
  (s/and
    (s/conformer keyword)
    #{:sql :human}))


(s/def ::type
  (s/and
    (s/conformer keyword)
    #{migrations/EMPTY-SQL-MIGRATION-TYPE}))


(s/def ::name (s/conformer name))


(s/def ::migrations-table
  (s/and
    string?
    (s/conformer
      (fn [v]
        (keyword (str/replace v #"_" "-"))))))


(s/def ::direction
  (s/and
    (s/conformer keyword)
    #{:forward :backward}))


(s/def ::make-args
  (s/keys
    :req-un [::models-file
             ::migrations-dir]
    :opt-un [::type
             ::name]))


(s/def ::migrate-args
  (s/keys
    :req-un [::jdbc-url
             ::migrations-dir]
    :opt-un [::number
             ::migrations-table]))


(s/def ::explain-args
  (s/keys
    :req-un [::migrations-dir
             ::number]
    :opt-un [::direction
             ::format]))


(s/def ::list-args
  (s/keys
    :req-un [::jdbc-url
             ::migrations-dir]
    :opt-un [::migrations-table]))


(s/def ::help-args
  (s/keys
    :opt-un [::cmd]))


(defn- run-fn
  [f args args-spec]
  (try+
    (let [args* (spec-util/conform args-spec args)]
      (f args*))
    (catch [:type ::s/invalid] e
      (file-util/prn-err e))
    (catch Object e
      (let [message (or (ex-message e) (str e))]
        (-> {:title "UNEXPECTED ERROR"
             :message message}
          (errors/custom-error->error-report)
          (file-util/prn-err))))))


; Public interface

(defn make
  "Create a new migration based on changes to the models.

Available options:
  :models-file - Path to the file with model definitions. (required)
  :migrations-dir - Path to directory containing migration files. (required)
  :name - Custom name for a migration. (optional)
  :type - Type of new migration, empty by default for auto-migration.
          Also, available `:empty-sql` - for creating an empty raw SQL migration. (optional)"
  [args]
  (run-fn migrations/make-migration args ::make-args))


(defn migrate
  "Run existing migrations and change the database schema.

Available options:
  :jdbc-url - JDBC url for the database connection. (required)
  :migrations-dir - Path to directory containing migration files. (required)
  :migrations-table - Custom name for the migrations table in the database. (optional)
  :number - Integer number of the target migration. (optional)"
  [args]
  (run-fn migrations/migrate args ::migrate-args))


(defn explain
  "Show raw SQL or human-readable description for a migration by number.

Available options:
  :migrations-dir - Path to directory containing migration files. (required)
  :number - Integer number of the migration to explain. (required)
  :direction - Direction of the migration to explain, can be `forward` (default) or `backward`. (optional)
  :format - Format of explanation, can be `sql` (default) or `human`. (optional)"
  [args]
  (run-fn migrations/explain args ::explain-args))


(defn list
  "Show the list of existing migrations with status.

Available options:
  :jdbc-url - JDBC url for the database connection. (required)
  :migrations-dir - Path to directory containing migration files. (required)
  :migrations-table - Custom name for the migrations table in the database. (optional)"
  [args]
  (run-fn migrations/list-migrations args ::list-args))


(defn help
  "Help information for all commands of automigrate tool.

Available options:
  :cmd - Command name to display help information for a specific command. (optional)"
  [args]
  (run-fn automigrate-help/show-help! args ::help-args))
