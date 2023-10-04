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

