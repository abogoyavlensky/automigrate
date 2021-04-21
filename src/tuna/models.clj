(ns tuna.models
  "Module for for transforming models to migrations."
  (:require [clojure.spec.alpha :as s]))


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
      ; TODO: move to named fn!
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
      ; TODO: move to named fn!
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


; Actions
(def CREATE-TABLE-ACTION :create-table)

(s/def ::action #{CREATE-TABLE-ACTION})

(s/def ::name keyword?)

(defmulti action :action)


(defmethod action CREATE-TABLE-ACTION
  [_]
  (s/keys
    :req-un [::action
             ::name
             ::model]))


(s/def ::->migration (s/multi-spec action :action))
