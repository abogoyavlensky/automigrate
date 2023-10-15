(ns automigrate.types-enum-test
  (:require
    [clojure.test :refer :all]
    [bond.james :as bond]
    [automigrate.migrations :as migrations]
    [automigrate.schema :as schema]
    [automigrate.util.db :as db-util]
    [automigrate.util.file :as file-util]
    [automigrate.testing-util :as test-util]
    [automigrate.testing-config :as config]))


(use-fixtures :each
  (test-util/with-drop-tables config/DATABASE-CONN)
  (test-util/with-delete-dir config/MIGRATIONS-DIR))


(deftest test-make-migration*-create-type-enum-ok
  (let [existing-actions '({:action :create-table
                            :model-name :account
                            :fields {:id {:type :serial}}})
        existing-models {:account
                         {:fields [[:id :serial]]
                          :types [[:account-role :enum {:choices ["admin" "customer"]}]]}}]
    (bond/with-stub [[schema/load-migrations-from-files
                      (constantly existing-actions)]
                     [file-util/read-edn (constantly existing-models)]]
      (is (= '({:action :create-type
                :model-name :account
                :type-name :account-role
                :options {:type :enum
                          :choices ["admin" "customer"]}})
            (#'migrations/make-migration* "" []))))))


(deftest test-make-migration*-create-type-enum-restore-ok
  (let [existing-actions '({:action :create-table
                            :model-name :account
                            :fields {:id {:type :serial}}}
                           {:action :create-type
                            :model-name :account
                            :type-name :account-role
                            :options {:type :enum
                                      :choices ["admin" "customer"]}})
        existing-models {:account
                         {:fields [[:id :serial]]
                          :types [[:account-role :enum {:choices ["admin" "customer"]}]]}}]
    (bond/with-stub [[schema/load-migrations-from-files
                      (constantly existing-actions)]
                     [file-util/read-edn (constantly existing-models)]]
      (is (= [] (#'migrations/make-migration* "" []))))))


(deftest test-make-and-migrate-create-type-enum-ok
  (let [existing-actions '({:action :create-table
                            :model-name :account
                            :fields {:id {:type :serial}}})
        changed-models {:account
                        {:fields [[:id :serial]]
                         :types [[:account-role :enum {:choices ["admin" "customer"]}]]}}
        expected-actions '({:action :create-type
                            :model-name :account
                            :type-name :account-role
                            :options {:type :enum
                                      :choices ["admin" "customer"]}})
        expected-q-edn '({:create-type
                          [:account-role :as (:enum "admin" "customer")]})
        expected-q-sql (list ["CREATE TYPE account_role AS ENUM('admin', 'customer')"])]

    (test-util/test-make-and-migrate-ok!
      existing-actions
      changed-models
      expected-actions
      expected-q-edn
      expected-q-sql)

    (testing "check created type in db"
      (is (= [{:typname "account_role"
               :typtype "e"
               :enumlabel "admin"}
              {:typname "account_role"
               :typtype "e"
               :enumlabel "customer"}]
            (db-util/exec!
              config/DATABASE-CONN
              {:select [:t.typname :t.typtype :e.enumlabel]
               :from [[:pg_type :t]]
               :join [[:pg_enum :e] [:= :e.enumtypid :t.oid]]
               :where [:= :t.typname "account_role"]}))))))


(deftest test-make-and-migrate-drop-type-enum-ok
  (let [existing-actions '({:action :create-table
                            :model-name :account
                            :fields {:id {:type :serial}}}
                           {:action :create-type
                            :model-name :account
                            :type-name :account-role
                            :options {:type :enum
                                      :choices ["admin" "customer"]}})
        changed-models {:account
                        {:fields [[:id :serial]]}}
        expected-actions '({:action :drop-type
                            :model-name :account
                            :type-name :account-role})
        expected-q-edn '({:drop-type [:account-role]})
        expected-q-sql (list ["DROP TYPE account_role"])]

    (test-util/test-make-and-migrate-ok!
      existing-actions
      changed-models
      expected-actions
      expected-q-edn
      expected-q-sql)

    (testing "check type has been dropped in db"
      (is (= []
            (db-util/exec!
              config/DATABASE-CONN
              {:select [:t.typname :t.typtype :e.enumlabel]
               :from [[:pg_type :t]]
               :join [[:pg_enum :e] [:= :e.enumtypid :t.oid]]
               :where [:= :t.typname "account_role"]}))))))


