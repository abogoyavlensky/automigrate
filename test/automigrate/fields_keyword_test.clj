(ns automigrate.fields-keyword-test
  (:require [clojure.test :refer :all]
            [automigrate.testing-util :as test-util]
            [automigrate.testing-config :as config]))


(use-fixtures :each
  (test-util/with-drop-tables config/DATABASE-CONN)
  (test-util/with-delete-dir config/MIGRATIONS-DIR))


(deftest test-fields-bigserial-create-table-ok
  (let [models {:account
                {:fields [[:id :bigserial]]}}]

    (testing "check generated actions, queries edn and sql from all actions"
      (is (= {:new-actions [{:action :create-table
                             :fields {:id {:type :bigserial}}
                             :model-name :account}]
              :q-edn [{:create-table [:account]
                       :with-columns ['(:id :bigserial)]}]
              :q-sql [["CREATE TABLE account (id BIGSERIAL)"]]}
            (test-util/perform-make-and-migrate!
              {:jdbc-url config/DATABASE-CONN
               :existing-actions []
               :existing-models models}))))

    (testing "check actual db changes"
      (testing "test actual db schema after applying the migration"
        (is (= [{:character_maximum_length nil
                 :column_default "nextval('account_id_seq'::regclass)"
                 :column_name "id"
                 :data_type "bigint"
                 :udt_name "int8"
                 :is_nullable "NO"
                 :table_name "account"}]
              (test-util/get-table-schema-from-db config/DATABASE-CONN "account")))))))


(deftest test-fields-bigserial-add-column-with-unique-true-ok
  (let [models {:account
                {:fields [[:id :serial]
                          [:number :bigserial {:unique true}]]}}
        existing-actions [{:action :create-table
                           :model-name :account
                           :fields {:id {:type :serial}}}]]

    (testing "check generated actions, queries edn and sql from all actions"
      (is (= {:new-actions [{:action :add-column
                             :field-name :number
                             :model-name :account
                             :options {:type :bigserial
                                       :unique true}}]
              :q-edn [{:create-table [:account]
                       :with-columns ['(:id :serial)]}
                      {:add-column '(:number :bigserial :unique)
                       :alter-table :account}]
              :q-sql [["CREATE TABLE account (id SERIAL)"]
                      ["ALTER TABLE account ADD COLUMN number BIGSERIAL UNIQUE"]]}
            (test-util/perform-make-and-migrate!
              {:jdbc-url config/DATABASE-CONN
               :existing-actions existing-actions
               :existing-models models}))))

    (testing "check actual db changes"
      (testing "test actual db schema after applying the migration"
        (is (= [{:character_maximum_length nil
                 :column_default "nextval('account_id_seq'::regclass)"
                 :column_name "id"
                 :data_type "integer"
                 :udt_name "int4"
                 :is_nullable "NO"
                 :table_name "account"}
                {:character_maximum_length nil
                 :column_default "nextval('account_number_seq'::regclass)"
                 :column_name "number"
                 :data_type "bigint"
                 :udt_name "int8"
                 :is_nullable "NO"
                 :table_name "account"}]
              (test-util/get-table-schema-from-db config/DATABASE-CONN "account")))))))
