(ns automigrate.fields-time-test
  (:require [automigrate.migrations :as migrations]
            [automigrate.schema :as schema]
            [automigrate.util.file :as file-util]
            [bond.james :as bond]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [automigrate.testing-util :as test-util]
            [automigrate.testing-config :as config]))


(use-fixtures :each
  (test-util/with-drop-tables config/DATABASE-CONN)
  (test-util/with-delete-dir config/MIGRATIONS-DIR))


(deftest ^:eftest/slow test-fields-time-create-table-ok
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


(deftest ^:eftest/slow test-fields-time-array-create-table-ok
  (doseq [{:keys [field-type field-name sql edn]}
          [{:field-type :interval
            :field-name "interval"}
           {:field-type [:interval 2]
            :field-name "interval"
            :edn [:raw "INTERVAL(2)"]
            :sql "INTERVAL(2)"}

           {:field-type :time
            :field-name "time"}
           {:field-type [:time 2]
            :field-name "time"
            :edn [:raw "TIME(2)"]
            :sql "TIME(2)"}

           {:field-type :timestamp
            :field-name "timestamp"}
           {:field-type [:timestamp 2]
            :field-name "timestamp"
            :edn [:raw "TIMESTAMP(2)"]
            :sql "TIMESTAMP(2)"}]]
    (test-util/drop-all-tables config/DATABASE-CONN)
    (test-util/delete-recursively config/MIGRATIONS-DIR)

    (testing "check generated actions, queries edn and sql from all actions"
      (is (= {:new-actions (list {:action :create-table
                                  :fields {:thing {:type field-type
                                                   :array "[][]"}}
                                  :model-name :account})
              :q-edn [{:create-table [:account]
                       :with-columns
                       [(list :thing (or edn field-type) [:raw "[][]"])]}]
              :q-sql [[(format "CREATE TABLE account (thing %s [][])"
                         (or sql (str/upper-case field-name)))]]}
            (test-util/perform-make-and-migrate!
              {:jdbc-url config/DATABASE-CONN
               :existing-actions []
               :existing-models {:account
                                 {:fields [[:thing field-type {:array "[][]"}]]}}})))

      (testing "check actual db changes"
        (testing "test actual db schema after applying the migration"
          (is (= [{:character_maximum_length nil
                   :column_default nil
                   :column_name "thing"
                   :data_type "ARRAY"
                   :udt_name (str "_" field-name)
                   :is_nullable "YES"
                   :table_name "account"
                   :datetime_precision nil}]
                (test-util/get-table-schema-from-db
                  config/DATABASE-CONN
                  "account"
                  {:add-cols [:datetime_precision]}))))))))


(deftest ^:eftest/slow test-fields-time-alter-column-ok
  (doseq [{:keys [field-type data-type]} [{:field-type :interval}
                                          {:field-type :time
                                           :data-type "time without time zone"}
                                          {:field-type :timetz
                                           :data-type "time with time zone"}
                                          {:field-type :timestamp
                                           :data-type "timestamp without time zone"}
                                          {:field-type :timestamptz
                                           :data-type "timestamp with time zone"}]
          :let [type-name (name field-type)
                type-name-up (str/upper-case type-name)]]
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
                      {:add-column (list :thing [:raw (str type-name-up "(3)")])
                       :alter-table :account}
                      {:alter-table (list :account
                                      {:alter-column
                                       (list :thing :type [:raw (str type-name-up "(6)")]
                                         :using :thing [:raw "::"]
                                         [:raw (str type-name-up "(6)")])})}]
              :q-sql [["CREATE TABLE account (id SERIAL)"]
                      [(format "ALTER TABLE account ADD COLUMN thing %s(3)"
                         type-name-up)]
                      [(format "ALTER TABLE account ALTER COLUMN thing TYPE %s(6) USING THING :: %s(6)"
                         type-name-up type-name-up)]]}
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
                   :data_type (or data-type type-name)
                   :udt_name type-name
                   :is_nullable "YES"
                   :table_name "account"
                   :datetime_precision 6}]
                (test-util/get-table-schema-from-db
                  config/DATABASE-CONN
                  "account"
                  {:add-cols [:datetime_precision]}))))))))


