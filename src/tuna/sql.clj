(ns tuna.sql
  "Module for transforming actions from migration to SQL queries."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [spec-dict :as d]
            [tuna.actions :as actions]
            [tuna.util.db :as db-util]
            [tuna.util.model :as model-util]))


(def ^:private UNIQUE-INDEX-POSTFIX "key")
(def ^:private PRIVATE-KEY-INDEX-POSTFIX "pkey")
(def ^:private FOREIGN-KEY-INDEX-POSTFIX "fkey")
(def ^:private DEFAULT-INDEX :btree)


(s/def :tuna.sql.option->sql/type
  (s/and
    :tuna.models.field/type
    (s/conformer identity)))


(s/def :tuna.sql.option->sql/null
  (s/and
    :tuna.models.field/null
    (s/conformer
      (fn [value]
        (if (true? value)
          nil
          [:not nil])))))


(s/def :tuna.sql.option->sql/primary-key
  (s/and
    :tuna.models.field/primary-key
    (s/conformer
      (fn [_]
        [:primary-key]))))


(s/def :tuna.sql.option->sql/unique
  (s/and
    :tuna.models.field/unique
    (s/conformer
      (fn [_]
        :unique))))


(s/def :tuna.sql.option->sql/default
  (s/and
    :tuna.models.field/default
    (s/conformer
      (fn [value]
        [:default value]))))


(s/def :tuna.sql.option->sql/foreign-key
  (s/and
    :tuna.models.field/foreign-key
    (s/conformer
      (fn [value]
        (cons :references (model-util/kw->vec value))))))


(s/def ::options->sql
  (s/keys
    :req-un [:tuna.sql.option->sql/type]
    :opt-un [:tuna.sql.option->sql/null
             :tuna.sql.option->sql/primary-key
             :tuna.sql.option->sql/unique
             :tuna.sql.option->sql/default
             :tuna.sql.option->sql/foreign-key]))


(s/def ::fields
  (s/map-of keyword? ::options->sql))


(defn- fields->columns
  [fields]
  (reduce
    (fn [acc [field-name options]]
      (conj acc (->> (dissoc options :type)
                  (vals)
                  (concat [field-name (:type options)]))))
    []
    fields))


(defmulti action->sql :action)


(s/def ::create-table->sql
  (s/conformer
    (fn [value]
      {:create-table [(:model-name value)]
       :with-columns (fields->columns (:fields value))})))


(defmethod action->sql actions/CREATE-TABLE-ACTION
  [_]
  (s/and
    (s/keys
      :req-un [::actions/action
               ::actions/model-name
               ::fields])
    ::create-table->sql))


(s/def ::options
  ::options->sql)


(s/def ::add-column->sql
  (s/conformer
    (fn [value]
      {:alter-table (:model-name value)
       :add-column (first (fields->columns [[(:field-name value) (:options value)]]))})))


(defmethod action->sql actions/ADD-COLUMN-ACTION
  [_]
  (s/and
    (s/keys
      :req-un [::actions/action
               ::actions/field-name
               ::actions/model-name
               ::options])
    ::add-column->sql))


