(ns tuna.errors
  (:require [clojure.string :as str]))


(def ^:private ERROR-TEMPLATE
  (str "-- %s -------------------------------------\n\n%s\n"))


(def ^:private EXTRA-PROBLEMS
  #{[:tuna.models/public-models `map?]
    [:tuna.fields/char-type `vector?]
    [:tuna.fields/float-type `vector?]})


(defn- get-model-name
  [data]
  (-> data :in first))


(defn- get-option-name
  [data]
  (-> data :in peek))


(defn- get-pred-name
  [data]
  (-> data :pred name))


(defn- get-field-path
  [data]
  (let [model-name (get-model-name data)
        model (get (:origin-value data) model-name)]
    (if (vector? model)
      [model-name (nth (:in data) 2)]
      [model-name :fields (nth (:in data) 3)])))


(defn- get-field
  [data]
  (let [field-path (get-field-path data)]
    (get-in (:origin-value data) field-path)))


(defn- get-field-name
  [data]
  (let [path (-> (get-field-path data)
               (conj 0))]
    (get-in (:origin-value data) path)))


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


(defn add-error-value
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
    (derive :tuna.fields/null :tuna.fields/options)))


(defmulti ->error-message :last-spec
  :hierarchy #'spec-hierarchy)


(defmethod ->error-message :default
  [data]
  (add-error-value "Spec failed." (:val data)))


(defmethod ->error-message :tuna.models/->internal-models
  [data]
  (let [value (:val data)]
    (case (:pred data)
      `keyword? (add-error-value "Model name should be a keyword." value)
      "Models definition error.")))


(defmethod ->error-message :tuna.models/public-models
  [data]
  (let [model-name (get-model-name data)
        value (:val data)]
    (format "Invalid definition of model %s.\n\n  %s %s"
      model-name
      model-name
      value)))


(defmethod ->error-message :tuna.fields/type
  [data]
  (let [fq-field-name (get-fq-field-name data)]
    (if (= "Insufficient input" (:reason data))
      (format "Missing type for field %s." fq-field-name)
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
      (add-error-value
        (format "Extra keys in option of field %s." fq-field-name)
        (:val data))
      (add-error-value
        (format "Option %s of field %s should satisfy: `%s`."
          (get-option-name data)
          fq-field-name
          (get-pred-name data))
        (get-field data)))))


(defn explain-data->error-report
  "Convert spec explain-data output to errors' report."
  [explain-data]
  (let [problems (->> (:clojure.spec.alpha/problems explain-data)
                   (remove extra-problem?))
        main-spec (:clojure.spec.alpha/spec explain-data)
        origin-value (:clojure.spec.alpha/value explain-data)
        reports (for [problem problems
                      :let [problem* (assoc problem
                                       :last-spec (last-spec problem)
                                       :main-spec main-spec
                                       :origin-value origin-value)]]
                  (format ERROR-TEMPLATE
                    (->error-title problem*)
                    (->error-message problem*)))]
        ; TODO: uncomment!
        ;reports* (distinct reports)]
    (str/join "\n" reports)))


(comment
  (let [data {:path [1 :vec :type :kw],
              :pred 'tuna.fields/field-types,
              :val :in,
              :via [:tuna.models/->internal-models
                    :tuna.models/public-models
                    :tuna.fields/field-vec
                    :tuna.fields/type
                    :tuna.fields/type],
              :in [:test 1 0 1]
              :main-spec :tuna.models/->internal-models,
              :origin-value {:feed {:fields [[:id :serial {:null false, :unique true}]
                                             [:name [:varchar 100]]
                                             [:account :integer {:foreign-key :account/id, :on-delete :cascade}]
                                             [:task :integer {:foreign-key :account/id, :on-delete :set-null}]
                                             [:bar1 :integer {:foreign-key :account/id}]
                                             [:created_at :timestamp {:default [:now]}]
                                             [:updated-at :timestamp {:default [:now]}]],
                                    :indexes [[:feed_name-id_unique_idx :btree {:fields [:name :id]}]]},
                             :test [[:id]],
                             :account [[:id :serial {:unique true}]
                                       [:name [:varchar 256]]
                                       [:foo1 :integer {:foreign-key :foo1/id, :null true}]
                                       [:slug :text {:null false, :unique true}]],
                             :articles [[:id :serial {:unique true}] [:bar1 :integer {:foreign-key :bar1/id}]],
                             :foo1 [[:id :serial {:unique true}] [:account :integer {:foreign-key :account/id}]],
                             :bar1 {:fields [[:id :serial {:unique true}]
                                             [:foo1 :integer {:foreign-key :foo1/id}]
                                             [:account :integer {:foreign-key :account/id}]]},
                             :demo {:fields [[:id :serial {:unique true}]
                                             [:name :text {:null false}]
                                             [:first-name :text]],
                                    :indexes [[:demo_id_idx :btree {:fields [:name :id]}]
                                              [:demo_name_idx :btree {:fields [:name], :unique true}]]}}}
        problems '({:path [1 :vec],
                    :pred clojure.core/coll?,
                    :val 1,
                    :via [:tuna.models/->internal-models :tuna.models/public-models],
                    :in [:test 1]}
                   {:reason "not a map",
                    :path [1 :map],
                    :pred clojure.core/map?,
                    :val 1,
                    :via [:tuna.models/->internal-models :tuna.models/public-models],
                    :in [:test 1]})]
    ;(get-field-name data)
    (get-field data)))
;(get-in (:origin-value data) [:test 0])))
;(map extra-problem? problems)))
