(ns tuna.models-test
  (:require [clojure.test :refer :all]
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
    (is (= models (#'models/validate-foreign-key models)))))


(deftest test-validate-foreign-key-check-referenced-models-exist-err
  (let [models {:feed
                {:fields {:id {:type :serial
                               :null false
                               :unique true}
                          :account {:type :integer
                                    :foreign-key [:missing-model :id]}}}
                :account
                {:fields {:id {:type :serial
                               :unique true}
                          :name {:type [:varchar 256]}}}}]
    (is (thrown-with-msg? ExceptionInfo #"Referenced model :missing-model is missing"
          (#'models/validate-foreign-key models)))))


(deftest test-validate-foreign-key-check-referenced-field-not-exist-err
  (let [models {:feed
                {:fields {:id {:type :serial
                               :null false
                               :unique true}
                          :account {:type :integer
                                    :foreign-key [:account :missing-field]}}}
                :account
                {:fields {:name {:type [:varchar 256]}}}}]
    (is (thrown-with-msg? ExceptionInfo #"Referenced field :missing-field of model :account is missing"
          (#'models/validate-foreign-key models)))))


(deftest test-validate-foreign-key-check-referenced-field-is-not-unique-err
  (let [models {:feed
                {:fields {:id {:type :serial
                               :null false
                               :unique true}
                          :account {:type :integer
                                    :foreign-key [:account :id]}}}
                :account
                {:fields {:id {:type :serial}}}}]
    (is (thrown-with-msg? ExceptionInfo #"Referenced field :id of model :account is not unique"
          (#'models/validate-foreign-key models)))))


(deftest test-validate-foreign-key-check-same-type-of-fields-ok
  (let [models {:feed
                {:fields {:id {:type :serial
                               :null false
                               :unique true}
                          :account {:type :integer
                                    :foreign-key [:account :id]}}}
                :account
                {:fields {:id {:type :serial
                               :unique true}}}}]
    (is (= models (#'models/validate-foreign-key models)))))


(deftest test-validate-foreign-key-check-same-type-of-fields-err
  (let [models {:feed
                {:fields {:id {:type :serial
                               :null false
                               :unique true}
                          :account {:type :integer
                                    :foreign-key [:account :id]}}}
                :account
                {:fields {:id {:type :uuid
                               :unique true}}}}]
    (is (thrown-with-msg? ExceptionInfo #"Referenced field :id and origin field :account have different types"
          (#'models/validate-foreign-key models)))))


(deftest test-validate-indexes-missing-indexed-fields-err
  (let [models {:feed
                {:fields {:id {:type :serial
                               :null false
                               :unique true}}
                 :indexes {:feed_name_id_ids {:type :btree
                                              :fields [:id :name]}}}}]

    (is (thrown-with-msg? ExceptionInfo #"Missing indexed fields: :name"
          (#'models/validate-indexes models)))))