(s/def ::changes
  (s/and
    (d/dict*
      (d/->opt (model-util/generate-type-option :tuna.sql.option->sql/type))
      (d/->opt (model-util/generate-changes [:tuna.sql.option->sql/null
                                             :tuna.sql.option->sql/primary-key
                                             :tuna.sql.option->sql/unique
                                             :tuna.sql.option->sql/default
                                             :tuna.sql.option->sql/foreign-key])))
    #(> (count (keys %)) 0)))


(defn- unique-index-name
  [model-name field-name]
  (->> [(name model-name) (name field-name) UNIQUE-INDEX-POSTFIX]
    (str/join #"-")
    (keyword)))


(defn- private-key-index-name
  [model-name]
  (->> [(name model-name) PRIVATE-KEY-INDEX-POSTFIX]
    (str/join #"-")
    (keyword)))


(defn- foreign-key-index-name
  [model-name field-name]
  (->> [(name model-name) (name field-name) FOREIGN-KEY-INDEX-POSTFIX]
    (str/join #"-")
    (keyword)))


(s/def ::alter-column->sql
  (s/conformer
    (fn [action]
      (let [changes-to-add (model-util/changes-to-add (:changes action))
            changes (for [[option value] changes-to-add
                          :let [field-name (:field-name action)
                                model-name (:model-name action)]]
                      (case option
                        :type {:alter-column [field-name :type value]}
                        :null (let [operation (if (nil? value) :drop :set)]
                                {:alter-column [field-name operation [:not nil]]})
                        :default {:alter-column [field-name :set value]}
                        :unique {:add-index [:unique nil field-name]}
                        :primary-key {:add-index [:primary-key field-name]}
                        :foreign-key {:add-constraint [(foreign-key-index-name model-name field-name)
                                                       [:foreign-key field-name]
                                                       value]}))

            changes-to-drop (model-util/changes-to-drop (:changes action))
            dropped (for [option changes-to-drop
                          :let [field-name (:field-name action)
                                model-name (:model-name action)]]
                      (case option
                        :null {:alter-column [field-name :set [:not nil]]}
                        :default {:alter-column [field-name :drop :default]}
                        :unique {:drop-constraint (unique-index-name model-name field-name)}
                        :primary-key {:drop-constraint (private-key-index-name model-name)}
                        :foreign-key {:drop-constraint (foreign-key-index-name model-name field-name)}))
            all-actions (concat changes dropped)]
        {:alter-table (cons (:model-name action) all-actions)}))))


(defmethod action->sql actions/ALTER-COLUMN-ACTION
  [_]
  (s/and
    (s/keys
      :req-un [::actions/action
               ::actions/field-name
               ::actions/model-name
               ::changes])
    ::alter-column->sql))


(s/def ::drop-column->sql
  (s/conformer
    (fn [value]
      {:alter-table (:model-name value)
       :drop-column (:field-name value)})))


(defmethod action->sql actions/DROP-COLUMN-ACTION
  [_]
  (s/and
    (s/keys
      :req-un [::actions/action
               ::actions/field-name
               ::actions/model-name])
    ::drop-column->sql))


(s/def ::drop-table->sql
  (s/conformer
    (fn [value]
      {:drop-table [:if-exists (:model-name value)]})))


(defmethod action->sql actions/DROP-TABLE-ACTION
  [_]
  (s/and
    (s/keys
      :req-un [::actions/action
               ::actions/model-name])
    ::drop-table->sql))


(s/def ::create-index->sql
  (s/conformer
    (fn [value]
      (let [options (:options value)
            index-type (or (:type options) DEFAULT-INDEX)
            index-action (if (true? (:unique options))
                           :create-unique-index
                           :create-index)]
        {index-action [(:index-name value) :on (:model-name value)
                       :using (cons index-type (:fields options))]}))))


(defmethod action->sql actions/CREATE-INDEX-ACTION
  [_]
  (s/and
    (s/keys
      :req-un [::actions/action
               ::actions/index-name
               ::actions/model-name
               :tuna.actions.indexes/options])
    ::create-index->sql))


(s/def ::drop-index->sql
  (s/conformer
    (fn [value]
      {:drop-index (:index-name value)})))


(defmethod action->sql actions/DROP-INDEX-ACTION
  [_]
  (s/and
    (s/keys
      :req-un [::actions/action
               ::actions/index-name
               ::actions/model-name])
    ::drop-index->sql))


(s/def ::alter-index->sql
  (s/conformer
    (fn [value]
      [(s/conform ::drop-index->sql (assoc value :action actions/DROP-INDEX-ACTION))
       (s/conform ::create-index->sql (assoc value :action actions/CREATE-INDEX-ACTION))])))


(defmethod action->sql actions/ALTER-INDEX-ACTION
  [_]
  (s/and
    (s/keys
      :req-un [::actions/action
               ::actions/index-name
               ::actions/model-name
               :tuna.actions.indexes/options])
    ::alter-index->sql))


(s/def ::->sql (s/multi-spec action->sql :action))


(defn ->sql
  "Convert migration action to sql."
  [action]
  (let [formatted-action (s/conform ::->sql action)]
    (if (sequential? formatted-action)
      (map #(db-util/fmt %) formatted-action)
      (db-util/fmt formatted-action))))
