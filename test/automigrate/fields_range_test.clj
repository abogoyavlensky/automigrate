(ns automigrate.fields-range-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [automigrate.testing-util :as test-util]
            [automigrate.testing-config :as config]))


(use-fixtures :each
  (test-util/with-drop-tables config/DATABASE-CONN)
  (test-util/with-delete-dir config/MIGRATIONS-DIR))


(deftest ^:eftest/slow test-fields-range-create-table-ok
  (doseq [{:keys [field-type]}
          ; interval
          [{:field-type :int4range}
           {:field-type :int4multirange}
           {:field-type :int8range}
           {:field-type :int8multirange}
           {:field-type :numrange}
           {:field-type :nummultirange}
           {:field-type :tsrange}
           {:field-type :tsmultirange}
           {:field-type :tstzrange}
           {:field-type :tstzmultirange}
           {:field-type :daterange}
           {:field-type :datemultirange}]
          :let [type-name (name field-type)]]
    (test-util/drop-all-tables config/DATABASE-CONN)
    (test-util/delete-recursively config/MIGRATIONS-DIR)

    (testing "check generated actions, queries edn and sql from all actions"
      (is (= {:new-actions (list {:action :create-table
                                  :fields {:thing {:type field-type}}
                                  :model-name :account})
              :q-edn [{:create-table [:account]
                       :with-columns
                       [(list :thing field-type)]}]
              :q-sql [[(format "CREATE TABLE account (thing %s)"
                         (str/upper-case type-name))]]}
            (test-util/perform-make-and-migrate!
              {:jdbc-url config/DATABASE-CONN
               :existing-actions []
               :existing-models {:account
                                 {:fields [[:thing field-type]]}}})))

      (testing "check actual db changes"
        (testing "test actual db schema after applying the migration"
          (is (= [{:character_maximum_length nil
                   :column_default nil
                   :column_name "thing"
                   :data_type type-name
                   :udt_name type-name
                   :is_nullable "YES"
                   :table_name "account"}]
                (test-util/get-table-schema-from-db
                  config/DATABASE-CONN
                  "account"))))))))


(deftest ^:eftest/slow test-fields-range-alter-column-ok
  (doseq [{:keys [field-type]} [{:field-type :int4range}
                                {:field-type :int4multirange}
                                {:field-type :int8range}
                                {:field-type :int8multirange}
                                {:field-type :numrange}
                                {:field-type :nummultirange}
                                {:field-type :tsrange}
                                {:field-type :tsmultirange}
                                {:field-type :tstzrange}
                                {:field-type :tstzmultirange}
                                {:field-type :daterange}
                                {:field-type :datemultirange}]
          :let [type-name (name field-type)
                type-name-up (str/upper-case type-name)]]
    (test-util/drop-all-tables config/DATABASE-CONN)
    (test-util/delete-recursively config/MIGRATIONS-DIR)

    (testing "check generated actions, queries edn and sql from all actions"
      (is (= {:new-actions (list {:action :alter-column
                                  :changes {:type {:from :char
                                                   :to field-type}}
                                  :field-name :thing
                                  :model-name :account
                                  :options {:type field-type}})
              :q-edn [{:create-table [:account]
                       :with-columns ['(:id :serial)]}
                      {:add-column (list :thing :char)
                       :alter-table :account}
                      {:alter-table (list :account
                                      {:alter-column
                                       (list :thing :type field-type
                                         :using [:raw "thing"] [:raw "::"]
                                         field-type)})}]
              :q-sql [["CREATE TABLE account (id SERIAL)"]
                      [(format "ALTER TABLE account ADD COLUMN thing CHAR")]
                      [(format "ALTER TABLE account ALTER COLUMN thing TYPE %s USING thing :: %s"
                         type-name-up type-name-up)]]}
            (test-util/perform-make-and-migrate!
              {:jdbc-url config/DATABASE-CONN
               :existing-actions [{:action :create-table
                                   :fields {:id {:type :serial}}
                                   :model-name :account}
                                  {:action :add-column
                                   :field-name :thing
                                   :model-name :account
                                   :options {:type :char}}]
               :existing-models {:account
                                 {:fields [[:id :serial]
                                           [:thing field-type]]}}})))

      (testing "check actual db changes"
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
                   :column_name "thing"
                   :data_type type-name
                   :udt_name type-name
                   :is_nullable "YES"
                   :table_name "account"}]
                (test-util/get-table-schema-from-db
                  config/DATABASE-CONN
                  "account"))))))))


