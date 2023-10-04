(ns automigrate.types
  (:require [clojure.spec.alpha :as s]))

(s/def ::name keyword?)
(s/def ::choice string?)
(s/def ::choices
  (s/coll-of ::choice :min-count 1 :kind vector? :distinct true))

(s/def :automigrate.types.define-as/type
  #{:enum})

(s/def ::type-vec
  (s/cat
    :name ::name
    :type :automigrate.types.define-as/type
    :options (s/keys
               :req-un [::choices])))

(s/def ::type
  (s/keys
    :req-un [:automigrate.types.define-as/type
             ::choices]))

(s/def ::types
  (s/map-of keyword? ::type :min-count 1 :distinct true))
