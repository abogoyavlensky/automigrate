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

  (testing "check actions should have existing action type"
    (let [data [[]]]
      (is (= [{:message "Missing action type.\n\n  []"
               :title migration-error-title}]
            (test-util/get-spec-error-data
              #(#'schema/actions->internal-models data))))))

  (testing "check actions should have existing action type"
    (let [data [{:action :MISSING-ACTION}]]
      (is (= [{:message "Missing action type.\n\n  {:action :MISSING-ACTION}"
               :title migration-error-title}]
            (test-util/get-spec-error-data
              #(#'schema/actions->internal-models data)))))))


(deftest test-spec-action->migration-create-table-invalid-model-name-and-type-error
  (testing "check actions should have existing action type"
    (let [data [{:action :create-table}]]
      (is (= [{:message "Missing :model-name key in action.\n\n  {:action :create-table}"
               :title migration-error-title}
              {:message "Missing :fields key in action.\n\n  {:action :create-table}"
               :title migration-error-title}]
            (test-util/get-spec-error-data
              #(#'schema/actions->internal-models data))))))

  (testing "check actions should have correct model name"
    (let [data [{:action :create-table
                 :model-name "wrong"}]]
      (is (= [{:message "Missing :fields key in action.\n\n  {:action :create-table, :model-name \"wrong\"}"
               :title migration-error-title}
              {:message "Action has invalid model name.\n\n  \"wrong\""
               :title migration-error-title}]
            (test-util/get-spec-error-data
              #(#'schema/actions->internal-models data)))))))


(deftest test-spec-action->migration-create-table-invalid-fields-error
  (testing "check actions should have correct fields"
    (let [data [{:action :create-table
                 :model-name :feed
                 :fields {}}]]
      (is (= [{:message "Action should contain at least one field.\n\n  {}"
               :title migration-error-title}]
            (test-util/get-spec-error-data
              #(#'schema/actions->internal-models data))))))

  (testing "check actions should have correct fields"
    (let [data [{:action :create-table
                 :model-name :feed
                 :fields {}}]]
      (is (= [{:message "Action should contain at least one field.\n\n  {}"
               :title migration-error-title}]
            (test-util/get-spec-error-data
              #(#'schema/actions->internal-models data))))))

  (testing "check actions should have field type"
    (let [data [{:action :create-table
                 :model-name :feed
                 :fields {:name {:wrong 1}}}]]
      (is (= [{:message "Field should contain type.\n\n  {:wrong 1}"
               :title migration-error-title}]
            (test-util/get-spec-error-data
              #(#'schema/actions->internal-models data))))))

  (testing "check actions should have correct field type"
    (let [data [{:action :create-table
                 :model-name :feed
                 :fields {:name {:type :wrong}}}]]
      (is (= [{:message "Unknown type of field :feed/name.\n\n  :wrong"
               :title migration-error-title}]
            (test-util/get-spec-error-data
              #(#'schema/actions->internal-models data)))))))


(deftest test-spec-action->migration-create-table-invalid-field-options-error
  (testing "check actions should have correct field options unique"
    (let [data [{:action :create-table
                 :model-name :feed
                 :fields {:name {:type :integer
                                 :unique 1}}}]]
      (is (= [{:message "Option :unique of field :feed/name should be `true`.\n\n  1"
               :title migration-error-title}]
            (test-util/get-spec-error-data
              #(#'schema/actions->internal-models data))))))

  (testing "check actions should have correct field options unique"
    (let [data [{:action :create-table
                 :model-name :feed
                 :fields {:name {:type :integer
                                 :primary-key 1}}}]]
      (is (= [{:message "Option :primary-key of field :feed/name should be `true`.\n\n  1"
               :title migration-error-title}]
            (test-util/get-spec-error-data
              #(#'schema/actions->internal-models data))))))

  (testing "check actions should have correct field options unique"
    (let [data [{:action :create-table
                 :model-name :feed
                 :fields {:name {:type :integer
                                 :foreign-key 1}}}]]
      (is (= [{:message "Option :foreign-key of field :feed/name should be qualified keyword.\n\n  1"
               :title migration-error-title}]
            (test-util/get-spec-error-data
              #(#'schema/actions->internal-models data))))))

  (testing "check actions should have correct field options unique"
    (let [data [{:action :create-table
                 :model-name :feed
                 :fields {:name {:type :integer
                                 :default {:wrong 1}}}}]]
      (is (= [{:message "Option :default of field :feed/name has invalid value.\n\n  {:wrong 1}"
               :title migration-error-title}]
            (test-util/get-spec-error-data
              #(#'schema/actions->internal-models data)))))))
