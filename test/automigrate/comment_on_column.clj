(ns automigrate.comment-on-column
  (:require [clojure.test :refer :all]
            [automigrate.testing-util :as test-util]
            [automigrate.testing-config :as config]))


(use-fixtures :each
  (test-util/with-drop-tables config/DATABASE-CONN)
  (test-util/with-delete-dir config/MIGRATIONS-DIR-FULL))


(deftest test-action-create-table-with-comment-on-column-ok
  (testing "check generated actions, queries edn and sql from all actions"
    (is (= {:new-actions (list {:action :create-table
                                :fields {:id {:type :serial}
                                         :name {:type :varchar
                                                :comment "The name of a user"}}
                                :model-name :users})
            :q-edn [(list
                      {:create-table [:users]
                       :with-columns ['(:id :serial)
                                      '(:name :varchar)]}
                      [:raw "COMMENT ON COLUMN users.name IS 'The name of a user'"])]
            :q-sql [[["CREATE TABLE users (id SERIAL, name VARCHAR)"]
                     ["COMMENT ON COLUMN users.name IS 'The name of a user'"]]]}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions []
             :existing-models {:users
                               {:fields [[:id :serial]
                                         [:name :varchar {:comment "The name of a user"}]]}}})))

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
               :column_name "name"
               :data_type "character varying"
               :udt_name "varchar"
               :is_nullable "YES"
               :table_name "users"}]
            (test-util/get-table-schema-from-db config/DATABASE-CONN "users")))

      (testing "test comment"
        (is (= [{:column_name "name"
                 :description "The name of a user"
                 :table_name "users"}]
              (test-util/get-column-comment config/DATABASE-CONN "users" "name")))))))


(deftest test-action-drop-table-with-comment-on-column-ok
  (testing "check generated actions, queries edn and sql from all actions"
    (is (= {:new-actions (list {:action :drop-table
                                :model-name :users})
            :q-edn [(list
                      {:create-table [:users]
                       :with-columns ['(:id :serial)
                                      '(:name :varchar)]}
                      [:raw "COMMENT ON COLUMN users.name IS 'The name of a user'"])
                    {:drop-table [:if-exists :users]}]
            :q-sql [[["CREATE TABLE users (id SERIAL, name VARCHAR)"]
                     ["COMMENT ON COLUMN users.name IS 'The name of a user'"]]
                    ["DROP TABLE IF EXISTS users"]]}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions [{:action :create-table
                                 :fields {:id {:type :serial}
                                          :name {:type :varchar
                                                 :comment "The name of a user"}}
                                 :model-name :users}]
             :existing-models {}})))

    (testing "check actual db changes"
      (is (= []
            (test-util/get-table-schema-from-db config/DATABASE-CONN "users"))))

    (testing "test comment"
      (is (= []
            (test-util/get-column-comment config/DATABASE-CONN "users" "name"))))))


(deftest test-action-add-column-with-comment-on-column-ok
  (testing "check generated actions, queries edn and sql from all actions"
    (is (= {:new-actions (list {:action :add-column
                                :options {:type :varchar
                                          :comment "The name of a user"}
                                :field-name :name
                                :model-name :users})
            :q-edn [{:create-table [:users]
                     :with-columns ['(:id :serial)]}
                    [{:add-column '(:name :varchar)
                      :alter-table :users}
                     [:raw "COMMENT ON COLUMN users.name IS 'The name of a user'"]]]
            :q-sql [["CREATE TABLE users (id SERIAL)"]
                    [["ALTER TABLE users ADD COLUMN name VARCHAR"]
                     ["COMMENT ON COLUMN users.name IS 'The name of a user'"]]]}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions [{:action :create-table
                                 :fields {:id {:type :serial}}
                                 :model-name :users}]
             :existing-models {:users
                               {:fields [[:id :serial]
                                         [:name :varchar {:comment "The name of a user"}]]}}})))

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
               :column_name "name"
               :data_type "character varying"
               :udt_name "varchar"
               :is_nullable "YES"
               :table_name "users"}]
            (test-util/get-table-schema-from-db config/DATABASE-CONN "users"))))

    (testing "test comment"
      (is (= [{:column_name "name"
               :description "The name of a user"
               :table_name "users"}]
            (test-util/get-column-comment config/DATABASE-CONN "users" "name"))))))


