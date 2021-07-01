(ns tuna.util.spec
  "Tools for simplifying spec usage."
  (:require [clojure.spec.alpha :as s]
            [slingshot.slingshot :refer [throw+]]))


(defn tagged->value
  "Convert tagged value to vector or identity without a tag."
  [tagged]
  (let [value-type (first tagged)
        value (last tagged)]
    (case value-type
      :fn (cond-> [(:name value)]
                  (some? (:val value)) (conj (:val value)))
      value)))


(defn conform
  "Conform data to spec or throw explained data."
  [spec data]
  (let [conformed (s/conform spec data)]
    (case conformed
      ::s/invalid (throw+ {:type ::s/invalid
                           :data (s/explain-data spec data)})
      conformed)))
