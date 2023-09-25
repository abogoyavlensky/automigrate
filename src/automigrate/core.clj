(ns automigrate.core
  "Public interface for lib's users."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [slingshot.slingshot :refer [try+]]
            [automigrate.migrations :as migrations]
            [automigrate.util.spec :as spec-util]
            [automigrate.util.file :as file-util]
            [automigrate.errors :as errors])
  (:refer-clojure :exclude [list]))


(s/def ::models-file string?)
(s/def ::migrations-dir string?)
(s/def ::jdbc-url (s/conformer str))
(s/def ::number int?)


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
    :opt-un [::direction]))


(s/def ::list-args
  (s/keys
    :req-un [::jdbc-url
             ::migrations-dir]
    :opt-un [::migrations-table]))


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
  "Create new migration based on changes of models.

  Available options:
    - :models-file - path to models' file (required)
    - :migrations-dir - path to dir for storing migrations (required)
    - :name - custom name for migration (optional)
    - :type - type of new migration, empty by default for auto-migration;
              also available :empty-sql - for creating empty raw SQL migration; (optional)"
  [args]
  (run-fn migrations/make-migration args ::make-args))


(defn migrate
  "Run existing migrations and change database schema.

  Available options:
  - :jdbc-url - jdbc url of database connection (required)
  - :migrations-dir - path to dir for storing migrations (required)
  - :migrations-table - optional migrations' table name (optional)
  - :number - integer number of target point migration (optional)"
  [args]
  (run-fn migrations/migrate args ::migrate-args))


(defn explain
  "Show raw SQL for migration by number

  Available options:
  - :migrations-dir - path to dir for storing migrations (required)
  - :number - integer number of migration to explain (required)
  - :direction - direction for SQL from migration (optional)"
  [args]
  (run-fn migrations/explain args ::explain-args))


(defn list
  "Show list of existing migrations with status.

  Available options:
  - :jdbc-url - jdbc url of database connection (required)
  - :migrations-dir - path to dir for storing migrations (required)
  - :migrations-table - optional migrations' table name (optional)"
  [args]
  (run-fn migrations/list-migrations args ::list-args))
