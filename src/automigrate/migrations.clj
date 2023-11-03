(ns automigrate.migrations
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
            [automigrate.actions :as actions]
            [automigrate.models :as models]
            [automigrate.fields :as fields]
            [automigrate.errors :as errors]
            [automigrate.sql :as sql]
            [automigrate.schema :as schema]
            [automigrate.util.file :as file-util]
            [automigrate.util.db :as db-util]
            [automigrate.util.spec :as su]
            [automigrate.util.model :as model-util])
  (:import [java.io FileNotFoundException]))


(def ^:private DROPPED-ENTITY-VALUE 0)
(def ^:private DEFAULT-ROOT-NODE :root)
(def ^:private AUTO-MIGRATION-PREFIX "auto")
(def ^:private AUTO-MIGRATION-POSTFIX "etc")
(def ^:private FORWARD-DIRECTION :forward)
(def ^:private BACKWARD-DIRECTION :backward)
(def ^:private AUTO-MIGRATION-EXT :edn)
(def ^:private SQL-MIGRATION-EXT :sql)
(def ^:private EXPLAIN-FORMAT-SQL :sql)
(def ^:private EXPLAIN-FORMAT-HUMAN :human)
(def EMPTY-SQL-MIGRATION-TYPE :empty-sql)
(def ^:private FORWARD-MIGRATION-DELIMITER "-- FORWARD")
(def ^:private BACKWARD-MIGRATION-DELIMITER "-- BACKWARD")


(def ^:private SQL-MIGRATION-TEMPLATE
  (format "%s\n\n\n%s\n" FORWARD-MIGRATION-DELIMITER BACKWARD-MIGRATION-DELIMITER))


