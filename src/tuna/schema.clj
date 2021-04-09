(ns tuna.schema
  (:require [tuna.util.file :as util-file]
            [tuna.sql :as sql]))


(defn- load-migrations-from-files
  [migrations-files]
  (map util-file/read-file-obj migrations-files))


(defmulti apply-migration-to-schema
  (fn [_schema migration]
    (:action migration)))


(defmethod apply-migration-to-schema sql/CREATE-TABLE-ACTION
  [schema migration]
  (assoc schema (:name migration) (:model migration)))


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
