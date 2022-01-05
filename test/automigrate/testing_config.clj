(ns automigrate.testing-config
  (:require [automigrate.util.db :as db-util]))


(def MIGRATIONS-DIR "test/automigrate/migrations")


(def MODELS-DIR "test/automigrate/models/")


(def DATABASE-URL
  (format "jdbc:postgresql://%s/automigrate?user=automigrate&password=automigrate"
    (or (System/getenv "DATABASE_HOST_PORT")
      "127.0.0.1:5555")))


(def DATABASE-CONN
  (db-util/db-conn DATABASE-URL))
