(ns tuna.core
  "Public interface for lib's users."
  (:require [clojure.spec.alpha :as s]
            [slingshot.slingshot :refer [try+]]
            [tuna.migrations :as migrations]
            [tuna.util.spec :as spec-util]
            [tuna.util.file :as file-util]
            [tuna.errors :as errors]))

; Enable asserts for spec in function's pre and post conditions
(s/check-asserts true)


(s/def ::model-file string?)
(s/def ::migrations-dir string?)
(s/def ::db-uri string?)
(s/def ::number int?)


(s/def ::type
  (s/and
    (s/conformer keyword)
    #{:sql}))


(s/def ::name (s/conformer name))


(s/def ::direction
  (s/and
    (s/conformer keyword)
    #{:forward :backward}))


(s/def ::cmd
  #{:make-migrations
    :migrate
    :explain
    :list-migrations})


(defmulti run-args :cmd)


(defmethod run-args :make-migrations
  [_]
  (s/keys
    :req-un [::cmd
             ::model-file
             ::migrations-dir]
    :opt-un [::type
             ::name]))


(defmethod run-args :migrate
  [_]
  (s/keys
    :req-un [::cmd
             ::db-uri
             ::migrations-dir]
    :opt-un [::number]))


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
             ::db-uri
             ::migrations-dir]))


(s/def ::args
  (s/multi-spec run-args :cmd))


(defn run
  "Main exec function with dispatcher for all commands."
  [{:keys [cmd] :as args}]
  (try+
    (let [args* (spec-util/conform ::args args)
          cmd-fn (case cmd
                   :make-migrations migrations/make-migrations
                   :migrate migrations/migrate
                   :explain migrations/explain
                   :list-migrations migrations/list-migrations)]
      (cmd-fn (dissoc args* :cmd)))
    (catch [:type ::s/invalid] e
      (file-util/prn-err e))
    (catch #(contains? #{:tuna.migrations/missing-migration-name} (:type %)) e
      (-> e
        (errors/custom-error->error-report)
        (file-util/prn-err)))
    (catch Exception e
      (let [message (or (ex-message e) (str e))]
        (-> {:title "UNEXPECTED ERROR"
             :message message}
          (errors/custom-error->error-report)
          (file-util/prn-err))))))
