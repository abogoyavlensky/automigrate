(ns automigrate.util.spec
  "Tools for simplifying spec usage."
  (:require [clojure.spec.alpha :as s]
            [slingshot.slingshot :refer [throw+]]
            [automigrate.errors :as spec-errors]))


(defn assert!
  "Check value against spec, throw detailed exception if it failed otherwise return true.

  Based on clojure.spec.alpha/assert*:
  https://github.com/clojure/spec.alpha/blob/13bf36628eb02904155d0bf0d140f591783c51af/src/main/clojure/clojure/spec/alpha.clj#L1966-L1975"
  [spec value]
  (if (s/valid? spec value)
    true
    (let [ed (merge (assoc (s/explain-data* spec [] [] [] value)
                      ::s/failure :assertion-failed))]
      (throw (ex-info
               (str "Spec assertion failed\n" (with-out-str (s/explain-out ed)))
               ed)))))


(defn tagged->value
  "Convert tagged value to vector or identity without a tag."
  [tagged]
  (peek tagged))


(defn- throw-exception-for-spec!
  ([spec data]
   (throw-exception-for-spec! spec data nil))
  ([spec data explained-data]
   (let [explain-data (or explained-data (s/explain-data spec data))
         {:keys [formatted reports]} (spec-errors/explain-data->error-report explain-data)]
     (throw+ {:type ::s/invalid
              :data explain-data
              :message formatted
              :reports reports}))))


(defn valid?
  "Check if data valid for spec and return the data or throw explained exception."
  [spec data]
  (let [explained-data (s/explain-data spec data)]
    (if (some? explained-data)
      (throw-exception-for-spec! spec data explained-data)
      data)))


(defn conform
  "Conform data to spec or throw explained data."
  [spec data]
  (let [conformed (s/conform spec data)]
    (if (= ::s/invalid conformed)
      (throw-exception-for-spec! spec data)
      conformed)))


(defn specs->dict
  [specs]
  {:pre [(assert! (s/coll-of qualified-keyword?) specs)]}
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
