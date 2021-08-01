(ns tuna.util.spec
  "Tools for simplifying spec usage."
  (:require [clojure.spec.alpha :as s]
            [slingshot.slingshot :refer [throw+]]
            [expound.alpha :as expound]))


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
    conformed
    (case conformed
      ::s/invalid (throw+ {:type ::s/invalid
                           :message (expound/expound-str spec data
                                      {:show-valid-values? true
                                       :print-specs? false
                                       :theme :figwheel-theme})})
      conformed)))


(defn specs->dict
  [specs]
  {:pre [(s/assert (s/coll-of qualified-keyword?) specs)]}
  (reduce #(assoc %1 (-> %2 name keyword) %2) {} specs))
