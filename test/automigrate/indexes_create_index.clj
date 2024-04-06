(ns automigrate.indexes-create-index
  (:require [clojure.test :refer :all]
            [automigrate.testing-util :as test-util]
            [automigrate.testing-config :as config]))


(use-fixtures :each
  (test-util/with-drop-tables config/DATABASE-CONN)
  (test-util/with-delete-dir config/MIGRATIONS-DIR-FULL))


(deftest test-action-create-partial-index-edn-condition-ok
  (testing "check generated actions, queries edn and sql from all actions"
    (is (= {:new-actions (list
                           {:action :create-table
                            :fields {:amount {:type :integer}
                                     :created-at {:type :timestamp}
                                     :id {:type :serial}}
                            :model-name :users}
                           {:action :create-index
                            :index-name :users-created-at-idx
                            :options {:type :btree
                                      :fields [:created-at]
                                      :where [:> :amount 10]}
                            :model-name :users})
            :q-edn [{:create-table [:users]
                     :with-columns ['(:id :serial)
                                    '(:amount :integer)
                                    '(:created-at :timestamp)]}
                    {:create-index '(:users-created-at-idx :on :users
                                      :using (:btree :created-at)
                                      :where [:> :amount 10])}]
            :q-sql [["CREATE TABLE users (id SERIAL, amount INTEGER, created_at TIMESTAMP)"]
                    ["CREATE INDEX users_created_at_idx ON USERS USING BTREE(created_at) WHERE amount > 10"]]}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions []
             :existing-models
             {:users
              {:fields [[:id :serial]
                        [:amount :integer]
                        [:created-at :timestamp]]
               :indexes [[:users-created-at-idx :btree {:fields [:created-at]
                                                        :where [:> :amount 10]}]]}}}))))

  (testing "check actual db changes"
    (is (= [{:character_maximum_length nil
             :column_default "nextval('users_id_seq'::regclass)"
             :column_name "id"
             :data_type "integer"
             :udt_name "int4"
             :is_nullable "NO"
             :table_name "users"}
            {:character_maximum_length nil
             :column_default nil
             :column_name "amount"
             :data_type "integer"
             :udt_name "int4"
             :is_nullable "YES"
             :table_name "users"}
            {:character_maximum_length nil
             :column_default nil
             :column_name "created_at"
             :data_type "timestamp without time zone"
             :udt_name "timestamp"
             :is_nullable "YES"
             :table_name "users"}]
          (test-util/get-table-schema-from-db config/DATABASE-CONN "users"))))

  (testing "test indexes"
    (is (= [{:indexdef (str "CREATE INDEX users_created_at_idx ON public.users"
                         " USING btree (created_at) WHERE (amount > 10)")
             :indexname "users_created_at_idx"
             :schemaname "public"
             :tablename "users"
             :tablespace nil}]
          (test-util/get-indexes config/DATABASE-CONN "users")))))


