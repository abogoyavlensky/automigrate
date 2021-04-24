(ns tuna.schema
  "Module for generating db schema from migrations."
  (:require [tuna.util.file :as file-util]
            [tuna.models :as models]
            [tuna.util.map :as map-util]))


(defn- load-migrations-from-files
  [migrations-files]
  (map file-util/read-file-obj migrations-files))


(defmulti apply-action-to-schema
  "Apply migrating action to schema in memory to reproduce current db state."
  (fn [_schema action]
    (:action action)))


(defmethod apply-action-to-schema models/CREATE-TABLE-ACTION
  [schema action]
  (assoc schema (:name action) (select-keys action [:fields])))


(defmethod apply-action-to-schema models/ADD-COLUMN-ACTION
  [schema action]
  (assoc-in schema [(:table-name action) :fields (:name action)]
    (:options action)))


(defmethod apply-action-to-schema models/ALTER-COLUMN-ACTION
  [schema action]
  (let [table-name (:table-name action)
        field-name (:name action)]
    (-> schema
      (update-in [table-name :fields field-name] merge (:changes action))
      (map-util/dissoc-in [table-name :fields field-name] (:drop action)))))


(defn current-db-schema
  "Return map of models derived from existing migrations."
  [migrations-files]
  ; TODO: add validation of migrations with spec!
  (let [actions (-> (load-migrations-from-files migrations-files)
                  (flatten))]
    (reduce apply-action-to-schema {} actions)))


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
;      [(s/conform ::models/->migration model)]))))
