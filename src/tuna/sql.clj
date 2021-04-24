(ns tuna.sql
  "Module for transforming actions from migration to SQL queries."
  (:require [clojure.spec.alpha :as s]
            [tuna.models :as models]))


(s/def :option->sql/type
  (s/and
    :field/type
    (s/conformer identity)))


(s/def :option->sql/null
  (s/and
    :field/null
    (s/conformer
      (fn [value]
        (if (true? value)
          nil
          [:not nil])))))


(s/def :option->sql/primary-key
  (s/and
    :field/primary-key
    (s/conformer
      (fn [_]
        [:primary-key]))))


(s/def :option->sql/unique
  (s/and
    :field/unique
    (s/conformer
      (fn [_]
        :unique))))


(s/def :option->sql/default
  (s/and
    :field/default
    (s/conformer
      (fn [value]
        [:default value]))))


(s/def ::options->sql
  (s/keys
    :req-un [:option->sql/type]
    :opt-un [:option->sql/null
             :option->sql/primary-key
             :option->sql/unique
             :option->sql/default]))


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


(derive ::options ::options->sql)


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
               ::options])
    ::add-column->sql))


(s/def ::->sql (s/multi-spec action->sql :action))
