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


(s/def :field/type
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
      tagged->value)))


(s/def ::field
  (s/keys
    :req-un [:field/type]
    :opt-un [:field/null
             :field/primary-key
             :field/unique
             :field/default]))


(s/def ::fields
  (s/map-of keyword? ::field))

; DB Actions
(def CREATE-TABLE-ACTION :create-table)
(def ADD-COLUMN-ACTION :add-column)


(s/def ::action #{CREATE-TABLE-ACTION
                  ADD-COLUMN-ACTION})


(s/def ::name keyword?)


(defmulti action :action)


(defmethod action CREATE-TABLE-ACTION
  [_]
  (s/keys
    :req-un [::action
             ::name
             ::fields]))


(s/def ::options
  ::field)


(s/def ::table-name keyword?)


(defmethod action ADD-COLUMN-ACTION
  [_]
  (s/keys
    :req-un [::action
             ::name
             ::table-name
             ::options]))


(s/def ::->migration (s/multi-spec action :action))
