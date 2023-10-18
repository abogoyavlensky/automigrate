(ns automigrate.types
  (:require [clojure.spec.alpha :as s]
            [automigrate.util.spec :as spec-util]))


(s/def ::name keyword?)
(s/def ::choice string?)


(s/def ::choices
  (s/coll-of ::choice :min-count 1 :kind vector? :distinct true))


(s/def ::type-vec-options
  (s/keys
    :req-un [::choices]))


(s/def ::type-vec-options-strict-keys
  (spec-util/validate-strict-keys ::type-vec-options))


(s/def :automigrate.types.define-as/type
  #{:enum})


(s/def ::type-vec
  (s/cat
    :name ::name
    :type :automigrate.types.define-as/type
    :options (s/and
               ::type-vec-options
               ::type-vec-options-strict-keys)))


(s/def ::type
  (s/keys
    :req-un [:automigrate.types.define-as/type
             ::choices]))


(s/def ::types
  (s/map-of keyword? ::type :min-count 1 :distinct true))
