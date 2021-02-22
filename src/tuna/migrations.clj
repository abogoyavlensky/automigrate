(ns tuna.migrations
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.pprint :as pprint]
            [honeysql.core :as hsql]
            [honeysql-postgres.format :as hformat]
            [honeysql-postgres.helpers :as phsql]))

; Config

;(def ^:private MIGRATION-DIR
;  "Root dir for migrations."
;  "src/rhino/migrations")


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
  (let [db-uri (or (System/getenv "RHINO_DB_URL")
                 "jdbc:postgresql://localhost:5432/rhino?user=rhino&password=rhino")]
    {:connection-uri db-uri}))


(defn- table-exists?
  "Check if table exists."
  [table-name]
  (->> {:select [:*]
        :from [:information_schema.tables]
        :where [:and
                [:= :table_name table-name]
                [:= :table_schema "public"]]}
    (hsql/format)
    (jdbc/query (db-conn))
    (seq)
    (some?)))


(defn- create-migrations-table
  "Create table to keep migrations history."
  []
  (when-not (table-exists? (name MIGRATIONS-TABLE))
    (->> {:create-table [MIGRATIONS-TABLE]
          :with-columns [[[:id :serial (hsql/call :not nil) (hsql/call :primary-key)]
                          [:name (hsql/call :varchar (hsql/inline 256)) (hsql/call :not nil) (hsql/call :unique)]
                          [:created_at :timestamp (hsql/call :default (hsql/call :now))]]]}
      (hsql/format)
      (jdbc/execute! (db-conn)))))


(defn- save-migration
  "Save migration to db after applying it."
  [migration-name]
  (->> {:insert-into MIGRATIONS-TABLE
        :values [{:name migration-name}]}
    (hsql/format)
    (jdbc/execute! (db-conn))))


; Spec

(s/def :field/type #{:int
                     :serial
                     :varchar
                     :text
                     :timestamp})


(s/def :field/null boolean?)
(s/def :field/primary boolean?)
(s/def :field/max-length pos-int?)


(s/def ::field
  (s/keys
    :req-un [:field/type]
    :opt-un [:field/null
             :field/primary
             :field/max-length]))


(s/def :model/fields
  (s/map-of keyword? ::field))


(s/def ::model
  (s/keys
    :req-un [:model/fields]))


(s/def ::models
  (s/map-of keyword? ::model))


(s/def ::model->action
  (s/conformer
    (fn [value]
      (assoc value :action :create-table))))


(s/def ::->action
  (s/and
    (s/cat
      :name keyword?
      :model ::model)
    ::model->action))


(s/def ::->migration
  (s/and
    ::->action))


(defn- migrations-list
  "Get migrations' files list."
  [migrations-dir]
  (->> (file-seq (io/file migrations-dir))
    (filter #(.isFile %))
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

(s/def :args/model-file string?)
(s/def :args/migrations-dir string?)


(s/def ::make-migrations-args
  (s/keys
    :req-un [:args/model-file
             :args/migrations-dir]))


(defn make-migrations
  "Make new migrations based on models' definitions automatically."
  [args]
  (s/valid? ::make-migrations-args args)
  (let [new-model (first (models (:model-file args)))
        migration (s/conform ::->migration new-model)
        migrations-dir (:migrations-dir args)
        _ (create-migrations-dir migrations-dir)
        migration-names (migrations-list migrations-dir)
        migration-number (next-migration-number migration-names)
        migration-name (next-migration-name [migration])
        migration-file-name (str migration-number "_" migration-name)
        migration-file-name-full-path (str migrations-dir "/" migration-file-name ".edn")]
    (spit migration-file-name-full-path
      (with-out-str
        (pprint/pprint [migration])))))


(s/def :option->sql/type
  (s/conformer
    (fn [value]
      ;(hsql/call value)
      (hsql/raw (name value)))))


(s/def :option->sql/null
  (s/conformer
    (fn [value]
      (if (true? value)
        (hsql/call nil)
        (hsql/call :not nil)))))


(s/def ::options->sql
  (s/keys
    :req-un [:option->sql/type]
    :opt-un [:option->sql/null]))


(s/def :action/action #{:create-table})
(s/def :action/name keyword?)


(s/def :model->/fields
  (s/map-of keyword? ::options->sql))


(s/def :action/model
  (s/keys
    :req-un [:model->/fields]))


(defn- fields->columns
  [fields]
  (->> fields
    (reduce (fn [acc [k v]]
              (conj acc (->> (vals v)
                          (cons k)
                          (vec)))) [])))


(s/def ::create-model->sql
  (s/conformer
    (fn [value]
      {(:action value) [(:name value)]
       :with-columns [(fields->columns (-> value :model :fields))]})))


(s/def ::->sql
  (s/conformer
    #(hsql/format %)))


(s/def ::action->sql
  (s/and
    (s/keys
      :req-un [:action/action
               :action/name
               :action/model])
    ::create-model->sql
    ::->sql))

;(-> {:create-table [(:name action)]
;     :with-columns [[[:id (hsql/raw "serial") (hsql/call :not nil)]]]}
;    (hsql/format)))))


(defn- sql
  "Generate raw sql from migration."
  [{:keys [migrations-dir]}]
  (let [migration-names (migrations-list migrations-dir)
        file-name (first migration-names)
        actions (read-migration file-name migrations-dir)
        action (first actions)]
    (s/conform ::action->sql action)))


(defn migrate
  "Run migration on a db."
  [{:keys [migrations-dir]}]
  (let [migration-names (migrations-list migrations-dir)
        file-name (first migration-names)
        migration-name (first (str/split file-name #"\."))
        actions (read-migration file-name migrations-dir)
        action (first actions)
        migration-sql (s/conform ::action->sql action)]
    (create-migrations-table)
    (jdbc/with-db-transaction [tx (db-conn)]
      (jdbc/execute! tx migration-sql)
      (save-migration migration-name))))

(defn run
  "Main exec function with dispatcher for all commands."
  [{:keys [action] :as args}]
  (let [action-fn (case action
                    :make make-migrations
                    :migrate migrate)]
    (action-fn (dissoc args :action))))


(comment
  (let [x 1]
    ;(s/explain ::models (models))
    ;(s/valid? ::models (models))
    ;(s/conform ::->migration (first (models)))))
    ;(make-migrations {})))
    (migrate {})))

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