(deftest ^:eftest/slow test-fields-time-alter-column-to-array-ok
  (doseq [{:keys [field-type data-type]} [{:field-type :interval}
                                          {:field-type :time}
                                          {:field-type :timetz}
                                          {:field-type :timestamp}
                                          {:field-type :timestamptz}]
          :let [type-name (name field-type)
                type-name-up (str/upper-case type-name)]]
    (test-util/drop-all-tables config/DATABASE-CONN)
    (test-util/delete-recursively config/MIGRATIONS-DIR)

    (testing "check generated actions, queries edn and sql from all actions"
      (is (= {:new-actions (list {:action :alter-column
                                  :changes {:type {:from :text
                                                   :to field-type}
                                            :array {:from :EMPTY
                                                    :to "[][202][1]"}}
                                  :field-name :thing
                                  :model-name :account
                                  :options {:type field-type
                                            :array "[][202][1]"}})
              :q-edn [{:create-table [:account]
                       :with-columns ['(:id :serial)]}
                      {:add-column (list :thing :text)
                       :alter-table :account}
                      {:alter-table
                       (list :account
                         {:alter-column
                          (list :thing :type field-type [:raw "[][202][1]"]
                            :using :thing [:raw "::"] field-type [:raw "[][202][1]"])})}]
              :q-sql [["CREATE TABLE account (id SERIAL)"]
                      ["ALTER TABLE account ADD COLUMN thing TEXT"]
                      [(format "ALTER TABLE account ALTER COLUMN thing TYPE %s [][202][1] USING THING :: %s [][202][1]"
                         type-name-up
                         type-name-up)]]}
            (test-util/perform-make-and-migrate!
              {:jdbc-url config/DATABASE-CONN
               :existing-actions [{:action :create-table
                                   :fields {:id {:type :serial}}
                                   :model-name :account}
                                  {:action :add-column
                                   :field-name :thing
                                   :model-name :account
                                   :options {:type :text}}]
               :existing-models {:account
                                 {:fields [[:id :serial]
                                           [:thing field-type {:array "[][202][1]"}]]}}})))

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
                   :data_type "ARRAY"
                   :udt_name (str "_" type-name)
                   :is_nullable "YES"
                   :table_name "account"
                   :datetime_precision nil}]
                (test-util/get-table-schema-from-db
                  config/DATABASE-CONN
                  "account"
                  {:add-cols [:datetime_precision]}))))))))


(deftest ^:eftest/slow test-fields-time-add-column-ok
  (doseq [{:keys [field-type data-type]} [{:field-type [:interval 3]}
                                          {:field-type :interval}

                                          {:field-type [:time 3]
                                           :data-type "time without time zone"}
                                          {:field-type :time
                                           :data-type "time without time zone"}

                                          {:field-type [:timetz 3]
                                           :data-type "time with time zone"}
                                          {:field-type :timetz
                                           :data-type "time with time zone"}

                                          {:field-type [:timestamp 3]
                                           :data-type "timestamp without time zone"}
                                          {:field-type :timestamp
                                           :data-type "timestamp without time zone"}

                                          {:field-type [:timestamptz 3]
                                           :data-type "timestamp with time zone"}
                                          {:field-type :timestamptz
                                           :data-type "timestamp with time zone"}]
          :let [type-name (name (if (vector? field-type)
                                  (first field-type)
                                  field-type))
                type-name-up (str/upper-case type-name)
                precision (if (vector? field-type)
                            (last field-type)
                            6)]]
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
                                       [:raw (format "%s(%s)" type-name-up precision)]
                                       field-type))
                       :alter-table :account}]
              :q-sql [["CREATE TABLE account (id SERIAL)"]
                      [(format "ALTER TABLE account ADD COLUMN thing %s"
                         (if (vector? field-type)
                           (format "%s(%s)" type-name-up precision)
                           type-name-up))]]}
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
                   :table_name "account"
                   :datetime_precision nil}
                  {:character_maximum_length nil
                   :column_default nil
                   :column_name "thing"
                   :data_type (or data-type type-name)
                   :udt_name type-name
                   :is_nullable "YES"
                   :table_name "account"
                   :datetime_precision precision}]
                (test-util/get-table-schema-from-db
                  config/DATABASE-CONN
                  "account"
                  {:add-cols [:datetime_precision]}))))))))


