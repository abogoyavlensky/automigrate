(ns tuna.errors
  (:require [clojure.string :as str]))


(def ^:private ERROR-TEMPLATE
  (str "-- %s -------------------------------------\n\n%s\n"))


(def ^:private EXTRA-PROBLEMS
  "Set of problems produced by `s/or` spec with format [spec pred]."
  #{[:tuna.models/public-models `map?]
    [:tuna.fields/char-type `vector?]
    [:tuna.fields/float-type `vector?]})


(def ^:private INDEX-FIELD-NAME-IN-SPEC 2)


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
  (let [spec-pred ((juxt last-spec :pred) problem)]
    (contains? EXTRA-PROBLEMS spec-pred)))


(defn- add-error-value
  "Add error value after the error message."
  [message value]
  (if (= '() value)
    message
    (str message "\n\n  " value)))


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
    (derive :tuna.fields/on-update :tuna.fields/options)
    (derive :tuna.models/public-models-as-vec :tuna.models/public-models)
    (derive :tuna.models/public-models-as-map :tuna.models/public-models)))


(defmulti ->error-message :last-spec
  :hierarchy #'spec-hierarchy)


(defmethod ->error-message :default
  [data]
  (add-error-value "Models' schema failed." (:val data)))


(defmethod ->error-message :tuna.models/->internal-models
  [data]
  (let [value (:val data)]
    (condp = (:pred data)
      `keyword? (add-error-value "Model name should be a keyword." value)
      '(clojure.core/<= 1 (clojure.core/count %) Integer/MAX_VALUE) "Models' definition should not be empty."
      "Models' definition error.")))


(defmethod ->error-message :tuna.models/public-models
  [data]
  (let [model-name (get-model-name data)
        value (:val data)]
    (format "Invalid definition of the model %s. Model could be a map or a vector.\n\n  %s" model-name value)))


(defmethod ->error-message :tuna.fields/type
  [data]
  (let [fq-field-name (get-fq-field-name data)]
    (if (= "Insufficient input" (:reason data))
      (format "Missing type of field %s." fq-field-name)
      (add-error-value
        (format "Invalid type of field %s." fq-field-name)
        (:val data)))))


(defmethod ->error-message :tuna.fields/field-name
  [data]
  (let [model-name (get-model-name data)]
    (add-error-value
      (format "Invalid field name in model %s." model-name)
      (:val data))))


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
  [first-part data]
  (let [total (count first-part)]
    (if (<= total (count data))
      (= first-part (subvec data 0 total))
      false)))


(defn- contains-by-in?
  [data in-vec]
  (let [in-items (->> data
                   (map :in))]
    (some #(starts-with-vec? in-vec %) in-items)))


(defn- sort-problems-by-in
  [problems]
  (sort-by
    (juxt #(-> % :in first)
      #(count (:in %)))
    #(compare %2 %1)
    problems))


(defn- remove-problems-by-in
  [problems]
  (reduce (fn [res item]
            (if (contains-by-in? res (:in item))
              res
              (conj res item)))
    []
    problems))


(defn explain-data->error-report
  "Convert spec explain-data output to errors' report."
  [explain-data]
  (let [problems (->> (:clojure.spec.alpha/problems explain-data)
                   (remove extra-problem?)
                   (sort-problems-by-in)
                   (remove-problems-by-in)
                   (reverse))
        main-spec (:clojure.spec.alpha/spec explain-data)
        origin-value (:clojure.spec.alpha/value explain-data)
        reports (for [problem problems
                      :let [problem* (assoc problem
                                       :last-spec (last-spec problem)
                                       :main-spec main-spec
                                       :origin-value origin-value)]]
                  {:title (->error-title problem*)
                   :message (->error-message problem*)
                   :problem problem*})
        messages (map #(format ERROR-TEMPLATE (:title %) (:message %))
                   reports)]
        ; TODO: uncomment!
        ;reports* (distinct reports)]
    {:reports reports
     :formatted (str/join "\n" messages)}))
