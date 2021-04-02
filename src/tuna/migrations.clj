(ns tuna.migrations
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.pprint :as pprint]
            [honeysql.core :as hsql]
            [honeysql-postgres.format :as phformat]
            [honeysql-postgres.helpers :as phsql]
            [tuna.models :as models]
            [tuna.sql :as sql]
            [tuna.schema :as schema]
            [tuna.util.file :as util-file]))

; Config

(def ^:private MIGRATIONS-TABLE
  "Default migrations table name."
  :migrations)


(defn- models
  "Return models' definitions."
  [model-path]
  (-> (slurp model-path)
    (edn/read-string)))


(defn- read-migration
  "Return models' definitions."
  [file-name migrations-dir]
  (-> (slurp (str migrations-dir "/" file-name))
    (edn/read-string)))


(defn- create-migrations-dir
  "Create migrations root dir if it is not exist."
  [migrations-dir]
  (when-not (.isDirectory (io/file migrations-dir))
    (.mkdir (java.io.File. migrations-dir))))


(defn- db-conn
  "Return db connection for performing migration."
  []
  ; TODO: remove defaults!
  ; TODO: move value to `run` fn params
  (let [db-uri (or (System/getenv "DATABASE_URL")
                 "jdbc:postgresql://localhost:5432/tuna?user=tuna&password=tuna")]
    {:connection-uri db-uri}))


(defn- create-migrations-table
  "Create table to keep migrations history."
  []
  (->> {:create-table [MIGRATIONS-TABLE :if-not-exists? true]
        :with-columns [[[:id :serial (hsql/call :not nil) (hsql/call :primary-key)]
                        [:name (hsql/call :varchar (hsql/inline 256)) (hsql/call :not nil) (hsql/call :unique)]
                        [:created_at :timestamp (hsql/call :default (hsql/call :now))]]]}
    (hsql/format)
    (jdbc/execute! (db-conn))))


(defn- save-migration
  "Save migration to db after applying it."
  [migration-name]
  (->> {:insert-into MIGRATIONS-TABLE
        :values [{:name migration-name}]}
    (hsql/format)
    (jdbc/execute! (db-conn))))


(defn- migrations-list
  "Get migrations' files list."
  [migrations-dir]
  (->> (util-file/list-files migrations-dir)
    (map #(.getName %))))


(defn- next-migration-number
  [file-names]
  (if (empty? file-names)
    "0000"
    ; TODO: define next migration number
    "0000"))


(defn- next-migration-name
  [actions]
  (let [action-name (-> actions first :action name)
        model-name (-> actions first :name name)]
    (-> (str action-name "_" model-name)
      (str/replace #"-" "_"))))


(defn make-migrations
  "Make new migrations based on models' definitions automatically."
  [{:keys [model-file migrations-dir] :as _args}]
  ; TODO: uncomment!
  ;(let [new-model (first (models model-file))
  ;      migration (s/conform ::models/->migration new-model)
  ;      _ (create-migrations-dir migrations-dir)
  ;      migration-names (migrations-list migrations-dir)]
    ;    migration-number (next-migration-number migration-names)
    ;    migration-name (next-migration-name [migration])
    ;    migration-file-name (str migration-number "_" migration-name)
    ;    migration-file-name-full-path (str migrations-dir "/" migration-file-name ".edn")]
    ;(spit migration-file-name-full-path
    ;  (with-out-str
    ;    (pprint/pprint [migration])))))

  (let [migrations-files (util-file/list-files migrations-dir)]
    (schema/current-db-schema migrations-files)))


(defn- sql
  "Generate raw sql from migration."
  [{:keys [migrations-dir]}]
  (let [migration-names (migrations-list migrations-dir)
        file-name (first migration-names)
        actions (read-migration file-name migrations-dir)
        action (first actions)]
    (s/conform ::sql/action->sql action)))


(defn migrate
  "Run migration on a db."
  [{:keys [migrations-dir]}]
  (let [migration-names (migrations-list migrations-dir)
        file-name (first migration-names)
        migration-name (first (str/split file-name #"\."))
        actions (read-migration file-name migrations-dir)
        action (first actions)
        migration-sql (s/conform ::sql/action->sql action)]
    (create-migrations-table)
    (jdbc/with-db-transaction [tx (db-conn)]
      (jdbc/execute! tx migration-sql)
      (save-migration migration-name))))


; Public

(s/def :args/model-file string?)
(s/def :args/migrations-dir string?)


(s/def ::make-migrations-args
  (s/keys
    :req-un [:args/model-file
             :args/migrations-dir]))


(defn run
  "Main exec function with dispatcher for all commands."
  [{:keys [action] :as args}]
  (s/valid? ::make-migrations-args args)
  (let [action-fn (case action
                    :make-migrations make-migrations
                    :migrate migrate)]
    (action-fn (dissoc args :action))))


(comment
  (let [config {:model-file "src/tuna/models.edn"
                :migrations-dir "src/tuna/migrations"}]
    ;(s/explain ::models (models))
    ;(s/valid? ::models (models))
    ;(s/conform ::->migration (first (models)))))
    ;MIGRATIONS-TABLE))
    (make-migrations config)))
    ;(migrate config)))

;(table-exists? "migrations")))

;(->> (get-in action [:model :fields])
;  (reduce (fn [acc [k v]]
;            (conj acc (->> (vals v)
;                        (cons k)
;                        (vec)))) []))))


;(conj acc [k (vals v)])) [])))

;(-> {:create-table [:feed]
;     :with-columns [[[:id (hsql/call :serial) (hsql/call :not nil)]]]}
;    (hsql/format))))

;(-> (phsql/create-table :films)
;    (phsql/with-columns [[:code (hsql/call :char 5) (hsql/call :constraint :firstkey) (hsql/call :primary-key)]
;                         [:title (hsql/call :varchar 40) (hsql/call :not nil)]
;                         [:did :integer (hsql/call :not nil)]
;                         [:date_prod :date]
;                         [:kind (hsql/call :varchar 10)]])
;    hsql/format)))
;    (hsql/raw :serial)))
