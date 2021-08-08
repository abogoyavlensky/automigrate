(ns user
  (:require [clojure.tools.namespace.repl :as repl]
            [clojure.test :as test]
            [hashp.core]
            [expound.alpha :as expound]))


(repl/set-refresh-dirs "dev" "src" "test")


(defn reset
  "Reload changed namespaces."
  []
  (reset! (deref #'expound/registry-ref) {})
  (repl/refresh))


(defn run-all-tests
  "Reload changed namespaces."
  []
  (reset)
  (test/run-all-tests #"tuna.*-test"))
