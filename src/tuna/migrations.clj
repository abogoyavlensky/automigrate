(ns tuna.migrations
  "Module for applying changes to migrations and db.
  Also contains tools for inspection of db state by migrations
  and state of migrations itself."
  (:require [next.jdbc :as jdbc]
            #_{:clj-kondo/ignore [:unused-namespace]}
            [clojure.spec.alpha :as s]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.set :as set]
            [clojure.pprint :as pprint]
            #_{:clj-kondo/ignore [:unused-referred-var]}
            [slingshot.slingshot :refer [throw+ try+]]
            [differ.core :as differ]
            [weavejester.dependency :as dep]
            [tuna.actions :as actions]
            [tuna.models :as models]
            [tuna.sql :as sql]
            [tuna.schema :as schema]
            [tuna.util.file :as file-util]
            [tuna.util.db :as db-util]
            [tuna.util.spec :as spec-util]
            [tuna.util.model :as model-util]))


(def ^:private DROPPED-ENTITY-VALUE 0)
(def ^:private DEFAULT-ROOT-NODE :root)
(def ^:private AUTO-MIGRATION-PREFIX "auto")
(def ^:private FORWARD-DIRECTION :forward)
(def ^:private BACKWARD-DIRECTION :backward)


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


(defn- get-migration-name
  "Return migration name without file format."
  [file-name]
  (first (str/split file-name #"\.")))


(defn- get-migration-number
  [migration-name]
  (-> (str/split migration-name #"_")
    (first)
    (Integer/parseInt)))


(defn- get-migration-type
  "Return migration type by migration file extension."
  [migration-name]
  (-> (str/split migration-name #"\.")
    (last)
    (keyword)))


(defn- validate-migration-numbers
  [migrations]
  (let [duplicated-numbers (->> migrations
                             (map get-migration-number)
                             (frequencies)
                             (filter #(> (val %) 1))
                             (keys)
                             (set))]
    (when (seq duplicated-numbers)
      (throw+ {:type ::duplicated-migration-numbers
               :numbers duplicated-numbers
               :message (str "There are duplicated migrations' numbers: "
                          (str/join ", " duplicated-numbers)
                          ". Please resolve the conflict and try again.")}))
    migrations))


(defn- migrations-list
  "Get migrations' files list."
  [migrations-dir]
  (->> (file-util/list-files migrations-dir)
    (map #(.getName %))
    (sort)
    (validate-migration-numbers)))


(defn- next-migration-number
  [file-names]
  ; migrations' numbers starting from 1
  (file-util/zfill (inc (count file-names))))


(defn- extract-item-name
  [action]
  (condp contains? (:action action)
    #{actions/CREATE-TABLE-ACTION
      actions/DROP-TABLE-ACTION} (:model-name action)
    #{actions/ADD-COLUMN-ACTION
      actions/DROP-COLUMN-ACTION
      actions/ALTER-COLUMN-ACTION} (:field-name action)
    #{actions/CREATE-INDEX-ACTION
      actions/DROP-INDEX-ACTION
      actions/ALTER-INDEX-ACTION} (:index-name action)))


(defn- next-migration-name
  [actions]
  (let [action (-> actions first)
        action-name (-> action :action name)
        item-name (-> action extract-item-name name)]
    (-> (str/join #"_" [AUTO-MIGRATION-PREFIX action-name item-name])
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


(defn- assoc-option-to-add
  [old-field changes option-key new-option-value]
  (let [old-option-value (if (contains? old-field option-key)
                           (get old-field option-key)
                           model-util/EMPTY-OPTION)]
    (-> changes
      (assoc-in [option-key :from] old-option-value)
      (assoc-in [option-key :to] new-option-value))))


(defn- assoc-option-to-drop
  [old-field changes option-key]
  (-> changes
    (assoc-in [option-key :from] (get old-field option-key))
    (assoc-in [option-key :to] model-util/EMPTY-OPTION)))


(defn- get-changes
  [old-options options-to-add options-to-drop]
  (as-> {} $
        (reduce-kv (partial assoc-option-to-add old-options) $ options-to-add)
        (reduce (partial assoc-option-to-drop old-options) $ options-to-drop)))


(defn- parse-fields-diff
  "Return field's migrations for model."
  [{:keys [model-diff removals old-model new-model model-name]}]
  (let [fields-diff (:fields model-diff)
        fields-removals (:fields removals)
        changed-fields (-> (set (keys fields-diff))
                         (set/union (set (keys fields-removals))))]
    (for [field-name changed-fields
          :let [options-to-add (get fields-diff field-name)
                options-to-drop (get fields-removals field-name)
                new-field?* (new-field? old-model fields-diff field-name)
                drop-field?* (drop-field? fields-removals field-name)
                field-options-old (get-in old-model [:fields field-name])
                field-options-new (get-in new-model [:fields field-name])]]
      (cond
        new-field?* {:action actions/ADD-COLUMN-ACTION
                     :field-name field-name
                     :model-name model-name
                     :options options-to-add}
        drop-field?* {:action actions/DROP-COLUMN-ACTION
                      :field-name field-name
                      :model-name model-name}
        :else {:action actions/ALTER-COLUMN-ACTION
               :field-name field-name
               :model-name model-name
               :options field-options-new
               :changes (get-changes field-options-old
                          options-to-add
                          (options-dropped options-to-drop))}))))


(defn- new-model?
  [alterations old-schema model-name]
  (and (contains? alterations model-name)
    (not (contains? old-schema model-name))))


(defn- drop-model?
  [removals model-name]
  (= DROPPED-ENTITY-VALUE (get removals model-name)))


(defn- read-models
  "Read and validate models from file."
  [model-file]
  (->> model-file
    (file-util/read-edn)
    (spec-util/conform ::models/->internal-models)))


(defn- action-dependencies
  "Return dependencies as vector of vectors for an action or nil.

  return: [[:model-name :field-name] ...]"
  [action]
  (let [changes-to-add (model-util/changes-to-add (:changes action))
        fk (condp contains? (:action action)
             #{actions/ADD-COLUMN-ACTION} (get-in action [:options :foreign-key])
             #{actions/ALTER-COLUMN-ACTION} (:foreign-key changes-to-add)
             nil)]
    (->> (condp contains? (:action action)
           #{actions/ADD-COLUMN-ACTION
             actions/ALTER-COLUMN-ACTION
             actions/DROP-COLUMN-ACTION} (cond-> [[(:model-name action) nil]]
                                           (some? fk) (conj (model-util/kw->vec fk)))
           #{actions/CREATE-TABLE-ACTION} (mapv (comp model-util/kw->vec :foreign-key)
                                            (vals (:fields action)))
           #{actions/CREATE-INDEX-ACTION
             actions/ALTER-INDEX-ACTION} (mapv (fn [field] [(:model-name action) field])
                                           (get-in action [:options :fields]))
           [])
      (remove nil?))))


(defn- parent-action?
  "Check if action is parent to one with presented dependencies."
  [deps action]
  (let [model-names (set (map first deps))]
    (condp contains? (:action action)
      #{actions/CREATE-TABLE-ACTION} (contains? model-names (:model-name action))
      #{actions/ADD-COLUMN-ACTION
        actions/ALTER-COLUMN-ACTION} (some
                                       #(and (= (:model-name action) (first %))
                                          (= (:field-name action) (last %)))
                                       deps)
      false)))


(defn- assoc-action-deps
  "Assoc dependencies to graph by actions."
  [actions graph next-action]
  (let [deps (action-dependencies next-action)
        parent-actions (filter (partial parent-action? deps) actions)]
    (as-> graph g
          (dep/depend g next-action DEFAULT-ROOT-NODE)
          (reduce #(dep/depend %1 next-action %2) g parent-actions))))


(defn- sort-actions
  "Apply order for migration's actions by foreign key between models."
  [actions]
  (->> actions
    (reduce (partial assoc-action-deps actions) (dep/graph))
    (dep/topo-sort)
    ; drop first default root node `:root`
    (drop 1)))


(defn- new-index?
  [old-model indexes-diff index-name]
  (and (contains? indexes-diff index-name)
    (not (contains? (:indexes old-model) index-name))))


(defn- drop-index?
  [indexes-removals index-name]
  (= DROPPED-ENTITY-VALUE (get indexes-removals index-name)))


(defn- parse-indexes-diff
  "Return index's migrations for model."
  [model-diff removals old-model new-model model-name]
  (let [indexes-diff (:indexes model-diff)
        indexes-removals (if (= DROPPED-ENTITY-VALUE (:indexes removals))
                           (->> (:indexes old-model)
                             (reduce-kv (fn [m k _v] (assoc m k DROPPED-ENTITY-VALUE)) {}))
                           (:indexes removals))
        changed-indexes (-> (set (keys indexes-diff))
                          (set/union (set (keys indexes-removals))))]
    (for [index-name changed-indexes
          :let [options-to-add (get indexes-diff index-name)
                options-to-alter (get-in new-model [:indexes index-name])
                new-index?* (new-index? old-model indexes-diff index-name)
                drop-index?* (drop-index? indexes-removals index-name)]]
      (cond
        new-index?* {:action actions/CREATE-INDEX-ACTION
                     :index-name index-name
                     :model-name model-name
                     :options options-to-add}
        drop-index?* {:action actions/DROP-INDEX-ACTION
                      :index-name index-name
                      :model-name model-name}
        :else {:action actions/ALTER-INDEX-ACTION
               :index-name index-name
               :model-name model-name
               :options options-to-alter}))))


(defn- make-migrations*
  [migrations-files model-file]
  (let [old-schema (schema/current-db-schema migrations-files)
        new-schema (read-models model-file)
        [alterations removals] (differ/diff old-schema new-schema)
        changed-models (-> (set (keys alterations))
                         (set/union (set (keys removals))))
        actions (for [model-name changed-models
                      :let [old-model (get old-schema model-name)
                            new-model (get new-schema model-name)
                            model-diff (get alterations model-name)
                            model-removals (get removals model-name)
                            new-model?* (new-model? alterations old-schema model-name)
                            drop-model?* (drop-model? removals model-name)]]
                  (concat
                    (cond
                      new-model?* [{:action actions/CREATE-TABLE-ACTION
                                    :model-name model-name
                                    :fields (:fields model-diff)}]
                      drop-model?* [{:action actions/DROP-TABLE-ACTION
                                     :model-name model-name}]
                      :else (parse-fields-diff {:model-diff model-diff
                                                :removals model-removals
                                                :old-model old-model
                                                :new-model new-model
                                                :model-name model-name}))
                    (parse-indexes-diff model-diff model-removals old-model new-model model-name)))]
    (->> actions
      (flatten)
      (sort-actions)
      (map #(spec-util/conform ::actions/->migration %)))))


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


(defn- get-migration-by-number
  "Return migration file name by number.

  migration-names [<str>]
  number: <str>"
  [migration-names number]
  ; TODO: add args validation!
  (->> migration-names
    (filter #(= number (get-migration-number %)))
    (first)))


(defn explain
  "Generate raw sql from migration."
  [{:keys [migrations-dir number] :as _args}]
  (let [migration-names (migrations-list migrations-dir)
        file-name (get-migration-by-number migration-names number)]
    (when-not (some? file-name)
      (throw+ {:type ::no-migration-by-number
               :number number}))
    (file-util/safe-println
      [(format "SQL for migration %s:\n" file-name)])
    (->> (read-migration file-name migrations-dir)
      (mapv sql/->sql)
      ; TODO: maybe remove!?
      (flatten)
      (file-util/safe-println))))


(defn- already-migrated
  "Get names of previously migrated migrations from db."
  [db]
  (->> {:select [:name]
        :from [db-util/MIGRATIONS-TABLE]
        :order-by [:created-at]}
    (db-util/exec! db)
    (map :name)))


(defn- exec-action!
  "Perform action on a database."
  [db action]
  (let [formatted-action (spec-util/conform ::sql/->sql action)]
    (if (sequential? formatted-action)
      (doseq [sub-action formatted-action]
        (db-util/exec! db sub-action))
      (db-util/exec! db formatted-action))))


(defn- exec-actions!
  "Perform list of actions on a database."
  [db actions]
  (doseq [action actions]
    (exec-action! db action)))


(defn- current-migration-number
  "Return current migration name."
  [migrated]
  (if (seq migrated)
    (let [res (->> (last migrated)
                (get-migration-number))]
      res)
    0))


(defn- detailed-migration
  "Return detailed info for each migration file."
  [file-name]
  {:file-name file-name
   :migration-name (get-migration-name file-name)
   :number-int (get-migration-number file-name)
   :migration-type (get-migration-type file-name)})


(defn- get-migrations-to-migrate
  "Return migrations to migrate and migration direction."
  [all-migrations migrated target-number]
  (let [all-migrations-detailed (map detailed-migration all-migrations)
        all-numbers (set (map :number-int all-migrations-detailed))
        last-number (apply max all-numbers)
        target-number* (or target-number last-number)
        current-number (current-migration-number migrated)
        direction (if (> target-number* current-number)
                    FORWARD-DIRECTION
                    BACKWARD-DIRECTION)]
    (when-not (contains? all-numbers target-number*)
      (throw+ {:type ::missing-target-migration-number
               :number target-number*
               :message "Missing target migration number"}))
    (if (= target-number* current-number)
      []
      (condp contains? direction
        #{FORWARD-DIRECTION} (->> all-migrations-detailed
                               (drop-while #(>= current-number (:number-int %)))
                               (take-while #(>= target-number* (:number-int %))))
        #{BACKWARD-DIRECTION} (->> all-migrations-detailed
                                (drop-while #(>= target-number* (:number-int %)))
                                (take-while #(>= current-number (:number-int %)))
                                (sort-by :number-int >))))))


(defn migrate
  "Run migration on a db."
  [{:keys [migrations-dir db-uri number]}]
  (let [db (db-util/db-conn db-uri)
        _ (db-util/create-migrations-table db)
        migrated (already-migrated db)
        all-migrations (migrations-list migrations-dir)
        to-migrate (get-migrations-to-migrate all-migrations migrated number)]
    (if (seq to-migrate)
      (doseq [{:keys [migration-name file-name]} to-migrate]
        (jdbc/with-transaction [tx db]
          (let [actions (read-migration file-name migrations-dir)]
            (exec-actions! tx actions))
          (save-migration db migration-name)
          (println "Successfully migrated: " migration-name)))
      (println "Noting to migrate."))))


(defn list-migrations
  "Print migration list with status."
  [{:keys [migrations-dir db-uri]}]
  ; TODO: reduce duplication with `migrate` fn!
  (let [migration-names (migrations-list migrations-dir)
        db (db-util/db-conn db-uri)
        _ (db-util/create-migrations-table db)
        migrated (set (already-migrated db))]
    (doseq [file-name migration-names
            :let [migration-name (get-migration-name file-name)
                  sign (if (contains? migrated migration-name) "✓" " ")]]
      (file-util/safe-println [(format "[%s] %s" sign file-name)]))))


(comment
  (let [config {:model-file "src/tuna/models.edn"
                ;:model-file "test/tuna/models/feed_add_column.edn"
                :migrations-dir "src/tuna/migrations"
                ;:migrations-dir "test/tuna/migrations"
                :db-uri "jdbc:postgresql://localhost:5432/tuna?user=tuna&password=tuna"
                :number 4}
        db (db-util/db-conn (:db-uri config))
        migrations-files (file-util/list-files (:migrations-dir config))
        model-file (:model-file config)]
      (try+
        (->> (read-models model-file))
        ;(->> (make-migrations* migrations-files model-file))
        ;     (flatten))

         ;(map #(spec-util/conform ::sql/->sql %)))
         ;(flatten)
         ;(map db-util/fmt))
         ;(map #(db-util/exec! db %)))
        (catch [:type ::s/invalid] e
          (:data e)))))


(comment
  (let [config {:model-file "src/tuna/models.edn"
                :migrations-dir "src/tuna/migrations"
                :db-uri "jdbc:postgresql://localhost:5432/tuna?user=tuna&password=tuna"
                :number 17}
        db (db-util/db-conn (:db-uri config))]
    ;(s/explain ::models (models))
    ;(s/valid? ::models (models))
    ;(s/conform ::->migration (first (models)))))
    ;MIGRATIONS-TABLE))
    ;(make-migrations config)))
    ;(migrate config)))
    ;(explain config)))
    (list-migrations config)))


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
    ;    (spec-util/conform ::action/->migration)
    ;    (spec-util/conform ::sql/->sql)
    ;    ;(db-util/fmt))
    ;    (db-util/exec! db))
    ;  (catch [:type ::s/invalid] e
    ;    (:data e)))))

    (->> {:alter-table :feed
            ;:alter-column [:name :type [:varchar 10]]}

            ;:alter-column [:name :set [:not nil]]}
            ;:alter-column [:name :drop [:not nil]]}

            ;:alter-column [:name :set [:default "test"]]}
            ;:alter-column [:name :drop :default]}

            ;:add-index [:unique nil :name]}
            ;:drop-constraint (keyword (str/join #"-" [(name :feed) (name :name) "key"]))}

            ;:add-index [:primary-key :name]
            ;:drop-constraint (keyword (str/join #"-" [(name :feed) "pkey"]))}

             ;:add-index [:constraint :feed-account-fkey [:foreign-key :id] [:references :account :id]]}
             ;:add-index [[:constraint :feed-account-fkey] [:foreign-key :id] [:references :account :id]]}
             :add-constraint [:feed-account-fkey [:foreign-key :id] [:references :account :id] [:raw "on delete"] :set-null]}


        ;{:create-index [:some-index-idx :on :feed :using [:btree :name :id]]}
        ;{:create-unique-index [:some-index-idx :on :feed :using [:btree :name :id]]}
        ;{:drop-index :some-index-idx}
        (db-util/fmt))))
;(db-util/exec! db)))))
