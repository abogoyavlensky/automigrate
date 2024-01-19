(ns automigrate.testing-util
  "Utils for simplifying tests."
  (:require [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.test :refer :all]
            [eftest.runner :as runner]
            [bond.james :as bond]
            [slingshot.slingshot :refer [try+]]
            [automigrate.migrations :as migrations]
            [automigrate.schema :as schema]
            [automigrate.testing-config :as config]
            [automigrate.util.db :as db-util]
            [automigrate.util.file :as file-util]))


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


(defn make-migration-spy!
  [params]
  (bond/with-spy [migrations/make-next-migration]
    (make-migration! params)
    ; Return generated new migration actions.
    (some-> #'migrations/make-next-migration
      (bond/calls)
      (first)
      :return)))


(defn actions->sql
  [actions]
  (if (sequential? actions)
    (mapv #(db-util/fmt %) actions)
    (db-util/fmt actions)))


(defn perform-migrate!
  [{:keys [jdbc-url all-actions direction]
    :or {all-actions []
         direction :forward}}]
  (bond/with-spy [migrations/action->honeysql]
    (bond/with-stub [[migrations/get-detailed-migrations-to-migrate
                      (constantly {:to-migrate
                                   '({:file-name "0001_test_migration.edn"
                                      :migration-name "0001_test_migration"
                                      :migration-type :edn
                                      :number-int 1})
                                   :direction direction})]

                     [migrations/migration->actions (constantly all-actions)]]
      ; Migrate all actions
      (#'migrations/migrate
       {:jdbc-url jdbc-url
        :migrations-dir config/MIGRATIONS-DIR})
      ; Response
      (let [q-edn (->> #'migrations/action->honeysql
                    (bond/calls)
                    (mapv :return))
            q-sql (mapv actions->sql q-edn)]
        {:q-edn q-edn
         :q-sql q-sql}))))


(defn perform-make-and-migrate!
  [{:keys [jdbc-url existing-actions existing-models direction]
    :or {existing-actions []
         existing-models {}
         direction :forward}}]
  (bond/with-spy [migrations/make-next-migration]
    ; Generate new actions
    (make-migration! {:existing-models existing-models
                      :existing-actions existing-actions})
    (let [new-actions (-> #'migrations/make-next-migration
                        (bond/calls)
                        (first)
                        :return)
          all-actions (concat (vec existing-actions) (vec new-actions))
          result (perform-migrate! {:jdbc-url jdbc-url
                                    :all-actions all-actions
                                    :direction direction})]
      (assoc result :new-actions new-actions))))


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


(defn get-column-comment
  [db table-name-str column-name-str]
  (->> {:select [:c.table_name :c.column_name :pgd.description]
        :from [[:pg_catalog.pg_statio_all_tables :st]]
        :inner-join [[:pg_catalog.pg_description :pgd] [:= :pgd.objoid :st.relid]

                     [:information_schema.columns :c]
                     [:and
                      [:= :pgd.objsubid :c.ordinal_position]
                      [:= :c.table_schema :st.schemaname]
                      [:= :c.table_name :st.relname]]]
        :where [:and
                [:= :c.table_name table-name-str]
                [:= :c.column_name column-name-str]]}
    (db-util/exec! db)))


(defn get-indexes
  [db table-name-str]
  (db-util/exec!
    db
    {:select [:*]
     :from [:pg-indexes]
     :where [:= :tablename table-name-str]
     :order-by [:indexname]}))


(defn get-all-migrations-set
  [db]
  (->> {:select [:*]
        :from [db-util/MIGRATIONS-TABLE]}
    (db-util/exec! db)
    (map :name)
    (set)))


(defn get-enum-type-choices
  [db type-name]
  (db-util/exec!
    db
    {:select [:t.typname :t.typtype :e.enumlabel :e.enumsortorder]
     :from [[:pg_type :t]]
     :join [[:pg_enum :e] [:= :e.enumtypid :t.oid]]
     :where [:= :t.typname type-name]
     :order-by [[:e.enumsortorder :asc]]}))


(defn get-constraints
  [model-name-str]
  (db-util/exec!
    config/DATABASE-CONN
    {:select [:tc.constraint_name
              :tc.constraint_type
              :tc.table_name
              [:ccu.column_name :colname]]
     :from [[:information_schema.table_constraints :tc]]
     :join [[:information_schema.key_column_usage :kcu]
            [:= :tc.constraint_name :kcu.constraint_name]

            [:information_schema.constraint_column_usage :ccu]
            [:= :ccu.constraint_name :tc.constraint_name]]
     :where [:in :ccu.table_name [model-name-str]]
     :order-by [:tc.constraint_name]}))
