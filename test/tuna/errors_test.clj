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
    (is (= [{:message "Models' definition error."
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
    (is (= [{:message "Model :foo has duplicated field.\n\n  [[:id :integer] [:id :integer]]"
             :title "MODEL ERROR"}]
          (get-spec-error-data #(models/->internal-models data)))))

  (let [data {:foo {:fields [[:id :integer]
                             [:id :integer]]}}]
    (is (= [{:message "Model :foo has duplicated field.\n\n  [[:id :integer] [:id :integer]]"
             :title "MODEL ERROR"}]
          (get-spec-error-data #(models/->internal-models data))))))
