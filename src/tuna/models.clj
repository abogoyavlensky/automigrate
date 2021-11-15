(ns tuna.models
  "Module for for transforming models to migrations."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [slingshot.slingshot :refer [throw+]]
            [clojure.set :as set]
            [tuna.util.model :as model-util]
            [tuna.util.spec :as spec-util]
            [tuna.fields :as fields]
            [expound.alpha :as expound]))


(s/def :tuna.models.index/type
  #{:btree :gin :gist :spgist :brin :hash})


(s/def :tuna.models.index/fields
  (s/coll-of keyword? :min-count 1 :kind vector? :distinct true))


(expound/defmsg :tuna.models.index/fields
  "index should contain at lest one model field")


(s/def :tuna.models.index/unique true?)


(s/def ::index
  (s/keys
    :req-un [:tuna.models.index/type
             :tuna.models.index/fields]
    :opt-un [:tuna.models.index/unique]))


(s/def ::index-vec-options
  (s/keys
    :req-un [:tuna.models.index/fields]
    :opt-un [:tuna.models.index/unique]))


(s/def ::index-vec-options-strict-keys
  (spec-util/validate-strict-keys ::index-vec-options))


(s/def ::index-name keyword?)


(s/def ::index-vec
  (s/cat
    :name ::index-name
    :type :tuna.models.index/type
    :options (s/and
               ::index-vec-options
               ::index-vec-options-strict-keys)))


(s/def ::indexes
  (s/map-of keyword? ::index :min-count 1 :distinct true))


(s/def ::model
  (s/keys
    :req-un [::fields/fields]
    ; TODO: move indexes to separated file
    :opt-un [::indexes]))


(defn- item-vec->map
  [acc field]
  (let [options (assoc (:options field) :type (:type field))]
    (assoc acc (:name field) options)))


(s/def ::item-vec->map
  (s/conformer #(reduce item-vec->map {} %)))


(s/def ::map-kw->kebab-case
  (s/conformer model-util/map-kw-keys->kebab-case))


(s/def ::validate-fields-duplication
  (fn [fields]
    (model-util/has-duplicates? (map :name fields))))


(s/def :tuna.models.fields->internal/fields
  (s/and
    ::validate-fields-duplication
    ::item-vec->map
    ::map-kw->kebab-case))


(s/def ::validate-indexes-duplication
  (fn [indexes]
    (->> (map :name indexes)
      (model-util/has-duplicates?))))


(s/def :tuna.models.indexes->internal/indexes
  (s/and
    ::validate-indexes-duplication
    ::item-vec->map
    ::map-kw->kebab-case))


(s/def ::model->internal
  (s/keys
    :req-un [:tuna.models.fields->internal/fields]
    :opt-un [:tuna.models.indexes->internal/indexes]))


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
  (let [field-type-group (fields/check-type-group (:type field-options))
        fk-field-type-group (fields/check-type-group (:type fk-field-options))]
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
            :let [[fk-model-name fk-field-name] (model-util/kw->vec
                                                  (:foreign-key field-options))
                  fk-field-options (get-in models [fk-model-name :fields fk-field-name])]]
      (when (and (some? fk-model-name) (some? fk-field-name))
        (check-referenced-model-exists? models fk-model-name)
        (check-referenced-field-exists? fk-field-options fk-model-name fk-field-name)
        (check-fields-type-valid? field-name field-options fk-field-options fk-model-name fk-field-name))))
  models)


(defn- validate-indexes-duplication
  [models]
  (->> (vals models)
    (mapcat (comp keys :indexes))
    (model-util/has-duplicates?)))


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


(s/def ::internal-models
  (s/and
    (s/map-of keyword? ::model)
    validate-foreign-key
    validate-indexes-duplication
    validate-indexes))


(s/def :tuna.models.fields-vec/fields
  (s/coll-of ::fields/field-vec :min-count 1 :kind vector? :distinct true))


(s/def :tuna.models.indexes-vec/indexes
  (s/coll-of ::index-vec :min-count 1 :kind vector? :distinct true))


(s/def ::public-model-as-vec
  :tuna.models.fields-vec/fields)


(s/def ::public-model-as-map
  (s/keys
    :req-un [:tuna.models.fields-vec/fields]
    :opt-un [:tuna.models.indexes-vec/indexes]))


(s/def ::public-model-as-map-strict-keys
  (spec-util/validate-strict-keys ::public-model-as-map))


(s/def ::public-model
  (s/or
    :vec ::public-model-as-vec
    :map (s/and
           ::public-model-as-map
           ::public-model-as-map-strict-keys)))


(s/def ::simplified-model->named-parts
  (s/conformer
    (fn [models]
      (reduce-kv
        (fn [m k [v-type v]]
          (if (= :vec v-type)
            (assoc m k {:fields v})
            (assoc m k v)))
        {}
        models))))


(s/def ::->internal-models
  (s/and
    (s/map-of keyword? ::public-model)
    ::simplified-model->named-parts
    (s/map-of keyword? ::model->internal)
    ::internal-models))


(defn ->internal-models
  "Transform public models from file to internal representation."
  [models]
  (spec-util/conform ::->internal-models models))
