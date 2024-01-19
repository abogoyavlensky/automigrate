(ns automigrate.constraints-test
  (:require [clojure.test :refer :all]
            [automigrate.testing-util :as test-util]
            [automigrate.testing-config :as config]))


(use-fixtures :each
  (test-util/with-drop-tables config/DATABASE-CONN)
  (test-util/with-delete-dir config/MIGRATIONS-DIR))


(deftest ^:eftest/slow test-create-table-with-primary-key-constraint
  (testing "check generated actions, queries edn and sql from all actions"
    (is (= {:new-actions (list {:action :create-table
                                :fields {:id {:type :serial
                                              :primary-key true}}
                                :model-name :users})
            :q-edn [{:create-table [:users]
                     :with-columns ['(:id :serial [:constraint :users-pkey] :primary-key)]}]
            :q-sql [["CREATE TABLE users (id SERIAL CONSTRAINT users_pkey PRIMARY KEY)"]]}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions []
             :existing-models {:users
                               {:fields [[:id :serial {:primary-key true}]]}}})))

    (testing "check actual db changes"
      (is (= [{:character_maximum_length nil
               :column_default "nextval('users_id_seq'::regclass)"
               :column_name "id"
               :data_type "integer"
               :udt_name "int4"
               :is_nullable "NO"
               :table_name "users"}]
            (test-util/get-table-schema-from-db config/DATABASE-CONN "users"))))

    (testing "test constraints in db"
      (is (= [{:colname "id"
               :constraint_name "users_pkey"
               :constraint_type "PRIMARY KEY"
               :table_name "users"}]
            (test-util/get-constraints "users"))))))


(deftest ^:eftest/slow test-alter-table-with-primary-key-constraint-added
  (testing "check generated actions, queries edn and sql from all actions"
    (is (= {:new-actions (list {:action :alter-column
                                :options {:type :serial
                                          :primary-key true}
                                :changes {:primary-key {:from :EMPTY
                                                        :to true}}
                                :field-name :id
                                :model-name :users})
            :q-edn [{:create-table [:users]
                     :with-columns ['(:id :serial)]}
                    {:alter-table '(:users {:add-constraint [:users-pkey [:primary-key :id]]})}]
            :q-sql [["CREATE TABLE users (id SERIAL)"]
                    ["ALTER TABLE users ADD CONSTRAINT users_pkey PRIMARY KEY(id)"]]}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions [{:action :create-table
                                 :fields {:id {:type :serial}}
                                 :model-name :users}]
             :existing-models {:users
                               {:fields [[:id :serial {:primary-key true}]]}}})))

    (testing "check actual db changes"
      (is (= [{:character_maximum_length nil
               :column_default "nextval('users_id_seq'::regclass)"
               :column_name "id"
               :data_type "integer"
               :udt_name "int4"
               :is_nullable "NO"
               :table_name "users"}]
            (test-util/get-table-schema-from-db config/DATABASE-CONN "users"))))

    (testing "test constraints in db"
      (is (= [{:colname "id"
               :constraint_name "users_pkey"
               :constraint_type "PRIMARY KEY"
               :table_name "users"}]
            (test-util/get-constraints "users"))))))


(deftest ^:eftest/slow test-drop-primary-key-constraint
  (testing "check generated actions, queries edn and sql from all actions"
    (is (= {:new-actions (list {:action :alter-column
                                :options {:type :serial}
                                :changes {:primary-key {:from true
                                                        :to :EMPTY}}
                                :field-name :id
                                :model-name :users})
            :q-edn [{:create-table [:users]
                     :with-columns ['(:id :serial [:constraint :users-pkey] :primary-key)]}
                    {:alter-table '(:users {:drop-constraint :users-pkey})}]
            :q-sql [["CREATE TABLE users (id SERIAL CONSTRAINT users_pkey PRIMARY KEY)"]
                    ["ALTER TABLE users DROP CONSTRAINT users_pkey"]]}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions [{:action :create-table
                                 :fields {:id {:type :serial
                                               :primary-key true}}
                                 :model-name :users}]
             :existing-models {:users
                               {:fields [[:id :serial]]}}})))

    (testing "check actual db changes"
      (is (= [{:character_maximum_length nil
               :column_default "nextval('users_id_seq'::regclass)"
               :column_name "id"
               :data_type "integer"
               :udt_name "int4"
               :is_nullable "NO"
               :table_name "users"}]
            (test-util/get-table-schema-from-db config/DATABASE-CONN "users"))))

    (testing "test constraints in db"
      (is (= []
            (test-util/get-constraints "users"))))))
