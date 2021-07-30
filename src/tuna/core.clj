(ns tuna.core
  "Public interface for lib's users."
  (:require [clojure.spec.alpha :as s]
            [tuna.migrations :as migrations]
            [tuna.util.spec :as spec-util]))

; Enable asserts for spec in function's pre and post conditions
(s/check-asserts true)


(s/def ::model-file string?)
(s/def ::migrations-dir string?)
(s/def ::db-uri string?)
; TODO: use conform str -> int!
(s/def ::number int?)
(s/def ::type (s/conformer keyword))
(s/def ::name (s/conformer name))


(s/def ::direction
  (s/and
    (s/conformer keyword)
    #{:forward :backward}))


(s/def ::run-args
  (s/keys
    :req-un [::migrations-dir]
    :opt-un [::model-file
             ::number
             ::db-uri
             ::type
             ::name
             ::direction]))


(defn run
  "Main exec function with dispatcher for all commands."
  [{:keys [action] :as args}]
  {:pre [(s/assert ::run-args args)]}
  ; TODO: switch validations for args by action, maybe using multi-spec!
  (let [args* (spec-util/conform ::run-args args)
        action-fn (case action
                    :make-migrations migrations/make-migrations
                    :migrate migrations/migrate
                    :explain migrations/explain
                    :list-migrations migrations/list-migrations)]
    (action-fn (dissoc args* :action))))
