(ns tuna.sql
  "Module for transforming actions from migration to SQL queries."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [tuna.models :as models]))


(def ^:private UNIQUE-INDEX-POSTFIX "key")
(def ^:private PRIVATE-KEY-INDEX-POSTFIX "pkey")
(def ^:private FOREIGN-KEY-INDEX-POSTFIX "fkey")


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
        (cons :references value)))))


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
      {(:action value) [(:name value)]
       :with-columns (fields->columns (:fields value))})))


(defmethod action->sql models/CREATE-TABLE-ACTION
  [_]
  (s/and
    (s/keys
      :req-un [::models/action
               ::models/name
               ::fields])
    ::create-table->sql))


(s/def ::options
  ::options->sql)


(s/def ::add-column->sql
  (s/conformer
    (fn [value]
      {:alter-table (:table-name value)
       :add-column (first (fields->columns [[(:name value) (:options value)]]))})))


(defmethod action->sql models/ADD-COLUMN-ACTION
  [_]
  (s/and
    (s/keys
      :req-un [::models/action
               ::models/name
               ::models/table-name
               ::options])
    ::add-column->sql))


(s/def ::changes
  (s/nilable
    (s/keys
      :opt-un [:tuna.sql.option->sql/type
               :tuna.sql.option->sql/null
               :tuna.sql.option->sql/primary-key
               :tuna.sql.option->sql/unique
               :tuna.sql.option->sql/default
               :tuna.sql.option->sql/foreign-key])))


(defn- unique-index-name
  [table-name field-name]
  (->> [(name table-name) (name field-name) UNIQUE-INDEX-POSTFIX]
    (str/join #"-")
    (keyword)))


(defn- private-key-index-name
  [table-name]
  (->> [(name table-name) PRIVATE-KEY-INDEX-POSTFIX]
    (str/join #"-")
    (keyword)))


(defn- foreign-key-index-name
  [table-name field-name]
  (->> [(name table-name) (name field-name) FOREIGN-KEY-INDEX-POSTFIX]
    (str/join #"-")
    (keyword)))


(s/def ::alter-column->sql
  (s/conformer
    (fn [action]
      (let [changes (for [[option value] (:changes action)
                          :let [field-name (:name action)]]
                      (case option
                        :type {:alter-column [field-name :type value]}
                        :null (let [operation (if (nil? value) :drop :set)]
                                {:alter-column [field-name operation [:not nil]]})
                        :default {:alter-column [field-name :set value]}
                        :unique {:add-index [:unique nil field-name]}
                        ; TODO: add foreign-key
                        ; Example: ADD CONSTRAINT fk_orders_customers FOREIGN KEY (customer_id) REFERENCES customers (id);
                        :primary-key {:add-index [:primary-key field-name]}))
            dropped (for [option (:drop action)
                          :let [field-name (:name action)
                                table-name (:table-name action)]]
                      (case option
                        :null {:alter-column [field-name :set [:not nil]]}
                        :default {:alter-column [field-name :drop :default]}
                        :unique {:drop-constraint (unique-index-name table-name field-name)}
                        :primary-key {:drop-constraint (private-key-index-name table-name)}
                        :foreign-key {:drop-constraint (foreign-key-index-name table-name field-name)}))
            all-actions (concat changes dropped)]
        {:alter-table (cons (:table-name action) all-actions)}))))


(defmethod action->sql models/ALTER-COLUMN-ACTION
  [_]
  (s/and
    (s/keys
      :req-un [::models/action
               ::models/name
               ::models/table-name
               ::changes
               ::models/drop])
    ::alter-column->sql))


(s/def ::drop-column->sql
  (s/conformer
    (fn [value]
      {:alter-table (:table-name value)
       :drop-column (:name value)})))


(defmethod action->sql models/DROP-COLUMN-ACTION
  [_]
  (s/and
    (s/keys
      :req-un [::models/action
               ::models/name
               ::models/table-name])
    ::drop-column->sql))


(s/def ::drop-table->sql
  (s/conformer
    (fn [value]
      {:drop-table [:if-exists (:name value)]})))


(defmethod action->sql models/DROP-TABLE-ACTION
  [_]
  (s/and
    (s/keys
      :req-un [::models/action
               ::models/name])
    ::drop-table->sql))


(s/def ::->sql (s/multi-spec action->sql :action))
