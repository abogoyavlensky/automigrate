(ns tuna.models
  "Module for for transforming models to migrations."
  (:require [clojure.spec.alpha :as s]))

; DB actions
(def CREATE-TABLE-ACTION :create-table)

; Specs

(s/def :field/type #{:integer
                     :serial
                     :varchar
                     :text
                     :timestamp})


(s/def :field/null boolean?)
(s/def :field/primary-key true?)
(s/def :field/unique true?)


(s/def :field/default
  ; TODO: update with dynamic value related to field's type
  (s/and
    (s/or :int integer?
      :bool boolean?
      :str string?
      :nil nil?)
    (s/conformer
      #(last %))))


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
