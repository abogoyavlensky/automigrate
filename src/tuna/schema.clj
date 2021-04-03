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
