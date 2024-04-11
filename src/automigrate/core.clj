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


(def ^:private JDBC-URL-ENV-VAR "DATABASE_URL")

(s/def ::models-file string?)
(s/def ::migrations-dir string?)
(s/def ::resources-dir string?)
(s/def ::jdbc-url (s/and some? (s/conformer str)))
(s/def ::jdbc-url-env-var string?)
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
    :opt-un [::type
             ::name
             ::models-file
             ::migrations-dir
             ::resources-dir]))


(s/def ::migrate-args
  (s/keys
    :req-un [::jdbc-url]
    :opt-un [::migrations-dir
             ::number
             ::migrations-table
             ::jdbc-url-env-var]))


(s/def ::explain-args
  (s/keys
    :req-un [::number]
    :opt-un [::migrations-dir
             ::direction
             ::format]))


(s/def ::list-args
  (s/keys
    :req-un [::jdbc-url]
    :opt-un [::migrations-table
             ::migrations-dir
             ::jdbc-url-env-var]))


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
  :name - Custom name for a migration. Default: auto-generated name by first action in migration. (optional)
  :type - Type of new migration, empty by default for auto-generated migration.
          Set `:empty-sql` - for creating an empty raw SQL migration. (optional)
  :models-file - Path to the file with model definitions relative to the `resources` dir. Default: `db/models.edn`. (optional)
  :migrations-dir - Path to directory containing migration files relative to the `resources` dir. Default: `db/migrations`. (optional)
  :resources-dir - Path to resources dir to create migrations dir, if it doesn't exist. Default: `resources` (optional)"
  [args]
  (run-fn migrations/make-migration args ::make-args))


(defn migrate
  "Run existing migrations and change the database schema.

Available options:
  :number - Integer number of the target migration. (optional)
  :jdbc-url - JDBC url for the database connection. Default: get from `DATABASE_URL` env var. (optional)
  :jdbc-url-env-var - Name of environment variable for jdbc-url. Default: `DATABASE_URL`. (optional)
  :migrations-dir - Path to directory containing migration files relative to the `resources` dir. Default: `db/migrations`. (optional)
  :migrations-table - Custom name for the migrations table in the database. (optional)"
  ([]
   ; 0-arity function can be used inside application code if there are no any options.
   (migrate {}))
  ([{:keys [jdbc-url-env-var] :as args}]
   (let [jdbc-url-env-var* (or jdbc-url-env-var JDBC-URL-ENV-VAR)
         args* (update args :jdbc-url #(or % (System/getenv jdbc-url-env-var*)))]
     (run-fn migrations/migrate args* ::migrate-args))))


(defn explain
  "Show raw SQL or human-readable description for a migration by number.

Available options:
  :number - Integer number of the migration to explain. (required)
  :direction - Direction of the migration to explain, can be `forward` (default) or `backward`. (optional)
  :format - Format of explanation, can be `sql` (default) or `human`. (optional)
  :migrations-dir - Path to directory containing migration files relative to the `resources` dir. Default: `db/migrations`. (optional)"
  [args]
  (run-fn migrations/explain args ::explain-args))


(defn list
  "Show the list of existing migrations with status.

Available options:
  :jdbc-url - JDBC url for the database connection. Default: get from `DATABASE_URL` env var. (optional)
  :jdbc-url-env-var - Name of environment variable for jdbc-url. Default: `DATABASE_URL`. (optional)
  :migrations-dir - Path to directory containing migration files relative to the `resources` dir. Default: `db/migrations`. (optional)
  :migrations-table - Custom name for the migrations table in the database. Default: `automigrate_migrations`. (optional)"
  [{:keys [jdbc-url-env-var] :as args}]
  (let [jdbc-url-env-var* (or jdbc-url-env-var JDBC-URL-ENV-VAR)
        args* (update args :jdbc-url #(or % (System/getenv jdbc-url-env-var*)))]
    (run-fn migrations/list-migrations args* ::list-args)))


(defn help
  "Help information for all commands of automigrate tool.

Available options:
  :cmd - Command name to display help information for a specific command. (optional)"
  [args]
  (run-fn automigrate-help/show-help! args ::help-args))
