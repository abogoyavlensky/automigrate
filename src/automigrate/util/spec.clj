(ns automigrate.util.spec
  "Tools for simplifying spec usage."
  (:require [clojure.spec.alpha :as s]
            [slingshot.slingshot :refer [throw+]]
            [automigrate.errors :as spec-errors]))


(defn tagged->value
  "Convert tagged value to vector or identity without a tag."
  [tagged]
  (peek tagged))


(defn- throw-exception-for-spec!
  [spec data]
  (let [explain-data (s/explain-data spec data)
        {:keys [formatted reports]} (spec-errors/explain-data->error-report explain-data)]
    (throw+ {:type ::s/invalid
             :data explain-data
             :message formatted
             :reports reports})))


(defn valid?
  "Check if data valid for spec and return the data or throw explained exception."
  [spec data]
  ; TODO: try to call validation once!
  (if (s/valid? spec data)
    data
    (throw-exception-for-spec! spec data)))


(defn conform
  "Conform data to spec or throw explained data."
  [spec data]
  (let [conformed (s/conform spec data)]
    (if (= ::s/invalid conformed)
      (throw-exception-for-spec! spec data)
      conformed)))


(defn specs->dict
  [specs]
  {:pre [(s/assert (s/coll-of qualified-keyword?) specs)]}
  (reduce #(assoc %1 (-> %2 name keyword) %2) {} specs))


(defn- check-keys
  [map-keys value]
  (every? map-keys (keys value)))


(defn- get-map-spec-keys
  [spec]
  (->> spec
    (s/form)
    (filter vector?)
    (apply concat)
    (map (comp keyword name))
    (set)))


(defn validate-strict-keys
  "Return fn to validate strict keys for given spec."
  [spec]
  (fn [value]
    (let [map-keys (get-map-spec-keys spec)]
      (check-keys map-keys value))))
