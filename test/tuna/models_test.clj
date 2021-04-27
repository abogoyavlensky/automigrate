(ns tuna.models-test
  (:require [clojure.test :refer :all]
            [slingshot.slingshot :refer [try+]]
            [tuna.models :as models]))


(deftest test-validate-foreign-key-check-fk-models-exist-ok
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


(deftest test-validate-foreign-key-check-fk-models-exist-err
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
