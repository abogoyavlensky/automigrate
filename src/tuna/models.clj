(ns tuna.models
  "Module for for transforming models to migrations."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [slingshot.slingshot :refer [throw+]]
            [clojure.set :as set]))


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


(s/def :tuna.models.index/type
  #{:btree :gin :gist :spgist :brin :hash})


(s/def :tuna.models.index/fields
  (s/coll-of keyword? :min-count 1 :kind vector? :distinct true))


(s/def :tuna.models.index/unique true?)


(s/def ::index
  (s/keys
    :req-un [:tuna.models.index/type
             :tuna.models.index/fields]
    :opt-un [:tuna.models.index/unique]))


(s/def ::indexes
  (s/map-of keyword? ::index))


(s/def ::model
  (s/keys
    :req-un [::fields]
    :opt-un [::indexes]))


(defn- check-referenced-model-exists?
  "Check that referenced model exists."
  [models fk-model-name]
  (when-not (contains? models fk-model-name)
    (throw+ {:type ::missing-referenced-model
             :data {:referenced-model fk-model-name}
             :message (format "Referenced model %s is missing" fk-model-name)})))


(defn- check-referenced-field-exists?
  "Check that referenced field exists in referenced model."
  [fk-field-options fk-model-name fk-field-name]
  (when-not (some? fk-field-options)
    (throw+ {:type ::missing-referenced-field
             :data {:referenced-model fk-model-name
                    :referenced-field fk-field-name}
             :message (format "Referenced field %s of model %s is missing"
                        fk-field-name fk-model-name)})))


(defn- check-fields-type-valid?
  "Check that referenced and origin fields has same types.

  Also check that field should has `:unique` option enabled and
  it has the same type as origin field."
  [field-name field-options fk-field-options fk-model-name fk-field-name]
  (when-not (true? (:unique fk-field-options))
    (throw+ {:type ::referenced-field-is-not-unique
             :data {:referenced-model fk-model-name
                    :referenced-field fk-field-name}
             :message (format "Referenced field %s of model %s is not unique"
                        fk-field-name fk-model-name)}))
  (let [field-type-group (check-type-group (:type field-options))
        fk-field-type-group (check-type-group (:type fk-field-options))]
    (when-not (and (some? field-type-group)
                (some? fk-field-type-group)
                (= field-type-group fk-field-type-group))
      (throw+ {:type ::origin-and-referenced-fields-have-different-types
               :data {:origin-field field-name
                      :referenced-field fk-field-name}
               :message (format "Referenced field %s and origin field %s have different types"
                          fk-field-name
                          field-name)}))))


(defn- validate-foreign-key
  [models]
  (doseq [[_model-name model-value] models]
    (doseq [[field-name field-options] (:fields model-value)
            :let [[fk-model-name fk-field-name] (:foreign-key field-options)
                  fk-field-options (get-in models [fk-model-name :fields fk-field-name])]]
      (when (and (some? fk-model-name) (some? fk-field-name))
        (check-referenced-model-exists? models fk-model-name)
        (check-referenced-field-exists? fk-field-options fk-model-name fk-field-name)
        (check-fields-type-valid? field-name field-options fk-field-options fk-model-name fk-field-name))))
  models)


(defn- validate-indexes
  [models]
  (doseq [[model-name model-value] models]
    (doseq [[_index-name index-options] (:indexes model-value)
            :let [index-fields (set (:fields index-options))
                  model-fields (set (keys (:fields model-value)))
                  missing-fields (set/difference index-fields model-fields)]]
      (when (seq missing-fields)
        (throw+ {:type ::missing-indexed-fields
                 :data {:model-name model-name
                        :missing-fields missing-fields
                        :message (format "Missing indexed fields: %s"
                                   (str/join ", " missing-fields))}}))))
  models)


(defn- validate-models
  [models]
  (doseq [[model-name {:keys [fields]}] models]
    (when (empty? fields)
      (throw+ {:type ::missing-fields-in-model
               :data {:model-name model-name}
               :message (format "Missing fields in model: %s" model-name)})))
  models)


(s/def ::models
  (s/and
    (s/map-of keyword? ::model)
    validate-models
    validate-foreign-key
    validate-indexes))
