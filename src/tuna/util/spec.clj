(ns tuna.util.spec
  "Tools for simplifying spec usage."
  (:require [clojure.spec.alpha :as s]
            [slingshot.slingshot :refer [throw+]]))


(defn conform
  "Conform data to spec or throw explained data."
  [spec data]
  (let [conformed (s/conform spec data)]
    (case conformed
      ::s/invalid (throw+ {:type ::s/invalid
                           :data (s/explain-data spec data)})
      conformed)))
