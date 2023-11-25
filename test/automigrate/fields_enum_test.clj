(ns automigrate.fields-enum-test
  (:require [clojure.test :refer :all]
            [bond.james :as bond]
            [automigrate.util.db :as db-util]
            [automigrate.migrations :as migrations]
            [automigrate.schema :as schema]
            [automigrate.util.file :as file-util]
            [automigrate.testing-util :as test-util]
            [automigrate.testing-config :as config]))


(use-fixtures :each
  (test-util/with-drop-tables config/DATABASE-CONN)
  (test-util/with-delete-dir config/MIGRATIONS-DIR))


(deftest test-make-migration*-add-column-enum-ok
  (let [existing-actions '({:action :create-table
                            :model-name :account
                            :fields {:id {:type :serial}}})
        existing-models {:account
                         {:fields [[:id :serial]
                                   [:role [:enum :account-role]]]
                          :types [[:account-role :enum {:choices ["admin" "customer"]}]]}}]
    (bond/with-stub [[schema/load-migrations-from-files
                      (constantly existing-actions)]
                     [file-util/read-edn (constantly existing-models)]]
      (is (= '({:action :create-type
                :model-name :account
                :type-name :account-role
                :options {:type :enum
                          :choices ["admin" "customer"]}}
               {:action :add-column
                :model-name :account
                :field-name :role
                :options {:type [:enum :account-role]}})
            (#'migrations/make-migration* "" []))))))


(deftest test-make-migration*-add-column-enum-restore-ok
  (let [existing-actions '({:action :create-table
                            :model-name :account
                            :fields {:id {:type :serial}}}
                           {:action :create-type
                            :model-name :account
                            :type-name :account-role
                            :options {:type :enum
                                      :choices ["admin" "customer"]}}
                           {:action :add-column
                            :model-name :account
                            :field-name :role
                            :options {:type [:enum :account-role]}})
        existing-models {:account
                         {:fields [[:id :serial]
                                   [:role [:enum :account-role]]]
                          :types [[:account-role :enum {:choices ["admin" "customer"]}]]}}]
    (bond/with-stub [[schema/load-migrations-from-files
                      (constantly existing-actions)]
                     [file-util/read-edn (constantly existing-models)]]
      (is (= [] (#'migrations/make-migration* "" []))))))


(deftest test-make-migration*-create-table-with-enum-column-ok
  (let [existing-actions '()
        existing-models {:account
                         {:fields [[:id :serial]
                                   [:role [:enum :account-role]]]
                          :types [[:account-role :enum {:choices ["admin" "customer"]}]]}}]
    (bond/with-stub [[schema/load-migrations-from-files
                      (constantly existing-actions)]
                     [file-util/read-edn (constantly existing-models)]]
      (is (= '({:action :create-type
                :model-name :account
                :type-name :account-role
                :options {:type :enum
                          :choices ["admin" "customer"]}}
               {:action :create-table
                :model-name :account
                :fields {:id {:type :serial}
                         :role {:type [:enum :account-role]}}})
            (#'migrations/make-migration* "" []))))))


(deftest test-make-migration*-create-table-with-enum-column-restore-ok
  (let [existing-actions '({:action :create-type
                            :model-name :account
                            :type-name :account-role
                            :options {:type :enum
                                      :choices ["admin" "customer"]}}
                           {:action :create-table
                            :model-name :account
                            :fields {:id {:type :serial}
                                     :role {:type [:enum :account-role]}}})
        existing-models {:account
                         {:fields [[:id :serial]
                                   [:role [:enum :account-role]]]
                          :types [[:account-role :enum {:choices ["admin" "customer"]}]]}}]
    (bond/with-stub [[schema/load-migrations-from-files
                      (constantly existing-actions)]
                     [file-util/read-edn (constantly existing-models)]]
      (is (= [] (#'migrations/make-migration* "" []))))))


(deftest test-make-migration*-drop-table-with-enum-column-ok
  (let [existing-actions '({:action :create-type
                            :model-name :account
                            :type-name :account-role
                            :options {:type :enum
                                      :choices ["admin" "customer"]}}
                           {:action :create-table
                            :model-name :account
                            :fields {:id {:type :serial}
                                     :role {:type [:enum :account-role]}}})
        existing-models {}]
    (bond/with-stub [[schema/load-migrations-from-files
                      (constantly existing-actions)]
                     [file-util/read-edn (constantly existing-models)]]
      (is (= '({:action :drop-table
                :model-name :account}
               {:action :drop-type
                :model-name :account
                :type-name :account-role})
            (#'migrations/make-migration* "" []))))))


(deftest test-make-and-migrate-add-column-enum-ok
  (let [existing-actions '({:action :create-table
                            :model-name :account
                            :fields {:id {:type :serial}}})
        changed-models {:account
                        {:fields [[:id :serial]
                                  [:role [:enum :account-role]]]
                         :types [[:account-role :enum {:choices ["admin" "customer"]}]]}}
        expected-actions '({:action :create-type
                            :model-name :account
                            :type-name :account-role
                            :options {:type :enum
                                      :choices ["admin" "customer"]}}
                           {:action :add-column
                            :model-name :account
                            :field-name :role
                            :options {:type [:enum :account-role]}})
        expected-q-edn '({:create-type
                          [:account-role :as (:enum "admin" "customer")]}
                         {:alter-table "account"
                          :add-column (:role :account_role)})
        expected-q-sql (list ["CREATE TYPE account_role AS ENUM('admin', 'customer')"]
                         ["ALTER TABLE \"account\" ADD COLUMN role ACCOUNT_ROLE"])]

    (test-util/test-make-and-migrate-ok!
      existing-actions
      changed-models
      expected-actions
      expected-q-edn
      expected-q-sql)

    (testing "test actual db schema after applying the migration"
      (is (= [{:character_maximum_length nil
               :column_default "nextval('account_id_seq'::regclass)"
               :column_name "id"
               :data_type "integer"
               :udt_name "int4"
               :is_nullable "NO"
               :table_name "account"}
              {:character_maximum_length nil
               :column_default nil
               :column_name "role"
               :data_type "USER-DEFINED"
               :udt_name "account_role"
               :is_nullable "YES"
               :table_name "account"}]
            (test-util/get-table-schema-from-db config/DATABASE-CONN "account"))))))


(deftest test-make-and-migrate-add-column-enum-with-type-from-another-model-ok
  (let [existing-actions '({:action :create-table
                            :model-name :account
                            :fields {:id {:type :serial}}})
        changed-models {:account
                        {:fields [[:id :serial]
                                  [:role [:enum :account-role]]]}

                        :feed
                        {:fields [[:id :serial]]
                         :types [[:account-role :enum {:choices ["admin" "customer"]}]]}}
        expected-actions '({:action :create-type
                            :model-name :feed
                            :type-name :account-role
                            :options {:type :enum
                                      :choices ["admin" "customer"]}}
                           {:action :create-table
                            :model-name :feed
                            :fields {:id {:type :serial}}}
                           {:action :add-column
                            :model-name :account
                            :field-name :role
                            :options {:type [:enum :account-role]}})
        expected-q-edn '({:create-type
                          [:account-role :as (:enum "admin" "customer")]}
                         {:create-table ["feed"]
                          :with-columns [(:id :serial)]}
                         {:alter-table "account"
                          :add-column (:role :account_role)})
        expected-q-sql (list ["CREATE TYPE account_role AS ENUM('admin', 'customer')"]
                         ["CREATE TABLE \"feed\" (id SERIAL)"]
                         ["ALTER TABLE \"account\" ADD COLUMN role ACCOUNT_ROLE"])]

    (test-util/test-make-and-migrate-ok!
      existing-actions
      changed-models
      expected-actions
      expected-q-edn
      expected-q-sql)

    (testing "test actual db schema after applying the migration"
      (is (= [{:character_maximum_length nil
               :column_default "nextval('account_id_seq'::regclass)"
               :column_name "id"
               :data_type "integer"
               :udt_name "int4"
               :is_nullable "NO"
               :table_name "account"}
              {:character_maximum_length nil
               :column_default nil
               :column_name "role"
               :data_type "USER-DEFINED"
               :udt_name "account_role"
               :is_nullable "YES"
               :table_name "account"}]
            (test-util/get-table-schema-from-db config/DATABASE-CONN "account"))))))


(deftest test-make-and-migrate-add-column-enum-with-default-ok
  (let [existing-actions '({:action :create-table
                            :model-name :account
                            :fields {:id {:type :serial}}})
        changed-models {:account
                        {:fields [[:id :serial]
                                  [:role [:enum :account-role] {:default "customer"}]]
                         :types [[:account-role :enum {:choices ["admin" "customer"]}]]}}
        expected-actions '({:action :create-type
                            :model-name :account
                            :type-name :account-role
                            :options {:type :enum
                                      :choices ["admin" "customer"]}}
                           {:action :add-column
                            :model-name :account
                            :field-name :role
                            :options {:type [:enum :account-role]
                                      :default "customer"}})
        expected-q-edn '({:create-type
                          [:account-role :as (:enum "admin" "customer")]}
                         {:alter-table "account"
                          :add-column (:role :account_role [:default "customer"])})
        expected-q-sql (list ["CREATE TYPE account_role AS ENUM('admin', 'customer')"]
                         ["ALTER TABLE \"account\" ADD COLUMN role ACCOUNT_ROLE DEFAULT 'customer'"])]

    (test-util/test-make-and-migrate-ok!
      existing-actions
      changed-models
      expected-actions
      expected-q-edn
      expected-q-sql)

    (testing "test actual db schema after applying the migration"
      (is (= [{:character_maximum_length nil
               :column_default "nextval('account_id_seq'::regclass)"
               :column_name "id"
               :data_type "integer"
               :udt_name "int4"
               :is_nullable "NO"
               :table_name "account"}
              {:character_maximum_length nil
               :column_default "'customer'::account_role"
               :column_name "role"
               :data_type "USER-DEFINED"
               :udt_name "account_role"
               :is_nullable "YES"
               :table_name "account"}]
            (test-util/get-table-schema-from-db config/DATABASE-CONN "account"))))))