(deftest ^:eftest/slow test-fields-range-add-column-ok
  (doseq [{:keys [field-type]} [{:field-type :int4range}
                                {:field-type :int4multirange}
                                {:field-type :int8range}
                                {:field-type :int8multirange}
                                {:field-type :numrange}
                                {:field-type :nummultirange}
                                {:field-type :tsrange}
                                {:field-type :tsmultirange}
                                {:field-type :tstzrange}
                                {:field-type :tstzmultirange}
                                {:field-type :daterange}
                                {:field-type :datemultirange}]
          :let [type-name (name (if (vector? field-type)
                                  (first field-type)
                                  field-type))
                type-name-up (str/upper-case type-name)]]
    (test-util/drop-all-tables config/DATABASE-CONN)
    (test-util/delete-recursively config/MIGRATIONS-DIR)

    (testing "check generated actions, queries edn and sql from all actions"
      (is (= {:new-actions (list {:action :add-column
                                  :field-name :thing
                                  :model-name :account
                                  :options {:type field-type}})
              :q-edn [{:create-table [:account]
                       :with-columns ['(:id :serial)]}
                      {:add-column (list :thing
                                     (if (vector? field-type)
                                       [:raw type-name-up]
                                       field-type))
                       :alter-table :account}]
              :q-sql [["CREATE TABLE account (id SERIAL)"]
                      [(format "ALTER TABLE account ADD COLUMN thing %s"
                         type-name-up)]]}
            (test-util/perform-make-and-migrate!
              {:jdbc-url config/DATABASE-CONN
               :existing-actions [{:action :create-table
                                   :fields {:id {:type :serial}}
                                   :model-name :account}]
               :existing-models {:account
                                 {:fields [[:id :serial]
                                           [:thing field-type]]}}})))

      (testing "check actual db changes"
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
                   :column_name "thing"
                   :data_type type-name
                   :udt_name type-name
                   :is_nullable "YES"
                   :table_name "account"}]
                (test-util/get-table-schema-from-db
                  config/DATABASE-CONN
                  "account"))))))))


(deftest ^:eftest/slow test-fields-range-drop-column-ok
  (doseq [{:keys [field-type]} [{:field-type :int4range}
                                {:field-type :int4multirange}
                                {:field-type :int8range}
                                {:field-type :int8multirange}
                                {:field-type :numrange}
                                {:field-type :nummultirange}
                                {:field-type :tsrange}
                                {:field-type :tsmultirange}
                                {:field-type :tstzrange}
                                {:field-type :tstzmultirange}
                                {:field-type :daterange}
                                {:field-type :datemultirange}]
          :let [type-name (name field-type)
                type-name-up (str/upper-case type-name)]]
    (test-util/drop-all-tables config/DATABASE-CONN)
    (test-util/delete-recursively config/MIGRATIONS-DIR)

    (testing "check generated actions, queries edn and sql from all actions"
      (is (= {:new-actions (list {:action :drop-column
                                  :field-name :thing
                                  :model-name :account})
              :q-edn [{:create-table [:account]
                       :with-columns ['(:id :serial)
                                      (list :thing
                                        (if (vector? field-type)
                                          [:raw type-name-up]
                                          field-type))]}
                      {:drop-column :thing
                       :alter-table :account}]
              :q-sql [[(format "CREATE TABLE account (id SERIAL, thing %s)"
                         type-name-up)]
                      ["ALTER TABLE account DROP COLUMN thing"]]}
            (test-util/perform-make-and-migrate!
              {:jdbc-url config/DATABASE-CONN
               :existing-actions [{:action :create-table
                                   :fields {:id {:type :serial}
                                            :thing {:type field-type}}
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
                   :table_name "account"}]
                (test-util/get-table-schema-from-db
                  config/DATABASE-CONN
                  "account"))))))))
