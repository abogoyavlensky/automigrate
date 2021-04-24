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


(s/def ::options-common
  (s/keys
    :opt-un [:field/null
             :field/primary-key
             :field/unique
             :field/default]))


(s/def ::field
  (s/merge
    ::options-common
    (s/keys
      :req-un [:field/type])))


(s/def ::fields
  (s/map-of keyword? ::field))

; DB Actions
(def CREATE-TABLE-ACTION :create-table)
(def ADD-COLUMN-ACTION :add-column)
(def ALTER-COLUMN-ACTION :alter-column)


(s/def ::action #{CREATE-TABLE-ACTION
                  ADD-COLUMN-ACTION
                  ALTER-COLUMN-ACTION})


(s/def ::name keyword?)


(defmulti action :action)


(defmethod action CREATE-TABLE-ACTION
  [_]
  (s/keys
    :req-un [::action
             ::name
             ::fields]))


(derive ::options ::field)


(s/def ::table-name keyword?)


(defmethod action ADD-COLUMN-ACTION
  [_]
  (s/keys
    :req-un [::action
             ::name
             ::table-name
             ::options]))


(s/def ::changes
  (s/merge
    ::options-common
    (s/keys
      :opt-un [:field/type])))


(s/def ::drop
  (s/coll-of #{:primary-key :unique :default :null}))


(defmethod action ALTER-COLUMN-ACTION
  [_]
  (s/keys
    :req-un [::action
             ::name
             ::table-name
             ::changes
             ::drop]))


(s/def ::->migration (s/multi-spec action :action))
