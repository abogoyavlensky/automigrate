(ns user
  (:require [clojure.tools.namespace.repl :as repl]
            [hashp.core]))


(repl/set-refresh-dirs "dev" "src" "test")


(defn reset
  "Reload changed namespaces."
  []
  (repl/refresh))