(deftest test-make-and-migrate-alter-column-enum-null-ok
  (let [existing-actions '({:action :create-type
                            :model-name :account
                            :type-name :account-role
                            :options {:type :enum
                                      :choices ["admin" "customer"]}}
                           {:action :create-table
                            :model-name :account
                            :fields {:id {:type :serial}
                                     :role {:type [:enum :account-role]}}})
        changed-models {:account
                        {:fields [[:id :serial]
                                  [:role [:enum :account-role] {:null false}]]
                         :types [[:account-role :enum {:choices ["admin" "customer"]}]]}}
        expected-actions '({:action :alter-column
                            :model-name :account
                            :field-name :role
                            :options {:type [:enum :account-role]
                                      :null false}
                            :changes {:null {:from :EMPTY :to false}}})
        expected-q-edn '({:alter-table
                          ("account"
                            {:alter-column [:role :set [:not nil]]})})
        expected-q-sql (list ["ALTER TABLE \"account\" ALTER COLUMN role SET NOT NULL"])]

    (test-util/test-make-and-migrate-ok!
      existing-actions
      changed-models
      expected-actions
      expected-q-edn
      expected-q-sql)

    (testing "test actual db schema after applying the migration"
      (is (= [{:character_maximum_length nil
               :column_default "nextval('account_id_seq'::regclass)"
               :column_name "id"
               :data_type "integer"
               :udt_name "int4"
               :is_nullable "NO"
               :table_name "account"}
              {:character_maximum_length nil
               :column_default nil
               :column_name "role"
               :data_type "USER-DEFINED"
               :udt_name "account_role"
               :is_nullable "NO"
               :table_name "account"}]
            (test-util/get-table-schema-from-db config/DATABASE-CONN "account"))))))