(deftest test-action-drop-column-with-comment-on-column-ok
  (testing "check generated actions, queries edn and sql from all actions"
    (is (= {:new-actions (list {:action :drop-column
                                :field-name :name
                                :model-name :users})
            :q-edn [{:create-table [:users]
                     :with-columns ['(:id :serial)]}
                    [{:add-column '(:name :varchar)
                      :alter-table :users}
                     [:raw "COMMENT ON COLUMN users.name IS 'The name of a user'"]]
                    {:drop-column :name
                     :alter-table :users}]
            :q-sql [["CREATE TABLE users (id SERIAL)"]
                    [["ALTER TABLE users ADD COLUMN name VARCHAR"]
                     ["COMMENT ON COLUMN users.name IS 'The name of a user'"]]
                    ["ALTER TABLE users DROP COLUMN name"]]}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions [{:action :create-table
                                 :fields {:id {:type :serial}}
                                 :model-name :users}
                                {:action :add-column
                                 :options {:type :varchar
                                           :comment "The name of a user"}
                                 :field-name :name
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

    (testing "test comment"
      (is (= []
            (test-util/get-column-comment config/DATABASE-CONN "users" "name"))))))


(deftest test-action-alter-column-add-comment-on-column-ok
  (testing "check generated actions, queries edn and sql from all actions"
    (is (= {:new-actions (list {:action :alter-column
                                :options {:type :varchar
                                          :comment "The name of a user"}
                                :changes {:comment {:from :EMPTY
                                                    :to "The name of a user"}}
                                :field-name :name
                                :model-name :users})
            :q-edn [{:create-table [:users]
                     :with-columns ['(:id :serial)
                                    '(:name :varchar)]}
                    [[:raw "COMMENT ON COLUMN users.name IS 'The name of a user'"]]]
            :q-sql [["CREATE TABLE users (id SERIAL, name VARCHAR)"]
                    [["COMMENT ON COLUMN users.name IS 'The name of a user'"]]]}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions [{:action :create-table
                                 :fields {:id {:type :serial}
                                          :name {:type :varchar}}
                                 :model-name :users}]
             :existing-models {:users
                               {:fields [[:id :serial]
                                         [:name :varchar {:comment "The name of a user"}]]}}})))

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
               :column_name "name"
               :data_type "character varying"
               :udt_name "varchar"
               :is_nullable "YES"
               :table_name "users"}]
            (test-util/get-table-schema-from-db config/DATABASE-CONN "users"))))

    (testing "test comment"
      (is (= [{:column_name "name"
               :description "The name of a user"
               :table_name "users"}]
            (test-util/get-column-comment config/DATABASE-CONN "users" "name"))))))


(deftest test-action-alter-column-with-comment-on-column-ok
  (testing "check generated actions, queries edn and sql from all actions"
    (is (= {:new-actions (list {:action :alter-column
                                :options {:type :varchar
                                          :comment "Updated comment"}
                                :changes {:comment {:to "Updated comment"
                                                    :from "The name of a user"}}
                                :field-name :name
                                :model-name :users})
            :q-edn [(list
                      {:create-table [:users]
                       :with-columns ['(:id :serial)
                                      '(:name :varchar)]}
                      [:raw "COMMENT ON COLUMN users.name IS 'The name of a user'"])
                    [[:raw "COMMENT ON COLUMN users.name IS 'Updated comment'"]]]
            :q-sql [[["CREATE TABLE users (id SERIAL, name VARCHAR)"]
                     ["COMMENT ON COLUMN users.name IS 'The name of a user'"]]
                    [["COMMENT ON COLUMN users.name IS 'Updated comment'"]]]}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions [{:action :create-table
                                 :fields {:id {:type :serial}
                                          :name {:type :varchar
                                                 :comment "The name of a user"}}
                                 :model-name :users}]
             :existing-models {:users
                               {:fields [[:id :serial]
                                         [:name :varchar {:comment "Updated comment"}]]}}})))

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
               :column_name "name"
               :data_type "character varying"
               :udt_name "varchar"
               :is_nullable "YES"
               :table_name "users"}]
            (test-util/get-table-schema-from-db config/DATABASE-CONN "users"))))

    (testing "test comment"
      (is (= [{:column_name "name"
               :description "Updated comment"
               :table_name "users"}]
            (test-util/get-column-comment config/DATABASE-CONN "users" "name"))))))


