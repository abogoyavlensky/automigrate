(ns automigrate.fields-test
  (:require
    [automigrate.testing-config :as config]
    [automigrate.testing-util :as test-util]
    [clojure.test :refer :all]
    [clojure.spec.alpha :as s]
    [automigrate.fields :as fields]))


(use-fixtures :each
  (test-util/with-drop-tables config/DATABASE-CONN)
  (test-util/with-delete-dir config/MIGRATIONS-DIR-FULL))


(deftest test-validate-fk-options-on-delete
  (testing "check no fk and no on-delete ok"
    (is (true?
          (s/valid? ::fields/validate-fk-options-on-delete {:null true}))))
  (testing "check fk and on-delete ok"
    (is (true?
          (s/valid? ::fields/validate-fk-options-on-delete {:null true
                                                            :foreign-key :account/id
                                                            :on-delete :cascade}))))
  (testing "check no fk and on-delete err"
    (is (false?
          (s/valid? ::fields/validate-fk-options-on-delete {:null true
                                                            :on-delete :cascade})))))


(deftest test-validate-fk-options-on-update
  (testing "check no fk and no on-update ok"
    (is (true?
          (s/valid? ::fields/validate-fk-options-on-update {:null true}))))
  (testing "check fk and on-update ok"
    (is (true?
          (s/valid? ::fields/validate-fk-options-on-update {:null true
                                                            :foreign-key :account/id
                                                            :on-update :cascade}))))
  (testing "check no fk and on-delete err"
    (is (false?
          (s/valid? ::fields/validate-fk-options-on-update {:null true
                                                            :on-update :cascade})))))


(deftest test-validate-default-with-null
  (testing "check default is nil and null is false ok"
    (is (true?
          (s/valid? ::fields/validate-default-and-null {:null true
                                                        :default nil}))))
  (testing "check default is nil and no null ok"
    (is (true?
          (s/valid? ::fields/validate-default-and-null {:default nil}))))
  (testing "check no default and null is false ok"
    (is (true?
          (s/valid? ::fields/validate-default-and-null {:null false}))))
  (testing "check default is nil and null is false err"
    (is (false?
          (s/valid? ::fields/validate-default-and-null {:null false
                                                        :default nil})))))


(deftest test-validate-default-with-type
  (testing "check default is int and type integer ok"
    (is (true?
          (s/valid? ::fields/validate-default-and-type {:type :integer
                                                        :default 10}))))
  (testing "check default is string and type integer err"
    (is (false?
          (s/valid? ::fields/validate-default-and-type {:type :integer
                                                        :default "wrong"}))))
  (testing "check default is int and type varchar err"
    (is (false?
          (s/valid? ::fields/validate-default-and-type {:type [:varchar 20]
                                                        :default 10}))))
  (testing "check default is int and type timestamp ok"
    (is (true?
          (s/valid? ::fields/validate-default-and-type {:type :timestamp
                                                        :default [:now]}))))

  (testing "check default is int and type float ok"
    (is (true?
          (s/valid? ::fields/validate-default-and-type {:type :float
                                                        :default 10.0}))))
  (testing "check default is int and type float err"
    (is (false?
          (s/valid? ::fields/validate-default-and-type {:type :float
                                                        :default 10}))))
  (testing "check default is int and type float as nil ok"
    (is (true?
          (s/valid? ::fields/validate-default-and-type {:type :float
                                                        :default nil}))))

  (testing "check default is numeric str and type decimal ok"
    (is (true?
          (s/valid? ::fields/validate-default-and-type {:type :decimal
                                                        :default "10.32"}))))
  (testing "check default is non numeric str and type decimal ok"
    (is (false?
          (s/valid? ::fields/validate-default-and-type {:type :decimal
                                                        :default "wrong"}))))
  (testing "check default is bigdec and type decimal ok"
    (is (true?
          (s/valid? ::fields/validate-default-and-type {:type :decimal
                                                        :default 10.32M}))))
  (testing "check default is int and type decimal ok"
    (is (true?
          (s/valid? ::fields/validate-default-and-type {:type :decimal
                                                        :default 10}))))
  (testing "check default is float and type decimal ok"
    (is (true?
          (s/valid? ::fields/validate-default-and-type {:type :decimal
                                                        :default 10.3}))))
  (testing "check default is int and type decimal as nil ok"
    (is (true?
          (s/valid? ::fields/validate-default-and-type {:type :decimal
                                                        :default nil})))))


(deftest test-validate-fk-options-and-null
  (testing "check on-delete is cascade and null is true ok"
    (is (true?
          (s/valid? ::fields/validate-fk-options-and-null-on-delete {:null true
                                                                     :on-delete :cascade}))))
  (testing "check on-delete is cascade and null is false ok"
    (is (true?
          (s/valid? ::fields/validate-fk-options-and-null-on-delete {:null false
                                                                     :on-delete :cascade}))))
  (testing "check on-delete not exists and null is false ok"
    (is (true?
          (s/valid? ::fields/validate-fk-options-and-null-on-update {:null false}))))
  (testing "check on-delete is set-null and null is false err"
    (is (false?
          (s/valid? ::fields/validate-fk-options-and-null-on-delete {:null false
                                                                     :on-delete :set-null}))))
  (testing "check on-upate is set-null and null is false err"
    (is (false?
          (s/valid? ::fields/validate-fk-options-and-null-on-update {:null false
                                                                     :on-update :set-null})))))


