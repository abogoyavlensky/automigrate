(ns automigrate.sql
  "Module for transforming actions from migration to SQL queries."
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [slingshot.slingshot :refer [throw+]]
    [spec-dict :as d]
    [automigrate.actions :as actions]
    [automigrate.fields :as fields]
    [automigrate.constraints :as constraints]
    [automigrate.util.db :as db-util]
    [automigrate.util.model :as model-util]
    [automigrate.util.spec :as spec-util]))


(def ^:private DEFAULT-INDEX :btree)


(s/def :automigrate.sql.option->sql/type
  (s/and
    ::fields/type
    (s/conformer identity)))


(s/def :automigrate.sql.option->sql/null
  (s/and
    ::fields/null
    (s/conformer
      (fn [value]
        (if (true? value)
          nil
          [:not nil])))))


(s/def :automigrate.sql.option->sql/primary-key
  (s/and
    ::fields/primary-key
    (s/conformer
      (fn [_]
        [:primary-key]))))


(s/def :automigrate.sql.option->sql/check
  (s/and
    ::fields/check
    (s/conformer
      (fn [value]
        [:check value]))))


(s/def :automigrate.sql.option->sql/unique
  (s/and
    ::fields/unique
    (s/conformer
      (fn [_]
        :unique))))


(s/def :automigrate.sql.option->sql/default
  (s/and
    ::fields/default
    (s/conformer
      (fn [value]
        [:default value]))))


(defn- fk-opt->raw
  [option value]
  (mapv db-util/kw->raw [option value]))


(s/def ::foreign-key
  (d/dict*
    {:foreign-field ::fields/foreign-key}
    ^:opt {fields/ON-DELETE-OPTION ::fields/on-delete
           fields/ON-UPDATE-OPTION ::fields/on-update}))


(s/def :automigrate.sql.option->sql/foreign-key
  (s/and
    (s/conformer
      (fn [value]
        [(cons :references (model-util/kw->vec value))]))))


(s/def :automigrate.sql.option->sql/on-delete
  (s/and
    ::fields/on-delete
    (s/conformer
      (fn [value]
        (fk-opt->raw fields/ON-DELETE-OPTION value)))))


(s/def :automigrate.sql.option->sql/on-update
  (s/and
    ::fields/on-update
    (s/conformer
      (fn [value]
        (fk-opt->raw fields/ON-UPDATE-OPTION value)))))


(s/def :automigrate.sql.option->sql/array
  (s/and
    ::fields/array
    (s/conformer
      (fn [value]
        [:raw value]))))


(s/def :automigrate.sql.option->sql/comment
  (s/and
    ::fields/comment
    (s/conformer identity)))


(s/def :automigrate.sql.option->sql/collate
  (s/and
    ::fields/collate
    (s/conformer
      (fn [value]
        [:raw (format "COLLATE \"%s\"" value)]))))


(def ^:private options-specs
  [:automigrate.sql.option->sql/null
   :automigrate.sql.option->sql/primary-key
   :automigrate.sql.option->sql/unique
   :automigrate.sql.option->sql/default
   :automigrate.sql.option->sql/foreign-key
   :automigrate.sql.option->sql/on-delete
   :automigrate.sql.option->sql/on-update
   :automigrate.sql.option->sql/check
   :automigrate.sql.option->sql/array
   :automigrate.sql.option->sql/comment
   :automigrate.sql.option->sql/collate])


(s/def ::->foreign-key-complete
  (s/conformer
    (fn [value]
      (if-let [foreign-key (:foreign-key value)]
        (let [on-delete (get value fields/ON-DELETE-OPTION)
              on-update (get value fields/ON-UPDATE-OPTION)
              foreign-key-complete (cond-> foreign-key
                                     (some? on-delete) (concat on-delete)
                                     (some? on-update) (concat on-update))]
          (-> value
            (assoc fields/FOREIGN-KEY-OPTION foreign-key-complete)
            (dissoc fields/ON-DELETE-OPTION fields/ON-UPDATE-OPTION)))
        value))))


(s/def ::options->sql
  (s/and
    (d/dict*
      {:type :automigrate.sql.option->sql/type}
      (d/->opt (spec-util/specs->dict options-specs)))
    ::->foreign-key-complete))


(s/def ::fields
  (s/map-of keyword? ::options->sql))


