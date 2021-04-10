(ns tuna.util.test
  "Utils for simplifying tests."
  (:require [clojure.java.io :as io]
            [tuna.util.db :as util-db]))


(defn- drop-all-tables
  "Drop all database tables for public schema."
  [db]
  (let [tables (->> {:select [:table_name]
                     :from [:information_schema.tables]
                     :where [:and
                             [:= :table_schema "public"]
                             [:= :table_type "BASE TABLE"]]}
                 (util-db/query db)
                 (map (comp keyword :table_name)))]
    (when (seq tables)
      (->> {:drop-table tables}
        (util-db/exec! db)))))


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
