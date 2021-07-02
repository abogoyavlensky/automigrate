(ns tuna.util.model
  (:require [clojure.spec.alpha :as s]))


(def OPTION-KEY-FORWARD :to)
(def OPTION-KEY-BACKWARD :from)


(s/def ::option-key
  #{OPTION-KEY-FORWARD OPTION-KEY-BACKWARD})


(defn kw->vec
  [kw]
  (when (qualified-keyword? kw)
    (mapv keyword
      ((juxt namespace name) kw))))


(defn changes-to-add
  [changes option-key]
  {:pre [(s/assert ::option-key option-key)]}
  (reduce-kv #(assoc %1 %2 (get %3 option-key)) {} changes))


(defn changes-to-drop
  [changes option-key]
  {:pre [(s/assert ::option-key option-key)]}
  (->> changes
       ; TODO: use var instead of :EMPTY val!
    (filter #(= :EMPTY (get (val %) option-key)))
    (map key)
    (set)))
