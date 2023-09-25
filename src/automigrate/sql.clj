(ns automigrate.sql
  "Module for transforming actions from migration to SQL queries."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [spec-dict :as d]
            [automigrate.actions :as actions]
            [automigrate.fields :as fields]
            [automigrate.util.db :as db-util]
            [automigrate.util.model :as model-util]
            [automigrate.util.spec :as spec-util]))


(def ^:private UNIQUE-INDEX-POSTFIX "key")
(def ^:private PRIVATE-KEY-INDEX-POSTFIX "pkey")
(def ^:private FOREIGN-KEY-INDEX-POSTFIX "fkey")
(def ^:private DEFAULT-INDEX :btree)


(s/def :automigrate.sql.option->sql/type
  (s/and
    ::fields/type
    (s/conformer identity)))


(s/def :automigrate.sql.option->sql/null
  (s/and
    ::fields/null
    (s/conformer
      (fn [value]
        (if (true? value)
          nil
          [:not nil])))))


(s/def :automigrate.sql.option->sql/primary-key
  (s/and
    ::fields/primary-key
    (s/conformer
      (fn [_]
        [:primary-key]))))


(s/def :automigrate.sql.option->sql/unique
  (s/and
    ::fields/unique
    (s/conformer
      (fn [_]
        :unique))))


(s/def :automigrate.sql.option->sql/default
  (s/and
    ::fields/default
    (s/conformer
      (fn [value]
        [:default value]))))


(defn- fk-opt->raw
  [option value]
  (mapv db-util/kw->raw [option value]))


(s/def ::foreign-key
  (d/dict*
    {:foreign-field ::fields/foreign-key}
    ^:opt {fields/ON-DELETE-OPTION ::fields/on-delete
           fields/ON-UPDATE-OPTION ::fields/on-update}))


(s/def :automigrate.sql.option->sql/foreign-key
  (s/and
    (s/conformer
      (fn [value]
        [(cons :references (model-util/kw->vec value))]))))


(s/def :automigrate.sql.option->sql/on-delete
  (s/and
    ::fields/on-delete
    (s/conformer
      (fn [value]
        (fk-opt->raw fields/ON-DELETE-OPTION value)))))


(s/def :automigrate.sql.option->sql/on-update
  (s/and
    ::fields/on-update
    (s/conformer
      (fn [value]
        (fk-opt->raw fields/ON-UPDATE-OPTION value)))))


(def ^:private options-specs
  [:automigrate.sql.option->sql/null
   :automigrate.sql.option->sql/primary-key
   :automigrate.sql.option->sql/unique
   :automigrate.sql.option->sql/default
   :automigrate.sql.option->sql/foreign-key
   :automigrate.sql.option->sql/on-delete
   :automigrate.sql.option->sql/on-update])


(s/def ::->foreign-key-complete
  (s/conformer
    (fn [value]
      (if-let [foreign-key (:foreign-key value)]
        (let [on-delete (get value fields/ON-DELETE-OPTION)
              on-update (get value fields/ON-UPDATE-OPTION)
              foreign-key-complete (cond-> foreign-key
                                     (some? on-delete) (concat on-delete)
                                     (some? on-update) (concat on-update))]
          (-> value
            (assoc fields/FOREIGN-KEY-OPTION foreign-key-complete)
            (dissoc fields/ON-DELETE-OPTION fields/ON-UPDATE-OPTION)))
        value))))


(s/def ::options->sql
  (s/and
    (d/dict*
      {:type :automigrate.sql.option->sql/type}
      (d/->opt (spec-util/specs->dict options-specs)))
    ::->foreign-key-complete))


(s/def ::fields
  (s/map-of keyword? ::options->sql))


(defn- fields->columns
  [fields]
  (reduce
    (fn [acc [field-name options]]
      (conj acc (->> (dissoc options :type :foreign-key)
                  (vals)
                  (concat [field-name (:type options)] (:foreign-key options)))))
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
      (d/->opt (model-util/generate-type-option :automigrate.sql.option->sql/type))
      (d/->opt (model-util/generate-changes options-specs)))
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


(defn- foreign-key-changes
  "Return full option changes or nil if option should be dropped."
  [options changes-to-add changes-to-drop]
  (let [keys-to-add (set (keys changes-to-add))]
    (if (contains? changes-to-drop :foreign-key)
      nil
      (when (or (some keys-to-add [:foreign-key :on-delete :on-update])
              (some changes-to-drop [:on-delete :on-update]))
        (:foreign-key options)))))


(defn- get-changes
  [action]
  (let [changes-to-add (model-util/changes-to-add (:changes action))
        changes-to-drop (model-util/changes-to-drop (:changes action))
        foreign-key (foreign-key-changes (:options action) changes-to-add changes-to-drop)]
    {:changes-to-add (cond-> changes-to-add
                       true (dissoc :on-delete :on-update)
                       (some? foreign-key) (assoc :foreign-key foreign-key))
     :changes-to-drop (disj changes-to-drop :on-delete :on-update)}))


(defn- changes-for-fk
  [{:keys [model-name field-name field-value action-changes]}]
  (let [from-value-empty? (= :EMPTY (get-in action-changes [:foreign-key :from]))
        drop-constraint {:drop-constraint
                         [[:raw "IF EXISTS"]
                          (foreign-key-index-name model-name field-name)]}
        add-constraint {:add-constraint
                        (concat [(foreign-key-index-name model-name field-name)
                                 [:foreign-key field-name]]
                          field-value)}]
    (cond-> []
      (not from-value-empty?) (conj drop-constraint)
      true (conj add-constraint))))


(s/def ::alter-column->sql
  (s/conformer
    (fn [action]
      (let [{:keys [changes-to-add changes-to-drop]} (get-changes action)
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
                        ; TODO: drop constraint if foreign-key has been changed key itself!
                        ; Don't drop if foreign-key was empty!
                        :foreign-key (changes-for-fk {:model-name model-name
                                                      :field-name field-name
                                                      :field-value value
                                                      :action-changes (:changes action)})))

            dropped (for [option changes-to-drop
                          :let [field-name (:field-name action)
                                model-name (:model-name action)]]
                      (case option
                        :null {:alter-column [field-name :drop [:not nil]]}
                        :default {:alter-column [field-name :drop :default]}
                        :unique {:drop-constraint (unique-index-name model-name field-name)}
                        :primary-key {:drop-constraint (private-key-index-name model-name)}
                        :foreign-key {:drop-constraint (foreign-key-index-name model-name field-name)}))
            all-actions (concat (flatten changes) dropped)]
        {:alter-table (cons (:model-name action) all-actions)}))))


(defmethod action->sql actions/ALTER-COLUMN-ACTION
  [_]
  (s/and
    (s/keys
      :req-un [::actions/action
               ::actions/field-name
               ::actions/model-name
               ::options
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
               :automigrate.actions.indexes/options])
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
               :automigrate.actions.indexes/options])
    ::alter-index->sql))


(s/def ::->sql (s/multi-spec action->sql :action))


(defn ->sql
  "Convert migration action to sql."
  [action]
  (let [formatted-action (s/conform ::->sql action)]
    (if (sequential? formatted-action)
      (map #(db-util/fmt %) formatted-action)
      (db-util/fmt formatted-action))))
