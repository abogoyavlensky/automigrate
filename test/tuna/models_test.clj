(ns tuna.models-test
  (:require [clojure.test :refer :all]
            [slingshot.slingshot :refer [try+]]
            [tuna.models :as models]))


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
    (try+
      (#'models/validate-foreign-key models)
      (catch [:type ::models/missing-referenced-model] e
        (is (= :missing-model (get-in e [:data :referenced-model])))))))


(deftest test-validate-foreign-key-check-referenced-field-not-exist-err
  (let [models {:feed
                {:fields {:id {:type :serial
                               :null false
                               :unique true}
                          :account {:type :integer
                                    :foreign-key [:account :missing-field]}}}
                :account
                {:fields {:name {:type [:varchar 256]}}}}]
    (try+
      (#'models/validate-foreign-key models)
      (catch [:type ::models/missing-referenced-field] e
        (is (= :account (get-in e [:data :referenced-model])))
        (is (= :missing-field (get-in e [:data :referenced-field])))))))


(deftest test-validate-foreign-key-check-referenced-field-is-not-unique-err
  (let [models {:feed
                {:fields {:id {:type :serial
                               :null false
                               :unique true}
                          :account {:type :integer
                                    :foreign-key [:account :id]}}}
                :account
                {:fields {:id {:type :serial}}}}]
    (try+
      (#'models/validate-foreign-key models)
      (catch [:type ::models/referenced-field-is-not-unique] e
        (is (= :account (get-in e [:data :referenced-model])))
        (is (= :id (get-in e [:data :referenced-field])))))))
