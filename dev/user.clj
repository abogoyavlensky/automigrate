(ns user
  (:require [clojure.tools.namespace.repl :as repl]))


(repl/set-refresh-dirs "dev" "src" "test")


(defn reset
  "Reload changed namespaces."
  []
  (repl/refresh))
