(ns automigrate.testing-util
  "Utils for simplifying tests."
  (:require [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [eftest.runner :as runner]
            [bond.james :as bond]
            [slingshot.slingshot :refer [try+]]
            [automigrate.migrations :as migrations]
            [automigrate.schema :as schema]
            [automigrate.sql :as sql]
            [automigrate.testing-config :as config]
            [automigrate.util.db :as db-util]
            [automigrate.util.file :as file-util]
            [automigrate.util.spec :as spec-util]))


(defn run-eftest
  "Run all  test using Eftest runner."
  [params]
  (runner/run-tests (runner/find-tests "test") params))


(defn drop-all-tables
  "Drop all database tables for public schema."
  [db]
  (let [tables (->> {:select [:table_name]
                     :from [:information_schema.tables]
                     :where [:and
                             [:= :table_schema "public"]
                             [:= :table_type "BASE TABLE"]]}
                 (db-util/exec! db)
                 (map (comp #(format "\"%s\"" %) :table_name))
                 (str/join ", "))]
    (when (seq tables)
      (->> {:drop-table [[[:raw tables]]]}
        (db-util/exec! db)))))


(defn- drop-all-enum-types
  "Drop all user defined enum types."
  [db]
  (let [enum-types (->> {:select [:typname]
                         :from [:pg_type]
                         :where [:= :typtype "e"]}
                     (db-util/exec! db)
                     (mapv (comp keyword :typname)))]
    (when (seq enum-types)
      (db-util/exec! db {:drop-type enum-types}))))


(defn with-drop-tables
  [db]
  (fn [f]
    (drop-all-tables db)
    (drop-all-enum-types db)
    (f)))


(defn delete-recursively
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


(defn make-migration!
  [{:keys [existing-actions existing-models]
    :or {existing-actions []
         existing-models {}}}]
  (bond/with-stub [[schema/load-migrations-from-files
                    (constantly existing-actions)]
                   ; existing models
                   [file-util/read-edn
                    (constantly existing-models)]]
    (#'migrations/make-migration
     ; parameters are not involved in test as they are mocked
     ; passing them here just to be able to run the make-migration fn
     {:models-file (str config/MODELS-DIR "feed_basic.edn")
      :migrations-dir config/MIGRATIONS-DIR})))


(defn perform-make-and-migrate!
  [{:keys [jdbc-url existing-actions existing-models]
    :or {existing-actions []
         existing-models {}}}]
  (bond/with-spy [migrations/make-next-migration
                  migrations/action->honeysql]
    ; Generate new actions
    (make-migration! {:existing-models existing-models
                      :existing-actions existing-actions})
    (let [new-actions (-> #'migrations/make-next-migration
                        (bond/calls)
                        (first)
                        :return)
          all-actions (concat (vec existing-actions) (vec new-actions))]
      (bond/with-stub [[migrations/get-detailed-migrations-to-migrate
                        (constantly {:to-migrate
                                     '({:file-name "0001_test_migration.edn"
                                        :migration-name "0001_test_migration"
                                        :migration-type :edn
                                        :number-int 1})
                                     :direction :forward})]
                       [migrations/read-migration
                        (constantly all-actions)]]
        ; Migrate all actions
        (#'migrations/migrate
         {:jdbc-url jdbc-url
          :migrations-dir config/MIGRATIONS-DIR})
        ; Response
        (let [q-edn (->> #'migrations/action->honeysql
                      (bond/calls)
                      (mapv :return))
              q-sql (mapv #(db-util/fmt %) q-edn)]
          {:new-actions new-actions
           :q-edn q-edn
           :q-sql q-sql})))))


(defn get-table-schema-from-db
  ([db model-name]
   (get-table-schema-from-db db model-name nil))
  ([db model-name {:keys [replace-cols add-cols]}]
   (let [default-columns [:table-name :data-type :udt-name :column-name :column-default
                          :is-nullable :character-maximum-length]]
     (db-util/exec!
       db
       {:select (or replace-cols
                  (-> (concat default-columns add-cols) (set) (vec)))
        :from [:information-schema.columns]
        :where [:= :table-name model-name]
        :order-by [:ordinal-position]}))))
