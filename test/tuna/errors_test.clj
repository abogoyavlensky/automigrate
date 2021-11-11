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
            {:message "Extra options of field :foo/id.\n\n  {:null2 false}"
             :title "MODEL ERROR"}
            {:message "Option :unique of field :zen/title should satisfy: `true?`.\n\n  {:unique :WRONG}"
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
      (is (= [{:message "Invalid field name in model :foo.\n\n  \"wrong\""
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data)))))

    (let [data {:foo {:fields [["wrong"]]}}]
      (is (= [{:message "Invalid field name in model :foo.\n\n  \"wrong\""
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

    (let [data {:foo {:fields [[:id [:float 0.1]]]}}]
      (is (= []
            (get-spec-error-data #(models/->internal-models data))))))

  (testing "check invalid float type as vector"
    (let [data {:foo [[:id [:float]]]}]
      (is (= [{:message "Invalid type of field :foo/id.\n\n  [:float]"
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data))))))

  (testing "check invalid float type as vector with int"
    (let [data {:foo [[:id [:float 1]]]}]
      (is (= [{:message "Invalid type of field :foo/id.\n\n  [:float 1]"
               :title "MODEL ERROR"}]
            (get-spec-error-data #(models/->internal-models data)))))))
