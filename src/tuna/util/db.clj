(ns tuna.util.db
  (:require [clojure.java.jdbc :as jdbc]
            [honeysql.core :as hsql]))


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
