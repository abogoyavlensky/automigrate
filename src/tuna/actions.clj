(ns tuna.actions
  (:require [clojure.spec.alpha :as s]
            [spec-dict :as d]
            [tuna.models :as models]
            [tuna.util.spec :as spec-util]
            [tuna.util.model :as model-util]))


(def CREATE-TABLE-ACTION :create-table)
(def DROP-TABLE-ACTION :drop-table)
(def ADD-COLUMN-ACTION :add-column)
(def ALTER-COLUMN-ACTION :alter-column)
(def DROP-COLUMN-ACTION :drop-column)
(def CREATE-INDEX-ACTION :create-index)
(def DROP-INDEX-ACTION :drop-index)
(def ALTER-INDEX-ACTION :alter-index)


(s/def ::action #{CREATE-TABLE-ACTION
                  DROP-TABLE-ACTION
                  ADD-COLUMN-ACTION
                  ALTER-COLUMN-ACTION
                  DROP-COLUMN-ACTION
                  CREATE-INDEX-ACTION
                  DROP-INDEX-ACTION
                  ALTER-INDEX-ACTION})


(s/def ::model-name keyword?)
(s/def ::field-name keyword?)
(s/def ::index-name keyword?)


(defmulti action :action)


(defmethod action CREATE-TABLE-ACTION
  [_]
  (s/keys
    :req-un [::action
             ::model-name
             ::models/fields]))


(defmethod action DROP-TABLE-ACTION
  [_]
  (s/keys
    :req-un [::action
             ::model-name]))


(s/def ::options
  ::models/field)


(defmethod action ADD-COLUMN-ACTION
  [_]
  (s/keys
    :req-un [::action
             ::field-name
             ::model-name
             ::options]))


(defn- check-option-state
  [value]
  (not= (:from value) (:to value)))


(defn- option-states
  [field-spec]
  (s/and
    (d/dict*
      ^:opt {:from (s/and (s/or :empty #{model-util/EMPTY-OPTION}
                            :value field-spec)
                     (s/conformer spec-util/tagged->value))
             :to (s/and (s/or :empty #{model-util/EMPTY-OPTION}
                          :value field-spec)
                   (s/conformer spec-util/tagged->value))})
    check-option-state))


(s/def ::changes
  (s/and
    (d/dict*
      ^:opt {:type (s/and
                     (d/dict*
                       ^:opt {:from :tuna.models.field/type
                              :to :tuna.models.field/type})
                     check-option-state)
             :unique (option-states :tuna.models.field/unique)
             :null (option-states :tuna.models.field/null)
             :primary-key (option-states :tuna.models.field/primary-key)
             :default (option-states :tuna.models.field/default)
             :foreign-key (option-states :tuna.models.field/foreign-key)})
    #(> (count (keys %)) 0)))


; TODO: remove
(comment
  (let [data {:type {:from :integer
                     :to :text}
              :unique {:from true
                       :to model-util/EMPTY-OPTION}}]
    (model-util/changes-to-drop data model-util/OPTION-KEY-BACKWARD)))


(defmethod action ALTER-COLUMN-ACTION
  [_]
  (s/keys
    :req-un [::action
             ::field-name
             ::model-name
             ::changes]))


(defmethod action DROP-COLUMN-ACTION
  [_]
  (s/keys
    :req-un [::action
             ::field-name
             ::model-name]))


(s/def :tuna.actions.indexes/options
  ::models/index)


(defmethod action CREATE-INDEX-ACTION
  [_]
  (s/keys
    :req-un [::action
             ::index-name
             ::model-name
             :tuna.actions.indexes/options]))


(defmethod action DROP-INDEX-ACTION
  [_]
  (s/keys
    :req-un [::action
             ::index-name
             ::model-name]))


(defmethod action ALTER-INDEX-ACTION
  [_]
  (s/keys
    :req-un [::action
             ::index-name
             ::model-name
             :tuna.actions.indexes/options]))


(s/def ::->migration (s/multi-spec action :action))
