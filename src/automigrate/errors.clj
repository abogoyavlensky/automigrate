(ns automigrate.errors
  (:require [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [clojure.set :as set]))


(def ^:private ERROR-TEMPLATE
  (str "-- %s -------------------------------------\n\n%s\n"))


(def ^:private INDEX-FIELD-NAME-IN-SPEC 2)


(defn- problem-reason
  [problem]
  (or (:reason problem) (:pred problem)))


(defn- duplicates
  "Return duplicated items in collection."
  [items]
  (->> items
    (frequencies)
    (filter #(> (val %) 1))
    (keys)))


(defn- get-model-name
  [data]
  (if (= :automigrate.actions/->migrations (:main-spec data))
    (get-in (:origin-value data) [(first (:in data)) :model-name])
    (-> data :in first)))


(defn- get-model-items-path
  [data items-key]
  {:pre [(contains? #{:fields :indexes :types} items-key)]}
  (let [model-name (get-model-name data)
        model (get (:origin-value data) model-name)
        in-path (:in data)
        index-fields-key (.indexOf in-path items-key)
        path-has-fields-key? (> index-fields-key 0)
        field-name (if path-has-fields-key?
                     (nth in-path (inc index-fields-key))
                     (nth in-path INDEX-FIELD-NAME-IN-SPEC))]
    (if (vector? model)
      [model-name field-name]
      [model-name items-key field-name])))


(defn- get-options
  [data]
  (if (= :automigrate.actions/->migrations (:main-spec data))
    (:val data)
    (let [field-path (conj (get-model-items-path data :fields) 2)]
      (get-in (:origin-value data) field-path))))


(defn- get-field-name
  [data]
  (if (and (= :automigrate.actions/->migrations (:main-spec data))
        (contains? #{:add-column :alter-column} (-> data :path first)))
    (get-in (:origin-value data) [(first (:in data)) :field-name])
    (let [path (get-model-items-path data :fields)
          last-item (peek path)]
      (if (keyword? last-item)
        last-item
        (get-in (:origin-value data) (conj path 0))))))


(defn- get-fq-field-name
  "Return full qualified field name with model namespace."
  [data]
  (let [model-name (name (get-model-name data))
        field-name (name (get-field-name data))]
    (keyword model-name field-name)))


(defn- get-index-name
  [data]
  (let [path (get-model-items-path data :indexes)
        last-item (peek path)]
    (if (keyword? last-item)
      last-item
      (get-in (:origin-value data) (conj path 0)))))


(defn- get-type-name
  [data]
  (let [path (get-model-items-path data :types)
        last-item (peek path)]
    (if (keyword? last-item)
      last-item
      (get-in (:origin-value data) (conj path 0)))))


(defn- get-fq-index-name
  "Return full qualified index name with model namespace."
  [data]
  (let [model-name (str (name (get-model-name data)) ".indexes")
        index-name (name (get-index-name data))]
    (keyword model-name index-name)))


(defn- get-fq-type-name
  "Return full qualified type name with model namespace."
  [data]
  (let [model-name (str (name (get-model-name data)) ".types")
        type-name (name (get-type-name data))]
    (keyword model-name type-name)))


(defn- last-spec
  [problem]
  (-> problem :via peek))


(defn- add-error-value
  "Add error value after the error message."
  [message value]
  (if (and (list? value) (empty? value))
    message
    (str message "\n\n  " (pr-str value))))


(def ^:private error-hierarchy
  (-> (make-hierarchy)
    (derive :automigrate.fields/field-with-type :automigrate.fields/field)
    (derive :automigrate.core/make-args ::common-command-args-errors)
    (derive :automigrate.core/migrate-args :automigrate.core/make-args)
    (derive :automigrate.core/explain-args :automigrate.core/make-args)
    (derive :automigrate.core/list-args :automigrate.core/make-args)))


(defmulti ->error-title :main-spec
  :hierarchy #'error-hierarchy)


(defmethod ->error-title :default
  [_]
  "ERROR")


(defmethod ->error-title :automigrate.models/->internal-models
  [_]
  "MODEL ERROR")


(defmethod ->error-title :automigrate.actions/->migrations
  [_]
  "MIGRATION ERROR")


(defmethod ->error-title :automigrate.models/internal-models
  [_]
  "MIGRATION ERROR")


(defmethod ->error-title ::common-command-args-errors
  [_]
  "COMMAND ERROR")


(defmulti ->error-message last-spec
  :hierarchy #'error-hierarchy)


(defn- get-model-name-by-default
  [data]
  (if-let [spec-val (seq (:val data))]
    spec-val
    (-> data :in first)))


(defmethod ->error-message :default
  [data]
  (case (:main-spec data)
    :automigrate.models/->internal-models
    (add-error-value "Schema failed for model." (get-model-name-by-default data))

    :automigrate.actions/->migrations
    (add-error-value "Schema failed for migration." (:val data))

    (add-error-value "Schema failed." (:val data))))


; Models

(defmethod ->error-message :automigrate.models/->internal-models
  [data]
  (condp = (:pred data)
    `keyword? (add-error-value "Model name should be a keyword." (:val data))
    `map? (add-error-value "Models should be defined as a map." (:val data))
    "Models' definition error."))


(defmethod ->error-message :automigrate.models/public-model
  [data]
  (let [model-name (get-model-name data)]
    (condp = (problem-reason data)
      '(clojure.core/fn [%] (clojure.core/contains? % :fields))
      (format "Model %s should contain the key :fields." model-name)

      "no method" (add-error-value
                    (format "Model %s should be a map or a vector." model-name)
                    (:val data))

      (format "Invalid definition of the model %s." model-name))))


(defmethod ->error-message :automigrate.models/public-model-as-map
  [data]
  (let [model-name (get-model-name data)]
    (when-not (vector? (:val data))
      (condp = (:pred data)
        '(clojure.core/fn [%] (clojure.core/contains? % :fields))
        (add-error-value
          (format "Model %s should contain :fields key." model-name)
          (:val data))

        (format "Model %s should be a map." model-name)))))


(defmethod ->error-message :automigrate.models/public-model-as-vec
  [data]
  (let [model-name (get-model-name data)]
    (format "Model %s should be a vector." model-name)))


(defmethod ->error-message :automigrate.models.fields-vec/fields
  [data]
  (let [model-name (get-model-name data)]
    (when-not (map? (:val data))
      (condp = (:pred data)
        '(clojure.core/<= 1 (clojure.core/count %) Integer/MAX_VALUE)
        (add-error-value
          (format "Model %s should contain at least one field." model-name)
          (:val data))

        `vector? (add-error-value
                   (format "Model %s should be a vector." model-name)
                   (:val data))

        'distinct? (add-error-value
                     (format "Model %s has duplicated fields." model-name)
                     (:val data))

        (format "Fields definition error in model %s." model-name)))))


(defmethod ->error-message :automigrate.models.indexes-vec/indexes
  [data]
  (let [model-name (get-model-name data)]
    (condp = (:pred data)
      '(clojure.core/<= 1 (clojure.core/count %) Integer/MAX_VALUE)
      (add-error-value
        (format "Model %s should contain at least one index if :indexes key exists." model-name)
        (:val data))

      `vector? (add-error-value
                 (format "Indexes definition of model %s should be a vector." model-name)
                 (:val data))

      'distinct? (add-error-value
                   (format "Indexes definition of model %s has duplicated indexes." model-name)
                   (:val data))

      (format "Indexes definition error in model %s." model-name))))


(defmethod ->error-message :automigrate.indexes/index-vec
  [data]
  (let [model-name (get-model-name data)]
    (if (= "Extra input" (:reason data))
      (let [fq-index-name (get-fq-index-name data)]
        (add-error-value
          (format "Index %s has extra value in definition." fq-index-name)
          (:val data)))
      (add-error-value
        (format "Invalid index definition in model %s." model-name)
        (:val data)))))


(defmethod ->error-message :automigrate.indexes/fields
  [data]
  (let [model-name (get-model-name data)
        fq-index-name (get-fq-index-name data)]
    (add-error-value
      (format "Index %s should have :fields option as vector with distinct fields of the model %s."
        fq-index-name
        model-name)
      (:val data))))


(defmethod ->error-message :automigrate.types/choices
  [data]
  (let [model-name (get-model-name data)
        fq-type-name (get-fq-type-name data)]
    (condp = (:pred data)
      '(clojure.core/<= 1 (clojure.core/count %) Integer/MAX_VALUE)
      (add-error-value
        (format "Enum type %s should contain at least one choice."
          fq-type-name)
        '())

      `vector? (add-error-value
                 (format "Choices definition of type %s should be a vector of strings."
                   fq-type-name)
                 (:val data))

      'distinct? (add-error-value
                   (format "Enum type definition %s has duplicated choices."
                     fq-type-name)
                   (:val data))

      (format "Enum type definition error in model %s." model-name))))


(defmethod ->error-message :automigrate.types/type-vec-options
  [data]
  (let [fq-type-name (get-fq-type-name data)]
    (condp = (:pred data)
      '(clojure.core/fn [%] (clojure.core/contains? % :choices))
      (format "Enum type %s misses :choices option." fq-type-name)

      (format "Invalid definition of the enum type %s." fq-type-name))))


(defmethod ->error-message :automigrate.types/type-vec
  [data]
  (let [fq-type-name (get-fq-type-name data)]
    (condp = (:pred data)
      '(clojure.spec.alpha/and
         :automigrate.types/type-vec-options
         :automigrate.types/type-vec-options-strict-keys)
      (format "Enum type %s misses :choices option." fq-type-name)

      (format "Invalid definition of the enum type %s." fq-type-name))))


(defmethod ->error-message :automigrate.types.define-as/type
  [data]
  (let [fq-type-name (get-fq-type-name data)]
    (format "Type %s must contain one of definition [:enum]." fq-type-name)))


(defmethod ->error-message :automigrate.types/name
  [data]
  (let [model-name (get-model-name data)]
    (format "Type definition in model %s must contain a name." model-name)))


(defmethod ->error-message :automigrate.indexes/index-vec-options
  [data]
  (let [fq-index-name (get-fq-index-name data)]
    (condp = (:pred data)
      '(clojure.core/fn [%] (clojure.core/contains? % :fields))
      (format "Index %s misses :fields options." fq-index-name)

      (format "Invalid definition of the index %s." fq-index-name))))


(defmethod ->error-message :automigrate.indexes/index-vec-options-strict-keys
  [data]
  (let [fq-index-name (get-fq-index-name data)]
    (format "Options of index %s have extra keys." fq-index-name)))


(defmethod ->error-message :automigrate.types/type-vec-options-strict-keys
  [data]
  (let [fq-type-name (get-fq-type-name data)]
    (format "Options of type %s have extra keys." fq-type-name)))


(defmethod ->error-message :automigrate.indexes/unique
  [data]
  (let [fq-index-name (get-fq-index-name data)]
    (format "Option :unique of index %s should satisfy: `true?`." fq-index-name)))


(defmethod ->error-message :automigrate.indexes/index-name
  [data]
  (let [model-name (get-model-name data)]
    (if (= "Insufficient input" (:reason data))
      (format "Missing index name in model %s." model-name)
      (add-error-value
        (format "Invalid index name in model %s. Index name should be a keyword." model-name)
        (:val data)))))


(defmethod ->error-message :automigrate.indexes/type
  [data]
  (let [fq-index-name (get-fq-index-name data)
        value (:val data)]
    (if (= "Insufficient input" (:reason data))
      (format "Missing type of index %s." fq-index-name)
      (add-error-value
        (format "Invalid type of index %s." fq-index-name)
        value))))


(defmethod ->error-message :automigrate.models/validate-fields-duplication
  [data]
  (let [model-name (get-model-name data)]
    (add-error-value
      (format "Model %s has duplicated fields." model-name)
      (:val data))))


(defmethod ->error-message :automigrate.models/validate-indexes-duplication
  [data]
  (let [model-name (get-model-name data)]
    (add-error-value
      (format "Model %s has duplicated indexes." model-name)
      (:val data))))


(defmethod ->error-message :automigrate.models/public-model-as-map-strict-keys
  [data]
  (let [model-name (get-model-name data)]
    (format "Model %s definition has extra key." model-name)))


(defmethod ->error-message :automigrate.models/validate-indexes-duplication-across-models
  [data]
  (let [duplicated-indexes (->> (:origin-value data)
                             (vals)
                             (map (fn [model]
                                    (when (map? model)
                                      (map first (:indexes model)))))
                             (remove nil?)
                             (flatten)
                             (duplicates))]
    (format "Models have duplicated indexes: [%s]." (str/join ", " duplicated-indexes))))


(defmethod ->error-message :automigrate.models/validate-types-duplication-across-models
  [data]
  (let [duplicated-types (->> (:origin-value data)
                           (vals)
                           (map (fn [model]
                                  (when (map? model)
                                    (map first (:types model)))))
                           (remove nil?)
                           (flatten)
                           (duplicates))]
    (format "Models have duplicated types: [%s]." (str/join ", " duplicated-types))))


(defmethod ->error-message :automigrate.models/validate-indexed-fields
  [data]
  (let [model-name (get-model-name data)
        model (:val data)
        model-fields (set (map :name (:fields model)))
        index-fields (->> (:indexes model)
                       (map #(get-in % [:options :fields]))
                       (flatten)
                       (set))
        missing-fields (vec (set/difference index-fields model-fields))]
    (format "Missing indexed fields %s in model %s." missing-fields model-name)))


(defmethod ->error-message :automigrate.fields/fields
  [data]
  (condp = (problem-reason data)
    '(clojure.core/<= 1
       (clojure.core/count %)
       Integer/MAX_VALUE) (add-error-value
                            "Action should contain at least one field."
                            (:val data))

    (add-error-value "Invalid fields definition." (:val data))))


(defmethod ->error-message :automigrate.fields/field
  [data]
  (condp = (problem-reason data)
    '(clojure.core/fn [%]
       (clojure.core/contains? % :type))
    (add-error-value "Field should contain type." (:val data))

    (add-error-value "Invalid field definition." (:val data))))


(defmethod ->error-message :automigrate.fields/field-vec
  [data]
  (let [model-name (get-model-name data)]
    (if (:reason data)
      (let [fq-field-name (get-fq-field-name data)]
        (case (:reason data)
          "Extra input" (add-error-value
                          (format "Field %s has extra value in definition." fq-field-name)
                          (:val data))

          (add-error-value
            (format "Invalid field definition in model %s." model-name)
            (:val data))))
      (add-error-value
        (format "Invalid field definition in model %s." model-name)
        (:val data)))))


(defmethod ->error-message :automigrate.fields/type
  [data]
  (let [fq-field-name (get-fq-field-name data)
        value (:val data)]
    (if (= "Insufficient input" (:reason data))
      (format "Missing type of field %s." fq-field-name)
      (add-error-value
        (format "Invalid type of field %s." fq-field-name)
        value))))


(defmethod ->error-message :automigrate.fields/float-type
  [data]
  (let [fq-field-name (get-fq-field-name data)
        value (:val data)]
    (condp = (problem-reason data)
      `pos-int? (add-error-value
                  (format "Parameter for float type of field %s should be positive integer." fq-field-name)
                  value)

      '(clojure.core/= (clojure.core/count %) 2)
      (add-error-value
        (format "Vector form of float type of field %s should have parameter." fq-field-name)
        value)

      (add-error-value
        (format "Invalid float type of field %s." fq-field-name)
        value))))


(defmethod ->error-message :automigrate.fields/keyword-type
  [data]
  (let [fq-field-name (get-fq-field-name data)
        value (:val data)]
    (add-error-value
      (format "Unknown type of field %s." fq-field-name)
      value)))


(defmethod ->error-message :automigrate.fields/char-type
  [data]
  (let [fq-field-name (get-fq-field-name data)
        value (:val data)]
    (condp = (problem-reason data)
      `pos-int? (add-error-value
                  (format "Parameter for char type of field %s should be positive integer." fq-field-name)
                  value)

      '(clojure.core/= (clojure.core/count %) 2)
      (add-error-value
        (format "Vector form of char type of field %s should have parameter." fq-field-name)
        value)

      (add-error-value
        (format "Invalid float type of field %s." fq-field-name)
        value))))


(defmethod ->error-message :automigrate.fields/decimal
  [data]
  (let [fq-field-name (get-fq-field-name data)
        value (:val data)]
    (add-error-value
      (format "Invalid definition decimal/numeric type of field %s." fq-field-name)
      value)))


(defmethod ->error-message :automigrate.fields/field-name
  [data]
  (let [model-name (get-model-name data)]
    (if (= "Insufficient input" (:reason data))
      (format "Missing field name in model %s." model-name)
      (add-error-value
        (format "Invalid field name in model %s. Field name should be a keyword." model-name)
        (:val data)))))


(defmethod ->error-message :automigrate.fields/options
  [data]
  (let [fq-field-name (get-fq-field-name data)
        value (get-options data)]
    (add-error-value
      (format "Invalid options of field %s." fq-field-name)
      value)))


(defmethod ->error-message :automigrate.fields/null
  [data]
  (let [fq-field-name (get-fq-field-name data)
        value (get-options data)]
    (add-error-value
      (format "Option :null of field %s should be boolean." fq-field-name)
      value)))


(defmethod ->error-message :automigrate.fields/primary-key
  [data]
  (let [fq-field-name (get-fq-field-name data)
        value (get-options data)]
    (add-error-value
      (format "Option :primary-key of field %s should be `true`." fq-field-name)
      value)))


(defmethod ->error-message :automigrate.fields/unique
  [data]
  (let [fq-field-name (get-fq-field-name data)
        value (get-options data)]
    (add-error-value
      (format "Option :unique of field %s should be `true`." fq-field-name)
      value)))


(defmethod ->error-message :automigrate.fields/options-strict-keys
  [data]
  (let [fq-field-name (get-fq-field-name data)]
    (add-error-value
      (format "Field %s has extra options." fq-field-name)
      (:val data))))


(defmethod ->error-message :automigrate.fields/default
  [data]
  (let [fq-field-name (get-fq-field-name data)
        value (get-options data)]
    (add-error-value
      (format "Option :default of field %s has invalid value." fq-field-name)
      value)))


(defmethod ->error-message :automigrate.fields/foreign-key
  [data]
  (let [fq-field-name (get-fq-field-name data)
        value (get-options data)]
    (add-error-value
      (format "Option :foreign-key of field %s should be qualified keyword." fq-field-name)
      value)))


(defmethod ->error-message :automigrate.fields/on-delete
  [data]
  (let [fq-field-name (get-fq-field-name data)
        value (get-options data)]
    (add-error-value
      (format "Option :on-delete of field %s should be one of available FK actions." fq-field-name)
      value)))


(defmethod ->error-message :automigrate.fields/on-update
  [data]
  (let [fq-field-name (get-fq-field-name data)
        value (get-options data)]
    (add-error-value
      (format "Option :on-update of field %s should be one of available FK actions." fq-field-name)
      value)))


(defmethod ->error-message :automigrate.fields/validate-fk-options-on-delete
  [data]
  (let [fq-field-name (get-fq-field-name data)
        value (get-options data)]
    (add-error-value
      (format "Field %s has :on-delete option without :foreign-key." fq-field-name)
      value)))


(defmethod ->error-message :automigrate.fields/validate-fk-options-on-update
  [data]
  (let [fq-field-name (get-fq-field-name data)
        value (get-options data)]
    (add-error-value
      (format "Field %s has :on-update option without :foreign-key." fq-field-name)
      value)))


(defmethod ->error-message :automigrate.fields/validate-default-and-type
  [data]
  (let [fq-field-name (get-fq-field-name data)
        field-type (get-in data [:val :type])
        value (-> data :val (select-keys [:default :type]))]
    (add-error-value
      (format "Option %s of field %s does not match the field type: `%s`."
        :default
        fq-field-name
        field-type)
      value)))


(defmethod ->error-message :automigrate.fields/validate-default-and-null
  [data]
  (let [fq-field-name (get-fq-field-name data)]
    (add-error-value
      (format "Option :default of field %s couldn't be `nil` because of: `:null false`."
        fq-field-name)
      (:val data))))


(defmethod ->error-message :automigrate.fields/validate-fk-options-and-null-on-delete
  [data]
  (let [fq-field-name (get-fq-field-name data)]
    (add-error-value
      (format "Option :on-delete of field %s couldn't be :set-null because of: `:null false`."
        fq-field-name)
      (:val data))))


(defmethod ->error-message :automigrate.fields/validate-fk-options-and-null-on-update
  [data]
  (let [fq-field-name (get-fq-field-name data)]
    (add-error-value
      (format "Option :on-update of field %s couldn't be :set-null because of: `:null false`."
        fq-field-name)
      (:val data))))


; Migrations

(defmethod ->error-message :automigrate.actions/->migrations
  [data]
  (let [reason (or (:reason data) (:pred data))]
    (condp = reason
      `coll? (add-error-value
               (format "Migration actions should be vector.")
               (:val data))

      "Migrations' schema error.")))


(defmethod ->error-message :automigrate.actions/->migration
  [data]
  (let [reason (or (:reason data) (:pred data))]
    (condp = reason
      "no method" (add-error-value
                    (format "Invalid action type.")
                    (:val data))

      '(clojure.core/fn [%]
         (clojure.core/contains? % :fields))
      (add-error-value (format "Missing :fields key in action.") (:val data))

      '(clojure.core/fn [%]
         (clojure.core/contains? % :model-name))
      (add-error-value (format "Missing :model-name key in action.") (:val data))

      "Migrations' schema error.")))


(defmethod ->error-message :automigrate.actions/model-name
  [data]
  (add-error-value
    (format "Action has invalid model name.")
    (:val data)))


(defn get-fq-type-from-action-error
  [data]
  (let [model-name (-> data :val :model-name)
        type-name (-> data :val :type-name)]
    (keyword (name model-name) (name type-name))))


(defmethod ->error-message :automigrate.actions/validate-type-choices-not-allow-to-remove
  [data]
  (let [fq-type-name (get-fq-type-from-action-error data)]
    (add-error-value
      (format "It is not possible to remove existing choices of enum type %s."
        fq-type-name)
      (-> data :val :changes))))


(defmethod ->error-message :automigrate.actions/validate-type-choices-not-allow-to-re-order
  [data]
  (let [fq-type-name (get-fq-type-from-action-error data)]
    (add-error-value
      (format "It is not possible to re-order existing choices of enum type %s."
        fq-type-name)
      (-> data :val :changes))))


; Command arguments

(defmethod ->error-message ::common-command-args-errors
  [data]
  (let [reason (problem-reason data)]
    (condp = reason
      '(clojure.core/fn [%] (clojure.core/contains? % :models-file))
      (add-error-value "Missing model file path." (:val data))

      '(clojure.core/fn [%] (clojure.core/contains? % :migrations-dir))
      (add-error-value "Missing migrations dir path." (:val data))

      '(clojure.core/fn [%] (clojure.core/contains? % :jdbc-url))
      (add-error-value "Missing db connection config." (:val data))

      '(clojure.core/fn [%] (clojure.core/contains? % :number))
      (add-error-value "Missing migration number." (:val data))

      "Invalid command arguments.")))


(defmethod ->error-message :automigrate.core/type
  [data]
  (add-error-value "Invalid migration type." (:val data)))


(defmethod ->error-message :automigrate.core/direction
  [data]
  (add-error-value "Invalid direction of migration." (:val data)))


; Public

(defn explain-data->error-report
  "Convert spec explain-data output to errors' report."
  [explain-data]
  (let [problems (::s/problems explain-data)
        main-spec (::s/spec explain-data)
        origin-value (::s/value explain-data)
        reports (for [problem problems
                      :let [problem* (assoc problem
                                       :origin-value origin-value
                                       :main-spec main-spec)]]
                  {:title (->error-title problem*)
                   :message (->error-message problem*)
                   :problem problem*})
        messages (->> reports
                   (map #(format ERROR-TEMPLATE (:title %) (:message %)))
                   (str/join "\n"))]
    {:reports reports
     :formatted messages}))


(defn custom-error->error-report
  "Convert custom error data output to errors' report."
  [error-data]
  (let [title (or (:title error-data) (->error-title {}))
        formatted-error #(format ERROR-TEMPLATE title %)]
    (update error-data :message formatted-error)))
