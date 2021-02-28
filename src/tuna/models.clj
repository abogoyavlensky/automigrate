(ns tuna.models
  (:require [clojure.spec.alpha :as s]))

; Specs

(s/def :field/type #{:int
                     :serial
                     :varchar
                     :text
                     :timestamp})


(s/def :field/null boolean?)
(s/def :field/primary boolean?)
(s/def :field/max-length pos-int?)


(s/def ::field
  (s/keys
    :req-un [:field/type]
    :opt-un [:field/null
             :field/primary
             :field/max-length]))


(s/def :model/fields
  (s/map-of keyword? ::field))


(s/def ::model
  (s/keys
    :req-un [:model/fields]))


(s/def ::models
  (s/map-of keyword? ::model))


; Conformers

(s/def ::model->action
  (s/conformer
    (fn [value]
      (assoc value :action :create-table))))


(s/def ::->action
  (s/and
    (s/cat
      :name keyword?
      :model ::model)
    ::model->action))


(s/def ::->migration
  (s/and
    ::->action))
