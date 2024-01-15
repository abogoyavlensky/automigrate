(ns automigrate.models-errors-test
  (:require [clojure.test :refer :all]
            [automigrate.core :as core]
            [automigrate.models :as models]
            [automigrate.testing-util :as test-util]
            [automigrate.testing-config :as config]))


(use-fixtures :each
  (test-util/with-delete-dir config/MIGRATIONS-DIR))


(deftest test-spec-public-model-filter-and-sort-multiple-errors
  (let [data {:foo {:fields [[:id :integer {:null2 false}]
                             [:name]]}
              :bar 10
              :zen [[:title :integer {:unique :WRONG}]]}]
    (is (= [{:message "Field :foo/id has extra options.\n\n  {:null2 false}"
             :title "MODEL ERROR"}
            {:message "Missing type of field :foo/name." :title "MODEL ERROR"}
            {:message "Model :bar should be a map or a vector.\n\n  10"
             :title "MODEL ERROR"}
            {:message "Option :unique of field :zen/title should be `true`.\n\n  {:unique :WRONG}"
             :title "MODEL ERROR"}]
          (test-util/get-spec-error-data #(models/->internal-models data))))))


(deftest test-spec-public-model-no-models-ok
  (let [data {}]
    (is (= []
          (test-util/get-spec-error-data #(models/->internal-models data))))))


(deftest test-spec->internal-models-invalid-structure-error
  (let [data []]
    (is (= [{:message "Models should be defined as a map.\n\n  []"
             :title "MODEL ERROR"}]
          (test-util/get-spec-error-data #(models/->internal-models data))))))


(deftest test-spec-public-model-invalid-definition-error
  (let [data {:foo :wrong}]
    (is (= [{:message "Model :foo should be a map or a vector.\n\n  :wrong"
             :title "MODEL ERROR"}]
          (test-util/get-spec-error-data #(models/->internal-models data))))))


(deftest test-spec-public-model-empty-model-error
  (let [data {:foo []}]
    (is (= [{:message "Model :foo should contain at least one field.\n\n  []"
             :title "MODEL ERROR"}]
          (test-util/get-spec-error-data #(models/->internal-models data)))))

  (let [data {:foo {}}]
    (is (= [{:message "Model :foo should contain :fields key.\n\n  {}"
             :title "MODEL ERROR"}]
          (test-util/get-spec-error-data #(models/->internal-models data)))))

  (let [data {:foo {:fields []}}]
    (is (= [{:message "Model :foo should contain at least one field.\n\n  []"
             :title "MODEL ERROR"}]
          (test-util/get-spec-error-data #(models/->internal-models data))))))


(deftest test-spec-public-model-duplicate-field-error
  (let [data {:foo [[:id :integer]
                    [:id :integer]]}]
    (is (= [{:message "Model :foo has duplicated fields.\n\n  [[:id :integer] [:id :integer]]"
             :title "MODEL ERROR"}]
          (test-util/get-spec-error-data #(models/->internal-models data)))))

  (let [data {:foo [[:id :integer]
                    [:id :bigint]]}]
    (is (= [{:message "Model :foo has duplicated fields.\n\n  [{:name :id, :type :integer} {:name :id, :type :bigint}]"
             :title "MODEL ERROR"}]
          (test-util/get-spec-error-data #(models/->internal-models data)))))

  (let [data {:foo {:fields [[:id :integer]
                             [:id :integer]]}}]
    (is (= [{:message "Model :foo has duplicated fields.\n\n  [[:id :integer] [:id :integer]]"
             :title "MODEL ERROR"}]
          (test-util/get-spec-error-data #(models/->internal-models data)))))

  (let [data {:foo {:fields [[:id :integer]
                             [:id :bigint]]}}]
    (is (= [{:message "Model :foo has duplicated fields.\n\n  [{:name :id, :type :integer} {:name :id, :type :bigint}]"
             :title "MODEL ERROR"}]
          (test-util/get-spec-error-data #(models/->internal-models data))))))


(deftest test-spec-public-model-invalid-model-name-error
  (let [data {"foo" [[:id :integer]]}]
    (is (= [{:message "Model name should be a keyword.\n\n  \"foo\""
             :title "MODEL ERROR"}]
          (test-util/get-spec-error-data #(models/->internal-models data)))))

  (let [data {"foo" {:fields [[:id :integer]]}}]
    (is (= [{:message "Model name should be a keyword.\n\n  \"foo\""
             :title "MODEL ERROR"}]
          (test-util/get-spec-error-data #(models/->internal-models data))))))


(deftest test-spec-public-model-extra-keys-error
  (let [data {:foo {:fields [[:id :integer]]
                    :extra-key []}}]
    (is (= [{:message "Model :foo definition has extra key."
             :title "MODEL ERROR"}]
          (test-util/get-spec-error-data #(models/->internal-models data))))))


(deftest test-spec-field-vec-invalid-field-name-error
  (testing "check field without nested vec"
    (let [data {:foo [:wrong]}]
      (is (= [{:message "Invalid field definition in model :foo.\n\n  :wrong"
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data)))))

    (let [data {:foo {:fields [:wrong]}}]
      (is (= [{:message "Invalid field definition in model :foo.\n\n  :wrong"
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (testing "check field defined as map"
    (let [data {:foo [{:name :int}]}]
      (is (= [{:message "Invalid field definition in model :foo.\n\n  {:name :int}"
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data)))))

    (let [data {:foo {:fields [{:name :int}]}}]
      (is (= [{:message "Invalid field definition in model :foo.\n\n  {:name :int}"
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (testing "check field missing name"
    (let [data {:foo [[]]}]
      (is (= [{:message "Missing field name in model :foo."
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data)))))

    (let [data {:foo {:fields [[]]}}]
      (is (= [{:message "Missing field name in model :foo."
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (testing "check invalid field name as a string"
    (let [data {:foo [["wrong"]]}]
      (is (= [{:message "Invalid field name in model :foo. Field name should be a keyword.\n\n  \"wrong\""
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data)))))

    (let [data {:foo {:fields [["wrong"]]}}]
      (is (= [{:message "Invalid field name in model :foo. Field name should be a keyword.\n\n  \"wrong\""
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data)))))))


(deftest test-spec-field-vec-invalid-keyword-field-type-error
  (testing "check missing field type"
    (let [data {:foo [[:id]]}]
      (is (= [{:message "Missing type of field :foo/id."
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data)))))

    (let [data {:foo {:fields [[:id]]}}]
      (is (= [{:message "Missing type of field :foo/id."
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (testing "check invalid field type as string"
    (let [data {:foo [[:id "wrong-type"]]}]
      (is (= [{:message "Invalid type of field :foo/id.\n\n  \"wrong-type\""
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data)))))

    (let [data {:foo {:fields [[:id "wrong-type"]]}}]
      (is (= [{:message "Invalid type of field :foo/id.\n\n  \"wrong-type\""
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (testing "check invalid field type as keyword"
    (let [data {:foo [[:id :wrong-type]]}]
      (is (= [{:message "Unknown type of field :foo/id.\n\n  :wrong-type"
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data)))))))


(deftest test-spec-field-vec-char-field-type-ok
  (testing "check valid char types without param"
    (let [data {:foo [[:id :char]]}]
      (is (= []
            (test-util/get-spec-error-data #(models/->internal-models data)))))
    (let [data {:foo [[:id :varchar]]}]
      (is (= []
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (let [data {:foo {:fields [[:id [:varchar "test"]]]}}]
    (is (= [{:message "Parameter for char type of field :foo/id should be positive integer.\n\n  \"test\""
             :title "MODEL ERROR"}]
          (test-util/get-spec-error-data #(models/->internal-models data))))))


(deftest test-spec-field-vec-invalid-float-field-type-error
  (testing "check valid float type"
    (let [data {:foo [[:id :float]]}]
      (is (= []
            (test-util/get-spec-error-data #(models/->internal-models data)))))

    (let [data {:foo {:fields [[:id [:float 1]]]}}]
      (is (= []
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (testing "check invalid float type as vector"
    (let [data {:foo [[:id [:float]]]}]
      (is (= [{:message "Vector form of float type of field :foo/id should have parameter.\n\n  [:float]"
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (testing "check invalid float type as vector with real"
    (let [data {:foo [[:id [:float 0]]]}]
      (is (= [{:message "Parameter for float type of field :foo/id should be integer between 1 and 53.\n\n  0"
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data)))))))


(deftest test-spec-field-vec-extra-item-in-vec-error
  (let [data {:foo [[:id :float {} :extra-item]]}]
    (is (= [{:message "Field :foo/id has extra value in definition.\n\n  (:extra-item)"
             :title "MODEL ERROR"}]
          (test-util/get-spec-error-data #(models/->internal-models data))))))


(deftest test-spec-field-vec-invalid-options-value-error
  (testing "check empty options ok"
    (let [data {:foo [[:id :float {}]]}]
      (is (= []
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (testing "check invalid value options error"
    (let [data {:foo [[:id :float []]]}]
      (is (= [{:message "Invalid options of field :foo/id.\n\n  []"
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data)))))

    (let [data {:foo {:fields [[:id :float []]]}}]
      (is (= [{:message "Invalid options of field :foo/id.\n\n  []"
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (testing "check extra key in options"
    (let [data {:foo [[:id :float {:extra-key nil}]]}]
      (is (= [{:message "Field :foo/id has extra options.\n\n  {:extra-key nil}"
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data)))))))


(deftest test-spec-field-vec-invalid-option-null-primary-key-unique-error
  (testing "check option :null error"
    (let [data {:foo [[:id :float {:null []}]]}]
      (is (= [{:message "Option :null of field :foo/id should be boolean.\n\n  {:null []}"
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (testing "check option :primary-key error"
    (let [data {:foo [[:id :float {:primary-key false}]]}]
      (is (= [{:message "Option :primary-key of field :foo/id should be `true`.\n\n  {:primary-key false}"
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (testing "check option :unique error"
    (let [data {:foo {:fields [[:id :float {:unique false}]]}}]
      (is (= [{:message "Option :unique of field :foo/id should be `true`.\n\n  {:unique false}"
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (testing "check option :default error"
    (let [data {:foo [[:id :float {:default {}}]]}]
      (is (= [{:message "Option :default of field :foo/id has invalid value.\n\n  {:default {}}"
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (testing "check option :foreign-key error"
    (let [data {:foo [[:id :float {:foreign-key :id}]]}]
      (is (= [{:message (str "Option :foreign-key of field :foo/id should be"
                          " qualified keyword.\n\n  {:foreign-key :id}")
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (testing "check option :on-delete error"
    (let [data {:foo [[:id :float {:on-delete :wrong}]]}]
      (is (= [{:message (str "Option :on-delete of field :foo/id should be"
                          " one of available FK actions.\n\n  {:on-delete :wrong}")
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (testing "check option :on-update error"
    (let [data {:foo [[:id :float {:on-update :wrong}]]}]
      (is (= [{:message (str "Option :on-update of field :foo/id should be"
                          " one of available FK actions.\n\n  {:on-update :wrong}")
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data)))))))


(deftest test-spec-field-vec-validate-fk-options-error
  (testing "check on-delete without foreign-key"
    (let [data {:foo [[:id :float {:on-delete :cascade}]]}]
      (is (= [{:message (str "Field :foo/id has :on-delete option"
                          " without :foreign-key.\n\n  {:on-delete :cascade}")
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (testing "check on-update without foreign-key"
    (let [data {:foo {:fields [[:id :float {:on-update :cascade}]]}}]
      (is (= [{:message (str "Field :foo/id has :on-update option"
                          " without :foreign-key.\n\n  {:on-update :cascade}")
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data)))))))


(deftest test-spec-index-vec-errors
  (testing "check empty indexes error"
    (let [data {:foo {:fields [[:id :integer]]
                      :indexes []}}]
      (is (= [{:message "Model :foo should contain at least one index if :indexes key exists.\n\n  []"
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (testing "check not vec indexes"
    (let [data {:foo {:fields [[:id :integer]]
                      :indexes {}}}]
      (is (= [{:message "Indexes definition of model :foo should be a vector.\n\n  {}"
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (testing "check duplicated indexes"
    (let [data {:foo {:fields [[:id :integer]]
                      :indexes [[:foo-idx :btree {:fields [:id]}]
                                [:foo-idx :btree {:fields [:id]}]]}}]
      (is (= [{:message (str "Indexes definition of model :foo has duplicated indexes.\n\n  "
                          "[[:foo-idx :btree {:fields [:id]}] [:foo-idx :btree {:fields [:id]}]]")
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data)))))))


(deftest test-spec-index-vec-index-name-error
  (testing "check empty index name error"
    (let [data {:foo {:fields [[:id :integer]]
                      :indexes [[]]}}]
      (is (= [{:message "Missing index name in model :foo."
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (testing "check invalid index name error"
    (let [data {:foo {:fields [[:id :integer]]
                      :indexes [["foo-idx"]]}}]
      (is (= [{:message "Invalid index name in model :foo. Index name should be a keyword.\n\n  \"foo-idx\""
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data)))))))


(deftest test-spec-index-vec-index-type-error
  (testing "check empty index type error"
    (let [data {:foo {:fields [[:id :integer]]
                      :indexes [[:foo-idx]]}}]
      (is (= [{:message "Missing type of index :foo.indexes/foo-idx."
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (testing "check invalid index type error"
    (let [data {:foo {:fields [[:id :integer]]
                      :indexes [[:foo-idx :wrong]]}}]
      (is (= [{:message "Invalid type of index :foo.indexes/foo-idx.\n\n  :wrong"
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data)))))))


(deftest test-spec-index-vec-extra-item-in-vec-error
  (let [data {:foo {:fields [[:id :float]]
                    :indexes [[:foo-idx :btree {:fields [:id]} :extra-key]]}}]
    (is (= [{:message "Index :foo.indexes/foo-idx has extra value in definition.\n\n  (:extra-key)"
             :title "MODEL ERROR"}]
          (test-util/get-spec-error-data #(models/->internal-models data))))))


(deftest test-spec-index-vec-fields-error
  (testing "check invalid value in index fields"
    (let [data {:foo {:fields [[:id [:char 50]]]
                      :indexes [[:foo-idx :btree {:fields {}}]]}}]
      (is (= [{:message (str "Index :foo.indexes/foo-idx should have :fields option"
                          " as vector with distinct fields of the model :foo.\n\n  {}")
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (testing "check duplicate value in index fields"
    (let [data {:foo {:fields [[:id [:char 50]]]
                      :indexes [[:foo-idx :btree {:fields [:id :id]}]]}}]
      (is (= [{:message (str "Index :foo.indexes/foo-idx should have :fields option"
                          " as vector with distinct fields of the model :foo.\n\n  [:id :id]")
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (testing "check duplicate value in index fields"
    (let [data {:foo {:fields [[:id [:char 50]]]
                      :indexes [[:foo-idx :btree {:fields []}]]}}]
      (is (= [{:message (str "Index :foo.indexes/foo-idx should have :fields option"
                          " as vector with distinct fields of the model :foo.\n\n  []")
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (testing "check duplicate value in index fields"
    (let [data {:foo {:fields [[:id [:char 50]]]
                      :indexes [[:foo-idx :btree {:fields ["id"]}]]}}]
      (is (= [{:message (str "Index :foo.indexes/foo-idx should have :fields option"
                          " as vector with distinct fields of the model :foo.\n\n  \"id\"")
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data)))))))


(deftest test-spec-index-vec-options-error
  (testing "check index misses fields"
    (let [data {:foo {:fields [[:id [:char 50]]]
                      :indexes [[:foo-idx :btree {}]]}}]
      (is (= [{:message "Index :foo.indexes/foo-idx misses :fields options."
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (testing "check extra option in index"
    (let [data {:foo {:fields [[:id [:char 50]]]
                      :indexes [[:foo-idx :btree {:fields [:id]
                                                  :extra-option nil}]]}}]
      (is (= [{:message "Options of index :foo.indexes/foo-idx have extra keys."
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (testing "check invalid unique option for index"
    (let [data {:foo {:fields [[:id [:char 50]]]
                      :indexes [[:foo-idx :btree {:fields [:id]
                                                  :unique nil}]]}}]
      (is (= [{:message "Option :unique of index :foo.indexes/foo-idx should satisfy: `true?`."
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data)))))))


(deftest test-spec-model-internal-duplicated-index-name
  (let [data {:foo {:fields [[:id :integer]
                             [:name :text]]
                    :indexes [[:foo-idx :btree {:fields [:id]}]
                              [:foo-idx :gin {:fields [:name]}]]}}]
    (is (= [{:message (str "Model :foo has duplicated indexes.\n\n  "
                        "[{:name :foo-idx, :type :btree, :options {:fields [:id]}} "
                        "{:name :foo-idx, :type :gin, :options {:fields [:name]}}]")
             :title "MODEL ERROR"}]
          (test-util/get-spec-error-data #(models/->internal-models data))))))


(deftest test-spec-validate-default-and-null-error
  (testing "check invalid :default and null"
    (let [data {:foo [[:id :integer {:null false
                                     :default nil}]]}]
      (is (= [{:message (str "Option :default of field :foo/id couldn't be `nil` because of:"
                          " `:null false`.\n\n  {:null false, :default nil, :type :integer}")
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data)))))))


(deftest test-spec-validate-fk-options-and-null-error
  (testing "check invalid fk action options and null, model as vec"
    (let [data {:bar [[:id :integer]]
                :foo [[:bar-id :integer {:null false
                                         :foreign-key :bar/id
                                         :on-delete :set-null}]]}]
      (is (= [{:message (str "Option :on-delete of field :foo/bar-id couldn't be :set-null because of:"
                          " `:null false`.\n\n  {:null false, :foreign-key :bar/id, "
                          ":on-delete :set-null, :type :integer}")
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (testing "check invalid fk action options and null, model as vec"
    (let [data {:bar [[:id :integer]]
                :foo {:fields [[:bar-id :integer {:null false
                                                  :foreign-key :bar/id
                                                  :on-update :set-null}]]}}]
      (is (= [{:message (str "Option :on-update of field :foo/bar-id couldn't be :set-null because of:"
                          " `:null false`.\n\n  {:null false, :foreign-key :bar/id, "
                          ":on-update :set-null, :type :integer}")
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data)))))))


(deftest test-spec-field-vec-invalid-validate-default-and-type-error
  (testing "check option :default and field type integer ok"
    (let [data {:foo [[:id :integer {:default 1}]]}]
      (is (= []
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (testing "check invalid :default value for type integer"
    (let [data {:foo [[:id :integer {:default "1"}]]}]
      (is (= [{:message (str "Option :default of field :foo/id does not match "
                          "the field type: `:integer`.\n\n  {:default \"1\", :type :integer}")
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (testing "check invalid :default value for type smallint"
    (let [data {:foo [[:id :smallint {:default true}]]}]
      (is (= [{:message (str "Option :default of field :foo/id does not match "
                          "the field type: `:smallint`.\n\n  {:default true, :type :smallint}")
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (testing "check invalid :default value for type bigint"
    (let [data {:foo [[:id :bigint {:default "1"}]]}]
      (is (= [{:message (str "Option :default of field :foo/id does not match "
                          "the field type: `:bigint`.\n\n  {:default \"1\", :type :bigint}")
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (testing "check invalid :default value for type serial"
    (let [data {:foo [[:id :serial {:default "1"}]]}]
      (is (= [{:message (str "Option :default of field :foo/id does not match "
                          "the field type: `:serial`.\n\n  {:default \"1\", :type :serial}")
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (testing "check invalid :default value for type text"
    (let [data {:foo [[:id :text {:default 1}]]}]
      (is (= [{:message (str "Option :default of field :foo/id does not match "
                          "the field type: `:text`.\n\n  {:default 1, :type :text}")
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (testing "check invalid :default value for type char"
    (let [data {:foo [[:id [:char 50] {:default 1}]]}]
      (is (= [{:message (str "Option :default of field :foo/id does not match "
                          "the field type: `[:char 50]`.\n\n  {:default 1, :type [:char 50]}")
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (testing "check invalid :default value for type timestamp"
    (let [data {:foo [[:id :timestamp {:default 1}]]}]
      (is (= [{:message (str "Option :default of field :foo/id does not match "
                          "the field type: `:timestamp`.\n\n  {:default 1, :type :timestamp}")
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (testing "check invalid :default value for type float"
    (let [data {:foo [[:id :float {:default 1}]]}]
      (is (= [{:message (str "Option :default of field :foo/id does not match "
                          "the field type: `:float`.\n\n  {:default 1, :type :float}")
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data)))))))


(deftest test-fk-field-missing-referenced-model-error
  (testing "check missing referenced model error"
    (let [data {:foo [[:bar-id :integer {:foreign-key :bar/id}]]}]
      (is (= {:message "Foreign key :foo/bar-id has reference on the missing model :bar."
              :title "MODEL ERROR"
              :type :automigrate.models/missing-referenced-model
              :data {:fk-field :foo/bar-id
                     :referenced-model :bar}}
            (test-util/thrown-with-slingshot-data?
              [:type ::models/missing-referenced-model]
              (models/->internal-models data)))))))


(deftest test-fk-field-missing-referenced-field-error
  (testing "check missing referenced field error"
    (let [data {:bar [[:name :text]]
                :foo {:fields [[:bar-id :integer {:foreign-key :bar/id}]]}}]
      (is (= {:data {:referenced-field :id
                     :referenced-model :bar}
              :message "Foreign key :foo/bar-id has reference on the missing field :bar/id."
              :title "MODEL ERROR"
              :type :automigrate.models/missing-referenced-field}
            (test-util/thrown-with-slingshot-data?
              [:type ::models/missing-referenced-field]
              (models/->internal-models data)))))))


(deftest test-fk-field-referenced-field-is-not-unique
  (testing "check referenced field is not unique error"
    (let [data {:bar [[:id :integer]]
                :foo {:fields [[:bar-id :integer {:foreign-key :bar/id}]]}}]
      (is (= (str "Foreign key :foo/bar-id there is no unique or primary key constraint"
               " on the referenced field :bar/id.")
            (:message (test-util/thrown-with-slingshot-data?
                        [:type ::models/referenced-field-is-not-unique]
                        (models/->internal-models data))))))))


(deftest test-fk-and-referenced-fields-have-different-types
  (testing "check fk and referenced fields have different types error"
    (let [data {:bar [[:id :text {:unique true}]]
                :foo {:fields [[:bar-id :integer {:foreign-key :bar/id}]]}}]
      (is (= "Foreign key field :foo/bar-id and referenced field :bar/id have different types."
            (:message (test-util/thrown-with-slingshot-data?
                        [:type ::models/fk-fields-have-different-types]
                        (models/->internal-models data))))))))


(deftest test-internal-models-spec-validate-indexes-duplication-accross-models
  (testing "check indexes duplication across models"
    (let [data {:bar {:fields [[:id :integer {:unique true}]]
                      :indexes [[:duplicated-idx :btree {:fields [:id]}]
                                [:another-duplicated-idx :btree {:fields [:id]}]]}
                :no-indexes [[:name :text]]
                :foo {:fields [[:id :integer]]
                      :indexes [[:duplicated-idx :btree {:fields [:id]}]]}
                :account {:fields [[:id :integer]]
                          :indexes [[:another-duplicated-idx :btree {:fields [:id]}]]}}]
      (is (= [{:message "Models have duplicated indexes: [:duplicated-idx, :another-duplicated-idx]."
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data)))))))


(deftest test-spec-validate-indexed-field-error
  (testing "check if indexed field presented"
    (let [data {:foo {:fields [[:id :integer]]
                      :indexes [[:foo-name-idx :btree {:fields [:name]}]]}}]
      (is (= [{:message "Missing indexed fields [:name] in model :foo."
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data)))))))


(deftest test-run-multiple-model-errors-error
  (is (= (str "-- MODEL ERROR -------------------------------------\n\n"
           "Parameter for char type of field :feed/name should be positive integer.\n\n  "
           "\"wrong-value\""
           "\n\n-- MODEL ERROR -------------------------------------\n\n"
           "Field :account/address has extra options.\n\n  {:primary-field 10}\n\n")
        (with-out-str
          (core/make {:migrations-dir config/MIGRATIONS-DIR
                      :models-file (str config/MODELS-DIR "feed_errors.edn")
                      :title "COMMAND ERROR"})))))


(deftest test-spec-field-vec-invalid-decimal-field-type-error
  (testing "check valid decimal type"
    (let [data {:foo [[:amount :decimal]]}]
      (is (= []
            (test-util/get-spec-error-data #(models/->internal-models data)))))

    (let [data {:foo {:fields [[:amount [:decimal 10 2]]]}}]
      (is (= []
            (test-util/get-spec-error-data #(models/->internal-models data))))))

  (testing "check invalid decimal type as vector"
    (let [data {:foo [[:amount [:decimal]]]}]
      (is (= [{:message "Invalid definition decimal/numeric type of field :foo/amount."
               :title "MODEL ERROR"}]
            (test-util/get-spec-error-data #(models/->internal-models data)))))))
