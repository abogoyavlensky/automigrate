(ns automigrate.util.validation
  (:require [clojure.spec.alpha :as s]))


(defn get-all-types
  [models]
  (mapcat #(-> % :types keys) (vals models)))


(defn get-all-enum-fields-without-type
  [models all-defined-types]
  (mapcat
    (fn [[model-name model-def]]
      (some->> (:fields model-def)
        (filterv
          (fn [[_field-name field-def]]
            ; Check if there are no defined enum types for enum field
            (and (s/valid? :automigrate.fields/enum-type (:type field-def))
              (not (contains? all-defined-types (-> field-def :type last))))))
        (mapv #(keyword (name model-name) (-> % key name)))))
    models))