(deftest test-action-alter-index-to-partial-edn-condition-ok
  (testing "check generated actions, queries edn and sql from all actions"
    (is (= {:new-actions (list
                           {:action :alter-index
                            :index-name :users-created-at-idx
                            :options {:type :btree
                                      :fields [:created-at]
                                      :where [:> :amount 10]}
                            :model-name :users})
            :q-edn [{:create-table [:users]
                     :with-columns ['(:id :serial)
                                    '(:amount :integer)
                                    '(:created-at :timestamp)]}
                    {:create-index [:users-created-at-idx :on :users
                                    :using '(:btree :created-at)]}
                    [{:drop-index :users-created-at-idx}
                     {:create-index '(:users-created-at-idx :on :users
                                       :using (:btree :created-at)
                                       :where [:> :amount 10])}]]
            :q-sql [["CREATE TABLE users (id SERIAL, amount INTEGER, created_at TIMESTAMP)"]
                    ["CREATE INDEX users_created_at_idx ON USERS USING BTREE(created_at)"]
                    [["DROP INDEX users_created_at_idx"]
                     ["CREATE INDEX users_created_at_idx ON USERS USING BTREE(created_at) WHERE amount > 10"]]]}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions [{:action :create-table
                                 :fields {:id {:type :serial}
                                          :amount {:type :integer}
                                          :created-at {:type :timestamp}}
                                 :model-name :users}
                                {:action :create-index
                                 :index-name :users-created-at-idx
                                 :options {:type :btree
                                           :fields [:created-at]}
                                 :model-name :users}]

             :existing-models
             {:users
              {:fields [[:id :serial]
                        [:amount :integer]
                        [:created-at :timestamp]]
               :indexes [[:users-created-at-idx :btree {:fields [:created-at]
                                                        :where [:> :amount 10]}]]}}}))))

  (testing "check actual db changes"
    (is (= [{:character_maximum_length nil
             :column_default "nextval('users_id_seq'::regclass)"
             :column_name "id"
             :data_type "integer"
             :udt_name "int4"
             :is_nullable "NO"
             :table_name "users"}
            {:character_maximum_length nil
             :column_default nil
             :column_name "amount"
             :data_type "integer"
             :udt_name "int4"
             :is_nullable "YES"
             :table_name "users"}
            {:character_maximum_length nil
             :column_default nil
             :column_name "created_at"
             :data_type "timestamp without time zone"
             :udt_name "timestamp"
             :is_nullable "YES"
             :table_name "users"}]
          (test-util/get-table-schema-from-db config/DATABASE-CONN "users"))))

  (testing "test indexes"
    (is (= [{:indexdef (str "CREATE INDEX users_created_at_idx ON public.users"
                         " USING btree (created_at) WHERE (amount > 10)")
             :indexname "users_created_at_idx"
             :schemaname "public"
             :tablename "users"
             :tablespace nil}]
          (test-util/get-indexes config/DATABASE-CONN "users")))))


(deftest test-action-alter-index-from-partial-to-partial-raw-condition-ok
  (testing "check generated actions, queries edn and sql from all actions"
    (is (= {:new-actions (list
                           {:action :alter-index
                            :index-name :users-created-at-idx
                            :options {:type :btree
                                      :fields [:created-at]
                                      :where [:raw "amount > 0 AND id IS NOT NULL"]}
                            :model-name :users})
            :q-edn [{:create-table [:users]
                     :with-columns ['(:id :serial)
                                    '(:amount :integer)
                                    '(:created-at :timestamp)]}
                    {:create-index '(:users-created-at-idx :on :users
                                      :using (:btree :created-at)
                                      :where [:> :amount 10])}
                    [{:drop-index :users-created-at-idx}
                     {:create-index '(:users-created-at-idx :on :users
                                       :using (:btree :created-at)
                                       :where [:raw "amount > 0 AND id IS NOT NULL"])}]]
            :q-sql [["CREATE TABLE users (id SERIAL, amount INTEGER, created_at TIMESTAMP)"]
                    [(str "CREATE INDEX users_created_at_idx ON USERS"
                       " USING BTREE(created_at) WHERE amount > 10")]
                    [["DROP INDEX users_created_at_idx"]
                     [(str "CREATE INDEX users_created_at_idx ON USERS"
                        " USING BTREE(created_at) WHERE amount > 0 AND id IS NOT NULL")]]]}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions [{:action :create-table
                                 :fields {:id {:type :serial}
                                          :amount {:type :integer}
                                          :created-at {:type :timestamp}}
                                 :model-name :users}
                                {:action :create-index
                                 :index-name :users-created-at-idx
                                 :options {:type :btree
                                           :fields [:created-at]
                                           :where [:> :amount 10]}
                                 :model-name :users}]

             :existing-models
             {:users
              {:fields [[:id :serial]
                        [:amount :integer]
                        [:created-at :timestamp]]
               :indexes [[:users-created-at-idx
                          :btree
                          {:fields [:created-at]
                           :where [:raw "amount > 0 AND id IS NOT NULL"]}]]}}}))))

  (testing "check actual db changes"
    (is (= [{:character_maximum_length nil
             :column_default "nextval('users_id_seq'::regclass)"
             :column_name "id"
             :data_type "integer"
             :udt_name "int4"
             :is_nullable "NO"
             :table_name "users"}
            {:character_maximum_length nil
             :column_default nil
             :column_name "amount"
             :data_type "integer"
             :udt_name "int4"
             :is_nullable "YES"
             :table_name "users"}
            {:character_maximum_length nil
             :column_default nil
             :column_name "created_at"
             :data_type "timestamp without time zone"
             :udt_name "timestamp"
             :is_nullable "YES"
             :table_name "users"}]
          (test-util/get-table-schema-from-db config/DATABASE-CONN "users"))))

  (testing "test indexes"
    (is (= [{:indexdef (str "CREATE INDEX users_created_at_idx ON public.users"
                         " USING btree (created_at) WHERE ((amount > 0) AND (id IS NOT NULL))")
             :indexname "users_created_at_idx"
             :schemaname "public"
             :tablename "users"
             :tablespace nil}]
          (test-util/get-indexes config/DATABASE-CONN "users")))))


(deftest test-create-index-invalid-option-where-errors
  (testing "invalid value in :where option"
    (is (= (str "-- MODEL ERROR -------------------------------------\n\n"
             "Option :where of index :account.indexes/users-created-at-idx should a not empty vector.\n\n")
          (with-out-str
            (test-util/make-migration!
              {:existing-models
               {:account
                {:fields [[:id :serial]]
                 :indexes [[:users-created-at-idx :btree {:fields [:created-at]
                                                          :where "INVALID"}]]}}})))))

  (testing "empty vector in :where option"
    (is (= (str "-- MODEL ERROR -------------------------------------\n\n"
             "Option :where of index :account.indexes/users-created-at-idx should a not empty vector.\n\n")
          (with-out-str
            (test-util/make-migration!
              {:existing-models
               {:account
                {:fields [[:id :serial]]
                 :indexes [[:users-created-at-idx :btree {:fields [:created-at]
                                                          :where []}]]}}}))))))
