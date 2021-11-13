(ns tuna.errors
  (:require [clojure.string :as str]
            [clojure.spec.alpha :as s]))


(def ^:private ERROR-TEMPLATE
  (str "-- %s -------------------------------------\n\n%s\n"))


(def ^:private INDEX-FIELD-NAME-IN-SPEC 2)


(def ^:private EXTRA-PROBLEMS
  "Set of problems produced by `s/or` spec."
  #{:tuna.fields/char-type
    :tuna.fields/float-type
    :tuna.fields/default-bool
    :tuna.fields/default-str
    :tuna.fields/default-nil
    :tuna.fields/default-fn})


(defn- get-model-name
  [data]
  (-> data :in first))


(defn- get-model-items-path
  [data items-key]
  {:pre [(contains? #{:fields :indexes} items-key)]}
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
  (let [field-path (conj (get-model-items-path data :fields) 2)]
    (get-in (:origin-value data) field-path)))


(defn- get-field-name
  [data]
  (let [path (get-model-items-path data :fields)
        last-item (peek path)]
    (if (keyword? last-item)
      last-item
      (get-in (:origin-value data) (conj path 0)))))


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


(defn- get-fq-index-name
  "Return full qualified field name with model namespace."
  [data]
  (let [model-name (str (name (get-model-name data)) ".indexes")
        field-name (name (get-index-name data))]
    (keyword model-name field-name)))


(defn- last-spec
  [problem]
  (-> problem :via peek))


(defn- extra-problem?
  [problem]
  (contains? EXTRA-PROBLEMS (last-spec problem)))


(defn- add-error-value
  "Add error value after the error message."
  [message value]
  (if (and (list? value) (empty? value))
    message
    (str message "\n\n  " (pr-str value))))


(defmulti ->error-title :main-spec)


(defmethod ->error-title :default
  [_]
  "ERROR")


(defmethod ->error-title :tuna.models/->internal-models
  [_]
  "MODEL ERROR")


; TODO: remove if not need!
(def ^:private spec-hierarchy
  (-> (make-hierarchy)))


(defmulti ->error-message last-spec
  :hierarchy #'spec-hierarchy)


(defmethod ->error-message :default
  [data]
  (add-error-value "Models' schema failed." (:val data)))


(defmethod ->error-message :tuna.models/->internal-models
  [data]
  (condp = (:pred data)
    `keyword? (add-error-value "Model name should be a keyword." (:val data))
    `map? (add-error-value "Models should be defined as a map." (:val data))
    "Models' definition error."))


(defmethod ->error-message :tuna.models/public-model
  [data]
  (let [model-name (get-model-name data)]
    (condp = (:pred data)
      '(clojure.core/fn [%] (clojure.core/contains? % :fields))
      (format "Model %s should contain the key :fields." model-name)

      (format "Invalid definition of the model %s." model-name))))


(defmethod ->error-message :tuna.models/public-model-as-map
  [data]
  (let [model-name (get-model-name data)]
    (when-not (vector? (:val data))
      (condp = (:pred data)
        '(clojure.core/fn [%] (clojure.core/contains? % :fields))
        (add-error-value
          (format "Model %s should contain :fields key." model-name)
          (:val data))

        (format "Model %s should be a map." model-name)))))


(defmethod ->error-message :tuna.models/public-model-as-vec
  [data]
  (let [model-name (get-model-name data)]
    (format "Model %s should be a vector." model-name)))


(defmethod ->error-message :tuna.models.fields-vec/fields
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


(defmethod ->error-message :tuna.models.indexes-vec/indexes
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


(defmethod ->error-message :tuna.models/index-vec
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


(defmethod ->error-message :tuna.models.index/fields
  [data]
  (let [model-name (get-model-name data)
        fq-index-name (get-fq-index-name data)]
    (add-error-value
      (format "Index %s should have :fields option as vector with distinct fields of the model %s."
        fq-index-name
        model-name)
      (:val data))))


(defmethod ->error-message :tuna.models/index-vec-options
  [data]
  (let [fq-index-name (get-fq-index-name data)]
    (condp = (:pred data)
      '(clojure.core/fn [%] (clojure.core/contains? % :fields))
      (format "Index %s misses :fields options." fq-index-name)

      (format "Invalid definition of the index %s." fq-index-name))))


(defmethod ->error-message :tuna.models/index-vec-options-strict-keys
  [data]
  (let [fq-index-name (get-fq-index-name data)]
    (format "Options of index %s have extra keys." fq-index-name)))


(defmethod ->error-message :tuna.models.index/unique
  [data]
  (let [fq-index-name (get-fq-index-name data)]
    (format "Option :unique of index %s should satisfy: `true?`." fq-index-name)))


(defmethod ->error-message :tuna.models/index-name
  [data]
  (let [model-name (get-model-name data)]
    (if (= "Insufficient input" (:reason data))
      (format "Missing index name in model %s." model-name)
      (add-error-value
        (format "Invalid index name in model %s. Index name should be a keyword." model-name)
        (:val data)))))


(defmethod ->error-message :tuna.models.index/type
  [data]
  (let [fq-index-name (get-fq-index-name data)
        value (:val data)]
    (if (= "Insufficient input" (:reason data))
      (format "Missing type of index %s." fq-index-name)
      (add-error-value
        (format "Invalid type of index %s." fq-index-name)
        value))))


(defmethod ->error-message :tuna.models/validate-fields-duplication
  [data]
  (let [model-name (get-model-name data)]
    (add-error-value
      (format "Model %s has duplicated fields." model-name)
      (:val data))))


(defmethod ->error-message :tuna.models/validate-indexes-duplication
  [data]
  (let [model-name (get-model-name data)]
    (add-error-value
      (format "Model %s has duplicated indexes." model-name)
      (:val data))))


(defmethod ->error-message :tuna.models/public-model-as-map-strict-keys
  [data]
  (let [model-name (get-model-name data)]
    (format "Model %s definition has extra key." model-name)))


(defmethod ->error-message :tuna.fields/field-vec
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


(defmethod ->error-message :tuna.fields/type
  [data]
  (let [fq-field-name (get-fq-field-name data)
        value (:val data)]
    (if (= "Insufficient input" (:reason data))
      (format "Missing type of field %s." fq-field-name)
      (add-error-value
        (format "Invalid type of field %s." fq-field-name)
        value))))


(defmethod ->error-message :tuna.fields/field-name
  [data]
  (let [model-name (get-model-name data)]
    (if (= "Insufficient input" (:reason data))
      (format "Missing field name in model %s." model-name)
      (add-error-value
        (format "Invalid field name in model %s. Field name should be a keyword." model-name)
        (:val data)))))


(defmethod ->error-message :tuna.fields/options
  [data]
  (let [fq-field-name (get-fq-field-name data)
        value (get-options data)]
    (add-error-value
      (format "Invalid options of field %s." fq-field-name)
      value)))


(defmethod ->error-message :tuna.fields/null
  [data]
  (let [fq-field-name (get-fq-field-name data)
        value (get-options data)]
    (add-error-value
      (format "Option :null of field %s should be boolean." fq-field-name)
      value)))


(defmethod ->error-message :tuna.fields/primary-key
  [data]
  (let [fq-field-name (get-fq-field-name data)
        value (get-options data)]
    (add-error-value
      (format "Option :primary-key of field %s should be `true`." fq-field-name)
      value)))


(defmethod ->error-message :tuna.fields/unique
  [data]
  (let [fq-field-name (get-fq-field-name data)
        value (get-options data)]
    (add-error-value
      (format "Option :unique of field %s should be `true`." fq-field-name)
      value)))


(defmethod ->error-message :tuna.fields/options-strict-keys
  [data]
  (let [fq-field-name (get-fq-field-name data)]
    (add-error-value
      (format "Field %s has extra options." fq-field-name)
      (:val data))))


(defmethod ->error-message :tuna.fields/default
  [data]
  (let [fq-field-name (get-fq-field-name data)
        value (get-options data)]
    (add-error-value
      (format "Option :default of field %s has invalid value." fq-field-name)
      value)))


(defmethod ->error-message :tuna.fields/foreign-key
  [data]
  (let [fq-field-name (get-fq-field-name data)
        value (get-options data)]
    (add-error-value
      (format "Option :foreign-key of field %s should be qualified keyword." fq-field-name)
      value)))


(defmethod ->error-message :tuna.fields/on-delete
  [data]
  (let [fq-field-name (get-fq-field-name data)
        value (get-options data)]
    (add-error-value
      (format "Option :on-delete of field %s should be one of available FK actions." fq-field-name)
      value)))


(defmethod ->error-message :tuna.fields/on-update
  [data]
  (let [fq-field-name (get-fq-field-name data)
        value (get-options data)]
    (add-error-value
      (format "Option :on-update of field %s should be one of available FK actions." fq-field-name)
      value)))


(defmethod ->error-message :tuna.fields/validate-fk-options-on-delete
  [data]
  (let [fq-field-name (get-fq-field-name data)
        value (get-options data)]
    (add-error-value
      (format "Field %s has :on-delete option without :foreign-key." fq-field-name)
      value)))


(defmethod ->error-message :tuna.fields/validate-fk-options-on-update
  [data]
  (let [fq-field-name (get-fq-field-name data)
        value (get-options data)]
    (add-error-value
      (format "Field %s has :on-update option without :foreign-key." fq-field-name)
      value)))


(defmethod ->error-message :tuna.fields/validate-default-and-type
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


(defn- starts-with-vec?
  "Filter only if first part is less than `in` data."
  [first-part data]
  (let [first-part-count (count first-part)]
    (if (< first-part-count (count data))
      (= first-part (subvec data 0 first-part-count))
      false)))


(defn- contains-by-in?
  [data in-vec]
  (let [in-items (map :in data)]
    (some #(starts-with-vec? in-vec %) in-items)))


(defn- sort-problems-by-in
  [problems]
  (sort-by
    (juxt #(-> % :in first)
      #(count (:in %)))
    #(compare %2 %1)
    problems))


(defn- squash-problems-by-in
  "Analyze and remove problems for `s/or` spec which are parts of another problem."
  [problems]
  (reduce (fn [res item]
            (if (contains-by-in? res (:in item))
              res
              (conj res item)))
    []
    problems))


(defn- remove-problems-by-in
  "Remove unused problems for `s/or` spec with sorting."
  [problems]
  (-> problems
    (sort-problems-by-in)
    (squash-problems-by-in)
    (reverse)))


(defn- group-problems-by-in
  [problems]
  (->> problems
    (group-by :in)
    (vals)))


(defn join-or-spec-problem-messages
  [messages]
  (str/join "\n\nor\n\n" messages))


(defn explain-data->error-report
  "Convert spec explain-data output to errors' report."
  [explain-data]
  (let [problems (->> (::s/problems explain-data)
                   (remove extra-problem?)
                   (remove-problems-by-in)
                   (group-problems-by-in))
        main-spec (::s/spec explain-data)
        origin-value (::s/value explain-data)
        reports (for [problem-vec problems
                      :let [main-spec {:main-spec main-spec}
                            problem-vec* (map #(assoc % :origin-value origin-value)
                                           problem-vec)
                            error-message (->> problem-vec*
                                            (map ->error-message)
                                            (remove nil?)
                                            (join-or-spec-problem-messages))]]
                  {:title (->error-title main-spec)
                   :message error-message
                   :problems problem-vec*})
        messages (->> reports
                   (map #(format ERROR-TEMPLATE (:title %) (:message %)))
                   (str/join "\n"))]
    {:reports reports
     :formatted messages}))
