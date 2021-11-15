(ns tuna.models-test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [tuna.models :as models])
  (:import (clojure.lang ExceptionInfo)))


(deftest test-validate-foreign-key-check-referenced-models-exist-ok
  (let [models {:feed
                {:fields {:id {:type :serial
                               :null false
                               :unique true}
                          :account {:type :integer
                                    :foreign-key [:account :id]}}}
                :account
                {:fields {:id {:type :serial
                               :unique true}
                          :name {:type [:varchar 256]}}}}]
    (is (true? (#'models/validate-foreign-key models)))))


(deftest test-validate-foreign-key-check-referenced-models-exist-err
  (let [models {:feed
                {:fields {:id {:type :serial
                               :null false
                               :unique true}
                          :account {:type :integer
                                    :foreign-key :missing-model/id}}}
                :account
                {:fields {:id {:type :serial
                               :unique true}
                          :name {:type [:varchar 256]}}}}]
    (is (thrown-with-msg? ExceptionInfo
                          #"Foreign key :feed/account has reference on the missing model :missing-model."
          (#'models/validate-foreign-key models)))))


(deftest test-validate-foreign-key-check-referenced-field-not-exist-err
  (let [models {:feed
                {:fields {:id {:type :serial
                               :null false
                               :unique true}
                          :account {:type :integer
                                    :foreign-key :account/missing-field}}}
                :account
                {:fields {:name {:type [:varchar 256]}}}}]
    (is (thrown-with-msg? ExceptionInfo
                          #"Foreign key :feed/account has reference on the missing field :account/missing-field."
          (#'models/validate-foreign-key models)))))


(deftest test-validate-foreign-key-check-referenced-field-is-not-unique-err
  (let [models {:feed
                {:fields {:id {:type :serial
                               :null false
                               :unique true}
                          :account {:type :integer
                                    :foreign-key :account/id}}}
                :account
                {:fields {:id {:type :serial}}}}]
    (is (thrown-with-msg? ExceptionInfo
                          #"Foreign key :feed/account has reference on the not unique field :account/id."
          (#'models/validate-foreign-key models)))))


(deftest test-validate-foreign-key-check-same-type-of-fields-ok
  (let [models {:feed
                {:fields {:id {:type :serial
                               :null false
                               :unique true}
                          :account {:type :integer
                                    :foreign-key :account/id}}}
                :account
                {:fields {:id {:type :serial
                               :unique true}}}}]
    (is (true? (#'models/validate-foreign-key models)))))


(deftest test-validate-foreign-key-check-same-type-of-fields-err
  (let [models {:feed
                {:fields {:id {:type :serial
                               :null false
                               :unique true}
                          :account {:type :integer
                                    :foreign-key :account/id}}}
                :account
                {:fields {:id {:type :uuid
                               :unique true}}}}]
    (is (thrown-with-msg? ExceptionInfo
                          #"Foreign key field :feed/account and referenced field :account/id have different types."
          (#'models/validate-foreign-key models)))))


(deftest test-validate-indexes-missing-indexed-fields-err
  (let [models {:fields [{:name :id
                          :options {:type :serial
                                    :null false
                                    :unique true}}]
                :indexes [{:name :feed_name_id_ids
                           :options {:type :btree
                                     :fields [:id :name]}}]}]
    (is (false? (s/valid? ::models/validate-indexed-fields models)))))


(deftest test-validate-indexes-empty-models-ok
  (let [models {:feed
                {:indexes {:feed_name_id_ids {:type :btree
                                              :fields [:id :name]}}}}]
    (is (true? (s/valid? ::models/validate-indexed-fields models)))))


(deftest test-validate-validate-fields-duplication
  (testing "check valid model fields ok"
    (let [fields [{:name :id}
                  {:name :text}]]
      (is (true? (s/valid? ::models/validate-fields-duplication fields)))))
  (testing "check duplicated model fields err"
    (let [fields [{:name :id}
                  {:name :id}]]
      (is (false? (s/valid? ::models/validate-fields-duplication fields))))))


(deftest test-validate-validate-indexes-duplication
  (testing "check valid model indexes ok"
    (let [fields {:feed
                  {:indexes {:feed_name_id_ids {:type :btree
                                                :fields [:id :name]}}}
                  :account
                  {:indexes {:account_name_id_ids {:type :btree
                                                   :fields [:id]}}}}]
      (is (true? (s/valid? ::models/validate-indexes-duplication-across-models fields)))))
  (testing "check valid model indexes ok"
    (let [fields {:feed
                  {:indexes {:feed_name_id_ids {:type :btree
                                                :fields [:id :name]}}}
                  :account
                  {:indexes {:feed_name_id_ids {:type :btree
                                                :fields [:id]}}}}]
      (is (false? (s/valid? ::models/validate-indexes-duplication-across-models fields))))))
