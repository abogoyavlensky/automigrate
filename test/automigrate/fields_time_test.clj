(ns automigrate.fields-time-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [automigrate.testing-util :as test-util]
            [automigrate.testing-config :as config]))


(use-fixtures :each
  (test-util/with-drop-tables config/DATABASE-CONN)
  (test-util/with-delete-dir config/MIGRATIONS-DIR))


(deftest ^:eftest/slow test-fields-interval-create-table-ok
  (doseq [{:keys [field-type field-name edn sql data-type]}
          ; interval
          [{:field-type :interval
            :field-name "interval"}
           {:field-type [:interval 2]
            :field-name "interval"
            :edn [:raw "INTERVAL(2)"]
            :sql "INTERVAL(2)"}
           ; time
           {:field-type :time
            :field-name "time"
            :data-type "time without time zone"}
           {:field-type [:time 3]
            :field-name "time"
            :edn [:raw "TIME(3)"]
            :sql "TIME(3)"
            :data-type "time without time zone"}
           ; timetz
           {:field-type :timetz
            :field-name "timetz"
            :data-type "time with time zone"}
           {:field-type [:timetz 3]
            :field-name "timetz"
            :edn [:raw "TIMETZ(3)"]
            :sql "TIMETZ(3)"
            :data-type "time with time zone"}
           ; timestamp
           {:field-type :timestamp
            :field-name "timestamp"
            :data-type "timestamp without time zone"}
           {:field-type [:timestamp 3]
            :field-name "timestamp"
            :edn [:raw "TIMESTAMP(3)"]
            :sql "TIMESTAMP(3)"
            :data-type "timestamp without time zone"}
           ; timestamptz
           {:field-type :timestamptz
            :field-name "timestamptz"
            :data-type "timestamp with time zone"}
           {:field-type [:timestamptz 3]
            :field-name "timestamptz"
            :edn [:raw "TIMESTAMPTZ(3)"]
            :sql "TIMESTAMPTZ(3)"
            :data-type "timestamp with time zone"}]]
    (test-util/drop-all-tables config/DATABASE-CONN)
    (test-util/delete-recursively config/MIGRATIONS-DIR)

    (testing "check generated actions, queries edn and sql from all actions"
      (is (= {:new-actions (list {:action :create-table
                                  :fields {:thing {:type field-type}}
                                  :model-name :account})
              :q-edn [{:create-table [:account]
                       :with-columns
                       [(list :thing (or edn field-type))]}]
              :q-sql [[(format "CREATE TABLE account (thing %s)"
                         (or sql (str/upper-case field-name)))]]}
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
                   :data_type (or data-type field-name)
                   :udt_name field-name
                   :is_nullable "YES"
                   :table_name "account"
                   :datetime_precision (if (vector? field-type)
                                         (last field-type)
                                         6)}]
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


(deftest test-fields-interval-uses-existing-enum-type
  (doseq [{:keys [field-type expected-output]}
          [{:field-type [:interval]
            :expected-output (str "-- MODEL ERROR -------------------------------------\n\n"
                               "Invalid definition of type for field :account/thing.\n\n"
                               "  [:interval]\n\n")}
           {:field-type [:interval 10]
            :expected-output (str "-- MODEL ERROR -------------------------------------\n\n"
                               "Invalid definition of type for field :account/thing."
                               " The allowed range of precision is from 0 to 6.\n\n"
                               "  10\n\n")}

           {:field-type [:time]
            :expected-output (str "-- MODEL ERROR -------------------------------------\n\n"
                               "Invalid definition of type for field :account/thing.\n\n"
                               "  [:time]\n\n")}
           {:field-type [:time 10]
            :expected-output (str "-- MODEL ERROR -------------------------------------\n\n"
                               "Invalid definition of type for field :account/thing."
                               " The allowed range of precision is from 0 to 6.\n\n"
                               "  10\n\n")}

           {:field-type [:timetz]
            :expected-output (str "-- MODEL ERROR -------------------------------------\n\n"
                               "Invalid definition of type for field :account/thing.\n\n"
                               "  [:timetz]\n\n")}
           {:field-type [:timetz 10]
            :expected-output (str "-- MODEL ERROR -------------------------------------\n\n"
                               "Invalid definition of type for field :account/thing."
                               " The allowed range of precision is from 0 to 6.\n\n"
                               "  10\n\n")}

           {:field-type [:timestamp]
            :expected-output (str "-- MODEL ERROR -------------------------------------\n\n"
                               "Invalid definition of type for field :account/thing.\n\n"
                               "  [:timestamp]\n\n")}
           {:field-type [:timestamp 10]
            :expected-output (str "-- MODEL ERROR -------------------------------------\n\n"
                               "Invalid definition of type for field :account/thing."
                               " The allowed range of precision is from 0 to 6.\n\n"
                               "  10\n\n")}

           {:field-type [:timestamptz]
            :expected-output (str "-- MODEL ERROR -------------------------------------\n\n"
                               "Invalid definition of type for field :account/thing.\n\n"
                               "  [:timestamptz]\n\n")}
           {:field-type [:timestamptz 10]
            :expected-output (str "-- MODEL ERROR -------------------------------------\n\n"
                               "Invalid definition of type for field :account/thing."
                               " The allowed range of precision is from 0 to 6.\n\n"
                               "  10\n\n")}]]

    (let [params {:existing-models
                  {:account
                   {:fields [[:thing field-type]]}}}]
      (is (= expected-output
            (test-util/get-make-migration-output params))))))
