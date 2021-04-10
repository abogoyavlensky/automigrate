(ns tuna.core
  "Public interface for lib's users."
  (:require [clojure.spec.alpha :as s]
            [tuna.migrations :as migrations]))


(s/def :args/model-file string?)
(s/def :args/migrations-dir string?)
(s/def :args/db-uri string?)


(s/def ::make-migrations-args
  (s/keys
    :req-un [:args/model-file
             :args/migrations-dir
             :args/db-uri]))


(defn run
  "Main exec function with dispatcher for all commands."
  [{:keys [action] :as args}]
  (s/valid? ::make-migrations-args args)
  (let [action-fn (case action
                    :make-migrations migrations/make-migrations
                    :migrate migrations/migrate)]
    (action-fn (dissoc args :action))))
