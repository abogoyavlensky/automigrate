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
      (is (= [{:message "Invalid action type.\n\n  []"
               :title migration-error-title}]
            (test-util/get-spec-error-data
              #(#'schema/actions->internal-models data))))))

  (testing "check actions should have existing action type"
    (let [data [{:action :MISSING-ACTION}]]
      (is (= [{:message "Invalid action type.\n\n  {:action :MISSING-ACTION}"
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

  (testing "check actions should have correct field options primary-key"
    (let [data [{:action :create-table
                 :model-name :feed
                 :fields {:name {:type :integer
                                 :primary-key 1}}}]]
      (is (= [{:message "Option :primary-key of field :feed/name should be `true`.\n\n  1"
               :title migration-error-title}]
            (test-util/get-spec-error-data
              #(#'schema/actions->internal-models data))))))

  (testing "check actions should have correct field options fk"
    (let [data [{:action :create-table
                 :model-name :feed
                 :fields {:name {:type :integer
                                 :foreign-key 1}}}]]
      (is (= [{:message "Option :foreign-key of field :feed/name should be qualified keyword.\n\n  1"
               :title migration-error-title}]
            (test-util/get-spec-error-data
              #(#'schema/actions->internal-models data))))))

  (testing "check actions should have correct field options default"
    (let [data [{:action :create-table
                 :model-name :feed
                 :fields {:name {:type :integer
                                 :default {:wrong 1}}}}]]
      (is (= [{:message "Option :default of field :feed/name has invalid value.\n\n  {:wrong 1}"
               :title migration-error-title}]
            (test-util/get-spec-error-data
              #(#'schema/actions->internal-models data))))))

  (testing "check actions should have correct field options default and type"
    (let [data [{:action :create-table
                 :model-name :feed
                 :fields {:name {:type :integer
                                 :default "wrong type"}}}]]
      (is (= [{:message (str "Option :default of field :feed/name does not match the field type: "
                          "`:integer`.\n\n  {:default \"wrong type\", :type :integer}")
               :title migration-error-title}]
            (test-util/get-spec-error-data
              #(#'schema/actions->internal-models data))))))

  (testing "check actions should have correct field options fk and default"
    (let [data [{:action :create-table
                 :model-name :feed
                 :fields {:name {:type :integer
                                 :foreign-key :account/id
                                 :on-update :set-null
                                 :null false}}}]]
      (is (= [{:message (str "Option :on-update of field :feed/name couldn't be :set-null because of:"
                          " `:null false`.\n\n  {:type :integer, :foreign-key :account/id, "
                          ":on-update :set-null, :null false}")
               :title migration-error-title}]
            (test-util/get-spec-error-data
              #(#'schema/actions->internal-models data)))))))


(deftest test-spec-action->migration-drop-table-invalid-model-name
  (testing "check actions should have correct model name"
    (let [data [{:action :drop-table
                 :model-name "wrong-name"}]]
      (is (= [{:message "Action has invalid model name.\n\n  \"wrong-name\""
               :title migration-error-title}]
            (test-util/get-spec-error-data
              #(#'schema/actions->internal-models data)))))))


(deftest test-spec-action->migration-add-column-invalid-field-options-error
  (testing "check actions should have correct field type"
    (let [data [{:action :add-column
                 :model-name :feed
                 :field-name :id
                 :options {:type :wrong-type}}]]
      (is (= [{:message "Unknown type of field :feed/id.\n\n  :wrong-type"
               :title migration-error-title}]
            (test-util/get-spec-error-data
              #(#'schema/actions->internal-models data))))))

  (testing "check actions should have correct field options unique"
    (let [data [{:action :add-column
                 :model-name :feed
                 :field-name :id
                 :options {:type :integer
                           :unique 1}}]]
      (is (= [{:message "Option :unique of field :feed/id should be `true`.\n\n  1"
               :title migration-error-title}]
            (test-util/get-spec-error-data
              #(#'schema/actions->internal-models data))))))

  (testing "check actions should have correct field options primary-key"
    (let [data [{:action :add-column
                 :model-name :feed
                 :field-name :id
                 :options {:type :integer
                           :primary-key 1}}]]
      (is (= [{:message "Option :primary-key of field :feed/id should be `true`.\n\n  1"
               :title migration-error-title}]
            (test-util/get-spec-error-data
              #(#'schema/actions->internal-models data))))))

  (testing "check actions should have correct field options fk"
    (let [data [{:action :add-column
                 :model-name :feed
                 :field-name :id
                 :options {:type :integer
                           :foreign-key 1}}]]
      (is (= [{:message "Option :foreign-key of field :feed/id should be qualified keyword.\n\n  1"
               :title migration-error-title}]
            (test-util/get-spec-error-data
              #(#'schema/actions->internal-models data))))))

  (testing "check actions should have correct field options default"
    (let [data [{:action :add-column
                 :model-name :feed
                 :field-name :id
                 :options {:type :integer
                           :default {:wrong 1}}}]]
      (is (= [{:message "Option :default of field :feed/id has invalid value.\n\n  {:wrong 1}"
               :title migration-error-title}]
            (test-util/get-spec-error-data
              #(#'schema/actions->internal-models data))))))

  (testing "check actions should have correct field options default and type"
    (let [data [{:action :add-column
                 :model-name :feed
                 :field-name :id
                 :options {:type :integer
                           :default "wrong type"}}]]
      (is (= [{:message (str "Option :default of field :feed/id does not match the field type: "
                          "`:integer`.\n\n  {:default \"wrong type\", :type :integer}")
               :title migration-error-title}]
            (test-util/get-spec-error-data
              #(#'schema/actions->internal-models data))))))

  (testing "check actions should have correct field options fk and default"
    (let [data [{:action :add-column
                 :model-name :feed
                 :field-name :id
                 :options {:type :integer
                           :foreign-key :account/id
                           :on-update :set-null
                           :null false}}]]
      (is (= [{:message (str "Option :on-update of field :feed/id couldn't be :set-null because of:"
                          " `:null false`.\n\n  {:type :integer, :foreign-key :account/id, "
                          ":on-update :set-null, :null false}")
               :title migration-error-title}]
            (test-util/get-spec-error-data
              #(#'schema/actions->internal-models data)))))))


(deftest test-spec-action->migration-alter-column-invalid-field-options-error
  (testing "check actions should have correct field type"
    (let [data [{:action :alter-column
                 :model-name :feed
                 :field-name :id
                 :options {:type :wrong-type}
                 :changes {:type {:from :integer
                                  :to :wrong-type}}}]]
      (is (= [{:message "Unknown type of field :feed/id.\n\n  :wrong-type"
               :title migration-error-title}
              {:message "Schema failed for migration.\n\n  {:from :integer, :to :wrong-type}"
               :title migration-error-title}]
            (test-util/get-spec-error-data
              #(#'schema/actions->internal-models data))))))

  (testing "check actions should have correct field options unique"
    (let [data [{:action :alter-column
                 :model-name :feed
                 :field-name :id
                 :options {:type :integer
                           :unique true}
                 :changes {:unique {:from true
                                    :to 1}}}]]
      (is (= [{:message "Schema failed for migration.\n\n  {:from true, :to 1}"
               :title migration-error-title}]
            (test-util/get-spec-error-data
              #(#'schema/actions->internal-models data)))))))


(deftest test-spec-action->migration-create-index-invalid-field-options-error
  (testing "check actions should have correct index type"
    (let [data [{:action :create-index
                 :model-name :feed
                 :index-name :feed-id-idx
                 :options {:type :wrong-type
                           :fields [:id]}}]]
      (is (= [{:message "Invalid type of index :feed.indexes/type.\n\n  :wrong-type"
               :title migration-error-title}]
            (test-util/get-spec-error-data
              #(#'schema/actions->internal-models data)))))))


(deftest test-spec-action->migration-alter-index-invalid-field-options-error
  (testing "check actions should have correct index unique option"
    (let [data [{:action :create-index
                 :model-name :feed
                 :index-name :feed-id-idx
                 :options {:type :btree
                           :fields [:id]
                           :unique "wrong-value"}}]]
      (is (= [{:message "Option :unique of index :feed.indexes/unique should satisfy: `true?`."
               :title migration-error-title}]
            (test-util/get-spec-error-data
              #(#'schema/actions->internal-models data)))))))