(deftest test-make-and-migrate-drop-type-and-column-enum-ok
  (let [existing-actions '({:action :create-type
                            :model-name :account
                            :type-name :account-role
                            :options {:type :enum
                                      :choices ["admin" "customer"]}}
                           {:action :create-table
                            :model-name :account
                            :fields {:id {:type :serial}
                                     :role {:type [:enum :account-role]}}})
        changed-models {:account
                        {:fields [[:id :serial]]}}
        expected-actions '({:action :drop-column
                            :model-name :account
                            :field-name :role}
                           {:action :drop-type
                            :model-name :account
                            :type-name :account-role})
        expected-q-edn '({:alter-table "account"
                          :drop-column :role}
                         {:drop-type [:account-role]})
        expected-q-sql (list ["ALTER TABLE \"account\" DROP COLUMN role"]
                         ["DROP TYPE account_role"])]

    (test-util/test-make-and-migrate-ok!
      existing-actions
      changed-models
      expected-actions
      expected-q-edn
      expected-q-sql)

    (testing "test actual db schema after applying the migration"
      (is (= [{:character_maximum_length nil
               :column_default "nextval('account_id_seq'::regclass)"
               :column_name "id"
               :data_type "integer"
               :udt_name "int4"
               :is_nullable "NO"
               :table_name "account"}]
            (test-util/get-table-schema-from-db config/DATABASE-CONN "account"))))

    (testing "check type has been dropped in db"
      (is (= []
            (db-util/exec!
              config/DATABASE-CONN
              {:select [:t.typname :t.typtype :e.enumlabel]
               :from [[:pg_type :t]]
               :join [[:pg_enum :e] [:= :e.enumtypid :t.oid]]
               :where [:= :t.typname "account_role"]}))))))


