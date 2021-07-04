(ns tuna.actions
  (:require [clojure.spec.alpha :as s]
            [spec-dict :as d]
            [tuna.models :as models]
            [tuna.fields :as fields]
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
             ::fields/fields]))


(defmethod action DROP-TABLE-ACTION
  [_]
  (s/keys
    :req-un [::action
             ::model-name]))


(s/def ::options
  ::fields/field)


(defmethod action ADD-COLUMN-ACTION
  [_]
  (s/keys
    :req-un [::action
             ::field-name
             ::model-name
             ::options]))


(s/def ::changes
  (s/and
    (d/dict*
      (d/->opt (model-util/generate-type-option ::fields/type))
      (d/->opt (model-util/generate-changes [::fields/unique
                                             ::fields/null
                                             ::fields/primary-key
                                             ::fields/default
                                             ::fields/foreign-key])))
    #(> (count (keys %)) 0)))


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
