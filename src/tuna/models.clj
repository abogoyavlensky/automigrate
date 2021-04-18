(ns tuna.models
  "Module for for transforming models to migrations."
  (:require [clojure.spec.alpha :as s]))

; DB actions
(def CREATE-TABLE-ACTION :create-table)

; Specs
(s/def :field/type
  (s/and
    ; TODO: maybe change :kw and :name spec to `keyword?`
    (s/or
      :kw #{:integer
            :smallint
            :bigint
            :float
            :real
            :serial
            :uuid
            :boolean
            :text
            :timestamp
            :date
            :time
            :point
            :json
            :jsonb}
      :fn (s/cat :name #{:char :varchar :float} :val pos-int?))
    (s/conformer
      #(let [value-type (first %)
             value (last %)]
         (case value-type
           :fn [(:name value) (:val value)]
           :kw value)))))


(s/def :field/null boolean?)
(s/def :field/primary-key true?)
(s/def :field/unique true?)


(s/def :field/default
  ; TODO: update with dynamic value related to field's type
  (s/and
    (s/or
      :int integer?
      :bool boolean?
      :str string?
      :nil nil?
      :fn (s/cat
            :name keyword?
            :val (s/? #((some-fn int? string?) %))))
    (s/conformer
      #(let [value-type (first %)
             value (last %)]
         (case value-type
           :fn (cond-> [(:name value)]
                 (some? (:val value)) (conj (:val value)))
           (last %))))))


(s/def ::field
  (s/keys
    :req-un [:field/type]
    :opt-un [:field/null
             :field/primary-key
             :field/unique
             :field/default]))


(s/def :model/fields
  (s/map-of keyword? ::field))


(s/def ::model
  (s/keys
    :req-un [:model/fields]))


(s/def ::models
  (s/map-of keyword? ::model))


; Action conformers

(s/def ::model->action
  (s/conformer
    (fn [value]
      (assoc value :action CREATE-TABLE-ACTION))))


(s/def ::->action
  (s/and
    (s/cat
      :name keyword?
      :model ::model)
    ::model->action))


(s/def ::->migration
  (s/and
    ::->action))
