(ns automigrate.types-enum-test
  (:require
    [clojure.test :refer :all]
    [bond.james :as bond]
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
    (bond/with-stub [[schema/load-migrations-from-files (constantly existing-actions)]
                     [file-util/read-edn (constantly existing-models)]]
      (is (= '({:action :create-type
                :model-name :account
                :type-name :account-role
                :options {:type :enum
                          :choices ["admin" "customer"]}})
            (test-util/make-migration-spy! {:existing-actions existing-actions
                                            :existing-models existing-models}))))))


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
    (is (= "There are no changes in models.\n"
          (with-out-str
            (test-util/make-migration! {:existing-actions existing-actions
                                        :existing-models existing-models}))))))


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
        expected-q-edn [{:create-table [:account]
                         :with-columns ['(:id :serial)]}
                        {:create-type
                         [:account-role :as '(:enum "admin" "customer")]}]
        expected-q-sql (list ["CREATE TABLE account (id SERIAL)"]
                         ["CREATE TYPE account_role AS ENUM('admin', 'customer')"])]

    (is (= {:new-actions expected-actions
            :q-edn expected-q-edn
            :q-sql expected-q-sql}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions existing-actions
             :existing-models changed-models})))

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
        expected-q-edn [{:create-table [:account]
                         :with-columns ['(:id :serial)]}
                        {:create-type [:account-role
                                       :as
                                       '(:enum
                                          "admin"
                                          "customer")]}
                        {:drop-type [:account-role]}]
        expected-q-sql (list ["CREATE TABLE account (id SERIAL)"]
                         ["CREATE TYPE account_role AS ENUM('admin', 'customer')"]
                         ["DROP TYPE account_role"])]

    (is (= {:new-actions expected-actions
            :q-edn expected-q-edn
            :q-sql expected-q-sql}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions existing-actions
             :existing-models changed-models})))

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
    (is (= "There are no changes in models.\n"
          (with-out-str
            (test-util/make-migration! {:existing-actions existing-actions
                                        :existing-models existing-models}))))))


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
        expected-q-edn [{:create-table [:account]
                         :with-columns ['(:id :serial)]}
                        {:create-type
                         [:account-role :as '(:enum "admin" "customer")]}]
        expected-q-sql (list
                         ["CREATE TABLE account (id SERIAL)"]
                         ["CREATE TYPE account_role AS ENUM('admin', 'customer')"])]

    (is (= {:new-actions expected-actions
            :q-edn expected-q-edn
            :q-sql expected-q-sql}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions existing-actions
             :existing-models changed-models})))

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
        expected-q-edn '({:create-table [:account]
                          :with-columns [(:id :serial)]}
                         {:create-type [:account-role
                                        :as (:enum "admin" "customer")]}
                         [{:alter-type
                           [:account-role :add-value "basic" :before "admin"]}
                          {:alter-type
                           [:account-role :add-value "developer" :before "customer"]}
                          {:alter-type
                           [:account-role :add-value "support" :after "customer"]}
                          {:alter-type
                           [:account-role :add-value "other" :after "support"]}])
        expected-q-sql (list
                         ["CREATE TABLE account (id SERIAL)"]
                         ["CREATE TYPE account_role AS ENUM('admin', 'customer')"]
                         [["ALTER TYPE account_role ADD VALUE 'basic' BEFORE 'admin'"]
                          ["ALTER TYPE account_role ADD VALUE 'developer' BEFORE 'customer'"]
                          ["ALTER TYPE account_role ADD VALUE 'support' AFTER 'customer'"]
                          ["ALTER TYPE account_role ADD VALUE 'other' AFTER 'support'"]])]

    (is (= {:new-actions expected-actions
            :q-edn expected-q-edn
            :q-sql expected-q-sql}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions existing-actions
             :existing-models changed-models})))

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


; ERRORS

(deftest test-types-enum-model-validation-strict-keys-error
  (let [params {:existing-models
                {:account {:fields [[:id :integer {:null false}]
                                    [:rol [:enum :account-role]]]
                           :types [[:account-role
                                    :enum
                                    {:choices ["admin" "customer"]
                                     :wrong true}]]}}}]
    (is (= (str "-- MODEL ERROR -------------------------------------\n\n"
             "Options of type :account.types/account-role have extra keys.\n\n")
          (with-out-str
            (test-util/make-migration! params))))))


(deftest test-types-enum-model-validation-duplicated-type-across-models-error
  (let [params {:existing-models
                {:account {:fields [[:id :integer {:null false}]]
                           :types [[:role :enum {:choices ["admin" "customer"]}]]}
                 :feed {:fields [[:id :integer {:null false}]]
                        :types [[:role :enum {:choices ["admin"]}]]}}}]
    (is (= (str "-- MODEL ERROR -------------------------------------\n\n"
             "Models have duplicated types: [:role].\n\n")
          (with-out-str
            (test-util/make-migration! params))))))