(defn field-type->sql
  [{array-value :array
    type-value :type}]
  (let [type-sql (cond
                   ; :add-column clause in honeysql converts type name in kebab case into
                   ; two separated words. So, for custom enum types we have to convert
                   ; custom type name to snake case to use it in SQL as a single word.
                   (s/valid? ::fields/enum-type type-value)
                   (-> type-value last model-util/kw->snake-case)

                   (s/valid? ::fields/time-types type-value)
                   (let [[type-name precision] type-value]
                     [:raw (format "%s(%s)" (-> type-name name str/upper-case) precision)])

                   :else type-value)]
    ; Add array type if it exists
    (cond-> [type-sql]
      (some? array-value) (conj array-value))))


(defn- ->primary-key-constraint
  [{:keys [model-name primary-key]}]
  (when (seq primary-key)
    (concat
      [[:constraint (constraints/primary-key-constraint-name model-name)]]
      primary-key)))


(defn- ->unique-constraint
  [{:keys [model-name field-name unique]}]
  (when (some? unique)
    [[:constraint (constraints/unique-constraint-name model-name field-name)]
     unique]))


(defn- ->foreign-key-constraint
  [{:keys [model-name field-name references]}]
  (when (some? references)
    (concat [[:constraint (constraints/foreign-key-constraint-name model-name field-name)]]
      references)))


(defn- ->check-constraint
  [{:keys [model-name field-name check]}]
  (when (some? check)
    [[:constraint (constraints/check-constraint-name model-name field-name)]
     check]))


(defn- fields->columns
  [{:keys [fields model-name]}]
  (reduce
    (fn [acc [field-name {:keys [unique primary-key foreign-key check] :as options}]]
      (let [rest-options (remove #(= :EMPTY %) [(:on-delete options :EMPTY)
                                                (:on-update options :EMPTY)
                                                (:null options :EMPTY)
                                                (:default options :EMPTY)
                                                (:collate options :EMPTY)])]
        (conj acc (concat
                    [field-name]
                    (field-type->sql options)
                    (->primary-key-constraint {:model-name model-name
                                               :primary-key primary-key})
                    (->unique-constraint {:model-name model-name
                                          :field-name field-name
                                          :unique unique})
                    (->foreign-key-constraint {:model-name model-name
                                               :field-name field-name
                                               :references foreign-key})
                    (->check-constraint {:model-name model-name
                                         :field-name field-name
                                         :check check})
                    rest-options))))
    []
    fields))


(defmulti action->sql :action)


