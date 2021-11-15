(ns tuna.migrations-errors-test
  (:require [clojure.test :refer :all]
            [tuna.schema :as schema]
            [tuna.util.test :as test-util]))


(def ^:private migration-error-title "MIGRATION ERROR")


(deftest test-invalid-actions-schema-error
  (testing "check actions should be a coll"
    (let [data :wrong]
      (is (= [{:message "Migration actions should be vector.\n\n  :wrong"
               :title migration-error-title}]
            (test-util/get-spec-error-data
              #(#'schema/actions->internal-models data))))))

  (testing "check actions should have existing action"
    (let [data [[]]]
      (is (= [{:message "Missing migration action type.\n\n  []"
               :title migration-error-title}]
            (test-util/get-spec-error-data
              #(#'schema/actions->internal-models data)))))))
