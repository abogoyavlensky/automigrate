(ns automigrate.fields-keyword-test
  (:require [clojure.test :refer :all]
            [bond.james :as bond]
            [automigrate.migrations :as migrations]
            [automigrate.testing-util :as test-util]
            [automigrate.testing-config :as config]))


(use-fixtures :each
  (test-util/with-drop-tables config/DATABASE-CONN)
  (test-util/with-delete-dir config/MIGRATIONS-DIR))


(deftest test-fields-bigserial-create-table-ok
  (let [initial-params {:existing-models
                        {:account
                         {:fields [[:id :bigserial]]}}}
        expected-actions '({:action :create-table
                            :fields {:id {:type :bigserial}}
                            :model-name :account})]
    (bond/with-spy [migrations/make-next-migration]
      (is (= (str "Created migration: "
               "test/automigrate/migrations/0001_auto_create_table_account.edn\n"
               "Actions:\n"
               "  - create table account\n")
            (test-util/get-make-migration-output initial-params)))
      (let [new-actions (-> #'migrations/make-next-migration
                          (bond/calls)
                          (first)
                          :return)]
        (testing "check generated actions"
          (is (= expected-actions new-actions)))
        (testing "check generated edn and sql from all actions"
          (is (= {:q-edn [{:create-table [:account]
                           :with-columns ['(:id :bigserial)]}]
                  :q-sql [["CREATE TABLE account (id BIGSERIAL)"]]}
                (test-util/perform-migrate!
                  {:jdbc-url config/DATABASE-CONN
                   :existing-actions new-actions}))))
        (testing "check actual db changes"
          (testing "test actual db schema after applying the migration"
            (is (= [{:character_maximum_length nil
                     :column_default "nextval('account_id_seq'::regclass)"
                     :column_name "id"
                     :data_type "bigint"
                     :udt_name "int8"
                     :is_nullable "NO"
                     :table_name "account"}]
                  (test-util/get-table-schema-from-db config/DATABASE-CONN "account")))))))))