(deftest test-make-migration*-drop-type-enum-restore-ok
  (let [existing-actions '({:action :create-table
                            :model-name :account
                            :fields {:id {:type :serial}}}
                           {:action :create-type
                            :model-name :account
                            :type-name :account-role
                            :options {:type :enum
                                      :choices ["admin" "customer"]}}
                           {:action :drop-type
                            :model-name :account
                            :type-name :account-role})
        existing-models {:account
                         {:fields [[:id :serial]]}}]
    (bond/with-stub [[schema/load-migrations-from-files
                      (constantly existing-actions)]
                     [file-util/read-edn (constantly existing-models)]]
      (is (= [] (#'migrations/make-migration* "" []))))))


(deftest test-make-and-migrate-create-type-enum-with-creating-table-ok
  (let [existing-actions '()
        changed-models {:account
                        {:fields [[:id :serial]]
                         :types [[:account-role :enum {:choices ["admin" "customer"]}]]}}
        expected-actions '({:action :create-table
                            :model-name :account
                            :fields {:id {:type :serial}}}
                           {:action :create-type
                            :model-name :account
                            :type-name :account-role
                            :options {:type :enum
                                      :choices ["admin" "customer"]}})
        expected-q-edn '({:create-table [:account]
                          :with-columns [(:id :serial)]}
                         {:create-type
                          [:account-role :as (:enum "admin" "customer")]})
        expected-q-sql (list
                         ["CREATE TABLE account (id SERIAL)"]
                         ["CREATE TYPE account_role AS ENUM('admin', 'customer')"])]

    (test-util/test-make-and-migrate-ok!
      existing-actions
      changed-models
      expected-actions
      expected-q-edn
      expected-q-sql)

    (testing "check created type in db"
      (is (= [{:typname "account_role"
               :typtype "e"
               :enumlabel "admin"}
              {:typname "account_role"
               :typtype "e"
               :enumlabel "customer"}]
            (db-util/exec!
              config/DATABASE-CONN
              {:select [:t.typname :t.typtype :e.enumlabel]
               :from [[:pg_type :t]]
               :join [[:pg_enum :e] [:= :e.enumtypid :t.oid]]
               :where [:= :t.typname "account_role"]}))))

    (testing "test actual db schema after applying the migration"
      (is (= [{:character_maximum_length nil
               :column_default "nextval('account_id_seq'::regclass)"
               :column_name "id"
               :data_type "integer"
               :udt_name "int4"
               :is_nullable "NO"
               :table_name "account"}]
            (test-util/get-table-schema-from-db config/DATABASE-CONN "account"))))))


(deftest test-make-and-migrate-alter-type-enum-ok
  (let [existing-actions '({:action :create-table
                            :model-name :account
                            :fields {:id {:type :serial}}}
                           {:action :create-type
                            :model-name :account
                            :type-name :account-role
                            :options {:type :enum
                                      :choices ["admin" "customer"]}})
        changed-models {:account
                        {:fields [[:id :serial]]
                         :types [[:account-role
                                  :enum
                                  {:choices ["basic" "admin" "developer" "customer" "support" "other"]}]]}}
        expected-actions '({:action :alter-type
                            :model-name :account
                            :type-name :account-role
                            :options {:type :enum
                                      :choices ["basic" "admin" "developer" "customer" "support" "other"]}
                            :changes {:choices {:from ["admin" "customer"]
                                                :to ["basic" "admin" "developer" "customer" "support" "other"]}}})
        expected-q-edn '([{:alter-type
                           [:account-role :add-value "basic" :before "admin"]}
                          {:alter-type
                           [:account-role :add-value "developer" :before "customer"]}
                          {:alter-type
                           [:account-role :add-value "support" :after "customer"]}
                          {:alter-type
                           [:account-role :add-value "other" :after "support"]}])
        expected-q-sql (list
                         [["ALTER TYPE account_role ADD VALUE 'basic' BEFORE 'admin'"]
                          ["ALTER TYPE account_role ADD VALUE 'developer' BEFORE 'customer'"]
                          ["ALTER TYPE account_role ADD VALUE 'support' AFTER 'customer'"]
                          ["ALTER TYPE account_role ADD VALUE 'other' AFTER 'support'"]])]

    (test-util/test-make-and-migrate-ok!
      existing-actions
      changed-models
      expected-actions
      expected-q-edn
      expected-q-sql)

    (testing "check created type in db"
      (is (= [{:typname "account_role"
               :typtype "e"
               :enumlabel "basic"
               :enumsortorder 0.0}
              {:typname "account_role"
               :typtype "e"
               :enumlabel "admin"
               :enumsortorder 1.0}
              {:typname "account_role"
               :typtype "e"
               :enumlabel "developer"
               :enumsortorder 1.5}
              {:typname "account_role"
               :typtype "e"
               :enumlabel "customer"
               :enumsortorder 2.0}
              {:typname "account_role"
               :typtype "e"
               :enumlabel "support"
               :enumsortorder 3.0}
              {:typname "account_role"
               :typtype "e"
               :enumlabel "other"
               :enumsortorder 4.0}]
            (db-util/exec!
              config/DATABASE-CONN
              {:select [:t.typname :t.typtype :e.enumlabel :e.enumsortorder]
               :from [[:pg_type :t]]
               :join [[:pg_enum :e] [:= :e.enumtypid :t.oid]]
               :where [:= :t.typname "account_role"]
               :order-by [[:e.enumsortorder :asc]]}))))))
