(ns tuna.util.test
  "Utils for simplifying tests."
  (:require [clojure.java.io :as io]
            [tuna.util.db :as db-util]))


(defn- drop-all-tables
  "Drop all database tables for public schema."
  [db]
  (let [tables (->> {:select [:table_name]
                     :from [:information_schema.tables]
                     :where [:and
                             [:= :table_schema "public"]
                             [:= :table_type "BASE TABLE"]]}
                 (db-util/exec! db)
                 (map (comp keyword :table_name)))]
    (when (seq tables)
      (->> {:drop-table tables}
        (db-util/exec! db)))))


(defn with-drop-tables
  [db]
  (fn [f]
    (drop-all-tables db)
    (f)))


(defn- delete-recursively
  "Delete dir and files inside recursively."
  [path]
  (when (.isDirectory (io/file path))
    (doseq [f (reverse (file-seq (io/file path)))]
      (io/delete-file f))))


(defn with-delete-dir
  [path]
  (fn [f]
    (delete-recursively path)
    (f)))


(defn get-table-fields
  [db table-name]
  (->> {:select [:column_name :data_type]
        :from [:information_schema.columns]
        :where [:= :table_name (name table-name)]}
    (db-util/exec! db)))
