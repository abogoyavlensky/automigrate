(ns tuna.schema
  "Module for generating db schema from migrations."
  (:require [tuna.util.file :as file-util]
            [tuna.actions :as actions]
            [tuna.models :as models]
            [tuna.util.map :as map-util]
            [tuna.util.spec :as spec-util]
            [tuna.util.model :as model-util]))


(defn- load-migrations-from-files
  [migrations-files]
  (map file-util/read-file-obj migrations-files))


(defmulti apply-action-to-schema
  "Apply migrating action to schema in memory to reproduce current db state."
  (fn [_schema action]
    (:action action)))


(defmethod apply-action-to-schema actions/CREATE-TABLE-ACTION
  [schema action]
  (assoc schema (:model-name action) (select-keys action [:fields])))


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


(defn- validate-actions
  "Validate actions one by one against spec."
  [actions]
  (doseq [action actions]
    (spec-util/valid? ::actions/->migration action)))


(defn current-db-schema
  "Return map of models derived from existing migrations."
  [migrations-files]
  ; TODO: add validation of migrations with spec!
  (let [actions (-> (load-migrations-from-files migrations-files)
                  (flatten))]
    (validate-actions actions)
    (->> actions
      (reduce apply-action-to-schema {})
      (spec-util/conform ::models/internal-models))))


; TODO: remove!
;(comment
;  (require '[clojure.spec.alpha :as s])
;  (require '[tuna.models :as models])
;  (require '[differ.core :as differ])
;  (let [old {:feed {:fields {:id {:type :serial
;                                  :null false}}}}
;        new {:feed {:fields {:url {:type :varchar}}}
;             :user {:fields {:id {:type :serial
;                                  :null false}}}}
;        [alterations removals] (differ/diff old new)]
;    (for [model alterations
;          :let [model-name (key model)]]
;     (when-not (contains? old model-name)
;      [(s/conform ::actions/->migration model)]))))
