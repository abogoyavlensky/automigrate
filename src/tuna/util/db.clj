(ns tuna.util.db
  "Utils for working with database."
  (:require [clojure.java.jdbc :as jdbc]
            [honey.sql :as honey]))


(def MIGRATIONS-TABLE
  "Default migrations table name."
  :migrations)


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


; TODO: remove!
(comment
  (let [db-uri "jdbc:postgresql://localhost:5432/tuna?user=tuna&password=tuna"
        db (tuna.migrations/db-conn db-uri)
        q {:create-table [MIGRATIONS-TABLE :if-not-exists]
           :with-columns [[:id :serial [:not nil] [:primary-key]]
                          [:name [:varchar 256] [:not nil] :unique]
                          [:created_at :timestamp [:default [:now]]]]}]
    (exec! db q)))
