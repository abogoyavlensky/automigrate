(ns automigrate.constraints
  (:require [clojure.string :as str]))


(def ^:private PRIMARY-KEY-CONSTRAINT-POSTFIX "pkey")
(def ^:private UNIQUE-CONSTRAINT-POSTFIX "key")
(def ^:private FOREIGN-KEY-CONSTRAINT-POSTFIX "fkey")
(def ^:private CHECK-CONSTRAINT-POSTFIX "check")


(defn primary-key-constraint-name
  [model-name]
  (->> [(name model-name) PRIMARY-KEY-CONSTRAINT-POSTFIX]
    (str/join #"-")
    (keyword)))


(defn unique-constraint-name
  [model-name field-name]
  (->> [(name model-name) (name field-name) UNIQUE-CONSTRAINT-POSTFIX]
    (str/join #"-")
    (keyword)))


(defn foreign-key-constraint-name
  [model-name field-name]
  (->> [(name model-name) (name field-name) FOREIGN-KEY-CONSTRAINT-POSTFIX]
    (str/join #"-")
    (keyword)))


(defn check-constraint-name
  [model-name field-name]
  (->> [(name model-name) (name field-name) CHECK-CONSTRAINT-POSTFIX]
    (str/join #"-")
    (keyword)))
