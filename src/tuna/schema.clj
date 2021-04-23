(ns tuna.schema
  "Module for generating db schema from migrations."
  (:require [tuna.util.file :as file-util]
            [tuna.models :as models]))


(defn- load-migrations-from-files
  [migrations-files]
  (map file-util/read-file-obj migrations-files))


(defmulti apply-migration-to-schema
  (fn [_schema migration]
    (:action migration)))


(defmethod apply-migration-to-schema models/CREATE-TABLE-ACTION
  [schema migration]
  (assoc schema (:name migration) (select-keys migration [:fields])))


(defmethod apply-migration-to-schema models/ADD-COLUMN-ACTION
  [schema migration]
  ; TODO: update!
  (assoc schema (:name migration) (select-keys migration [:fields])))


(defn current-db-schema
  "Return map of models derived from existing migrations."
  [migrations-files]
  ; TODO: add validation of migrations with spec!
  (let [migrations (-> (load-migrations-from-files migrations-files)
                     (flatten))]
    (reduce apply-migration-to-schema {} migrations)))


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