(deftest test-make-and-migrate-drop-type-and-table-with-enum-ok
  (let [existing-actions '({:action :create-type
                            :model-name :account
                            :type-name :account-role
                            :options {:type :enum
                                      :choices ["admin" "customer"]}}
                           {:action :create-table
                            :model-name :account
                            :fields {:id {:type :serial}
                                     :role {:type [:enum :account-role]}}})
        changed-models {}
        expected-actions '({:action :drop-table
                            :model-name :account}
                           {:action :drop-type
                            :model-name :account
                            :type-name :account-role})
        expected-q-edn '({:drop-table [:if-exists [[:raw "\"account\""]]]}
                         {:drop-type [:account-role]})
        expected-q-sql (list ["DROP TABLE IF EXISTS  \"account\""]
                         ["DROP TYPE account_role"])]

    (test-util/test-make-and-migrate-ok!
      existing-actions
      changed-models
      expected-actions
      expected-q-edn
      expected-q-sql)

    (testing "test actual db schema after applying the migration"
      (is (= []
            (test-util/get-table-schema-from-db config/DATABASE-CONN "account"))))

    (testing "check type has been dropped in db"
      (is (= []
            (db-util/exec!
              config/DATABASE-CONN
              {:select [:t.typname :t.typtype :e.enumlabel]
               :from [[:pg_type :t]]
               :join [[:pg_enum :e] [:= :e.enumtypid :t.oid]]
               :where [:= :t.typname "account_role"]}))))))


; ERRORS

(deftest test-fields-enum-uses-existing-enum-type
  (let [params {:existing-models
                {:account
                 {:fields [[:id :serial]
                           [:role [:enum :account-role]]]
                  :types [[:account-role-wrong :enum {:choices ["admin" "support"]}]]}

                 :feed {:fields [[:id :serial]
                                 [:status [:enum :feed-status]]]
                        :types [[:account-role :enum {:choices ["admin" "customer"]}]]}}}]
    (is (= (str "-- MODEL ERROR -------------------------------------\n\n"
             "There are enum fields with missing enum types: [:feed/status].\n\n")
          (with-out-str
            (test-util/make-migration! params))))))