(deftest test-action-alter-column-with-comment-and-another-option-ok
  (testing "check generated actions, queries edn and sql from all actions"
    (is (= {:new-actions (list {:action :alter-column
                                :options {:type :varchar
                                          :null false
                                          :comment "Updated comment"}
                                :changes {:comment {:to "Updated comment"
                                                    :from "The name of a user"}
                                          :null {:from :EMPTY
                                                 :to false}}
                                :field-name :name
                                :model-name :users})
            :q-edn [(list
                      {:create-table [:users]
                       :with-columns ['(:id :serial)
                                      '(:name :varchar)]}
                      [:raw "COMMENT ON COLUMN users.name IS 'The name of a user'"])
                    [{:alter-table '(:users {:alter-column [:name :set [:not nil]]})}
                     [:raw "COMMENT ON COLUMN users.name IS 'Updated comment'"]]]
            :q-sql [[["CREATE TABLE users (id SERIAL, name VARCHAR)"]
                     ["COMMENT ON COLUMN users.name IS 'The name of a user'"]]
                    [["ALTER TABLE users ALTER COLUMN name SET NOT NULL"]
                     ["COMMENT ON COLUMN users.name IS 'Updated comment'"]]]}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions [{:action :create-table
                                 :fields {:id {:type :serial}
                                          :name {:type :varchar
                                                 :comment "The name of a user"}}
                                 :model-name :users}]
             :existing-models {:users
                               {:fields [[:id :serial]
                                         [:name :varchar {:null false
                                                          :comment "Updated comment"}]]}}})))

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
               :column_name "name"
               :data_type "character varying"
               :udt_name "varchar"
               :is_nullable "NO"
               :table_name "users"}]
            (test-util/get-table-schema-from-db config/DATABASE-CONN "users"))))

    (testing "test comment"
      (is (= [{:column_name "name"
               :description "Updated comment"
               :table_name "users"}]
            (test-util/get-column-comment config/DATABASE-CONN "users" "name"))))))


(deftest test-action-alter-column-drop-comment-on-column-ok
  (testing "check generated actions, queries edn and sql from all actions"
    (is (= {:new-actions (list {:action :alter-column
                                :options {:type :varchar}
                                :changes {:comment {:to :EMPTY
                                                    :from "The name of a user"}}
                                :field-name :name
                                :model-name :users})
            :q-edn [(list
                      {:create-table [:users]
                       :with-columns ['(:id :serial)
                                      '(:name :varchar)]}
                      [:raw "COMMENT ON COLUMN users.name IS 'The name of a user'"])
                    [[:raw "COMMENT ON COLUMN users.name IS NULL"]]]
            :q-sql [[["CREATE TABLE users (id SERIAL, name VARCHAR)"]
                     ["COMMENT ON COLUMN users.name IS 'The name of a user'"]]
                    [["COMMENT ON COLUMN users.name IS NULL"]]]}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions [{:action :create-table
                                 :fields {:id {:type :serial}
                                          :name {:type :varchar
                                                 :comment "The name of a user"}}
                                 :model-name :users}]
             :existing-models {:users
                               {:fields [[:id :serial]
                                         [:name :varchar]]}}})))

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
               :column_name "name"
               :data_type "character varying"
               :udt_name "varchar"
               :is_nullable "YES"
               :table_name "users"}]
            (test-util/get-table-schema-from-db config/DATABASE-CONN "users"))))

    (testing "test comment"
      (is (= []
            (test-util/get-column-comment config/DATABASE-CONN "users" "name"))))))


(deftest test-commment-on-column-errors
  (testing "comment can't be integer"
    (is (= (str "-- MODEL ERROR -------------------------------------\n\n"
             "Option :comment of field :account/thing should be string.\n\n"
             "  {:comment 1}\n\n")
          (with-out-str
            (test-util/make-migration!
              {:existing-models
               {:account {:fields [[:thing :interval {:comment 1}]]}}})))))

  (testing "comment can't be nil"
    (is (= (str "-- MODEL ERROR -------------------------------------\n\n"
             "Option :comment of field :account/thing should be string.\n\n"
             "  {:comment nil}\n\n")
          (with-out-str
            (test-util/make-migration!
              {:existing-models
               {:account {:fields [[:thing :interval {:comment nil}]]}}})))))

  (testing "comment can't be an empty string"
    (is (= (str "-- MODEL ERROR -------------------------------------\n\n"
             "Option :comment of field :account/thing should be string.\n\n"
             "  {:comment \"\"}\n\n")
          (with-out-str
            (test-util/make-migration!
              {:existing-models
               {:account {:fields [[:thing :interval {:comment ""}]]}}}))))))
