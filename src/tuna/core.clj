(ns tuna.core
  "Public interface for lib's users."
  (:require [clojure.spec.alpha :as s]
            [tuna.migrations :as migrations]))

; Enable asserts for spec in function's pre and post conditions
(s/check-asserts true)


(s/def ::model-file string?)
(s/def ::migrations-dir string?)
(s/def ::db-uri string?)
; TODO: use conform str -> int!
(s/def ::number int?)


(s/def ::run-args
  (s/keys
    :req-un [::migrations-dir]
    :opt-un [::model-file
             ::number
             ::db-uri]))


(defn run
  "Main exec function with dispatcher for all commands."
  [{:keys [action] :as args}]
  {:pre [(s/assert ::run-args args)]}
  ; TODO: switch validations for args by action, maybe using multi-spec!
  (let [action-fn (case action
                    :make-migrations migrations/make-migrations
                    :migrate migrations/migrate
                    :explain migrations/explain
                    :migration-list migrations/migration-list)]
    (action-fn (dissoc args :action))))
