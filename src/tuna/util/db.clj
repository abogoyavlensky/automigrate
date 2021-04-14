(ns tuna.util.db
  "Utils for working with database."
  (:require [clojure.java.jdbc :as jdbc]
            [honeysql.core :as hsql]
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


(defn exec!
  "Write data to db using honeysql."
  [db q]
  (->> q
    (hsql/format)
    (jdbc/execute! db)))


(defn hexec!
  "Write data to db using honeysql."
  [db q]
  (->> q
    (honey/format)
    (jdbc/execute! db)))


(defn query
  "Read db data using honeysql."
  [db q]
  (->> q
    (hsql/format)
    (jdbc/query db)))


(defn hquery
  "Read db data using honeysql."
  [db q]
  (->> q
    (honey/format)
    (jdbc/query db)))


(defn create-migrations-table
  "Create table to keep migrations history."
  [db]
  (->> {:create-table [MIGRATIONS-TABLE :if-not-exists]
        :with-columns [[:id :serial [:not nil] [:primary-key]]
                       [:name [:varchar 256] [:not nil] :unique]
                       [:created_at :timestamp [:default [:now]]]]}
    (hexec! db)))


; TODO: remove!
(comment
  (let [db-uri "jdbc:postgresql://localhost:5432/tuna?user=tuna&password=tuna"
        db (tuna.migrations/db-conn db-uri)
        q {:create-table [MIGRATIONS-TABLE :if-not-exists]
           :with-columns [[:id :serial [:not nil] [:primary-key]]
                          [:name [:varchar 256] [:not nil] :unique]
                          [:created_at :timestamp [:default [:now]]]]}]
    (->> q
      (honey/format)
      (jdbc/execute! db))))
