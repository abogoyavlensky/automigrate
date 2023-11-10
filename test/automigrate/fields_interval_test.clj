(ns automigrate.fields-interval-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [automigrate.testing-util :as test-util]
            [automigrate.testing-config :as config]))


(use-fixtures :each
  (test-util/with-drop-tables config/DATABASE-CONN)
  (test-util/with-delete-dir config/MIGRATIONS-DIR))


(deftest ^:eftest/slow test-fields-interval-create-table-ok
  (let [field-type :interval]
    (test-util/drop-all-tables config/DATABASE-CONN)
    (test-util/delete-recursively config/MIGRATIONS-DIR)

    (testing "check generated actions, queries edn and sql from all actions"
      (is (= {:new-actions (list {:action :create-table
                                  :fields {:thing {:type field-type}
                                           :duration {:type [field-type 2]}}
                                  :model-name :account})
              :q-edn [{:create-table [:account]
                       :with-columns [(list :thing field-type)
                                      '(:duration [:raw "INTERVAL(2)"])]}]
              :q-sql [[(format "CREATE TABLE account (thing %s, duration INTERVAL(2))"
                         (str/upper-case (name field-type)))]]}
            (test-util/perform-make-and-migrate!
              {:jdbc-url config/DATABASE-CONN
               :existing-actions []
               :existing-models {:account
                                 {:fields [[:thing field-type]
                                           [:duration [field-type 2]]]}}})))

      (testing "check actual db changes"
        (testing "test actual db schema after applying the migration"
          (is (= [{:character_maximum_length nil
                   :column_default nil
                   :column_name "thing"
                   :data_type (name field-type)
                   :udt_name (name field-type)
                   :is_nullable "YES"
                   :table_name "account"
                   :datetime_precision 6}
                  {:character_maximum_length nil
                   :column_default nil
                   :column_name "duration"
                   :data_type (name field-type)
                   :udt_name (name field-type)
                   :is_nullable "YES"
                   :table_name "account"
                   :datetime_precision 2}]
                (test-util/get-table-schema-from-db
                  config/DATABASE-CONN
                  "account"
                  {:add-cols [:datetime_precision]}))))))))


(deftest test-fields-interval-alter-column-ok
  (doseq [{:keys [field-type]} [{:field-type :interval}]]
    (test-util/drop-all-tables config/DATABASE-CONN)
    (test-util/delete-recursively config/MIGRATIONS-DIR)

    (testing "check generated actions, queries edn and sql from all actions"
      (is (= {:new-actions (list {:action :alter-column
                                  :changes {:type {:from [field-type 3]
                                                   :to [field-type 6]}}
                                  :field-name :thing
                                  :model-name :account
                                  :options {:type [field-type 6]}})
              :q-edn [{:create-table [:account]
                       :with-columns ['(:id :serial)]}
                      {:add-column (list :thing [:raw "INTERVAL(3)"])
                       :alter-table :account}
                      {:alter-table (list :account
                                      {:alter-column [:thing :type [:raw "INTERVAL(6)"]]})}]
              :q-sql [["CREATE TABLE account (id SERIAL)"]
                      [(format "ALTER TABLE account ADD COLUMN thing %s(3)"
                         (str/upper-case (name field-type)))]
                      [(format "ALTER TABLE account ALTER COLUMN thing TYPE %s(6)"
                         (str/upper-case (name field-type)))]]}
            (test-util/perform-make-and-migrate!
              {:jdbc-url config/DATABASE-CONN
               :existing-actions [{:action :create-table
                                   :fields {:id {:type :serial}}
                                   :model-name :account}
                                  {:action :add-column
                                   :field-name :thing
                                   :model-name :account
                                   :options {:type [field-type 3]}}]
               :existing-models {:account
                                 {:fields [[:id :serial]
                                           [:thing [field-type 6]]]}}})))

      (testing "check actual db changes"
        (testing "test actual db schema after applying the migration"
          (is (= [{:character_maximum_length nil
                   :column_default "nextval('account_id_seq'::regclass)"
                   :column_name "id"
                   :data_type "integer"
                   :udt_name "int4"
                   :is_nullable "NO"
                   :table_name "account"
                   :datetime_precision nil}
                  {:character_maximum_length nil
                   :column_default nil
                   :column_name "thing"
                   :data_type (name field-type)
                   :udt_name (name field-type)
                   :is_nullable "YES"
                   :table_name "account"
                   :datetime_precision 6}]
                (test-util/get-table-schema-from-db
                  config/DATABASE-CONN
                  "account"
                  {:add-cols [:datetime_precision]}))))))))


(deftest ^:eftest/slow test-fields-interval-add-column-ok
  (doseq [{:keys [field-type field-name]} [{:field-type :interval}]]
    (test-util/drop-all-tables config/DATABASE-CONN)
    (test-util/delete-recursively config/MIGRATIONS-DIR)

    (testing "check generated actions, queries edn and sql from all actions"
      (is (= {:new-actions (list {:action :add-column
                                  :field-name :thing
                                  :model-name :account
                                  :options {:type [field-type 3]}})
              :q-edn [{:create-table [:account]
                       :with-columns ['(:id :serial)]}
                      {:add-column (list :thing [:raw "INTERVAL(3)"])
                       :alter-table :account}]
              :q-sql [["CREATE TABLE account (id SERIAL)"]
                      [(format "ALTER TABLE account ADD COLUMN thing %s(3)"
                         (str/upper-case (name field-type)))]]}
            (test-util/perform-make-and-migrate!
              {:jdbc-url config/DATABASE-CONN
               :existing-actions [{:action :create-table
                                   :fields {:id {:type :serial}}
                                   :model-name :account}]
               :existing-models {:account
                                 {:fields [[:id :serial]
                                           [:thing [field-type 3]]]}}})))

      (testing "check actual db changes"
        (testing "test actual db schema after applying the migration"
          (is (= [{:character_maximum_length nil
                   :column_default "nextval('account_id_seq'::regclass)"
                   :column_name "id"
                   :data_type "integer"
                   :udt_name "int4"
                   :is_nullable "NO"
                   :table_name "account"
                   :datetime_precision nil}
                  {:character_maximum_length nil
                   :column_default nil
                   :column_name "thing"
                   :data_type (or field-name (name field-type))
                   :udt_name (name field-type)
                   :is_nullable "YES"
                   :table_name "account"
                   :datetime_precision 3}]
                (test-util/get-table-schema-from-db
                  config/DATABASE-CONN
                  "account"
                  {:add-cols [:datetime_precision]}))))))))


(deftest ^:eftest/slow test-fields-interval-drop-column-ok
  (doseq [{:keys [field-type]} [{:field-type :interval}]]
    (test-util/drop-all-tables config/DATABASE-CONN)
    (test-util/delete-recursively config/MIGRATIONS-DIR)

    (testing "check generated actions, queries edn and sql from all actions"
      (is (= {:new-actions (list {:action :drop-column
                                  :field-name :thing
                                  :model-name :account})
              :q-edn [{:create-table [:account]
                       :with-columns ['(:id :serial)
                                      '(:thing [:raw "INTERVAL(3)"])]}
                      {:drop-column :thing
                       :alter-table :account}]
              :q-sql [["CREATE TABLE account (id SERIAL, thing INTERVAL(3))"]
                      [(format "ALTER TABLE account DROP COLUMN thing")]]}
            (test-util/perform-make-and-migrate!
              {:jdbc-url config/DATABASE-CONN
               :existing-actions [{:action :create-table
                                   :fields {:id {:type :serial}
                                            :thing {:type [field-type 3]}}
                                   :model-name :account}]
               :existing-models {:account
                                 {:fields [[:id :serial]]}}})))

      (testing "check actual db changes"
        (testing "test actual db schema after applying the migration"
          (is (= [{:character_maximum_length nil
                   :column_default "nextval('account_id_seq'::regclass)"
                   :column_name "id"
                   :data_type "integer"
                   :udt_name "int4"
                   :is_nullable "NO"
                   :table_name "account"
                   :datetime_precision nil}]
                (test-util/get-table-schema-from-db
                  config/DATABASE-CONN
                  "account"
                  {:add-cols [:datetime_precision]}))))))))


(deftest test-fields-enum-uses-existing-enum-type
  (let [params {:existing-models
                {:account
                 {:fields [[:thing [:interval]]]}}}]
    (is (= (str "-- MODEL ERROR -------------------------------------\n\n"
             "Invalid definition interval type of field :account/thing.\n\n"
             "  [:interval]\n\n")
          (test-util/get-make-migration-output params)))))
