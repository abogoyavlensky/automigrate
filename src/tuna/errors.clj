(ns tuna.errors
  (:require [clojure.string :as str]
            [clojure.spec.alpha :as s]))


(def ^:private ERROR-TEMPLATE
  (str "-- %s -------------------------------------\n\n%s\n"))


(def ^:private INDEX-FIELD-NAME-IN-SPEC 2)


(def ^:private EXTRA-PROBLEMS
  "Set of problems produced by `s/or` spec."
  #{:tuna.fields/char-type
    :tuna.fields/float-type})


(defn- get-model-name
  [data]
  (-> data :in first))


(defn- get-option-name
  [data]
  (-> data :in peek))


(defn- get-pred-name
  [data]
  (let [pred (-> data :pred)]
    (if (symbol? pred)
      (name pred)
      pred)))


(defn- get-field-path
  [data]
  (let [model-name (get-model-name data)
        model (get (:origin-value data) model-name)
        in-path (:in data)
        index-fields-key (.indexOf in-path :fields)
        path-has-fields-key? (> index-fields-key 0)
        field-name (if path-has-fields-key?
                     (nth in-path (inc index-fields-key))
                     (nth in-path INDEX-FIELD-NAME-IN-SPEC))]
    (if (vector? model)
      [model-name field-name]
      [model-name :fields field-name])))


(defn- get-options
  [data]
  (let [field-path (-> (get-field-path data)
                     (conj 2))]
    (get-in (:origin-value data) field-path)))


(defn- get-field-name
  [data]
  (let [path (get-field-path data)
        last-item (peek path)]
    (if (keyword? last-item)
      last-item
      (get-in (:origin-value data) (conj path 0)))))


(defn- get-fq-field-name
  "Return full qualified field name with modle namespace."
  [data]
  (let [model-name (name (get-model-name data))
        field-name (name (get-field-name data))]
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


(def ^:private spec-hierarchy
  (-> (make-hierarchy)
    (derive :tuna.fields/unique :tuna.fields/options)
    (derive :tuna.fields/null :tuna.fields/options)
    (derive :tuna.fields/primary-key :tuna.fields/options)
    (derive :tuna.fields/foreign-key :tuna.fields/options)
    (derive :tuna.fields/on-delete :tuna.fields/options)
    (derive :tuna.fields/on-update :tuna.fields/options)))


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

        (format "Model %s definition error." model-name)))))


(defmethod ->error-message :tuna.models/validate-fields-duplication
  [data]
  (let [model-name (get-model-name data)]
    (add-error-value
      (format "Model %s has duplicated fields." model-name)
      (:val data))))


(defmethod ->error-message :tuna.models/public-model-as-map-strict
  [data]
  (let [model-name (get-model-name data)]
    (format "Model %s definition has extra key." model-name)))


(defmethod ->error-message :tuna.fields/field-vec
  [data]
  (let [model-name (get-model-name data)
        cat-pred '(clojure.core/fn [%]
                    (clojure.core/or
                      (clojure.core/nil? %)
                      (clojure.core/sequential? %)))]
    (condp = (:pred data)
      cat-pred (add-error-value
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
        (format "Invalid field name in model %s." model-name)
        (:val data)))))


(defmethod ->error-message :tuna.fields/options
  [data]
  (let [fq-field-name (get-fq-field-name data)]
    (if (= "extra keys" (:reason data))
      ; TODO: remove first branch of condition!
      (add-error-value
        (format "Extra keys in option of field %s." fq-field-name)
        (:val data))
      (add-error-value
        (format "Option %s of field %s should satisfy: `%s`."
          (get-option-name data)
          fq-field-name
          (get-pred-name data))
        (get-options data)))))


(defmethod ->error-message :tuna.fields/options-strict
  [data]
  (let [fq-field-name (get-fq-field-name data)]
    (add-error-value
      (format "Extra options of field %s." fq-field-name)
      (:val data))))


(defmethod ->error-message :tuna.fields/default
  [data]
  (let [fq-field-name (get-fq-field-name data)
        pred (str/join ", " ["integer?" "boolean?" "string?" "nil?"
                             "[keyword? integer?|float?|string?]"])]
    (add-error-value
      (format "Option %s of field %s should satisfy one of the predicates: \n`%s`."
        (get-option-name data)
        fq-field-name
        pred)
      (get-options data))))


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
