(ns automigrate.core
  "Public interface for lib's users."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [slingshot.slingshot :refer [try+]]
            [automigrate.migrations :as migrations]
            [automigrate.util.spec :as spec-util]
            [automigrate.util.file :as file-util]
            [automigrate.errors :as errors]))

; Enable asserts for spec in function's pre and post conditions
(s/check-asserts true)


(s/def ::models-file string?)
(s/def ::migrations-dir string?)
(s/def ::jdbc-url string?)
(s/def ::number int?)


(s/def ::type
  (s/and
    (s/conformer keyword)
    #{migrations/EMPTY-SQL-MIGRATION-TYPE}))


(s/def ::name (s/conformer name))


(s/def ::migrations-table
  (s/and
    string?
    (s/conformer
      (fn [v]
        (keyword (str/replace v #"_" "-"))))))


(s/def ::direction
  (s/and
    (s/conformer keyword)
    #{:forward :backward}))


(s/def ::cmd
  #{:make-migration
    :migrate
    :explain
    :list-migrations})


(defmulti run-args :cmd)


(defmethod run-args :make-migration
  [_]
  (s/keys
    :req-un [::cmd
             ::models-file
             ::migrations-dir]
    :opt-un [::type
             ::name]))


(defmethod run-args :migrate
  [_]
  (s/keys
    :req-un [::cmd
             ::jdbc-url
             ::migrations-dir]
    :opt-un [::number
             ::migrations-table]))


(defmethod run-args :explain
  [_]
  (s/keys
    :req-un [::cmd
             ::migrations-dir
             ::number]
    :opt-un [::direction]))


(defmethod run-args :list-migrations
  [_]
  (s/keys
    :req-un [::cmd
             ::jdbc-url
             ::migrations-dir]
    :opt-un [::migrations-table]))


(s/def ::args
  (s/multi-spec run-args :cmd))


(defn run
  "Main exec function with dispatcher for all commands."
  [{:keys [cmd] :as args}]
  (try+
    (let [args* (spec-util/conform ::args args)
          cmd-fn (case cmd
                   :make-migration migrations/make-migration
                   :migrate migrations/migrate
                   :explain migrations/explain
                   :list-migrations migrations/list-migrations)]
      (cmd-fn (dissoc args* :cmd)))
    (catch [:type ::s/invalid] e
      (file-util/prn-err e))
    (catch Object e
      (let [message (or (ex-message e) (str e))]
        (-> {:title "UNEXPECTED ERROR"
             :message message}
          (errors/custom-error->error-report)
          (file-util/prn-err))))))