(deftest test-fields-alter-column-char-to-int-ok
  (testing "check generated actions, queries edn and sql from all actions"
    (is (= {:new-actions (list {:action :alter-column
                                :changes {:type {:from [:char 255]
                                                 :to :integer}}
                                :field-name :num
                                :model-name :account
                                :options {:type :integer}})
            :q-edn [{:create-table [:account]
                     :with-columns ['(:id :serial)
                                    '(:num [:char 255])]}
                    {:alter-table
                     (list :account
                       {:alter-column
                        (list :num :type :integer :using [:raw "num"] [:raw "::"] :integer)})}]

            :q-sql [["CREATE TABLE account (id SERIAL, num CHAR(255))"]
                    ["ALTER TABLE account ALTER COLUMN num TYPE INTEGER USING num :: INTEGER"]]}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions [{:action :create-table
                                 :fields {:id {:type :serial}
                                          :num {:type [:char 255]}}
                                 :model-name :account}]
             :existing-models {:account
                               {:fields [[:id :serial]
                                         [:num :integer]]}}}))))

  (testing "check actual db changes"
    (is (= [{:character_maximum_length nil
             :column_default "nextval('account_id_seq'::regclass)"
             :column_name "id"
             :data_type "integer"
             :udt_name "int4"
             :is_nullable "NO"
             :table_name "account"}
            {:character_maximum_length nil
             :column_default nil
             :column_name "num"
             :data_type "integer"
             :udt_name "int4"
             :is_nullable "YES"
             :table_name "account"}]
          (test-util/get-table-schema-from-db
            config/DATABASE-CONN
            "account")))))


(deftest test-fields-alter-column-array-with-underscore-ok
  (testing "check generated actions, queries edn and sql from all actions"
    (is (= {:new-actions (list {:action :alter-column
                                :field-name :num-val
                                :model-name :account
                                :options {:type :integer}
                                :changes {:type {:from :char
                                                 :to :integer}}})
            :q-edn [{:create-table [:account]
                     :with-columns ['(:id :serial)
                                    '(:num-val :char)]}
                    {:alter-table
                     (list :account
                       {:alter-column
                        (list :num-val :type :integer :using [:raw "num_val"] [:raw "::"] :integer)})}]

            :q-sql [["CREATE TABLE account (id SERIAL, num_val CHAR)"]
                    ["ALTER TABLE account ALTER COLUMN num_val TYPE INTEGER USING num_val :: INTEGER"]]}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions [{:action :create-table
                                 :fields {:id {:type :serial}
                                          :num-val {:type :char}}
                                 :model-name :account}]
             :existing-models {:account
                               {:fields [[:id :serial]
                                         [:num-val :integer]]}}}))))

  (testing "check actual db changes"
    (is (= [{:character_maximum_length nil
             :column_default "nextval('account_id_seq'::regclass)"
             :column_name "id"
             :data_type "integer"
             :udt_name "int4"
             :is_nullable "NO"
             :table_name "account"}
            {:character_maximum_length nil
             :column_default nil
             :column_name "num_val"
             :data_type "integer"
             :udt_name "int4"
             :is_nullable "YES"
             :table_name "account"}]
          (test-util/get-table-schema-from-db
            config/DATABASE-CONN
            "account")))))


(deftest test-fields-alter-column-array-ok
  (testing "check generated actions, queries edn and sql from all actions"
    (is (= {:new-actions (list {:action :alter-column
                                :field-name :num-val
                                :model-name :account
                                :options {:type :char}
                                :changes {:array {:from "[][]"
                                                  :to :EMPTY}
                                          :type {:from :integer
                                                 :to :char}}})
            :q-edn [{:create-table [:account]
                     :with-columns ['(:id :serial)
                                    '(:num-val :integer [:raw "[][]"])]}
                    {:alter-table
                     (list :account
                       {:alter-column
                        (list :num-val :type :char
                          :using [:raw "num_val"] [:raw "::"] :char)})}]

            :q-sql [["CREATE TABLE account (id SERIAL, num_val INTEGER [][])"]
                    ["ALTER TABLE account ALTER COLUMN num_val TYPE CHAR USING num_val :: CHAR"]]}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions [{:action :create-table
                                 :fields {:id {:type :serial}
                                          :num-val {:type :integer
                                                    :array "[][]"}}
                                 :model-name :account}]
             :existing-models {:account
                               {:fields [[:id :serial]
                                         [:num-val :char]]}}}))))

  (testing "check actual db changes"
    (is (= [{:character_maximum_length nil
             :column_default "nextval('account_id_seq'::regclass)"
             :column_name "id"
             :data_type "integer"
             :udt_name "int4"
             :is_nullable "NO"
             :table_name "account"}
            {:character_maximum_length 1
             :column_default nil
             :column_name "num_val"
             :data_type "character"
             :udt_name "bpchar"
             :is_nullable "YES"
             :table_name "account"}]
          (test-util/get-table-schema-from-db
            config/DATABASE-CONN
            "account")))))

(deftest test-validate-collate-ok
  (testing "check valid collate ok"
    (is (true? (s/valid? ::fields/validate-type-for-collate {:type :text
                                                             :collate "ko_KR"}))))
  
  (testing "check invalid collate err"
    (is (false? (s/valid? ::fields/validate-type-for-collate {:type :timestamp
                                                              :collate "ko_KR"})))))
