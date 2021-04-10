(ns tuna.util.db
  "Utils for working with database."
  (:require [clojure.java.jdbc :as jdbc]
            [honeysql.core :as hsql]
            #_[honey.sql :as honey]))


(defn exec!
  "Write data to db using honeysql."
  [db q]
  (->> q
    (hsql/format)
    (jdbc/execute! db)))


(defn query
  "Read db data using honeysql."
  [db q]
  (->> q
    (hsql/format)
    (jdbc/query db)))
