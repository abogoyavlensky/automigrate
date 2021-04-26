(ns tuna.migrations
  "Module for applying changes to migrations and db.
  Also contains tools for inspection of db state by migrations
  and state of migrations itself."
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.set :as set]
            [clojure.pprint :as pprint]
            #_{:clj-kondo/ignore [:unused-referred-var]}
            [slingshot.slingshot :refer [throw+ try+]]
            [differ.core :as differ]
            [tuna.models :as models]
            [tuna.sql :as sql]
            [tuna.schema :as schema]
            [tuna.util.file :as file-util]
            [tuna.util.db :as db-util]
            [tuna.util.spec :as spec-util]))


(def ^:private DROPPED-ENTITY-VALUE 0)


(defn- read-migration
  "Return models' definitions."
  [file-name migrations-dir]
  (-> (str migrations-dir "/" file-name)
    (file-util/read-edn)))


(defn- create-migrations-dir
  "Create migrations root dir if it is not exist."
  [migrations-dir]
  (when-not (.isDirectory (io/file migrations-dir))
    (.mkdir (java.io.File. migrations-dir))))


(defn- save-migration
  "Save migration to db after applying it."
  [db migration-name]
  (->> {:insert-into db-util/MIGRATIONS-TABLE
        :values [{:name migration-name}]}
    (db-util/exec! db)))