(defn- get-migration-type
  "Return migration type by migration file extension."
  [migration-name]
  (-> (str/split migration-name #"\.")
    (last)
    (keyword)))


(defmulti read-migration #(get-migration-type (:file-name %)))


(defmethod read-migration :default
  ;"Return models' definitions."
  [{:keys [file-name migrations-dir]}]
  (let [migration-file-path (file-util/join-path migrations-dir file-name)]
    (file-util/read-edn migration-file-path)))


(defn- get-forward-sql-migration
  [migration]
  (-> (str/split migration (re-pattern BACKWARD-MIGRATION-DELIMITER))
    (first)
    (str/replace (re-pattern FORWARD-MIGRATION-DELIMITER) "")
    (vector)))


(defn- get-backward-sql-migration
  [migration]
  (-> (str/split migration (re-pattern BACKWARD-MIGRATION-DELIMITER))
    (last)
    (vector)))


(defmethod read-migration SQL-MIGRATION-EXT
  ; Return model definitions.
  [{:keys [file-name migrations-dir]}]
  (-> (file-util/join-path migrations-dir file-name)
    (slurp)
    (vector)))


(defn- create-migrations-dir
  "Create migrations root dir if it is not exist."
  [migrations-dir]
  (when-not (.isDirectory (io/file migrations-dir))
    (.mkdir (java.io.File. migrations-dir))))


(defn- save-migration!
  "Save migration to db after applying it."
  [db migration-name migrations-table]
  (->> {:insert-into migrations-table
        :values [{:name migration-name}]}
    (db-util/exec! db))
  (println (str migration-name " successfully applied.")))


(defn- delete-migration!
  "Delete reverted migration from db."
  [db migration-name migrations-table]
  (->> {:delete-from migrations-table
        :where [:= :name migration-name]}
    (db-util/exec! db))
  (println (str migration-name " successfully reverted.")))


(defn- get-migration-name
  "Return migration name without file format."
  [file-name]
  (first (str/split file-name #"\.")))


(defn- get-migration-number
  [migration-name]
  (-> (str/split migration-name #"_")
    (first)
    (Integer/parseInt)))


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
               :message (str "There are duplicated migration numbers: "
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
  ; migration numbers starting from 1
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
      actions/ALTER-INDEX-ACTION} (:index-name action)
    #{actions/CREATE-TYPE-ACTION
      actions/DROP-TYPE-ACTION
      actions/ALTER-TYPE-ACTION} (:type-name action)))


(defn- get-action-description-vec-basic
  [action]
  (let [action-name (-> action :action name (str/replace #"-" "_") (str/split #"_"))
        item-name (-> action extract-item-name name (str/replace #"-" "_"))]
    (conj action-name item-name)))


(defn- get-action-description-vec-with-table
  [action preposition]
  (let [action-desc-basic (get-action-description-vec-basic action)
        model-name (-> action :model-name name (str/replace #"-" "_"))]
    (conj action-desc-basic preposition model-name)))


(defn- get-action-description-vec
  [action]
  (condp contains? (:action action)
    #{actions/ADD-COLUMN-ACTION} (get-action-description-vec-with-table action "to")
    #{actions/ALTER-COLUMN-ACTION} (get-action-description-vec-with-table action "in")
    #{actions/DROP-COLUMN-ACTION} (get-action-description-vec-with-table action "from")

    #{actions/CREATE-INDEX-ACTION
      actions/ALTER-INDEX-ACTION
      actions/DROP-INDEX-ACTION} (get-action-description-vec-with-table action "on")

    ; default
    (get-action-description-vec-basic action)))


(defn- get-next-migration-name-auto
  [actions]
  (let [first-action (first actions)
        action-desc-vec (get-action-description-vec first-action)
        action-desc-vec* (cond-> (concat [AUTO-MIGRATION-PREFIX] action-desc-vec)
                           (> (count actions) 1) (concat [AUTO-MIGRATION-POSTFIX]))]
    (str/join #"_" action-desc-vec*)))


(defn- get-next-migration-name
  "Return given custom name with underscores or first action name."
  [actions custom-name]
  (let [migration-name (or custom-name (get-next-migration-name-auto actions))]
    (str/replace migration-name #"-" "_")))


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


(defn- options-added
  "Get field options with deletion changes.
  Example:
  - old: `{:type [:decimal 10 2]}`
  - new: `{:type [:decimal 10]}`"
  [{:keys [to-add to-drop new-field]}]
  (reduce-kv
    (fn [m k v]
      (cond-> m
        ; Check diff value from differ not 0, then add new value for option
        ; to be able to see it in `:changes` key of migration action.
        (not= DROPPED-ENTITY-VALUE v) (assoc k (get new-field k))))
    to-add
    to-drop))


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


(defn- get-options-to-add
  "Update option in diff to option from new model if diff is a vector.

  It is a caveat how differ lib works with changes in vectors. For example, it uses
  in case when we change field type [:varchar 100] to [:varchar 200]. In diff we see [1 200]
  cause just second item of a vector has been changed. So for us it is important to have whole
  new type in options to add, and we just copy it from new model."
  [fields-diff field-name new-model]
  (let [field-options-diff (get fields-diff field-name)
        fields-options-new (get-in new-model [:fields field-name])]
    (reduce-kv
      (fn [m k v]
        (if (vector? v)
          (assoc m k (get fields-options-new k))
          (assoc m k v)))
      {}
      field-options-diff)))


(defn- parse-fields-diff
  "Return field's migrations for model."
  [{:keys [model-diff removals old-model new-model model-name]}]
  (let [fields-diff (:fields model-diff)
        fields-removals (:fields removals)
        changed-fields (-> (set (keys fields-diff))
                         (set/union (set (keys fields-removals))))]
    (for [field-name changed-fields
          :let [options-to-add (get-options-to-add fields-diff field-name new-model)
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
                          (options-added
                            {:to-add options-to-add
                             :to-drop options-to-drop
                             :new-field field-options-new})
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
  [models-file]
  (->> models-file
    (file-util/read-edn)
    (models/->internal-models)))


(s/def ::model-name keyword?)
(s/def ::field-name keyword?)
(s/def ::type-name keyword?)


(s/def ::action-dependencies-ret-item
  (s/keys
    :opt-un [::model-name
             ::field-name
             ::type-name]))


(s/def ::action-dependencies-ret
  (s/coll-of ::action-dependencies-ret-item))


(defn- action-dependencies
  "Return dependencies as vector of vectors for an action or nil."
  [action]
  {:pre [(su/assert! map? action)]
   :post [(su/assert! ::action-dependencies-ret %)]}
  (let [changes-to-add (model-util/changes-to-add (:changes action))
        fk (condp contains? (:action action)
             #{actions/ADD-COLUMN-ACTION} (get-in action [:options :foreign-key])
             #{actions/ALTER-COLUMN-ACTION} (:foreign-key changes-to-add)
             nil)
        type-def (condp contains? (:action action)
                   #{actions/ADD-COLUMN-ACTION} (get-in action [:options :type])
                   #{actions/ALTER-COLUMN-ACTION} (:type changes-to-add)
                   nil)]
    (->> (condp contains? (:action action)
           #{actions/ADD-COLUMN-ACTION
             actions/ALTER-COLUMN-ACTION} (cond-> [{:model-name (:model-name action)}]
                                            (some? fk) (conj (model-util/kw->map fk))

                                            (s/valid? ::fields/enum-type type-def)
                                            (conj {:type-name (last type-def)}))
           #{actions/DROP-COLUMN-ACTION} (cond-> [{:model-name (:model-name action)}]
                                           (some? fk) (conj (model-util/kw->map fk)))
           #{actions/CREATE-TABLE-ACTION} (mapv
                                            (fn [field]
                                              (cond
                                                (contains? field :foreign-key)
                                                (-> field
                                                  :foreign-key
                                                  model-util/kw->map)

                                                (s/valid? ::fields/enum-type (:type field))
                                                {:type-name (-> field :type last)}))
                                            (vals (:fields action)))
           #{actions/CREATE-INDEX-ACTION
             actions/ALTER-INDEX-ACTION} (mapv (fn [field]
                                                 {:model-name (:model-name action)
                                                  :field-name field})
                                           (get-in action [:options :fields]))
           #{actions/DROP-TYPE-ACTION} [{:type-name (:type-name action)}]
           [])
      (remove nil?))))


(defn- parent-action?
  "Check if action is parent to one with presented dependencies."
  [old-schema deps next-action action]
  (let [model-names (set (map :model-name deps))
        type-names (set (map :type-name deps))]
    (if (= next-action action)
      false
      (condp contains? (:action action)
        #{actions/CREATE-TABLE-ACTION} (contains? model-names (:model-name action))
        #{actions/ADD-COLUMN-ACTION
          actions/ALTER-COLUMN-ACTION} (some
                                         #(and (= (:model-name action) (:model-name %))
                                            (= (:field-name action) (:field-name %)))
                                         deps)
        ; First, drop enum column, then drop enum.
        #{actions/DROP-COLUMN-ACTION} (let [field-type (get-in old-schema
                                                         [(:model-name action)
                                                          :fields
                                                          (:field-name action)
                                                          :type])]
                                        (and (s/valid? ::fields/enum-type field-type)
                                          (contains? type-names (last field-type))))
        ; First, drop table with enum column, then drop enum.
        #{actions/DROP-TABLE-ACTION} (let [fields (get-in old-schema
                                                    [(:model-name action)
                                                     :fields])
                                           field-types (mapv :type (vals fields))]
                                       (some
                                         #(and (s/valid? ::fields/enum-type %)
                                            (contains? type-names (last %)))
                                         field-types))
        ; First, create/alter enum type, then add/alter column/table
        #{actions/CREATE-TYPE-ACTION
          actions/ALTER-TYPE-ACTION} (contains? type-names (:type-name action))
        false))))


(defn- assoc-action-deps
  "Assoc dependencies to graph by actions."
  [old-schema actions graph next-action]
  (let [deps (action-dependencies next-action)
        parent-actions (filter
                         (partial parent-action? old-schema deps next-action)
                         actions)]
    (as-> graph $
      (dep/depend $ next-action DEFAULT-ROOT-NODE)
      (reduce #(dep/depend %1 next-action %2) $ parent-actions))))


(defn- compare-actions
  "Secondary comparator for sorting actions in migration the same way."
  [a b]
  (< (hash a) (hash b)))


(defn- sort-actions
  "Apply order for migration's actions by foreign key between models."
  [old-schema actions]
  (->> actions
    (reduce (partial assoc-action-deps old-schema actions) (dep/graph))
    (dep/topo-sort compare-actions)
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


(defn- new-type?
  [old-model types-diff type-name]
  (and (contains? types-diff type-name)
    (not (contains? (:types old-model) type-name))))


(defn- drop-type?
  [types-removals type-name type-from-dropped-model?]
  (or
    (= DROPPED-ENTITY-VALUE (get types-removals type-name))
    type-from-dropped-model?))


(defn- parse-types-diff
  "Return type's migrations for model."
  [{:keys [model-diff model-removals old-model new-model model-name
           type-from-dropped-model?]}]
  ; TODO: abstract this function for types/indexes/fields
  (let [types-diff (:types model-diff)
        types-removals (if (or (= DROPPED-ENTITY-VALUE (:types model-removals))
                             type-from-dropped-model?)
                         (->> (:types old-model)
                           (reduce-kv (fn [m k _v] (assoc m k DROPPED-ENTITY-VALUE)) {}))
                         (:types model-removals))
        changed-types (-> (set (keys types-diff))
                        (set/union (set (keys types-removals))))]
    (for [type-name changed-types
          :let [options-to-add (get types-diff type-name)
                new-type?* (new-type? old-model types-diff type-name)
                drop-type?* (drop-type? types-removals type-name type-from-dropped-model?)
                type-options-old (get-in old-model [:types type-name])
                type-options-new (get-in new-model [:types type-name])]]
      (cond
        new-type?* {:action actions/CREATE-TYPE-ACTION
                    :type-name type-name
                    :model-name model-name
                    :options options-to-add}
        drop-type?* {:action actions/DROP-TYPE-ACTION
                     :type-name type-name
                     :model-name model-name}
        :else {:action actions/ALTER-TYPE-ACTION
               :type-name type-name
               :model-name model-name
               :options type-options-new
               :changes (get-changes
                          type-options-old
                          (options-added
                            {:to-add (dissoc type-options-new :type)})
                          #{})}))))


(defn- make-migration*
  [models-file migrations-files]
  (let [old-schema (schema/current-db-schema migrations-files)
        new-schema (read-models models-file)
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
                    (parse-indexes-diff model-diff model-removals old-model new-model model-name)
                    (parse-types-diff
                      {:model-diff model-diff
                       :model-removals model-removals
                       :old-model old-model
                       :new-model new-model
                       :model-name model-name
                       :type-from-dropped-model? drop-model?*})))]
    (->> actions
      (flatten)
      (sort-actions old-schema)
      (actions/->migrations))))


(defn- get-next-migration-file-name
  "Return next migration file name based on existing migrations."
  [{:keys [migration-type migrations-dir next-migration-name]}]
  (let [migration-names (migrations-list migrations-dir)
        migration-number (next-migration-number migration-names)
        migration-file-name (str migration-number "_" next-migration-name)
        migration-file-with-ext (str migration-file-name "." (name migration-type))]
    (file-util/join-path migrations-dir migration-file-with-ext)))


(defn- auto-migration?
  "Return true if migration has been created automatically false otherwise."
  [f]
  (str/ends-with? (.getName f) (str "." (name AUTO-MIGRATION-EXT))))


(defn- make-next-migration
  "Return actions for next migration."
  [{:keys [models-file migrations-dir]}]
  (->> (file-util/list-files migrations-dir)
    (filter auto-migration?)
    (make-migration* models-file)
    (flatten)
    (seq)))


(defn- get-action-name-verbose
  [action]
  (->> action
    (get-action-description-vec)
    (cons "  -")
    (str/join " ")))


(defn- print-action-names
  [actions]
  (let [action-names (mapv get-action-name-verbose actions)]
    (file-util/safe-println (cons "Actions:" action-names) "")))


(defmulti make-migration :type)


(defmethod make-migration :default
  ; Make new migration based on models definitions automatically.
  [{:keys [models-file migrations-dir]
    custom-migration-name :name}]
  (try+
    (if-let [next-migration (make-next-migration {:models-file models-file
                                                  :migrations-dir migrations-dir})]
      (let [_ (create-migrations-dir migrations-dir)
            next-migration-name (get-next-migration-name next-migration custom-migration-name)
            migration-file-name-full-path (get-next-migration-file-name
                                            {:migration-type AUTO-MIGRATION-EXT
                                             :migrations-dir migrations-dir
                                             :next-migration-name next-migration-name})]
        (spit migration-file-name-full-path
          (with-out-str
            (pprint/pprint next-migration)))
        (println (str "Created migration: " migration-file-name-full-path))
        ; Print all actions from migration in human-readable format
        (print-action-names next-migration))
      (println "There are no changes in models."))
    (catch [:type ::s/invalid] e
      (file-util/prn-err e))
    (catch #(contains? #{::models/missing-referenced-model
                         ::models/missing-referenced-field
                         ::models/referenced-field-is-not-unique
                         ::models/fk-fields-have-different-types} (:type %)) e
      (-> e
        (errors/custom-error->error-report)
        (file-util/prn-err)))
    (catch [:reason ::dep/circular-dependency] e
      (-> {:title "MIGRATION ERROR"
           :message (format (str "Circular dependency between two migration actions: \n  %s\nand\n  %s\n\n"
                              "Please split actions by different migrations.")
                      (pr-str (:dependency e))
                      (pr-str (:node e)))}
        (errors/custom-error->error-report)
        (file-util/prn-err)))
    (catch FileNotFoundException e
      (-> {:title "ERROR"
           :message (format "Missing file:\n\n  %s" (ex-message e))}
        (errors/custom-error->error-report)
        (file-util/prn-err)))))


(defmethod make-migration EMPTY-SQL-MIGRATION-TYPE
  ; Make new migrations based on models definitions automatically.
  [{next-migration-name :name
    migrations-dir :migrations-dir}]
  (try+
    (when (empty? next-migration-name)
      (throw+ {:type ::missing-migration-name
               :message "Missing migration name."}))
    (let [_ (create-migrations-dir migrations-dir)
          next-migration-name* (str/replace next-migration-name #"-" "_")
          migration-file-name-full-path (get-next-migration-file-name
                                          {:migration-type SQL-MIGRATION-EXT
                                           :migrations-dir migrations-dir
                                           :next-migration-name next-migration-name*})]
      (spit migration-file-name-full-path SQL-MIGRATION-TEMPLATE)
      (println (str "Created migration: " migration-file-name-full-path)))
    (catch [:type ::s/invalid] e
      (file-util/prn-err e))
    (catch #(contains? #{::missing-migration-name
                         ::duplicated-migration-numbers} (:type %)) e
      (-> e
        (errors/custom-error->error-report)
        (file-util/prn-err)))))


(defn- get-migration-by-number
  "Return migration file name by number."
  [migration-names number]
  {:pre [(su/assert! (s/coll-of string?) migration-names)
         (su/assert! integer? number)]}
  (->> migration-names
    (filter #(= number (get-migration-number %)))
    (first)))


(defmulti explain*
  (juxt #(get-migration-type (:file-name %)) :direction :explain-format))


(defn- add-transaction-to-explain
  [actions]
  (concat ["BEGIN"] actions ["COMMIT;"]))


(defmethod explain* [AUTO-MIGRATION-EXT FORWARD-DIRECTION EXPLAIN-FORMAT-SQL]
  ; Generate raw sql from migration.
  [{:keys [file-name migrations-dir] :as _args}]
  (->> (read-migration {:file-name file-name
                        :migrations-dir migrations-dir})
    (mapv sql/->sql)
    (flatten)
    (add-transaction-to-explain)
    (file-util/safe-println)))


(defmethod explain* [AUTO-MIGRATION-EXT FORWARD-DIRECTION EXPLAIN-FORMAT-HUMAN]
  ; Generate raw sql from migration.
  [{:keys [file-name migrations-dir] :as _args}]
  (let [actions-explained (->> (read-migration {:file-name file-name
                                                :migrations-dir migrations-dir})
                            (mapv get-action-name-verbose))]
    (file-util/safe-println actions-explained "")))


(defmethod explain* [AUTO-MIGRATION-EXT BACKWARD-DIRECTION EXPLAIN-FORMAT-SQL]
  [_]
  (println "WARNING: backward auto-migration isn't fully supported yet."))


(defmethod explain* [AUTO-MIGRATION-EXT BACKWARD-DIRECTION EXPLAIN-FORMAT-HUMAN]
  [_]
  (println "WARNING: backward auto-migration isn't fully supported yet."))


(defmethod explain* [SQL-MIGRATION-EXT FORWARD-DIRECTION EXPLAIN-FORMAT-SQL]
  ; Generate raw sql from migration for forward direction.
  [{:keys [file-name migrations-dir] :as _args}]
  (->> (read-migration {:file-name file-name
                        :migrations-dir migrations-dir})
    (first)
    (get-forward-sql-migration)
    (file-util/safe-println)))


(defmethod explain* [SQL-MIGRATION-EXT FORWARD-DIRECTION EXPLAIN-FORMAT-HUMAN]
  [_]
  (println "Explain in human-readable format is not supported for custom SQL migration."))


(defmethod explain* [SQL-MIGRATION-EXT BACKWARD-DIRECTION EXPLAIN-FORMAT-SQL]
  ; Generate raw sql from migration for backward direction.
  [{:keys [file-name migrations-dir] :as _args}]
  (->> (read-migration {:file-name file-name
                        :migrations-dir migrations-dir})
    (first)
    (get-backward-sql-migration)
    (file-util/safe-println)))


(defmethod explain* [SQL-MIGRATION-EXT BACKWARD-DIRECTION EXPLAIN-FORMAT-HUMAN]
  [_]
  (println "Explain in human-readable format is not supported for custom SQL migration."))


(defn explain
  "Generate raw sql or human-readable text from migration."
  [{:keys [migrations-dir number direction]
    explain-format :format
    :or {direction FORWARD-DIRECTION
         explain-format EXPLAIN-FORMAT-SQL} :as _args}]
  (try+
    (let [migration-names (migrations-list migrations-dir)
          file-name (get-migration-by-number migration-names number)
          format-title (condp = explain-format
                         :sql "SQL"
                         :human "Actions")]


      (when-not (some? file-name)
        (throw+ {:type ::no-migration-by-number
                 :number number
                 :message (format "Missing migration by number %s" (str number))}))

      (file-util/safe-println
        [(format "%s for migration %s:\n" format-title file-name)])

      (explain* {:file-name file-name
                 :migrations-dir migrations-dir
                 :direction direction
                 :explain-format explain-format}))
    (catch [:type ::s/invalid] e
      (file-util/prn-err e))
    (catch #(contains? #{::no-migration-by-number
                         ::duplicated-migration-numbers} (:type %)) e
      (-> e
        (errors/custom-error->error-report)
        (file-util/prn-err)))))


(defn- already-migrated
  "Get names of previously migrated migrations from db."
  [db migrations-table]
  (try
    (->> {:select [:name]
          :from [migrations-table]
          :order-by [:created-at]}
      (db-util/exec! db)
      (map :name))
    (catch Exception e
      (let [msg (ex-message e)
            table-exists-err-pattern #"relation .+ does not exist"]
        (if (re-find table-exists-err-pattern msg)
          (throw+ {:type ::no-migrations-table
                   :message "Migrations table does not exist."})
          (throw+ {:type ::unexpected-db-error
                   :data (or (ex-message e) (str e))
                   :message "Unexpected db error."}))))))


(defmulti exec-action! (juxt :migration-type :direction))


(defn- action->honeysql
  [action]
  (su/conform ::sql/->sql action))


(defmethod exec-action! [AUTO-MIGRATION-EXT FORWARD-DIRECTION]
  [{:keys [db action]}]
  (let [formatted-action (action->honeysql action)]
    (if (sequential? formatted-action)
      (doseq [sub-action formatted-action]
        (db-util/exec! db sub-action))
      (db-util/exec! db formatted-action))))


(defmethod exec-action! [AUTO-MIGRATION-EXT BACKWARD-DIRECTION]
  ; TODO: implement backward migration!
  [_])


(defmethod exec-action! [SQL-MIGRATION-EXT FORWARD-DIRECTION]
  [{:keys [db action]}]
  (->> action
    (get-forward-sql-migration)
    (db-util/exec-raw! db)))


(defmethod exec-action! [SQL-MIGRATION-EXT BACKWARD-DIRECTION]
  [{:keys [db action]}]
  (->> action
    (get-backward-sql-migration)
    (db-util/exec-raw! db)))


(defn- exec-actions!
  "Perform list of actions on a database."
  [{:keys [db actions direction migration-type]}]
  (when (and (= AUTO-MIGRATION-EXT migration-type)
          (= BACKWARD-DIRECTION direction))
    (println (str "WARNING: backward migration isn't fully implemented yet. "
               "Database schema has not been changed!")))
  (doseq [action actions]
    (exec-action! {:db db
                   :action action
                   :direction direction
                   :migration-type migration-type})))


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


(defn- get-detailed-migrations-to-migrate
  "Return migrations to migrate and migration direction."
  [all-migrations migrated target-number]
  (if-not (seq all-migrations)
    {}
    (let [all-migrations-detailed (map detailed-migration all-migrations)
          all-numbers (set (map :number-int all-migrations-detailed))
          last-number (apply max all-numbers)
          target-number* (or target-number last-number)
          current-number (current-migration-number migrated)
          direction (if (> target-number* current-number)
                      FORWARD-DIRECTION
                      BACKWARD-DIRECTION)]
      (when-not (or (contains? all-numbers target-number*)
                  (= 0 target-number*))
        (throw+ {:type ::invalid-target-migration-number
                 :number target-number*
                 :message "Invalid target migration number."}))
      (if (= target-number* current-number)
        []
        (condp contains? direction
          #{FORWARD-DIRECTION} {:to-migrate (->> all-migrations-detailed
                                              (drop-while #(>= current-number (:number-int %)))
                                              (take-while #(>= target-number* (:number-int %))))
                                :direction direction}
          #{BACKWARD-DIRECTION} {:to-migrate (->> all-migrations-detailed
                                               (drop-while #(>= target-number* (:number-int %)))
                                               (take-while #(>= current-number (:number-int %)))
                                               (sort-by :number-int >))
                                 :direction direction})))))


(defn migrate
  "Run migration on a db."
  [{:keys [migrations-dir jdbc-url number migrations-table]
    :or {migrations-table db-util/MIGRATIONS-TABLE}}]
  (try+
    (let [db (db-util/db-conn jdbc-url)
          _ (db-util/create-migrations-table db migrations-table)
          migrated (already-migrated db migrations-table)
          all-migrations (migrations-list migrations-dir)
          {:keys [to-migrate direction]}
          (get-detailed-migrations-to-migrate all-migrations migrated number)]
      (if (seq to-migrate)
        (doseq [{:keys [migration-name file-name migration-type]} to-migrate]
          (if (= direction FORWARD-DIRECTION)
            (println (str "Applying " migration-name "..."))
            (println (str "Reverting " migration-name "...")))
          (jdbc/with-transaction [tx db]
            (let [actions (read-migration {:file-name file-name
                                           :migrations-dir migrations-dir})]
              (exec-actions! {:db tx
                              :actions actions
                              :direction direction
                              :migration-type migration-type}))
            (if (= direction FORWARD-DIRECTION)
              (save-migration! db migration-name migrations-table)
              (delete-migration! db migration-name migrations-table))))
        (println "Nothing to migrate.")))
    (catch [:type ::s/invalid] e
      (file-util/prn-err e))
    (catch #(contains? #{::duplicated-migration-numbers
                         ::invalid-target-migration-number} (:type %)) e
      (-> e
        (errors/custom-error->error-report)
        (file-util/prn-err)))))


(defn- get-already-migrated-migrations
  [db migrations-table]
  (try+
    (set (already-migrated db migrations-table))
    (catch [:type ::no-migrations-table]
      ; There is no migrated migrations if table doesn't exist.
      [])))


(defn list-migrations
  "Print migration list with status."
  [{:keys [migrations-dir jdbc-url migrations-table]
    :or {migrations-table db-util/MIGRATIONS-TABLE}}]
  ; TODO: reduce duplication with `migrate` fn!
  (try+
    (let [migration-names (migrations-list migrations-dir)
          db (db-util/db-conn jdbc-url)
          migrated (set (get-already-migrated-migrations db migrations-table))]
      (if (seq migration-names)
        (do
          (println "Existing migrations:\n")
          (doseq [file-name migration-names
                  :let [migration-name (get-migration-name file-name)
                        sign (if (contains? migrated migration-name) "✓" " ")]]
            (file-util/safe-println [(format "[%s] %s" sign file-name)])))
        (println "Migrations not found.")))
    (catch [:type ::s/invalid] e
      (file-util/prn-err e))
    (catch #(contains? #{::duplicated-migration-numbers
                         ::no-migrations-table
                         ::unexpected-db-error} (:type %)) e
      (-> e
        (errors/custom-error->error-report)
        (file-util/prn-err)))))


; Comments for development.

(comment
  (let [config {:models-file "src/automigrate/models.edn"
                ;:models-file "test/automigrate/models/feed_add_column.edn"
                :migrations-dir "src/automigrate/migrations"
                :jdbc-url "jdbc:postgresql://localhost:5432/automigrate?user=automigrate&password=automigrate"
                :number 4}
        db (db-util/db-conn (:jdbc-url config))
        migrations-files (file-util/list-files (:migrations-dir config))
        models-file (:models-file config)]
      (try+
        (->> (read-models models-file))
        ;(->> (make-migration* models-file migrations-files))
        ;(make-next-migration config)
        ;     (flatten))
        (catch [:type ::s/invalid] e
          (print (:message e))
          (:data e)))))


(comment
  (let [config {:models-file "src/automigrate/models.edn"
                :migrations-dir "src/automigrate/migrations"
                :jdbc-url "jdbc:postgresql://localhost:5432/automigrate?user=automigrate&password=automigrate"}
        db (db-util/db-conn (:jdbc-url config))]
    (make-migration config)))
