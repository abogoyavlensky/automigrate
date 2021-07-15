(ns tuna.fields
  (:require [clojure.spec.alpha :as s]
            [clojure.set :as set]
            [spec-dict :as d]
            [tuna.util.spec :as spec-util]))


(def FOREIGN-KEY-OPTION :foreign-key)
(def ON-DELETE-OPTION :on-delete)
(def ON-UPDATE-OPTION :on-update)

(def FK-CASCADE :cascade)
(def FK-SET-NULL :set-null)
(def FK-SET-DEFAULT :set-default)
(def FK-RESTRICT :restrict)
(def FK-NO-ACTION :no-action)


(def FK-ACTIONS
  #{FK-CASCADE
    FK-SET-NULL
    FK-SET-DEFAULT
    FK-RESTRICT
    FK-NO-ACTION})


(s/def ::type
  (s/and
    ; TODO: switch available fields according to db dialect!
    (s/or
      :kw #{:integer
            :smallint
            :bigint
            :float
            :real
            :serial
            :uuid
            :boolean
            :text
            :timestamp
            :date
            :time
            :point
            :json
            :jsonb}
      :fn (s/cat :name #{:char
                         :varchar
                         :float}
            :val pos-int?))
    (s/conformer spec-util/tagged->value)))


(def type-groups
  "Type groups for definition of type's relations.

  Used for foreign-key field type validation."
  ; TODO: instead try to use derive!
  {:int #{:integer :serial :bigint :smallint}
   :char #{:varchar :text :uuid}})


(defn check-type-group
  [t]
  (some->> type-groups
    (filter #(contains? (val %) t))
    (first)
    (key)))


(s/def ::null boolean?)
(s/def ::primary-key true?)
(s/def ::unique true?)


(s/def ::foreign-key
  qualified-keyword?)


(s/def ::default
  ; TODO: update with dynamic value related to field's type
  (s/and
    (s/or
      :int integer?
      :bool boolean?
      :str string?
      :nil nil?
      :fn (s/cat
            :name keyword?
            :val (s/? #((some-fn int? string?) %))))
    (s/conformer
      spec-util/tagged->value)))


(s/def ::on-delete FK-ACTIONS)
(s/def ::on-update FK-ACTIONS)


(s/def ::options
  (d/dict
    (d/->opt (spec-util/specs->dict
               [::null
                ::primary-key
                ::unique
                ::default
                ::foreign-key
                ::on-delete
                ::on-update]))))


(defn- validate-fk-options
  "Validate that basic fields mustn't contain foreign-key specific options."
  [value]
  (let [option-names (-> (keys value) set)]
    (if (not (contains? option-names FOREIGN-KEY-OPTION))
      (empty? (set/intersection option-names
                #{ON-DELETE-OPTION ON-UPDATE-OPTION}))
      true)))


(s/def ::options-strict
  (s/and
    (d/dict* ::options)
    validate-fk-options))


(s/def ::field
  (d/dict*
    {:type ::type}
    ::options))


(s/def ::field-vec
  (s/cat
    :name keyword?
    :type ::type
    :options (s/? ::options-strict)))


(s/def ::fields
  (s/map-of keyword? ::field))


;;;;;;;;;;;;;;;

(comment
  (let [data {:null true
              :foreign-key :account/id
              :on-delete FK-CASCADE}
              ;:type :integer}
        data2 {:null false, :type :serial}]))
;(s/explain ::opts data)
;(s/explain ::options-strict data)))