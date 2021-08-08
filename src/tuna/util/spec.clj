(ns tuna.util.spec
  "Tools for simplifying spec usage."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [slingshot.slingshot :refer [throw+]]
            [expound.alpha :as expound]))


(def ^:private ERR-ITEMS-COLS 8)


(defn tagged->value
  "Convert tagged value to vector or identity without a tag."
  [tagged]
  (let [value-type (first tagged)
        value (last tagged)]
    (case value-type
      :fn (cond-> [(:name value)]
            (some? (:val value)) (conj (:val value)))
      value)))


(defn- throw-exception-for-spec!
  [spec data]
  (throw+ {:type ::s/invalid
           :message (expound/expound-str spec data
                      {:show-valid-values? true
                       :print-specs? false
                       :theme :figwheel-theme})}))


(defn valid?
  "Check if data valid for spec or throw explained data."
  [spec data]
  ; TODO: call validation once!
  (when-not (s/valid? spec data)
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


(defn should-be-one-of-err-msg
  [title items]
  (str title " should be one of:\n"
    (str/join "\n"
      (map #(str/join ", " %) (partition-all ERR-ITEMS-COLS items)))))
