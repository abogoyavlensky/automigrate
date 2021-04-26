(ns tuna.util.db
  "Utils for working with database."
  (:require [clojure.java.jdbc :as jdbc]
            [honey.sql :as honey]))


(def MIGRATIONS-TABLE
  "Default migrations table name."
  :migrations)


; Additional sql clauses

(honey/register-clause! :alter-column
  (fn [k spec]
    ; TODO: try to check second place of :type, :set, :drop
    (#'honey/format-add-item k spec))
  :rename-column)


(honey/register-clause! :add-constraint
  ; TODO: update with more precise formatter
  (fn [k spec]
    (#'honey/format-add-item k spec))
  :drop-constraint)


(honey/register-clause! :drop-constraint
  (fn [k spec]
    (#'honey/format-selector k spec))
  :rename-table)


; Public

(defn db-conn
  "Return db connection for performing migration."
  ([]
   (db-conn nil))
  ([db-uri]
   (let [uri (or db-uri
                 ; TODO: add ability to read .end file
                 ; TODO: add ability to change env var name
               (System/getenv "DATABASE_URL"))]
     {:connection-uri uri})))


(defn fmt
  "Format data to sql query with configured dialect."
  ([data]
   (fmt data nil))
  ([data dialect]
   (if (some? dialect)
     (honey/format data {:dialect dialect})
     (honey/format data))))


(defn exec!
  "Write data to db."
  [db q]
  (->> q
    (fmt)
    (jdbc/execute! db)))


(defn query
  "Read data from db."
  [db q]
  (->> q
    (fmt)
    (jdbc/query db)))


(defn create-migrations-table
  "Create table to keep migrations history."
  [db]
  (->> {:create-table [MIGRATIONS-TABLE :if-not-exists]
        :with-columns [[:id :serial [:not nil] [:primary-key]]
                       [:name [:varchar 256] [:not nil] :unique]
                       [:created_at :timestamp [:default [:now]]]]}
    (exec! db)))
