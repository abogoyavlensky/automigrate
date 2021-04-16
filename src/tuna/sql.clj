(ns tuna.sql
  "Module for transforming actions from migration to SQL queries."
  (:require [clojure.spec.alpha :as s]
            [tuna.models :as models]))


(s/def :option->sql/type
  (s/conformer
    (fn [value]
      ; TODO: add fields type validation and personal conforming by field type
      value)))


(s/def :option->sql/null
  (s/conformer
    (fn [value]
      (if (true? value)
        nil
        [:not nil]))))


(s/def :option->sql/primary-key
  (s/and
    :field/primary-key
    (s/conformer
      (fn [_value]
        [:primary-key]))))


(s/def ::options->sql
  (s/keys
    :req-un [:option->sql/type]
    :opt-un [:option->sql/null
             :option->sql/primary-key]))


(s/def :action/action #{models/CREATE-TABLE-ACTION})
(s/def :action/name keyword?)


(s/def :model->/fields
  (s/map-of keyword? ::options->sql))


(s/def :action/model
  (s/keys
    :req-un [:model->/fields]))


(defn- fields->columns
  [fields]
  (reduce
    (fn [acc [field-name options]]
      (conj acc (->> (dissoc options :type)
                  (vals)
                  (concat [field-name (:type options)]))))
    []
    fields))


(s/def ::create-model->sql
  (s/conformer
    (fn [value]
      {(:action value) [(:name value)]
       :with-columns (fields->columns (-> value :model :fields))})))


(s/def ::->edn
  (s/conformer
    (s/and
      (s/keys
        :req-un [:action/action
                 :action/name
                 :action/model])
      ::create-model->sql)))
