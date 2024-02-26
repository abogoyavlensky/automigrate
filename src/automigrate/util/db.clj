(ns automigrate.util.db
  "Utils for working with database."
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as jdbc-rs]
            [honey.sql :as honey]
            [clojure.string :as str]))


(def MIGRATIONS-TABLE
  "Default migrations table name."
  :automigrate-migrations)


; Additional sql clauses
; TODO: update custom clauses with more precise formatters

(honey/register-clause! :drop-constraint
  (fn [k spec]
    (#'honey/format-selector k spec))
  :rename-table)


(honey/register-clause! :add-constraint
  (fn [k spec]
    (#'honey/format-add-item k spec))
  :drop-constraint)


(honey/register-clause! :create-index
  (fn [k spec]
    (#'honey/format-add-item k spec))
  :add-index)


(honey/register-clause! :create-unique-index
  (fn [k spec]
    (#'honey/format-add-item k spec))
  :add-index)


(honey/register-clause! :create-type
  (fn [k spec]
    (#'honey/format-add-item k spec))
  :create-unique-index)


(honey/register-clause! :drop-type
  (fn [k spec]
    (#'honey/format-add-item k spec))
  :create-type)


(honey/register-clause! :alter-type
  (fn [k spec]
    (#'honey/format-add-item k spec))
  :drop-type)


; Public

(defn db-conn
  "Return db connection for performing migration."
  [jdbc-url]
  (jdbc/get-datasource {:jdbcUrl jdbc-url}))


(defn fmt
  "Format data to sql query with configured dialect."
  ([data]
   (fmt data nil))
  ([data dialect]
   (if (some? dialect)
     (honey/format data {:dialect dialect})
     (honey/format data))))


(defn exec!
  "Send query to db."
  [db query]
  (let [fmt-query (fmt query)]
    (jdbc/execute! db fmt-query {:builder-fn jdbc-rs/as-unqualified-lower-maps})))


(defn exec-raw!
  "Send raw sql query to db."
  [db query]
  (jdbc/execute! db [query] {:builder-fn jdbc-rs/as-unqualified-lower-maps}))


(defn create-migrations-table
  "Create table to keep migrations history."
  [db migrations-table]
  (->> {:create-table [migrations-table :if-not-exists]
        :with-columns [[:id :serial [:not nil] [:primary-key]]
                       [:name [:varchar 255] [:not nil] :unique]
                       [:created_at :timestamp [:default [:now]]]]}
    (exec! db)))


(defn kw->raw
  [kw]
  [:raw (str/replace (name kw) #"-" " ")])