(defn- migrations-list
  "Get migrations' files list."
  [migrations-dir]
  (->> (file-util/list-files migrations-dir)
    (map #(.getName %))
    (sort)))


(defn- next-migration-number
  [file-names]
  (file-util/zfill (count file-names)))


(defn- next-migration-name
  [actions]
  (let [action-name (-> actions first :action name)
        model-name (-> actions first :name name)]
    (-> (str action-name "_" model-name)
      (str/replace #"-" "_"))))


(defn- new-field?
  [old-model fields-diff field-name]
  (and (contains? fields-diff field-name)
    (not (contains? (:fields old-model) field-name))))


(defn- drop-field?
  [fields-removals field-name]
  (= DROPPED-ENTITY-VALUE (get fields-removals field-name)))


(defn- options-dropped
  [removals]
  (-> (filter #(= DROPPED-ENTITY-VALUE (val %)) removals)
    (keys)
    (set)
    (set/difference #{:type})))


(defn- parse-fields-diff
  "Return field's migrations for model."
  [model-diff removals old-model model-name]
  (let [fields-diff (:fields model-diff)
        fields-removals (:fields removals)
        changed-fields (-> (set (keys fields-diff))
                         (set/union (set (keys fields-removals))))]
    (for [field-name changed-fields
          :let [options-to-add (get fields-diff field-name)
                options-to-drop (get fields-removals field-name)
                new-field?* (new-field? old-model fields-diff field-name)
                drop-field?* (drop-field? fields-removals field-name)
                action-params (cond
                                new-field?* {:action models/ADD-COLUMN-ACTION
                                             :name field-name
                                             :table-name model-name
                                             :options options-to-add}
                                drop-field?* {:action models/DROP-COLUMN-ACTION
                                              :name field-name
                                              :table-name model-name}
                                :else {:action models/ALTER-COLUMN-ACTION
                                       :name field-name
                                       :table-name model-name
                                       :changes options-to-add
                                       :drop (options-dropped options-to-drop)})]]
      (spec-util/conform ::models/->migration action-params))))


(defn- new-model?
  [alterations old-schema model-name]
  (and (contains? alterations model-name)
    (not (contains? old-schema model-name))))


(defn- drop-model?
  [removals model-name]
  (= DROPPED-ENTITY-VALUE (get removals model-name)))


(defn- make-migrations*
  [migrations-files model-file]
  (let [old-schema (schema/current-db-schema migrations-files)
        new-schema (file-util/read-edn model-file)
        [alterations removals] (differ/diff old-schema new-schema)
        changed-models (-> (set (keys alterations))
                         (set/union (set (keys removals))))]
    (for [model-name changed-models
          :let [old-model (get old-schema model-name)
                model-diff (get alterations model-name)
                model-removals (get removals model-name)
                new-model?* (new-model? alterations old-schema model-name)
                drop-model?* (drop-model? removals model-name)
                action-params (cond
                                new-model?* {:action models/CREATE-TABLE-ACTION
                                             :name model-name
                                             :fields (:fields model-diff)}
                                drop-model?* {:action models/DROP-TABLE-ACTION
                                              :name model-name}
                                :else nil)]]
      (if (some? action-params)
        (spec-util/conform ::models/->migration action-params)
        (parse-fields-diff model-diff model-removals old-model model-name)))))


(defn make-migrations
  "Make new migrations based on models' definitions automatically."
  [{:keys [model-file migrations-dir] :as _args}]
  ; TODO: remove second level of let!
  (let [migrations-files (file-util/list-files migrations-dir)
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


(defn- migration-number
  [migration-name]
  (first (str/split migration-name #"_")))


(defn- get-migration-by-number
  "Return migration file name by number.

  migration-names [<str>]
  number: <str>"
  [migration-names number]
  ; TODO: add args validation!
  (->> migration-names
    (filter #(= number (migration-number %)))
    (first)))


(defn explain
  "Generate raw sql from migration."
  [{:keys [migrations-dir number] :as _args}]
  (let [number* (file-util/zfill number)
        migration-names (migrations-list migrations-dir)
        file-name (get-migration-by-number migration-names number*)]
    (when-not (some? file-name)
      (throw+ {:type ::no-migration-by-number
               :number number}))
    (file-util/safe-println
      [(format "SQL for migration %s:\n" file-name)])
    (->> (read-migration file-name migrations-dir)
      (mapv  (comp db-util/fmt #(s/conform ::sql/->sql %)))
      (flatten)
      (file-util/safe-println))))


(defn- already-migrated
  "Get names of previously migrated migrations from db."
  [db]
  (->> {:select [:name]
        :from [db-util/MIGRATIONS-TABLE]}
    (db-util/query db)
    (map :name)
    (set)))


(defn migrate
  "Run migration on a db."
  [{:keys [migrations-dir db-uri]}]
  (let [migration-names (migrations-list migrations-dir)
        db (db-util/db-conn db-uri)
        _ (db-util/create-migrations-table db)
        migrated (already-migrated db)]
    ; TODO: print if nothing to migrate!
    (doseq [file-name migration-names
            :let [migration-name (first (str/split file-name #"\."))]]
      (when-not (contains? migrated migration-name)
        (jdbc/with-db-transaction [tx db]
          (doseq [action (read-migration file-name migrations-dir)]
            (->> (spec-util/conform ::sql/->sql action)
              (db-util/exec! tx)))
          (save-migration db migration-name)
          (println "Successfully migrated: " migration-name))))))


(comment
  (let [config {:model-file "src/tuna/models.edn"
                :migrations-dir "src/tuna/migrations"
                :db-uri "jdbc:postgresql://localhost:5432/tuna?user=tuna&password=tuna"
                :number 4}
        db (db-util/db-conn (:db-uri config))
        migrations-files (file-util/list-files (:migrations-dir config))
        model-file (:model-file config)]
      (try+
        (->> (make-migrations* migrations-files model-file)
             (flatten)
             (map #(spec-util/conform ::sql/->sql %))
             ;(map db-util/fmt))
             (map #(db-util/exec! db %)))
        (catch [:type ::s/invalid] e
          (:data e)))))


(comment
  (let [config {:model-file "src/tuna/models.edn"
                :migrations-dir "src/tuna/migrations"
                :db-uri "jdbc:postgresql://localhost:5432/tuna?user=tuna&password=tuna"
                :number 4}]
    ;(s/explain ::models (models))
    ;(s/valid? ::models (models))
    ;(s/conform ::->migration (first (models)))))
    ;MIGRATIONS-TABLE))
    ;(make-migrations config)))
    (migrate config)))
    ;(explain config)))



(comment
  (spec-util/conform ::models/->migration
    {:action models/ADD-COLUMN-ACTION
     :name :name
     :field {:null true, :type [:varchar 100]}}))

; TODO: remove!
;[honeysql-postgres.format :as phformat]
;[honeysql-postgres.helpers :as phsql]


;(-> (phsql/create-table :films)
;    (phsql/with-columns [[:code (hsql/call :char 5) (hsql/call :constraint :firstkey) (hsql/call :primary-key)]
;                         [:title (hsql/call :varchar 40) (hsql/call :not nil)]
;                         [:did :integer (hsql/call :not nil)]
;                         [:date_prod :date]
;                         [:kind (hsql/call :varchar 10)]])
;    hsql/format)))
;    (hsql/raw :serial)))


; TODO: remove!
(comment
  (let [db (db-util/db-conn "jdbc:postgresql://localhost:5432/tuna?user=tuna&password=tuna")
        model [:feed
               {:fields {:id {:type :integer
                              :null false
                              :primary-key true
                              :default 1
                              :unique true}
                         :name {:type [:varchar 100]
                                :null false}
                         :created_at {:type :timestamp
                                      :default [:now]}
                         :is_active {:type :boolean}
                         :opts {:type :jsonb}}}]]
    ;(try+
    ;  (->> model
    ;    ;(s/valid? ::models/model)
    ;    (spec-util/conform ::models/->migration)
    ;    (spec-util/conform ::sql/->sql)
    ;    ;(db-util/fmt))
    ;    (db-util/exec! db))
    ;  (catch [:type ::s/invalid] e
    ;    (:data e)))))

    (->> {:alter-table :feed}
          ;:alter-column [:name :type [:varchar 10]]}

          ;:alter-column [:name :set [:not nil]]}
          ;:alter-column [:name :drop [:not nil]]}

          ;:alter-column [:name :set [:default "test"]]}
          ;:alter-column [:name :drop :default]}

          ;:add-index [:unique nil :name]}
          ;:drop-constraint (keyword (str/join #"-" [(name :feed) (name :name) "key"]))}

          ;:add-index [:primary-key :name]
          ;:drop-constraint (keyword (str/join #"-" [(name :feed) "pkey"]))}
        ;(db-util/fmt))))
         (db-util/exec! db))))
