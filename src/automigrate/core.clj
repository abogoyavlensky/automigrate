(ns automigrate.core
  "Public interface for lib's users."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [slingshot.slingshot :refer [try+]]
            [automigrate.migrations :as migrations]
            [automigrate.util.spec :as spec-util]
            [automigrate.util.file :as file-util]
            [automigrate.errors :as errors])
  (:refer-clojure :exclude [list]))

; Enable asserts for spec in function's pre and post conditions
(s/check-asserts true)


(s/def ::models-file string?)
(s/def ::migrations-dir string?)
(s/def ::jdbc-url (s/conformer str))
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


(s/def ::make-args
  (s/keys
    :req-un [::models-file
             ::migrations-dir]
    :opt-un [::type
             ::name]))


(s/def ::migrate-args
  (s/keys
    :req-un [::jdbc-url
             ::migrations-dir]
    :opt-un [::number
             ::migrations-table]))


(s/def ::explain-args
  (s/keys
    :req-un [::migrations-dir
             ::number]
    :opt-un [::direction]))


(s/def ::list-args
  (s/keys
    :req-un [::jdbc-url
             ::migrations-dir]
    :opt-un [::migrations-table]))


(defn- run-fn
  [f args args-spec]
  (try+
    (let [args* (spec-util/conform args-spec args)]
      (f args*))
    (catch [:type ::s/invalid] e
      (file-util/prn-err e))
    (catch Object e
      (let [message (or (ex-message e) (str e))]
        (-> {:title "UNEXPECTED ERROR"
             :message message}
          (errors/custom-error->error-report)
          (file-util/prn-err))))))


; Public interface

(defn make
  [args]
  (run-fn migrations/make-migration args ::make-args))


(defn migrate
  [args]
  (run-fn migrations/migrate args ::migrate-args))


(defn explain
  [args]
  (run-fn migrations/explain args ::explain-args))


(defn list
  [args]
  (run-fn migrations/list-migrations args ::list-args))


; TODO: remove!
(defn run
  "Main exec function with dispatcher for all commands."
  [{:keys [cmd] :as args}]
  (try+
    (let [args* (spec-util/conform ::args args)
          cmd-fn (case cmd
                   :make migrations/make-migration
                   :migrate migrations/migrate
                   :explain migrations/explain
                   :list migrations/list-migrations)]
      (cmd-fn (dissoc args* :cmd)))
    (catch [:type ::s/invalid] e
      (file-util/prn-err e))
    (catch Object e
      (let [message (or (ex-message e) (str e))]
        (-> {:title "UNEXPECTED ERROR"
             :message message}
          (errors/custom-error->error-report)
          (file-util/prn-err))))))
