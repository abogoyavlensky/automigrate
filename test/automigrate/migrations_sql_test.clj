(ns automigrate.migrations-sql-test
  (:require [clojure.test :refer :all]
            [automigrate.core :as core]
            [automigrate.util.db :as db-util]
            [automigrate.util.file :as file-util]
            [automigrate.testing-util :as test-util]
            [automigrate.testing-config :as config]))


(use-fixtures :each
  (test-util/with-drop-tables config/DATABASE-CONN)
  (test-util/with-delete-dir config/MIGRATIONS-DIR))


(deftest test-make-sql-migration-ok
  (is (= (str "Created migration: test/automigrate/migrations/0001_auto_create_table_feed.edn\n"
           "Actions:\n"
           "  - create table feed\n")
        (with-out-str
          (core/make {:models-file (str config/MODELS-DIR "feed_basic.edn")
                      :migrations-dir config/MIGRATIONS-DIR}))))
  (is (= "Created migration: test/automigrate/migrations/0002_add_description_field.sql\n"
        (with-out-str
          (core/make {:models-file (str config/MODELS-DIR "feed_basic.edn")
                      :migrations-dir config/MIGRATIONS-DIR
                      :type "empty-sql"
                      :name "add-description-field"}))))
  (testing "check that sql migration has been made"
    (let [files (file-util/list-files config/MIGRATIONS-DIR)]
      (is (= 2 (count files)))
      (is (= "0002_add_description_field.sql"
            (.getName (last files))))))
  (testing "check making next auto migration"
    (is (= (str "Created migration: test/automigrate/migrations/0003_auto_add_column_created_at_to_feed_etc.edn\n"
             "Actions:\n"
             "  - add column created_at to feed\n"
             "  - add column name to feed\n")
          (with-out-str
            (core/make {:models-file (str config/MODELS-DIR "feed_add_column.edn")
                        :migrations-dir config/MIGRATIONS-DIR}))))
    (let [files (file-util/list-files config/MIGRATIONS-DIR)]
      (is (= 3 (count files)))
      (is (= "0003_auto_add_column_created_at_to_feed_etc.edn"
            (.getName (last files)))))))


(deftest ^:eftest/slow test-migrate-sql-migration-ok
  (core/make {:models-file (str config/MODELS-DIR "feed_basic.edn")
              :migrations-dir config/MIGRATIONS-DIR})
  (core/make {:models-file (str config/MODELS-DIR "feed_basic.edn")
              :migrations-dir config/MIGRATIONS-DIR
              :type "empty-sql"
              :name "add-description-field"})
  (spit (str config/MIGRATIONS-DIR "/0002_add_description_field.sql")
    (str "-- FORWARD\n"
      "ALTER TABLE feed ADD COLUMN description text;\n"
      "-- BACKWARD\n"
      "ALTER TABLE feed DROP COLUMN description;\n"))

  (testing "check forward migration"
    (core/migrate {:migrations-dir config/MIGRATIONS-DIR
                   :jdbc-url config/DATABASE-URL})
    (is (= [{:id 1
             :name "0001_auto_create_table_feed"}
            {:id 2
             :name "0002_add_description_field"}]
          (->> {:select [:id :name]
                :from [db-util/MIGRATIONS-TABLE]}
            (db-util/exec! config/DATABASE-CONN))))
    (is (= "text"
          (->> (test-util/get-table-fields config/DATABASE-CONN :feed)
            (filter #(= "description" (:column_name %)))
            (first)
            :data_type))))

  (testing "check backward migration"
    (core/migrate {:migrations-dir config/MIGRATIONS-DIR
                   :jdbc-url config/DATABASE-URL
                   :number 1})
    (is (= '({:id 1
              :name "0001_auto_create_table_feed"})
          (->> {:select [:*]
                :from [db-util/MIGRATIONS-TABLE]}
            (db-util/exec! config/DATABASE-CONN)
            (map #(dissoc % :created_at)))))
    (is (nil?
          (->> (test-util/get-table-fields config/DATABASE-CONN :feed)
            (filter #(= "description" (:column_name %)))
            (seq))))))


(deftest test-explain-sql-migration-ok
  (core/make {:models-file (str config/MODELS-DIR "feed_basic.edn")
              :migrations-dir config/MIGRATIONS-DIR})
  (core/make {:models-file (str config/MODELS-DIR "feed_basic.edn")
              :migrations-dir config/MIGRATIONS-DIR
              :type "empty-sql"
              :name "add-description-field"})
  (spit (str config/MIGRATIONS-DIR "/0002_add_description_field.sql")
    (str "-- FORWARD\n"
      "ALTER TABLE feed ADD COLUMN description text;\n"
      "-- BACKWARD\n"
      "ALTER TABLE feed DROP COLUMN description;\n"))
  (testing "explain forward migration"
    (is (= (str "SQL for migration 0002_add_description_field.sql:\n\n\n"
             "ALTER TABLE feed ADD COLUMN description text;\n\n")
          (with-out-str
            (core/explain {:migrations-dir config/MIGRATIONS-DIR
                           :jdbc-url config/DATABASE-URL
                           :number 2})))))
  (testing "explain backward migration"
    (is (= (str "SQL for migration 0002_add_description_field.sql:\n\n\n"
             "ALTER TABLE feed DROP COLUMN description;\n\n")
          (with-out-str
            (core/explain {:migrations-dir config/MIGRATIONS-DIR
                           :jdbc-url config/DATABASE-URL
                           :number 2
                           :direction "backward"}))))))


(deftest test-list-migrations-with-sql-one-ok
  (core/make {:models-file (str config/MODELS-DIR "feed_basic.edn")
              :migrations-dir config/MIGRATIONS-DIR})
  (core/make {:models-file (str config/MODELS-DIR "feed_basic.edn")
              :migrations-dir config/MIGRATIONS-DIR
              :type "empty-sql"
              :name "add-description-field"})
  (testing "check that migrations table does not exist"
    (is (thrown? Exception
          (->> {:select [:name]
                :from [db-util/MIGRATIONS-TABLE]
                :order-by [:created-at]}
            (db-util/exec! config/DATABASE-CONN)))))
  (testing "check list-migrations output"
    (is (= (str "Existing migrations:\n\n"
             "[ ] 0001_auto_create_table_feed.edn\n"
             "[ ] 0002_add_description_field.sql\n")
          (with-out-str
            (core/list {:migrations-dir config/MIGRATIONS-DIR
                        :jdbc-url config/DATABASE-URL}))))))