(deftest test-types-enum-model-validation-choices-error
  (testing "do not allow empty value in type choices option"
    (let [params {:existing-models
                  {:account {:fields [[:id :integer {:null false}]]
                             :types [[:role :enum {:choices []}]]}}}]
      (is (= (str "-- MODEL ERROR -------------------------------------\n\n"
               "Enum type :account.types/role should contain at least one choice.\n\n")
            (with-out-str
              (test-util/make-migration! params))))))

  (testing "type option choices must be a vector"
    (let [params {:existing-models
                  {:account {:fields [[:id :integer {:null false}]]
                             :types [[:role :enum {:choices '("admin")}]]}}}]
      (is (= (str "-- MODEL ERROR -------------------------------------\n\n"
               "Choices definition of type :account.types/role should be a vector of strings.\n\n"
               "  (\"admin\")\n\n")
            (with-out-str
              (test-util/make-migration! params))))))

  (testing "type option choices must not have duplicated values"
    (let [params {:existing-models
                  {:account {:fields [[:id :integer {:null false}]]
                             :types [[:role :enum {:choices ["admin" "admin"]}]]}}}]
      (is (= (str "-- MODEL ERROR -------------------------------------\n\n"
               "Enum type definition :account.types/role has duplicated choices.\n\n"
               "  [\"admin\" \"admin\"]\n\n")
            (with-out-str
              (test-util/make-migration! params))))))

  (testing "type option must contain choices"
    (let [params {:existing-models
                  {:account {:fields [[:id :integer {:null false}]]
                             :types [[:role :enum {}]]}}}]
      (is (= (str "-- MODEL ERROR -------------------------------------\n\n"
               "Enum type :account.types/role misses :choices option.\n\n")
            (with-out-str
              (test-util/make-migration! params))))))

  (testing "type must contain options with choices"
    (let [params {:existing-models
                  {:account {:fields [[:id :integer {:null false}]]
                             :types [[:role :enum]]}}}]
      (is (= (str "-- MODEL ERROR -------------------------------------\n\n"
               "Enum type :account.types/role misses :choices option.\n\n")
            (with-out-str
              (test-util/make-migration! params)))))))


(deftest test-types-enum-model-validation-type-definition-error
  (testing "invalid type definition"
    (let [params {:existing-models
                  {:account {:fields [[:id :integer {:null false}]]
                             :types [[:role]]}}}]
      (is (= (str "-- MODEL ERROR -------------------------------------\n\n"
               "Type :account.types/role must contain one of definition [:enum].\n\n")
            (with-out-str
              (test-util/make-migration! params))))))

  (testing "empty type"
    (let [params {:existing-models
                  {:account {:fields [[:id :integer {:null false}]]
                             :types [[]]}}}]
      (is (= (str "-- MODEL ERROR -------------------------------------\n\n"
               "Type definition in model :account must contain a name.\n\n")
            (with-out-str
              (test-util/make-migration! params)))))))


(deftest test-types-enum-migrations-choices-error
  (testing "do not allow removing existing choice values"
    (let [params {:existing-actions [{:action :create-table
                                      :model-name :account
                                      :fields {:id {:type :serial}}}
                                     {:action :create-type
                                      :model-name :account
                                      :type-name :role
                                      :options {:type :enum
                                                :choices ["admin" "customer"]}}]
                  :existing-models
                  {:account {:fields [[:id :serial]]
                             :types [[:role :enum {:choices ["admin" "test"]}]]}}}]
      (is (= (str "-- MIGRATION ERROR -------------------------------------\n\n"
               "It is not possible to remove existing choices of enum type :account/role.\n\n"
               "  {:choices {:from [\"admin\" \"customer\"], :to [\"admin\" \"test\"]}}\n\n")
            (with-out-str
              (test-util/make-migration! params))))))

  (testing "do not allow to re-order existing choice values"
    (let [params {:existing-actions [{:action :create-table
                                      :model-name :account
                                      :fields {:id {:type :serial}}}
                                     {:action :create-type
                                      :model-name :account
                                      :type-name :role
                                      :options {:type :enum
                                                :choices ["admin" "customer"]}}]
                  :existing-models
                  {:account {:fields [[:id :serial]]
                             :types [[:role :enum {:choices ["customer" "admin"]}]]}}}]
      (is (= (str "-- MIGRATION ERROR -------------------------------------\n\n"
               "It is not possible to re-order existing choices of enum type :account/role.\n\n"
               "  {:choices {:from [\"admin\" \"customer\"], :to [\"customer\" \"admin\"]}}\n\n")
            (with-out-str
              (test-util/make-migration! params)))))))


(deftest test-types-enum-make-migration-alter-type-ok
  (testing "do not allow empty value in type choices option"
    (let [params {:existing-models
                  {:account {:fields [[:id :integer]
                                      [:role [:enum :account-role]]]
                             :types [[:account-role :enum
                                      {:choices ["admin" "customer" "support"]}]]}}

                  :existing-actions '({:action :create-table
                                       :model-name :account
                                       :fields {:id {:type :serial}}}
                                      {:action :create-type
                                       :model-name :account
                                       :type-name :account-role
                                       :options {:type :enum
                                                 :choices ["admin" "customer"]}}
                                      {:action :alter-type
                                       :model-name :account
                                       :type-name :account-role
                                       :options {:type :enum
                                                 :choices ["admin" "customer"]}
                                       :changes {:choices {:from ["admin" "customer"]
                                                           :to ["admin" "customer" "support"]}}})}]
      (is (= (str "Created migration: test/automigrate/migrations/0001_auto_alter_type_account_role_etc.edn\n"
               "Actions:\n"
               "  - alter type account_role\n"
               "  - add column role to account\n"
               "  - alter column id in account\n")
            (with-out-str
              (test-util/make-migration! params)))))))
