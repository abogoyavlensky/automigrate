(ns automigrate.schema
  "Module for generating db schema from migrations."
  (:require [automigrate.util.file :as file-util]
            [automigrate.actions :as actions]
            [automigrate.models :as models]
            [automigrate.util.map :as map-util]
            [automigrate.util.spec :as spec-util]
            [automigrate.util.model :as model-util]))


(defn- load-migrations-from-files
  [migrations-files]
  (map file-util/read-edn migrations-files))


(defmulti apply-action-to-schema
  "Apply migrating action to schema in memory to reproduce current db state."
  (fn [_schema action]
    (:action action)))


(defmethod apply-action-to-schema actions/CREATE-TABLE-ACTION
  [schema action]
  (assoc-in schema [(:model-name action) :fields] (:fields action)))


(defmethod apply-action-to-schema actions/ADD-COLUMN-ACTION
  [schema action]
  (assoc-in schema [(:model-name action) :fields (:field-name action)]
    (:options action)))


(defmethod apply-action-to-schema actions/ALTER-COLUMN-ACTION
  [schema action]
  (let [model-name (:model-name action)
        field-name (:field-name action)
        changes-to-add (model-util/changes-to-add (:changes action))
        changes-to-drop (model-util/changes-to-drop (:changes action))
        dissoc-actions-fn (fn [schema]
                            (apply map-util/dissoc-in
                              schema
                              [model-name :fields field-name]
                              changes-to-drop))]

    (-> schema
      (update-in [model-name :fields field-name] merge changes-to-add)
      (dissoc-actions-fn))))


(defmethod apply-action-to-schema actions/DROP-COLUMN-ACTION
  [schema action]
  (map-util/dissoc-in schema [(:model-name action) :fields] (:field-name action)))


(defmethod apply-action-to-schema actions/DROP-TABLE-ACTION
  [schema action]
  (dissoc schema (:model-name action)))


(defmethod apply-action-to-schema actions/CREATE-INDEX-ACTION
  [schema action]
  (assoc-in schema [(:model-name action) :indexes (:index-name action)]
    (:options action)))


(defmethod apply-action-to-schema actions/DROP-INDEX-ACTION
  [schema action]
  (let [action-name (:model-name action)
        result (map-util/dissoc-in
                 schema
                 [action-name :indexes]
                 (:index-name action))]
    (if (seq (get-in result [action-name :indexes]))
      result
      (map-util/dissoc-in result [action-name] :indexes))))


(defmethod apply-action-to-schema actions/ALTER-INDEX-ACTION
  [schema action]
  (assoc-in schema [(:model-name action) :indexes (:index-name action)]
    (:options action)))


(defmethod apply-action-to-schema actions/CREATE-TYPE-ACTION
  [schema action]
  (assoc-in schema [(:model-name action) :types (:type-name action)]
    (:options action)))


(defmethod apply-action-to-schema actions/DROP-TYPE-ACTION
  [schema action]
  (let [action-name (:model-name action)
        result (map-util/dissoc-in
                 schema
                 [action-name :types]
                 (:type-name action))]
    (if (seq (get-in result [action-name :types]))
      result
      (map-util/dissoc-in result [action-name] :types))))


(defn- actions->internal-models
  [actions]
  ; Throws spec exception if not valid.
  (actions/validate-actions! actions)
  (->> actions
    (reduce apply-action-to-schema {})
    (spec-util/conform ::models/internal-models)))


(defn current-db-schema
  "Return map of models derived from existing migrations."
  [migrations-files]
  (let [actions (flatten (load-migrations-from-files migrations-files))]
    (actions->internal-models actions)))
