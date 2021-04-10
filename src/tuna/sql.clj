(ns tuna.sql
  "Module for transforming actions from migration to SQL queries."
  (:require [clojure.spec.alpha :as s]
            [honeysql.core :as hsql]
            #_[honey.sql :as honey]))

; DB actions
(def CREATE-TABLE-ACTION :create-table)


(s/def :option->sql/type
  (s/conformer
    (fn [value]
      ;(hsql/call value)
      (hsql/raw (name value)))))


(s/def :option->sql/null
  (s/conformer
    (fn [value]
      (if (true? value)
        (hsql/call nil)
        (hsql/call :not nil)))))


(s/def ::options->sql
  (s/keys
    :req-un [:option->sql/type]
    :opt-un [:option->sql/null]))


(s/def :action/action #{CREATE-TABLE-ACTION})
(s/def :action/name keyword?)


(s/def :model->/fields
  (s/map-of keyword? ::options->sql))


(s/def :action/model
  (s/keys
    :req-un [:model->/fields]))


(defn- fields->columns
  [fields]
  (->> fields
    (reduce (fn [acc [k v]]
              (conj acc (->> (vals v)
                          (cons k)
                          (vec)))) [])))


(s/def ::create-model->sql
  (s/conformer
    (fn [value]
      {(:action value) [(:name value)]
       :with-columns [(fields->columns (-> value :model :fields))]})))


(s/def ::->sql
  (s/conformer
    #(hsql/format %)))


(s/def ::action->sql
  (s/and
    (s/keys
      :req-un [:action/action
               :action/name
               :action/model])
    ::create-model->sql
    ::->sql))

;(-> {:create-table [(:name action)]
;     :with-columns [[[:id (hsql/raw "serial") (hsql/call :not nil)]]]}
;    (hsql/format)))))
