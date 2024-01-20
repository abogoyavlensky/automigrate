(ns automigrate.fields-keyword-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
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
      (is (= [{:character_maximum_length nil
               :column_default "nextval('account_id_seq'::regclass)"
               :column_name "id"
               :data_type "bigint"
               :udt_name "int8"
               :is_nullable "NO"
               :table_name "account"}]
            (test-util/get-table-schema-from-db config/DATABASE-CONN "account"))))))


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
                      {:add-column '(:number :bigserial [:constraint :account-number-key] :unique)
                       :alter-table :account}]
              :q-sql [["CREATE TABLE account (id SERIAL)"]
                      ["ALTER TABLE account ADD COLUMN number BIGSERIAL CONSTRAINT account_number_key UNIQUE"]]}
            (test-util/perform-make-and-migrate!
              {:jdbc-url config/DATABASE-CONN
               :existing-actions existing-actions
               :existing-models models}))))

    (testing "check actual db changes"
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
            (test-util/get-table-schema-from-db config/DATABASE-CONN "account"))))))


(deftest test-fields-smallserial-create-table-ok
  (let [models {:account
                {:fields [[:id :smallserial]]}}]

    (testing "check generated actions, queries edn and sql from all actions"
      (is (= {:new-actions [{:action :create-table
                             :fields {:id {:type :smallserial}}
                             :model-name :account}]
              :q-edn [{:create-table [:account]
                       :with-columns ['(:id :smallserial)]}]
              :q-sql [["CREATE TABLE account (id SMALLSERIAL)"]]}
            (test-util/perform-make-and-migrate!
              {:jdbc-url config/DATABASE-CONN
               :existing-actions []
               :existing-models models}))))

    (testing "check actual db changes"
      (is (= [{:character_maximum_length nil
               :column_default "nextval('account_id_seq'::regclass)"
               :column_name "id"
               :data_type "smallint"
               :udt_name "int2"
               :is_nullable "NO"
               :table_name "account"}]
            (test-util/get-table-schema-from-db config/DATABASE-CONN "account"))))))


(deftest ^:eftest/slow test-fields-kw-create-table-ok
  (doseq [{:keys [field-type field-name udt]} [{:field-type :box}
                                               {:field-type :bytea}
                                               {:field-type :cidr}
                                               {:field-type :circle}
                                               {:field-type :double-precision
                                                :field-name "double precision"
                                                :udt "float8"}
                                               {:field-type :inet}
                                               {:field-type :line}
                                               {:field-type :lseg}
                                               {:field-type :macaddr}
                                               {:field-type :macaddr8}
                                               {:field-type :money}
                                               {:field-type :path}
                                               {:field-type :pg_lsn}
                                               {:field-type :pg_snapshot}
                                               {:field-type :polygon}
                                               {:field-type :tsquery}
                                               {:field-type :tsvector}
                                               {:field-type :txid_snapshot}
                                               {:field-type :xml}]]
    (test-util/drop-all-tables config/DATABASE-CONN)
    (test-util/delete-recursively config/MIGRATIONS-DIR)

    (testing "check generated actions, queries edn and sql from all actions"
      (is (= {:new-actions (list {:action :create-table
                                  :fields {:thing {:type field-type}}
                                  :model-name :account})
              :q-edn [{:create-table [:account]
                       :with-columns [(list :thing field-type)]}]
              :q-sql [[(format "CREATE TABLE account (thing %s)"
                         (str/upper-case
                           (or field-name (name field-type))))]]}
            (test-util/perform-make-and-migrate!
              {:jdbc-url config/DATABASE-CONN
               :existing-actions []
               :existing-models {:account
                                 {:fields [[:thing field-type]]}}}))))

    (testing "check actual db changes"
      (is (= [{:character_maximum_length nil
               :column_default nil
               :column_name "thing"
               :data_type (or field-name (name field-type))
               :udt_name (or udt (name field-type))
               :is_nullable "YES"
               :table_name "account"}]
            (test-util/get-table-schema-from-db config/DATABASE-CONN "account"))))))


(deftest ^:eftest/slow test-fields-kw-alter-column-ok
  (doseq [{:keys [field-type field-name udt]} [{:field-type :box}
                                               {:field-type :bytea}
                                               {:field-type :cidr}
                                               {:field-type :circle}
                                               {:field-type :double-precision
                                                :field-name "double precision"
                                                :udt "float8"}
                                               {:field-type :inet}
                                               {:field-type :line}
                                               {:field-type :lseg}
                                               {:field-type :macaddr}
                                               {:field-type :macaddr8}
                                               {:field-type :money}
                                               {:field-type :path}
                                               {:field-type :pg_lsn}
                                               {:field-type :pg_snapshot}
                                               {:field-type :polygon}
                                               {:field-type :tsquery}
                                               {:field-type :tsvector}
                                               {:field-type :txid_snapshot}
                                               {:field-type :xml}]]
    (test-util/drop-all-tables config/DATABASE-CONN)
    (test-util/delete-recursively config/MIGRATIONS-DIR)

    (testing "check generated actions, queries edn and sql from all actions"
      (is (= {:new-actions (list {:action :alter-column
                                  :changes {:null {:from :EMPTY
                                                   :to false}}
                                  :field-name :thing
                                  :model-name :account
                                  :options {:null false
                                            :type field-type}})
              :q-edn [{:create-table [:account]
                       :with-columns [(list :thing field-type)]}
                      {:alter-table '(:account
                                       {:alter-column [:thing :set [:not nil]]})}]
              :q-sql [[(format "CREATE TABLE account (thing %s)"
                         (str/upper-case (or field-name (name field-type))))]
                      ["ALTER TABLE account ALTER COLUMN thing SET NOT NULL"]]}
            (test-util/perform-make-and-migrate!
              {:jdbc-url config/DATABASE-CONN
               :existing-actions [{:action :create-table
                                   :fields {:thing {:type field-type}}
                                   :model-name :account}]
               :existing-models {:account
                                 {:fields [[:thing field-type {:null false}]]}}}))))

    (testing "check actual db changes"
      (is (= [{:character_maximum_length nil
               :column_default nil
               :column_name "thing"
               :data_type (or field-name (name field-type))
               :udt_name (or udt (name field-type))
               :is_nullable "NO"
               :table_name "account"}]
            (test-util/get-table-schema-from-db config/DATABASE-CONN "account"))))))