(defn- create-comment-on-field-raw
  [{:keys [model-name field-name comment-val]}]
  (let [obj-str (->> [model-name field-name]
                  (map #(-> % (name) (model-util/kw->snake-case-str)))
                  (str/join "."))
        create-comment-tmp "COMMENT ON COLUMN %s IS %s"
        comment-val* (if (some? comment-val)
                       (format "'%s'" comment-val)
                       "NULL")]
    [:raw (format create-comment-tmp obj-str comment-val*)]))


(defn- fields->comments-sql
  [model-name fields]
  (reduce
    (fn [acc [field-name options]]
      (if (some? (:comment options))
        (conj acc (create-comment-on-field-raw
                    {:model-name model-name
                     :field-name field-name
                     :comment-val (:comment options)}))
        acc))
    []
    fields))


(s/def ::create-table->sql
  (s/conformer
    (fn [{:keys [model-name fields]}]
      (let [create-table-q {:create-table [model-name]
                            :with-columns (fields->columns {:fields fields
                                                            :model-name model-name})}
            create-comments-q-vec (fields->comments-sql model-name fields)]
        (if (seq create-comments-q-vec)
          (concat [create-table-q] (vec create-comments-q-vec))
          create-table-q)))))


(defmethod action->sql actions/CREATE-TABLE-ACTION
  [_]
  (s/and
    (s/keys
      :req-un [::actions/action
               ::actions/model-name
               ::fields])
    ::create-table->sql))


(s/def ::options
  ::options->sql)


(s/def ::add-column->sql
  (s/conformer
    (fn [{:keys [model-name field-name options]}]
      (let [add-column {:alter-table model-name
                        :add-column (first (fields->columns {:model-name model-name
                                                             :fields [[field-name options]]}))}]
        (if (some? (:comment options))
          [add-column
           (create-comment-on-field-raw
             {:model-name model-name
              :field-name field-name
              :comment-val (:comment options)})]
          add-column)))))


(defmethod action->sql actions/ADD-COLUMN-ACTION
  [_]
  (s/and
    (s/keys
      :req-un [::actions/action
               ::actions/field-name
               ::actions/model-name
               ::options])
    ::add-column->sql))


(s/def ::changes
  (s/and
    (d/dict*
      (d/->opt (model-util/generate-type-option :automigrate.sql.option->sql/type))
      (d/->opt (model-util/generate-changes options-specs)))
    #(> (count (keys %)) 0)))


(defn- foreign-key-changes
  "Return full option changes or nil if option should be dropped."
  [options changes-to-add changes-to-drop]
  (let [keys-to-add (set (keys changes-to-add))]
    (if (contains? changes-to-drop :foreign-key)
      nil
      (when (or (some keys-to-add [:foreign-key :on-delete :on-update])
              (some changes-to-drop [:on-delete :on-update]))
        (:foreign-key options)))))


(defn- get-changes
  [action]
  (let [changes-to-add (model-util/changes-to-add (:changes action))
        changes-to-drop (model-util/changes-to-drop (:changes action))
        foreign-key (foreign-key-changes (:options action) changes-to-add changes-to-drop)]
    {:changes-to-add (cond-> changes-to-add
                       true (dissoc :on-delete :on-update)
                       (some? foreign-key) (assoc :foreign-key foreign-key))
     :changes-to-drop (disj changes-to-drop :on-delete :on-update)}))


(defn- alter-foreign-key->edn
  [{:keys [model-name field-name field-value action-changes]}]
  (let [from-value-empty? (= :EMPTY (get-in action-changes [:foreign-key :from]))
        drop-constraint {:drop-constraint
                         [[:raw "IF EXISTS"]
                          (constraints/foreign-key-constraint-name model-name field-name)]}
        add-constraint {:add-constraint
                        (concat [(constraints/foreign-key-constraint-name model-name field-name)
                                 [:foreign-key field-name]]
                          field-value)}]
    (cond-> []
      (not from-value-empty?) (conj drop-constraint)
      true (conj add-constraint))))


(defn- alter-check->edn
  [{:keys [model-name field-name field-value action-changes]}]
  (let [from-value-empty? (= :EMPTY (get-in action-changes [:check :from]))
        drop-constraint {:drop-constraint
                         [[:raw "IF EXISTS"]
                          (constraints/check-constraint-name model-name field-name)]}
        add-constraint {:add-constraint
                        [(constraints/check-constraint-name model-name field-name)
                         field-value]}]
    (cond-> []
      (not from-value-empty?) (conj drop-constraint)
      true (conj add-constraint))))


(defn- ->alter-column
  [field-name option {:keys [changes options] :as _action}]
  (when (or (= option :type)
          (and (not (contains? changes :type))
            (= option :array)))
    (let [type-sql (field-type->sql options)
          field-name-str (-> field-name (name) (str/replace #"-" "_"))]
      {:alter-column
       (concat
         [field-name :type]
         type-sql
         ; always add `using` to be able to convert different types
         [:using [:raw field-name-str] [:raw "::"]]
         type-sql)})))


(defn- alter-primary-key->edn
  [model-name field-name]
  {:add-constraint
   [(constraints/primary-key-constraint-name model-name)
    [:primary-key field-name]]})


(defn- alter-unique->edn
  [model-name field-name]
  {:add-index [:unique nil field-name]}
  {:add-constraint
   [(constraints/unique-constraint-name model-name field-name)
    [:unique nil field-name]]})


(s/def ::alter-column->sql
  (s/conformer
    (fn [{:keys [field-name model-name] :as action}]
      (let [{:keys [changes-to-add changes-to-drop]} (get-changes action)
            changes (for [[option value] changes-to-add
                          :when (not= option :comment)]
                      (condp contains? option
                        #{:type :array} (->alter-column field-name option action)
                        #{:null} (let [operation (if (nil? value) :drop :set)]
                                   {:alter-column [field-name operation [:not nil]]})
                        #{:default} {:alter-column [field-name :set value]}
                        #{:unique} (alter-unique->edn model-name field-name)
                        #{:primary-key} (alter-primary-key->edn model-name field-name)
                        #{:foreign-key} (alter-foreign-key->edn
                                          {:model-name model-name
                                           :field-name field-name
                                           :field-value value
                                           :action-changes (:changes action)})
                        #{:check} (alter-check->edn
                                    {:model-name model-name
                                     :field-name field-name
                                     :field-value value
                                     :action-changes (:changes action)})))
            ; remove nil if options type and array have been changed
            changes* (->> changes (remove nil?) (flatten))

            dropped (for [option changes-to-drop
                          :when (not= option :comment)]
                      (case option
                        :array (->alter-column field-name option action)
                        :null {:alter-column [field-name :drop [:not nil]]}
                        :default {:alter-column [field-name :drop :default]}
                        :unique {:drop-constraint (constraints/unique-constraint-name
                                                    model-name
                                                    field-name)}
                        :primary-key {:drop-constraint (constraints/primary-key-constraint-name
                                                         model-name)}
                        :foreign-key {:drop-constraint (constraints/foreign-key-constraint-name
                                                         model-name
                                                         field-name)}
                        :check {:drop-constraint (constraints/check-constraint-name
                                                   model-name
                                                   field-name)}))
            dropped* (remove nil? dropped)
            all-actions (concat changes* dropped*)
            alter-table-sql {:alter-table (cons model-name all-actions)}

            new-comment-val (-> action :changes :comment :to)
            comment-sql (when (some? new-comment-val)
                          (create-comment-on-field-raw
                            {:model-name model-name
                             :field-name field-name
                             :comment-val (when (not= new-comment-val :EMPTY)
                                            new-comment-val)}))]
        (if (and (seq all-actions) (not (seq comment-sql)))
          ; for compatibility with existing tests
          alter-table-sql
          (cond-> []
            (seq all-actions) (conj alter-table-sql)
            (seq comment-sql) (conj comment-sql)))))))


(defmethod action->sql actions/ALTER-COLUMN-ACTION
  [_]
  (s/and
    (s/keys
      :req-un [::actions/action
               ::actions/field-name
               ::actions/model-name
               ::options
               ::changes])
    ::alter-column->sql))


(s/def ::drop-column->sql
  (s/conformer
    (fn [value]
      {:alter-table (:model-name value)
       :drop-column (:field-name value)})))


(defmethod action->sql actions/DROP-COLUMN-ACTION
  [_]
  (s/and
    (s/keys
      :req-un [::actions/action
               ::actions/field-name
               ::actions/model-name])
    ::drop-column->sql))


(s/def ::drop-table->sql
  (s/conformer
    (fn [value]
      {:drop-table [:if-exists (:model-name value)]})))


(defmethod action->sql actions/DROP-TABLE-ACTION
  [_]
  (s/and
    (s/keys
      :req-un [::actions/action
               ::actions/model-name])
    ::drop-table->sql))


(s/def ::create-index->sql
  (s/conformer
    (fn [value]
      (let [options (:options value)
            index-type (or (:type options) DEFAULT-INDEX)
            index-action (if (true? (:unique options))
                           :create-unique-index
                           :create-index)]
        {index-action (cond-> [(:index-name value)
                               :on (:model-name value)
                               :using (cons index-type (:fields options))]
                        (seq (:where options)) (concat [:where (:where options)]))}))))


(defmethod action->sql actions/CREATE-INDEX-ACTION
  [_]
  (s/and
    (s/keys
      :req-un [::actions/action
               ::actions/index-name
               ::actions/model-name
               :automigrate.actions.indexes/options])
    ::create-index->sql))


(s/def ::drop-index->sql
  (s/conformer
    (fn [value]
      {:drop-index (:index-name value)})))


(defmethod action->sql actions/DROP-INDEX-ACTION
  [_]
  (s/and
    (s/keys
      :req-un [::actions/action
               ::actions/index-name
               ::actions/model-name])
    ::drop-index->sql))


(s/def ::alter-index->sql
  (s/conformer
    (fn [value]
      [(s/conform ::drop-index->sql (assoc value :action actions/DROP-INDEX-ACTION))
       (s/conform ::create-index->sql (assoc value :action actions/CREATE-INDEX-ACTION))])))


(defmethod action->sql actions/ALTER-INDEX-ACTION
  [_]
  (s/and
    (s/keys
      :req-un [::actions/action
               ::actions/index-name
               ::actions/model-name
               :automigrate.actions.indexes/options])
    ::alter-index->sql))


(s/def ::create-type->sql
  (s/conformer
    (fn [value]
      (when (= :enum (get-in value [:options :type]))
        (let [options (:options value)
              type-action actions/CREATE-TYPE-ACTION]
          {type-action [(:type-name value) :as (cons :enum (:choices options))]})))))


(defmethod action->sql actions/CREATE-TYPE-ACTION
  [_]
  (s/and
    (s/keys
      :req-un [::actions/action
               ::actions/type-name
               ::actions/model-name
               :automigrate.actions.types/options])
    ::create-type->sql))


(s/def ::drop-type->sql
  (s/conformer
    (fn [value]
      ; TODO: try to remove vector
      {:drop-type [(:type-name value)]})))


(defmethod action->sql actions/DROP-TYPE-ACTION
  [_]
  (s/and
    (s/keys
      :req-un [::actions/action
               ::actions/type-name
               ::actions/model-name])
    ::drop-type->sql))


(defn- ->alter-type-action
  [{:keys [type-name new-value position existing-value]}]
  {:pre [(spec-util/assert! keyword? type-name)
         (spec-util/assert! string? new-value)
         (spec-util/assert! #{:before :after} position)
         (spec-util/assert! string? existing-value)]}
  {:alter-type [type-name :add-value new-value position existing-value]})


(defn- get-actions-for-new-choices
  [type-name initial-value choices-to-add position]
  {:pre [(spec-util/assert! #{:before :after} position)]}
  (loop [prev-value initial-value
         [new-value & rest-choices] choices-to-add
         actions []]
    (if-not new-value
      actions
      (let [next-action (->alter-type-action {:type-name type-name
                                              :new-value new-value
                                              :position position
                                              :existing-value prev-value})]
        (recur new-value rest-choices (conj actions next-action))))))


(defn- last-item?
  [idx items]
  (= (inc idx) (count items)))


(defn- get-actions-for-enum-choices-changes
  [type-name
   from-choices
   to-choices
   result-actions
   [from-idx from-value]]
  (let [value-in-to-idx (.indexOf to-choices from-value)]
    ; It is not possible to remove choices from the enum type
    (when (< value-in-to-idx 0)
      (throw+ {:type ::alter-type-missing-old-choice
               :message "Missing old choice value in new enum type definition"
               :data {:from-idx from-idx
                      :from-value from-value}}))

    (let [prev-value-count-drop (if (= from-idx 0)
                                  0
                                  (->> (nth from-choices (dec from-idx) 0)
                                    (.indexOf to-choices)
                                    ; inc index to get count of  items from the start of vec
                                    (inc)))
          before-reversed (-> to-choices
                            (subvec prev-value-count-drop value-in-to-idx)
                            (reverse))
          new-actions-before (get-actions-for-new-choices
                               type-name
                               from-value
                               before-reversed
                               :before)
          new-actions-after (if (last-item? from-idx from-choices)
                              (get-actions-for-new-choices
                                type-name
                                from-value
                                ; the rest of the `to-choices` vec
                                (subvec to-choices (inc value-in-to-idx))
                                :after)
                              [])]
      (vec (concat result-actions new-actions-before new-actions-after)))))


(s/def ::alter-type->sql
  (s/conformer
    (fn [action]
      ; Currently there is implementation for enum type
      (let [{:keys [from to]} (get-in action [:changes :choices])]
        (reduce
          (partial get-actions-for-enum-choices-changes (:type-name action) from to)
          []
          (map-indexed vector from))))))


(defmethod action->sql actions/ALTER-TYPE-ACTION
  [_]
  (s/and
    (s/keys
      :req-un [::actions/action
               ::actions/type-name
               ::actions/model-name
               :automigrate.actions.types/options
               :automigrate.actions.types/changes])
    ::alter-type->sql))


; Public

(s/def ::->sql (s/multi-spec action->sql :action))


(defn ->sql
  "Convert migration action to sql."
  [action]
  (let [formatted-action (s/conform ::->sql action)]
    (if (sequential? formatted-action)
      (map #(db-util/fmt %) formatted-action)
      (db-util/fmt formatted-action))))