(deftest ^:eftest/slow test-fields-time-drop-column-ok
  (doseq [{:keys [field-type]} [{:field-type :interval}
                                {:field-type [:interval 3]}

                                {:field-type :time}
                                {:field-type [:time 3]}

                                {:field-type :timetz}
                                {:field-type [:timetz 3]}

                                {:field-type :timestamp}
                                {:field-type [:timestamp 3]}

                                {:field-type :timestamptz}
                                {:field-type [:timestamptz 3]}]
          :let [type-name (name (if (vector? field-type)
                                  (first field-type)
                                  field-type))
                type-name-up (str/upper-case type-name)
                precision (if (vector? field-type)
                            (last field-type)
                            6)]]
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
                                          [:raw (format "%s(%s)" type-name-up precision)]
                                          field-type))]}
                      {:drop-column :thing
                       :alter-table :account}]
              :q-sql [[(format "CREATE TABLE account (id SERIAL, thing %s)"
                         (if (vector? field-type)
                           (format "%s(%s)" type-name-up precision)
                           type-name-up))]
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


(deftest ^:eftest/slow test-fields-time-drop-column-array-ok
  (testing "check generated actions, queries edn and sql from all actions"
    (is (= {:new-actions (list {:action :drop-column
                                :field-name :thing
                                :model-name :account})
            :q-edn [{:create-table [:account]
                     :with-columns ['(:id :serial)
                                    (list :thing :interval [:raw "[][]"])]}
                    {:drop-column :thing
                     :alter-table :account}]
            :q-sql [["CREATE TABLE account (id SERIAL, thing INTERVAL [][])"]
                    ["ALTER TABLE account DROP COLUMN thing"]]}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions [{:action :create-table
                                 :fields {:id {:type :serial}
                                          :thing {:type :interval
                                                  :array "[][]"}}
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
                "account")))))))


(deftest test-make-migration*-create-table-with-array-of-interval-restore-ok
  (let [existing-actions '({:action :create-table
                            :model-name :account
                            :fields {:id {:type :serial}
                                     :times {:type :time
                                             :array "[]"}}}
                           {:action :add-column
                            :field-name :durations
                            :model-name :account
                            :options
                            {:type :interval
                             :array "[10][10]"}})
        existing-models {:account
                         {:fields [[:id :serial]
                                   [:times :time {:array "[]"}]
                                   [:durations :interval {:array "[10][10]"}]]}}]
    (bond/with-stub [[schema/load-migrations-from-files
                      (constantly existing-actions)]
                     [file-util/read-edn (constantly existing-models)]]
      (is (= [] (#'migrations/make-migration* "" []))))))


(deftest test-fields-time-uses-existing-enum-type
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
            (with-out-str
              (test-util/make-migration! params)))))))


(deftest test-fields-interval-type-with-wrong-array-option-value-error
  (testing "not balanced brackets as :array value"
    (is (= (str "-- MODEL ERROR -------------------------------------\n\n"
             "Option :array of field :account/thing should be string"
             " showing array dimension: \"[]\", \"[][]\", etc.\n\n"
             "  {:array \"[][\"}\n\n")
          (with-out-str
            (test-util/make-migration!
              {:existing-models
               {:account
                {:fields [[:thing :interval {:array "[]["}]]}}})))))

  (testing "integer instead of string"
    (is (= (str "-- MODEL ERROR -------------------------------------\n\n"
             "Option :array of field :account/thing should be string"
             " showing array dimension: \"[]\", \"[][]\", etc.\n\n"
             "  {:array 2}\n\n")
          (with-out-str
            (test-util/make-migration!
              {:existing-models
               {:account
                {:fields [[:thing :interval {:array 2}]]}}})))))

  (testing "array size can't be 0"
    (is (= (str "-- MODEL ERROR -------------------------------------\n\n"
             "Option :array of field :account/thing should be string"
             " showing array dimension: \"[]\", \"[][]\", etc.\n\n"
             "  {:array \"[0]\"}\n\n")
          (with-out-str
            (test-util/make-migration!
              {:existing-models
               {:account
                {:fields [[:thing :interval {:array "[0]"}]]}}}))))))
