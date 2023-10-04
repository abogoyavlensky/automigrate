(ns automigrate.testing-util
  "Utils for simplifying tests."
  (:require [automigrate.migrations :as migrations]
            [automigrate.schema :as schema]
            [automigrate.sql :as sql]
            [automigrate.testing-config :as config]
            [automigrate.util.db :as db-util]
            [automigrate.util.file :as file-util]
            [automigrate.util.spec :as spec-util]
            [bond.james :as bond]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.test :refer :all]
            [slingshot.slingshot :refer [try+]]))


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

(defn- drop-all-enum-types
  "Drop all user defined enum types."
  [db]
  (let [enum-types (->> {:select [:typname]
                         :from [:pg_type]
                         :where [:= :typtype "e"]}
                     (db-util/exec! db)
                     (mapv (comp keyword :typname)))]
    (doseq [type-name enum-types]
      (db-util/exec! db {:drop-type enum-types}))))

(defn with-drop-tables
  [db]
  (fn [f]
    (drop-all-tables db)
    (drop-all-enum-types db)
    (f)))


(defn- delete-recursively
  "Delete dir and files inside recursively."
  [path]
  (let [file-obj (io/file path)]
    (when (.isDirectory file-obj)
      (doseq [f (reverse (file-seq file-obj))]
        (io/delete-file f)))))


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


(defmacro thrown-with-slingshot-data?
  "Catch exception by calling function and return slingshot error data or nil.

  `exception-check`: could be a vector of keywords or a function;
  `f`: function that should be tested."
  [exception-check f]
  `(try+
     ~f
     (catch ~exception-check e#
       e#)))


(defn get-spec-error-data
  [f]
  (->> (thrown-with-slingshot-data? [:type ::s/invalid] (f))
    :reports
    (map #(dissoc % :problem))))

(defn test-make-and-migrate-ok!
  [existing-actions changed-models expected-actions expected-q-edn expected-q-sql]
  (bond/with-stub [[schema/load-migrations-from-files
                    (constantly existing-actions)]
                   [file-util/read-edn (constantly changed-models)]]
    (let [db config/DATABASE-CONN
          actions (#'migrations/make-migration* "" [])
          queries (map #(spec-util/conform ::sql/->sql %) actions)]
      (testing "test make-migration for model changes"
        (is (= expected-actions actions)))
      (testing "test converting migration actions to sql queries formatted as edn"
        (is (= expected-q-edn queries)))
      (testing "test converting actions to sql"
        (is (= expected-q-sql (map #(sql/->sql %) actions))))
      (testing "test running migrations on db"
        (is (every?
              #(= [#:next.jdbc{:update-count 0}] %)
              (#'migrations/exec-actions!
                {:db db
                 :actions (concat existing-actions actions)
                 :direction :forward
                 :migration-type :edn})))))))
