(ns tuna.testing-config
  (:require [tuna.util.db :as db-util]))


(def MIGRATIONS-DIR "test/tuna/migrations/")


(def MODELS-DIR "test/tuna/models/")


(def DATABASE-URL
  (format "jdbc:postgresql://%s/tuna?user=tuna&password=tuna"
    (or (System/getenv "DATABASE_HOST_PORT")
      "127.0.0.1:5555")))


(def DATABASE-CONN
  (db-util/db-conn DATABASE-URL))
