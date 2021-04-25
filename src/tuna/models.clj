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
             :tuna.models.field/default]))


(s/def ::field
  (s/merge
    ::options-common
    (s/keys
      :req-un [:tuna.models.field/type])))


(s/def ::fields
  (s/map-of keyword? ::field))

; DB Actions
(def CREATE-TABLE-ACTION :create-table)
(def DROP-TABLE-ACTION :drop-table)
(def ADD-COLUMN-ACTION :add-column)
(def ALTER-COLUMN-ACTION :alter-column)
(def DROP-COLUMN-ACTION :drop-column)


(s/def ::action #{CREATE-TABLE-ACTION
                  DROP-TABLE-ACTION
                  ADD-COLUMN-ACTION
                  ALTER-COLUMN-ACTION
                  DROP-COLUMN-ACTION})


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


(s/def ::changes
  (s/nilable
    (s/merge
      ::options-common
      (s/keys
        :opt-un [:tuna.models.field/type]))))


(s/def ::drop
  (s/coll-of #{:primary-key :unique :default :null}
    :kind set?
    :distinct true))


(defmethod action ALTER-COLUMN-ACTION
  [_]
  (s/keys
    :req-un [::action
             ::name
             ::table-name
             ::changes
             ::drop]))


(defmethod action DROP-COLUMN-ACTION
  [_]
  (s/keys
    :req-un [::action
             ::name
             ::table-name]))


(defmethod action DROP-TABLE-ACTION
  [_]
  (s/keys
    :req-un [::action
             ::name]))


(s/def ::->migration (s/multi-spec action :action))
