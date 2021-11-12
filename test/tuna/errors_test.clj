(ns tuna.errors-test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [tuna.models :as models]
            [tuna.util.test :as test-util]))


(defn- get-spec-error-data
  [f]
  ; TODO: remove debug print!
  (->> #p (test-util/thrown-with-slingshot-data? [:type ::s/invalid] (f))
    :reports
    (map #(dissoc % :problems))))


(deftest test-spec-public-model-filter-and-sort-multiple-errors
  (let [data {:foo {:fields [[:id :integer {:null2 false}]
                             [:name]]}
              :bar 10
              :zen [[:title :integer {:unique :WRONG}]]}]
    (is (= [{:message "Model :bar should be a map.\n\nor\n\nModel :bar should be a vector.\n\n  10"
             :title "MODEL ERROR"}
            {:message "Missing type of field :foo/name." :title "MODEL ERROR"}
            {:message "Field :foo/id has extra options.\n\n  {:null2 false}"
             :title "MODEL ERROR"}
            {:message "Option :unique of field :zen/title should be `true`.\n\n  {:unique :WRONG}"
             :title "MODEL ERROR"}]
          (get-spec-error-data #(models/->internal-models data))))))


(deftest test-spec-public-model-no-models-ok
  (let [data {}]
    (is (= []
          (get-spec-error-data #(models/->internal-models data))))))


(deftest test-spec->internal-models-invalid-structure-error
  (let [data []]
    (is (= [{:message "Models should be defined as a map.\n\n  []"
             :title "MODEL ERROR"}]
          (get-spec-error-data #(models/->internal-models data))))))


(deftest test-spec-public-model-invalid-definition-error
  (let [data {:foo :wrong}]
    (is (= [{:message "Model :foo should be a map.\n\nor\n\nModel :foo should be a vector.\n\n  :wrong"
             :title "MODEL ERROR"}]
          (get-spec-error-data #(models/->internal-models data))))))


(deftest test-spec-public-model-empty-model-error
  (let [data {:foo []}]
    (is (= [{:message "Model :foo should contain at least one field.\n\n  []"
             :title "MODEL ERROR"}]
          (get-spec-error-data #(models/->internal-models data)))))

  (let [data {:foo {}}]
    (is (= [{:message "Model :foo should contain :fields key.\n\n  {}"
             :title "MODEL ERROR"}]
          (get-spec-error-data #(models/->internal-models data)))))

  (let [data {:foo {:fields []}}]
    (is (= [{:message "Model :foo should contain at least one field.\n\n  []"
             :title "MODEL ERROR"}]
          (get-spec-error-data #(models/->internal-models data))))))


(deftest test-spec-public-model-duplicate-field-error
  (let [data {:foo [[:id :integer]
                    [:id :integer]]}]
    (is (= [{:message "Model :foo has duplicated fields.\n\n  [[:id :integer] [:id :integer]]"
             :title "MODEL ERROR"}]
          (get-spec-error-data #(models/->internal-models data)))))

  (let [data {:foo [[:id :integer]
                    [:id :bigint]]}]
    (is (= [{:message "Model :foo has duplicated fields.\n\n  [{:name :id, :type :integer} {:name :id, :type :bigint}]"
             :title "MODEL ERROR"}]
          (get-spec-error-data #(models/->internal-models data)))))

  (let [data {:foo {:fields [[:id :integer]
                             [:id :integer]]}}]
    (is (= [{:message "Model :foo has duplicated fields.\n\n  [[:id :integer] [:id :integer]]"
             :title "MODEL ERROR"}]
          (get-spec-error-data #(models/->internal-models data)))))

  (let [data {:foo {:fields [[:id :integer]
                             [:id :bigint]]}}]
    (is (= [{:message "Model :foo has duplicated fields.\n\n  [{:name :id, :type :integer} {:name :id, :type :bigint}]"
             :title "MODEL ERROR"}]
          (get-spec-error-data #(models/->internal-models data))))))


(deftest test-spec-public-model-invalid-model-name-error
  (let [data {"foo" [[:id :integer]]}]
    (is (= [{:message "Model name should be a keyword.\n\n  \"foo\""
             :title "MODEL ERROR"}]
          (get-spec-error-data #(models/->internal-models data)))))

  (let [data {"foo" {:fields [[:id :integer]]}}]
    (is (= [{:message "Model name should be a keyword.\n\n  \"foo\""
             :title "MODEL ERROR"}]
          (get-spec-error-data #(models/->internal-models data))))))


(deftest test-spec-public-model-extra-keys-error
  (let [data {:foo {:fields [[:id :integer]]
                    :extra-key []}}]
    (is (= [{:message "Model :foo definition has extra key."
             :title "MODEL ERROR"}]
          (get-spec-error-data #(models/->internal-models data))))))


(deftest test-spec-field-vec-invalid-field-name-error
  (testing "check field without nested vec"
    (let [data {:foo [:wrong]}]
      (is (= [{:message "Invalid field definition in model :foo.\n\n  :wrong"
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data)))))

    (let [data {:foo {:fields [:wrong]}}]
      (is (= [{:message "Invalid field definition in model :foo.\n\n  :wrong"
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data))))))

  (testing "check field defined as map"
    (let [data {:foo [{:name :int}]}]
      (is (= [{:message "Invalid field definition in model :foo.\n\n  {:name :int}"
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data)))))

    (let [data {:foo {:fields [{:name :int}]}}]
      (is (= [{:message "Invalid field definition in model :foo.\n\n  {:name :int}"
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data))))))

  (testing "check field missing name"
    (let [data {:foo [[]]}]
      (is (= [{:message "Missing field name in model :foo."
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data)))))

    (let [data {:foo {:fields [[]]}}]
      (is (= [{:message "Missing field name in model :foo."
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data))))))

  (testing "check invalid field name as a string"
    (let [data {:foo [["wrong"]]}]
      (is (= [{:message "Invalid field name in model :foo. Field name should be a keyword.\n\n  \"wrong\""
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data)))))

    (let [data {:foo {:fields [["wrong"]]}}]
      (is (= [{:message "Invalid field name in model :foo. Field name should be a keyword.\n\n  \"wrong\""
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data)))))))


(deftest test-spec-field-vec-invalid-keyword-field-type-error
  (testing "check missing field type"
    (let [data {:foo [[:id]]}]
      (is (= [{:message "Missing type of field :foo/id."
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data)))))

    (let [data {:foo {:fields [[:id]]}}]
      (is (= [{:message "Missing type of field :foo/id."
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data))))))

  (testing "check invalid field type as string"
    (let [data {:foo [[:id "wrong-type"]]}]
      (is (= [{:message "Invalid type of field :foo/id.\n\n  \"wrong-type\""
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data)))))

    (let [data {:foo {:fields [[:id "wrong-type"]]}}]
      (is (= [{:message "Invalid type of field :foo/id.\n\n  \"wrong-type\""
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data))))))

  (testing "check invalid field type as keyword"
    (let [data {:foo [[:id :wrong-type]]}]
      (is (= [{:message "Invalid type of field :foo/id.\n\n  :wrong-type"
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data)))))))


(deftest test-spec-field-vec-invalid-char-field-type-error
  (let [data {:foo [[:id :char]]}]
    (is (= [{:message "Invalid type of field :foo/id.\n\n  :char"
             :title "MODEL ERROR"}]
          (get-spec-error-data #(models/->internal-models data)))))

  (let [data {:foo {:fields [[:id [:varchar "test"]]]}}]
    (is (= [{:message "Invalid type of field :foo/id.\n\n  [:varchar \"test\"]"
             :title "MODEL ERROR"}]
          (get-spec-error-data #(models/->internal-models data))))))


(deftest test-spec-field-vec-invalid-float-field-type-error
  (testing "check valid float type"
    (let [data {:foo [[:id :float]]}]
      (is (= []
            (get-spec-error-data #(models/->internal-models data)))))

    (let [data {:foo {:fields [[:id [:float 1]]]}}]
      (is (= []
            (get-spec-error-data #(models/->internal-models data))))))

  (testing "check invalid float type as vector"
    (let [data {:foo [[:id [:float]]]}]
      (is (= [{:message "Invalid type of field :foo/id.\n\n  [:float]"
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data))))))

  (testing "check invalid float type as vector with int"
    (let [data {:foo [[:id [:float 0.1]]]}]
      (is (= [{:message "Invalid type of field :foo/id.\n\n  [:float 0.1]"
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data)))))))


(deftest test-spec-field-vec-extra-item-in-vec-error
  (let [data {:foo [[:id :float {} :extra-item]]}]
    (is (= [{:message "Field :foo/id has extra value in definition.\n\n  (:extra-item)"
             :title "MODEL ERROR"}]
          (get-spec-error-data #(models/->internal-models data))))))


(deftest test-spec-field-vec-invalid-options-value-error
  (testing "check empty options ok"
    (let [data {:foo [[:id :float {}]]}]
      (is (= []
            (get-spec-error-data #(models/->internal-models data))))))

  (testing "check invalid value options error"
    (let [data {:foo [[:id :float []]]}]
      (is (= [{:message "Invalid options of field :foo/id.\n\n  []"
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data)))))

    (let [data {:foo {:fields [[:id :float []]]}}]
      (is (= [{:message "Invalid options of field :foo/id.\n\n  []"
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data))))))

  (testing "check extra key in options"
    (let [data {:foo [[:id :float {:extra-key nil}]]}]
      (is (= [{:message "Field :foo/id has extra options.\n\n  {:extra-key nil}"
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data)))))))


(deftest test-spec-field-vec-invalid-option-null-primary-key-unique-error
  (testing "check option :null error"
    (let [data {:foo [[:id :float {:null []}]]}]
      (is (= [{:message "Option :null of field :foo/id should be boolean.\n\n  {:null []}"
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data))))))

  (testing "check option :primary-key error"
    (let [data {:foo [[:id :float {:primary-key false}]]}]
      (is (= [{:message "Option :primary-key of field :foo/id should be `true`.\n\n  {:primary-key false}"
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data))))))

  (testing "check option :unique error"
    (let [data {:foo {:fields [[:id :float {:unique false}]]}}]
      (is (= [{:message "Option :unique of field :foo/id should be `true`.\n\n  {:unique false}"
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data))))))

  (testing "check option :default error"
    (let [data {:foo [[:id :float {:default {}}]]}]
      (is (= [{:message "Option :default of field :foo/id has invalid value.\n\n  {:default {}}"
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data))))))

  (testing "check option :foreign-key error"
    (let [data {:foo [[:id :float {:foreign-key :id}]]}]
      (is (= [{:message (str "Option :foreign-key of field :foo/id should be"
                          " qualified keyword.\n\n  {:foreign-key :id}")
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data))))))

  (testing "check option :on-delete error"
    (let [data {:foo [[:id :float {:on-delete :wrong}]]}]
      (is (= [{:message (str "Option :on-delete of field :foo/id should be"
                          " one of available FK actions.\n\n  {:on-delete :wrong}")
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data))))))

  (testing "check option :on-update error"
    (let [data {:foo [[:id :float {:on-update :wrong}]]}]
      (is (= [{:message (str "Option :on-update of field :foo/id should be"
                          " one of available FK actions.\n\n  {:on-update :wrong}")
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data)))))))


(deftest test-spec-field-vec-validate-fk-options-error
  (testing "check on-delete without foreign-key"
    (let [data {:foo [[:id :float {:on-delete :cascade}]]}]
      (is (= [{:message (str "Field :foo/id has :on-delete option"
                          " without :foreign-key.\n\n  {:on-delete :cascade}")
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data))))))

  (testing "check on-update without foreign-key"
    (let [data {:foo {:fields [[:id :float {:on-update :cascade}]]}}]
      (is (= [{:message (str "Field :foo/id has :on-update option"
                          " without :foreign-key.\n\n  {:on-update :cascade}")
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data)))))))


(deftest test-spec-indexes-vec-errors
  (testing "check empty indexes error"
    (let [data {:foo {:fields [[:id :integer]]
                      :indexes []}}]
      (is (= [{:message "Model :foo should contain at least one index if :indexes key exists.\n\n  []"
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data))))))

  (testing "check not vec indexes"
    (let [data {:foo {:fields [[:id :integer]]
                      :indexes {}}}]
      (is (= [{:message "Indexes definition of model :foo should be a vector.\n\n  {}"
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data))))))

  (testing "check duplicated indexes"
    (let [data {:foo {:fields [[:id :integer]]
                      :indexes [[:foo-idx :btree {:fields [:id]}]
                                [:foo-idx :btree {:fields [:id]}]]}}]
      (is (= [{:message (str "Indexes definition of model :foo has duplicated indexes.\n\n  "
                          "[[:foo-idx :btree {:fields [:id]}] [:foo-idx :btree {:fields [:id]}]]")
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data)))))))


(deftest test-spec-indexes-vec-index-name-error
  (testing "check empty index name error"
    (let [data {:foo {:fields [[:id :integer]]
                      :indexes [[]]}}]
      (is (= [{:message "Missing index name in model :foo."
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data))))))

  (testing "check invalid index name error"
    (let [data {:foo {:fields [[:id :integer]]
                      :indexes [["foo-idx"]]}}]
      (is (= [{:message "Invalid index name in model :foo. Index name should be a keyword.\n\n  \"foo-idx\""
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data)))))))


(deftest test-spec-indexes-vec-index-type-error
  (testing "check empty index type error"
    (let [data {:foo {:fields [[:id :integer]]
                      :indexes [[:foo-idx]]}}]
      (is (= [{:message "Missing type of index :foo.indexes/foo-idx."
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data))))))

  (testing "check invalid index type error"
    (let [data {:foo {:fields [[:id :integer]]
                      :indexes [[:foo-idx :wrong]]}}]
      (is (= [{:message "Invalid type of index :foo.indexes/foo-idx.\n\n  :wrong"
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data)))))))


; TODO: uncomment for testing internal-models

;(deftest test-spec-field-vec-invalid-validate-default-and-type-error
;  (testing "check option :default and field type integer ok"
;    (let [data {:foo [[:id :integer {:default 1}]]}]
;      (is (= []
;             (get-spec-error-data #(models/->internal-models data))))))
;
;  (testing "check invalid :default value for type integer"
;    (let [data {:foo [[:id :integer {:default "1"}]]}]
;      (is (= [{:message (str "Option :default of field :foo/id does not match "
;                             "the field type: `:integer`.\n\n  {:default \"1\", :type :integer}")
;               :title "MODEL ERROR"}]
;             (get-spec-error-data #(models/->internal-models data))))))
;
;  (testing "check invalid :default value for type smallint"
;    (let [data {:foo [[:id :smallint {:default true}]]}]
;      (is (= [{:message (str "Option :default of field :foo/id does not match "
;                             "the field type: `:smallint`.\n\n  {:default true, :type :smallint}")
;               :title "MODEL ERROR"}]
;             (get-spec-error-data #(models/->internal-models data))))))
;
;  (testing "check invalid :default value for type bigint"
;    (let [data {:foo [[:id :bigint {:default "1"}]]}]
;      (is (= [{:message (str "Option :default of field :foo/id does not match "
;                             "the field type: `:bigint`.\n\n  {:default \"1\", :type :bigint}")
;               :title "MODEL ERROR"}]
;             (get-spec-error-data #(models/->internal-models data))))))
;
;  (testing "check invalid :default value for type serial"
;    (let [data {:foo [[:id :serial {:default "1"}]]}]
;      (is (= [{:message (str "Option :default of field :foo/id does not match "
;                             "the field type: `:serial`.\n\n  {:default \"1\", :type :serial}")
;               :title "MODEL ERROR"}]
;             (get-spec-error-data #(models/->internal-models data))))))
;
;  (testing "check invalid :default value for type text"
;    (let [data {:foo [[:id :text {:default 1}]]}]
;      (is (= [{:message (str "Option :default of field :foo/id does not match "
;                             "the field type: `:text`.\n\n  {:default 1, :type :text}")
;               :title "MODEL ERROR"}]
;             (get-spec-error-data #(models/->internal-models data)))))))