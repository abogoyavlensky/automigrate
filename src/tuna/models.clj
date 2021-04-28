(ns tuna.models
  "Module for for transforming models to migrations."
  (:require [clojure.spec.alpha :as s]
            [slingshot.slingshot :refer [throw+]]))


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


(s/def ::model
  (s/keys
    :req-un {:fields ::fields}))


(defn- check-referenced-model-exists?
  "Check that referenced model exists."
  [models fk-model-name]
  (when-not (contains? models fk-model-name)
    (throw+ {:type ::missing-referenced-model
             :data {:referenced-model fk-model-name}
             :message (format "Referenced model %s doesn't exist" fk-model-name)})))


(defn- check-referenced-field-exists?
  "Check that referenced field exists in referenced model.

  Also check that field should has `:unique` option enabled and
  it has the same type as origin field."
  [models fk-model-name fk-field-name]
  (let [fk-field-options (get-in models [fk-model-name :fields fk-field-name])]
    (when-not (some? fk-field-options)
      (throw+ {:type ::missing-referenced-field
               :data {:referenced-model fk-model-name
                      :referenced-field fk-field-name}
               :message (format "Referenced field %s of model %s does not exists"
                          fk-field-name fk-model-name)}))
    (when-not (true? (:unique fk-field-options))
      (throw+ {:type ::referenced-field-is-not-unique
               :data {:referenced-model fk-model-name
                      :referenced-field fk-field-name}
               :message (format "Referenced field %s of model %s is not unique"
                          fk-field-name fk-model-name)}))))


(defn- validate-foreign-key
  [models]
  (doseq [[_model-name model-value] models]
    (doseq [[_field-name field-value] (:fields model-value)
            :let [[fk-model-name fk-field-name] (:foreign-key field-value)]]
      (when (and (some? fk-model-name) (some? fk-field-name))
        (check-referenced-model-exists? models fk-model-name)
        (check-referenced-field-exists? models fk-model-name fk-field-name))))
  models)


(s/def ::models
  (s/and
    (s/map-of keyword? ::model)
    validate-foreign-key))
