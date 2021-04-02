(ns tuna.schema
  (:require [tuna.util.file :as util-file]
            [tuna.sql :as sql]))


(defn- load-migrations-from-files
  [migrations-files]
  (map util-file/read-file-obj migrations-files))


(defmulti apply-migration
  "Apply migration to schema."
  (fn [_schema migration]
    (:action migration)))


(defmethod apply-migration sql/CREATE-TABLE-ACTION
  [schema migration]
  (assoc schema (:name migration) (:model migration)))


(defn- make-schema
  ([]
   (make-schema [] nil))
  ([schema migration]
   (apply-migration schema migration)))


(defn current-db-schema
  "Returns map of models derived from existing migrations."
  [migrations-files]
  ; TODO: add validation of migrations!
  (let [migrations (load-migrations-from-files migrations-files)]
    ; TODO: reduce all migrations not only first!
    (reduce make-schema (first migrations))))
