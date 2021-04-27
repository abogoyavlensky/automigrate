(ns tuna.models
  "Module for for transforming models to migrations."
  (:require [clojure.spec.alpha :as s]))


; Specs

(defn- tagged->value
  "Convert tagged value to vector or identity without a tag."
  [tagged]
  (let [value-type (first tagged)
        value (last tagged)]
    (case value-type
      :fn (cond-> [(:name value)]
            (some? (:val value)) (conj (:val value)))
      value)))


(s/def :tuna.models.field/type
  (s/and
    ; TODO: switch available fields according to db dialect!
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
      :fn (s/cat :name #{:char
                         :varchar
                         :float}
            :val pos-int?))
    (s/conformer tagged->value)))


(s/def :tuna.models.field/null boolean?)
(s/def :tuna.models.field/primary-key true?)
(s/def :tuna.models.field/unique true?)


(s/def :tuna.models.field/foreign-key
  (s/coll-of keyword? :count 2))


(s/def :tuna.models.field/default
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
      tagged->value)))


(s/def ::options-common
  (s/keys
    :opt-un [:tuna.models.field/null
             :tuna.models.field/primary-key
             :tuna.models.field/unique
             :tuna.models.field/default
             :tuna.models.field/foreign-key]))


(s/def ::field
  (s/merge
    ::options-common
    (s/keys
      :req-un [:tuna.models.field/type])))


(s/def ::fields
  (s/map-of keyword? ::field))
