(ns tuna.fields-test
  (:require [clojure.test :refer :all]
            [tuna.fields :as fields]))


(deftest test-validate-fk-options
  (testing "check no fk and no on-delete ok"
    (is (true?
          (#'fields/validate-fk-options {:null true}))))
  (testing "check fk and on-delete ok"
    (is (true?
          (#'fields/validate-fk-options {:null true
                                         :foreign-key :account/id
                                         :on-delete :cascade
                                         :on-update :cascade}))))
  (testing "check no fk and on-delete err"
    (is (false?
          (#'fields/validate-fk-options {:null true
                                         :on-delete :cascade
                                         :on-update :cascade})))))
