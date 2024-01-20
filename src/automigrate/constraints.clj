(ns automigrate.constraints
  (:require [clojure.string :as str]))


(def ^:private PRIMARY-KEY-CONSTRAINT-POSTFIX "pkey")
(def ^:private UNIQUE-INDEX-POSTFIX "key")


; TODO: move other constraint related code here!

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
