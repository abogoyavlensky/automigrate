(ns automigrate.actions
  (:require [clojure.spec.alpha :as s]
            [clojure.set :as set]
            [spec-dict :as d]
            [automigrate.indexes :as indexes]
            [automigrate.fields :as fields]
            [automigrate.types :as types]
            [automigrate.util.model :as model-util]
            [automigrate.util.spec :as spec-util]))


; Tables
(def CREATE-TABLE-ACTION :create-table)
(def DROP-TABLE-ACTION :drop-table)


; Columns
(def ADD-COLUMN-ACTION :add-column)
(def ALTER-COLUMN-ACTION :alter-column)
(def DROP-COLUMN-ACTION :drop-column)


; Indexes
(def CREATE-INDEX-ACTION :create-index)
(def DROP-INDEX-ACTION :drop-index)
(def ALTER-INDEX-ACTION :alter-index)


; Types
(def CREATE-TYPE-ACTION :create-type)
(def DROP-TYPE-ACTION :drop-type)
(def ALTER-TYPE-ACTION :alter-type)


(s/def ::action #{CREATE-TABLE-ACTION
                  DROP-TABLE-ACTION
                  ADD-COLUMN-ACTION
                  ALTER-COLUMN-ACTION
                  DROP-COLUMN-ACTION
                  CREATE-INDEX-ACTION
                  DROP-INDEX-ACTION
                  ALTER-INDEX-ACTION
                  CREATE-TYPE-ACTION
                  DROP-TYPE-ACTION
                  ALTER-TYPE-ACTION})


(s/def ::model-name keyword?)
(s/def ::field-name keyword?)
(s/def ::index-name keyword?)
(s/def ::type-name keyword?)


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
    (s/map-of keyword? map? :min-count 1)
    (d/dict*
      (d/->opt (model-util/generate-type-option ::fields/type))
      (d/->opt (model-util/generate-changes [::fields/unique
                                             ::fields/null
                                             ::fields/primary-key
                                             ::fields/default
                                             ::fields/foreign-key
                                             ::fields/on-delete
                                             ::fields/on-update])))))


(defmethod action ALTER-COLUMN-ACTION
  [_]
  (s/keys
    :req-un [::action
             ::field-name
             ::model-name
             ::options
             ::changes]))


(defmethod action DROP-COLUMN-ACTION
  [_]
  (s/keys
    :req-un [::action
             ::field-name
             ::model-name]))


(s/def :automigrate.actions.indexes/options
  ::indexes/index)


(defmethod action CREATE-INDEX-ACTION
  [_]
  (s/keys
    :req-un [::action
             ::index-name
             ::model-name
             :automigrate.actions.indexes/options]))


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
             :automigrate.actions.indexes/options]))


(s/def :automigrate.actions.types/options
  ::types/type)


(defmethod action CREATE-TYPE-ACTION
  [_]
  (s/keys
    :req-un [::action
             ::type-name
             ::model-name
             :automigrate.actions.types/options]))


(defmethod action DROP-TYPE-ACTION
  [_]
  (s/keys
    :req-un [::action
             ::type-name
             ::model-name]))


(s/def :automigrate.actions.types/changes
  (s/and
    (s/map-of keyword? map? :min-count 1)
    (d/dict*
      (d/->opt (model-util/generate-changes [::types/choices])))))


(s/def :automigrate.actions.types/options
  ::types/type)


(s/def ::validate-type-choices-not-allow-to-remove
  (fn [action-data]
    (let [choices-from (-> action-data :changes :choices :from set)
          choices-to (-> action-data :changes :choices :to set)]
      (set/subset? choices-from choices-to))))


(defmethod action ALTER-TYPE-ACTION
  [_]
  (s/and
    (s/keys
      :req-un [::action
               ::type-name
               ::model-name
               :automigrate.actions.types/options
               :automigrate.actions.types/changes])
    ::validate-type-choices-not-allow-to-remove))


; Public

(s/def ::->migration (s/multi-spec action :action))


(s/def ::->migrations
  (s/coll-of ::->migration))


(defn ->migrations
  [actions]
  (spec-util/conform ::->migrations actions))


(defn validate-actions!
  [actions]
  (spec-util/valid? ::->migrations actions))
