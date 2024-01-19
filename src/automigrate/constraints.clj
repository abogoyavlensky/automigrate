(ns automigrate.constraints
  (:require [clojure.string :as str]))


(def ^:private PRIMARY-KEY-CONSTRAINT-POSTFIX "pkey")


; TODO: move other constraint related code here!

(defn primary-key-constraint-name
  [model-name]
  (->> [(name model-name) PRIMARY-KEY-CONSTRAINT-POSTFIX]
    (str/join #"-")
    (keyword)))
