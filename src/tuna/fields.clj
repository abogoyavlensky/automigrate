(ns tuna.fields
  (:require [clojure.spec.alpha :as s]
            [tuna.util.spec :as spec-util]))


(s/def ::type
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
    (s/conformer spec-util/tagged->value)))


(def type-groups
  "Type groups for definition of type's relations.

  Used for foreign-key field type validation."
  {:int #{:integer :serial :bigint :smallint}
   :char #{:varchar :text :uuid}})


(defn check-type-group
  [t]
  (some->> type-groups
    (filter #(contains? (val %) t))
    (first)
    (key)))


(s/def ::null boolean?)
(s/def ::primary-key true?)
(s/def ::unique true?)


(s/def ::foreign-key
  qualified-keyword?)


(s/def ::default
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
      spec-util/tagged->value)))


(s/def ::options-common
  (s/keys
    :opt-un [::null
             ::primary-key
             ::unique
             ::default
             ::foreign-key]))


(s/def ::field
  (s/merge
    ::options-common
    (s/keys
      :req-un [::type])))


(s/def ::field-vec
  (s/cat
    :name keyword?
    :type ::type
    :options (s/? ::options-common)))


(s/def ::fields
  (s/map-of keyword? ::field))


; TODO: uncomment!
;(defmulti field :type)
;
;(defmethod field FOREIGN-KEY-FIELD
;  [_]
;  (s/keys
;    :req-un [::action
;             ::model-name
;             ::models/fields]))
