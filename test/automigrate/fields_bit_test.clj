(ns automigrate.fields-bit-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [automigrate.testing-util :as test-util]
            [automigrate.testing-config :as config]))


(use-fixtures :each
  (test-util/with-drop-tables config/DATABASE-CONN)
  (test-util/with-delete-dir config/MIGRATIONS-DIR))


(deftest ^:eftest/slow test-fields-bit-create-table-ok
  (doseq [{:keys [field-type field-name]} [{:field-type :bit}
                                           {:field-type :varbit
                                            :field-name "bit varying"}]]
    (test-util/drop-all-tables config/DATABASE-CONN)
    (test-util/delete-recursively config/MIGRATIONS-DIR)

    (testing "check generated actions, queries edn and sql from all actions"
      (is (= {:new-actions [{:action :create-table
                             :fields {:thing {:type [field-type 3]}}
                             :model-name :account}]
              :q-edn [{:create-table [:account]
                       :with-columns [(list :thing [field-type 3])]}]
              :q-sql [[(format "CREATE TABLE account (thing %s(3))"
                         (str/upper-case (name field-type)))]]}
            (test-util/perform-make-and-migrate!
              {:jdbc-url config/DATABASE-CONN
               :existing-actions []
               :existing-models {:account
                                 {:fields [[:thing [field-type 3]]]}}})))

      (testing "check actual db changes"
        (testing "test actual db schema after applying the migration"
          (is (= [{:character_maximum_length 3
                   :column_default nil
                   :column_name "thing"
                   :data_type (or field-name (name field-type))
                   :udt_name (name field-type)
                   :is_nullable "YES"
                   :table_name "account"}]
                (test-util/get-table-schema-from-db config/DATABASE-CONN "account"))))))))


(deftest test-fields-bit-alter-column-ok
  (doseq [{:keys [field-type field-name]} [{:field-type :bit}
                                           {:field-type :varbit
                                            :field-name "bit varying"}]]
    (test-util/drop-all-tables config/DATABASE-CONN)
    (test-util/delete-recursively config/MIGRATIONS-DIR)

    (testing "check generated actions, queries edn and sql from all actions"
      (is (= {:new-actions (list {:action :alter-column
                                  :changes {:type {:from [field-type 3]
                                                   :to [field-type 10]}}
                                  :field-name :thing
                                  :model-name :account
                                  :options {:type [field-type 10]}})
              :q-edn [{:create-table [:account]
                       :with-columns [(list :thing [field-type 3])]}
                      {:alter-table (list :account
                                      {:alter-column [:thing :type [field-type 10]]})}]
              :q-sql [[(format "CREATE TABLE account (thing %s(3))"
                         (str/upper-case (name field-type)))]
                      [(format "ALTER TABLE account ALTER COLUMN thing TYPE %s(10)"
                         (str/upper-case (name field-type)))]]}
            (test-util/perform-make-and-migrate!
              {:jdbc-url config/DATABASE-CONN
               :existing-actions [{:action :create-table
                                   :fields {:thing {:type [field-type 3]}}
                                   :model-name :account}]
               :existing-models {:account
                                 {:fields [[:thing [field-type 10]]]}}})))

      (testing "check actual db changes"
        (testing "test actual db schema after applying the migration"
          (is (= [{:character_maximum_length 10
                   :column_default nil
                   :column_name "thing"
                   :data_type (or field-name (name field-type))
                   :udt_name (name field-type)
                   :is_nullable "YES"
                   :table_name "account"}]
                (test-util/get-table-schema-from-db config/DATABASE-CONN "account"))))))))


(deftest test-fields-enum-uses-existing-enum-type
  (let [params {:existing-models
                {:account
                 {:fields [[:thing [:bit]]]}}}]
    (is (= (str "-- MODEL ERROR -------------------------------------\n\n"
             "Invalid definition bit type of field :account/thing.\n\n"
             "  [:bit]\n\n")
          (test-util/get-make-migration-output params)))))
