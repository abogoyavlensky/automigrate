(ns tuna.errors-test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [tuna.models :as models]
            [tuna.util.test :as test-util]))


(deftest test-spec-error-public-models-filter-and-sort-multiple-errors
  (let [data {:foo {:fields [[:id :integer {:null2 false}]
                             [:name]]}
              :bar 10
              :zen [[:title :integer {:unique :WRONG}]]}]
    (is (= [{:message "Invalid definition of the model :bar. Model could be a map or a vector.\n\n  10"
             :title "MODEL ERROR"}
            {:message "Missing type of field :foo/name." :title "MODEL ERROR"}
            {:message "Extra options of field :foo/id.\n\n  {:null2 false}"
             :title "MODEL ERROR"}
            {:message "Option :unique of field :zen/title should satisfy: `true?`.\n\n  {:unique :WRONG}"
             :title "MODEL ERROR"}]
          (->> (test-util/thrown-with-slingshot-data? [:type ::s/invalid]
                 (models/->internal-models data))
            :reports
            (map #(dissoc % :problem)))))))
