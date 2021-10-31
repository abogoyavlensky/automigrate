(ns tuna.errors-test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [slingshot.slingshot :refer [try+]]
            [tuna.models :as models]))


(deftest test-spec-error-public-models-filter-and-sort-multiple-errors
  (let [data {:foo {:fields [[:id :integer {:null2 false}]
                             [:name]]}
              :bar 10
              :zen [[:title :integer {:unique :WRONG}]]}]
    (try+
      (models/->internal-models data)
      (catch [:type ::s/invalid] e
        (is '({:message "Invalid definition of model :bar. Model could be map or vector.\n\n  10",
               :title "MODEL ERROR"}
              {:message "Missing type of field :foo/name.", :title "MODEL ERROR"}
              {:message "Extra options of field :foo/id.\n\n  {:null2 false}",
               :title "MODEL ERROR"}
              {:message "Option :unique of field :zen/title should satisfy: `true?`.\n\n  {:unique :WRONG}",
               :title "MODEL ERROR"})
          (:reports e))))))
