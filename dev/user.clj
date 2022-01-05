(ns user
  (:require [clojure.tools.namespace.repl :as repl]
            [clojure.test :as test]
            [hashp.core]))


(repl/set-refresh-dirs "dev" "src" "test")


(defn reset
  "Reload changed namespaces."
  []
  (repl/refresh))


(defn run-all-tests
  "Reload changed namespaces."
  []
  (reset)
  (test/run-all-tests #"automigrate.*-test"))
