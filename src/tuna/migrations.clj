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
            [differ.core :as differ]
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
  ([]
   (db-conn nil))
  ([db-url]
   (let [db-uri (or db-url
                  (System/getenv "DATABASE_URL")
                  ; TODO: remove defaults!
                  "jdbc:postgresql://localhost:5432/tuna?user=tuna&password=tuna")]
     {:connection-uri db-uri})))


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
  (util-file/zfill (count file-names)))


(defn- next-migration-name
  [actions]
  (let [action-name (-> actions first :action name)
        model-name (-> actions first :name name)]
    (-> (str action-name "_" model-name)
      (str/replace #"-" "_"))))


(defn- make-migrations*
  [migrations-files model-file]
  (let [old-schema (schema/current-db-schema migrations-files)
        new-schema (models model-file)
        [alterations _removals] (differ/diff old-schema new-schema)]
    (for [model alterations
          :let [model-name (key model)]]
      (when-not (contains? old-schema model-name)
        [(s/conform ::models/->migration model)]))))


(defn make-migrations
  "Make new migrations based on models' definitions automatically."
  [{:keys [model-file migrations-dir] :as _args}]
  ; TODO: remove second level of let!
  (let [migrations-files (util-file/list-files migrations-dir)
        migrations (-> (make-migrations* migrations-files model-file)
                       (flatten))]
    (if (seq migrations)
      (let [_ (create-migrations-dir migrations-dir)
            migration-names (migrations-list migrations-dir)
            migration-number (next-migration-number migration-names)
            migration-name (next-migration-name migrations)
            migration-file-name (str migration-number "_" migration-name)
            migration-file-name-full-path (str migrations-dir "/" migration-file-name ".edn")]
        (spit migration-file-name-full-path
          (with-out-str
            (pprint/pprint migrations)))
        (println (str "Created migration: " migration-file-name)))
        ; TODO: print all changes from migration
      ; TODO: use some special tool for printing to console
      (println "There are no changes in models."))))


(defn- sql
  "Generate raw sql from migration."
  [{:keys [migrations-dir]}]
  (let [migration-names (migrations-list migrations-dir)
        file-name (first migration-names)
        actions (read-migration file-name migrations-dir)
        action (first actions)]
    (s/conform ::sql/action->sql action)))


(defn- already-migrated
  "Get names of previously migrated migrations from db."
  []
  (->> {:select [:name]
        :from [MIGRATIONS-TABLE]}
       (hsql/format)
       (jdbc/query (db-conn))
       (map :name)
       (set)))


(defn migrate
  "Run migration on a db."
  [{:keys [migrations-dir]}]
  (create-migrations-table)
  (let [migration-names (migrations-list migrations-dir)
        migrated (already-migrated)]
    ; TODO: print if nothing to migrate!
    (doseq [file-name migration-names
            :let [migration-name (first (str/split file-name #"\."))]]
      (when-not (contains? migrated migration-name)
        (jdbc/with-db-transaction [tx (db-conn)]
          (doseq [action (read-migration file-name migrations-dir)]
            (->> (s/conform ::sql/action->sql action)
              (jdbc/execute! tx)))
          (save-migration migration-name)
          (println "Successfully migrated: " migration-name))))))


(comment
  (let [config {:model-file "src/tuna/models.edn"
                :migrations-dir "src/tuna/migrations"}]
    ;(s/explain ::models (models))
    ;(s/valid? ::models (models))
    ;(s/conform ::->migration (first (models)))))
    ;MIGRATIONS-TABLE))
    ;(make-migrations config)))
    (migrate config)))

    ;(create-migrations-table)

    ;(already-migrated)))

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
