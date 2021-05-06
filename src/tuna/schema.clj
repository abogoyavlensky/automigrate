(ns tuna.schema
  "Module for generating db schema from migrations."
  (:require [tuna.util.file :as file-util]
            [tuna.actions :as actions]
            [tuna.models :as models]
            [tuna.util.map :as map-util]
            [tuna.util.spec :as spec-util]))


(defn- load-migrations-from-files
  [migrations-files]
  (map file-util/read-file-obj migrations-files))


(defmulti apply-action-to-schema
  "Apply migrating action to schema in memory to reproduce current db state."
  (fn [_schema action]
    (:action action)))


(defmethod apply-action-to-schema actions/CREATE-TABLE-ACTION
  [schema action]
  (assoc schema (:name action) (select-keys action [:fields])))


(defmethod apply-action-to-schema actions/ADD-COLUMN-ACTION
  [schema action]
  (assoc-in schema [(:table-name action) :fields (:name action)]
    (:options action)))


(defmethod apply-action-to-schema actions/ALTER-COLUMN-ACTION
  [schema action]
  (let [table-name (:table-name action)
        field-name (:name action)
        dissoc-actions-fn (fn [schema]
                            (apply map-util/dissoc-in
                              schema
                              [table-name :fields field-name]
                              (:drop action)))]

    (-> schema
      (update-in [table-name :fields field-name] merge (:changes action))
      (dissoc-actions-fn))))


(defmethod apply-action-to-schema actions/DROP-COLUMN-ACTION
  [schema action]
  (map-util/dissoc-in schema [(:table-name action) :fields] (:name action)))


(defmethod apply-action-to-schema actions/DROP-TABLE-ACTION
  [schema action]
  (dissoc schema (:name action)))


(defmethod apply-action-to-schema actions/CREATE-INDEX-ACTION
  [schema action]
  (assoc-in schema [(:table-name action) :indexes (:name action)]
    (:options action)))


(defmethod apply-action-to-schema actions/DROP-INDEX-ACTION
  [schema action]
  (map-util/dissoc-in schema [(:table-name action) :indexes] (:name action)))


(defmethod apply-action-to-schema actions/ALTER-INDEX-ACTION
  [schema action]
  (assoc-in schema [(:table-name action) :indexes (:name action)]
    (:options action)))


(defn current-db-schema
  "Return map of models derived from existing migrations."
  [migrations-files]
  ; TODO: add validation of migrations with spec!
  (let [actions (-> (load-migrations-from-files migrations-files)
                  (flatten))]
    (->> actions
      (reduce apply-action-to-schema {})
      (spec-util/conform ::models/models))))


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
