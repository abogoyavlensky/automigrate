(ns tuna.core
  "Public interface for lib's users."
  (:require [clojure.spec.alpha :as s]
            [tuna.migrations :as migrations]))

; TODO: try to check args without assertion
(s/check-asserts true)


(s/def :args/model-file string?)
(s/def :args/migrations-dir string?)
(s/def :args/db-uri string?)
; TODO: use conform str -> int!
(s/def :args/number int?)


(s/def ::run-args
  (s/keys
    :req-un [:args/migrations-dir]
    :opt-un [:args/model-file
             :args/number
             :args/db-uri]))


(defn run
  "Main exec function with dispatcher for all commands."
  [{:keys [action] :as args}]
  {:pre [(s/assert ::run-args args)]}
  ; TODO: switch validations for args by action, maybe using multi-spec!
  (let [action-fn (case action
                    :make-migrations migrations/make-migrations
                    :migrate migrations/migrate
                    :explain migrations/explain)]
    (action-fn (dissoc args :action))))
