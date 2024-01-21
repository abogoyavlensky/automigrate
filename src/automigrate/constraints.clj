(ns automigrate.constraints
  (:require [clojure.string :as str]))


(def ^:private PRIMARY-KEY-CONSTRAINT-POSTFIX "pkey")
(def ^:private UNIQUE-INDEX-POSTFIX "key")
(def ^:private FOREIGN-KEY-INDEX-POSTFIX "fkey")


(defn primary-key-constraint-name
  [model-name]
  (->> [(name model-name) PRIMARY-KEY-CONSTRAINT-POSTFIX]
    (str/join #"-")
    (keyword)))


(defn unique-constraint-name
  [model-name field-name]
  (->> [(name model-name) (name field-name) UNIQUE-INDEX-POSTFIX]
    (str/join #"-")
    (keyword)))


(defn foreign-key-index-name
  [model-name field-name]
  (->> [(name model-name) (name field-name) FOREIGN-KEY-INDEX-POSTFIX]
    (str/join #"-")
    (keyword)))
