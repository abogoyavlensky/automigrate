(ns tuna.fields
  (:require [clojure.spec.alpha :as s]
            [spec-dict :as d]
            [tuna.util.spec :as spec-util]))


(def FOREIGN-KEY-OPTION :foreign-key)


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


(s/def ::options-common
  (d/dict*
    (d/->opt (spec-util/specs->dict
               [::null
                ::primary-key
                ::unique
                ::default
                ::foreign-key]))))


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


(s/def ::on-delete FK-ACTIONS)
(s/def ::on-update FK-ACTIONS)


(s/def ::options-foreign-key
  (d/dict*
    (d/->opt (spec-util/specs->dict [::on-delete
                                     ::on-update]))))


(s/def ::field
  (d/dict*
    {:type ::type}
    ::options-common))


(s/def ::field-vec
  (s/cat
    :name keyword?
    :type ::type
    :options (s/? ::options-common)))


(s/def ::fields
  (s/map-of keyword? ::field))


;;;;;;;;;;;;;;;

(defmulti opts
  "Add foreign key specific options to common options if needed."
  (fn [value]
    (cond
      (contains? value FOREIGN-KEY-OPTION) FOREIGN-KEY-OPTION
      :else nil)))


(defmethod opts FOREIGN-KEY-OPTION
  [_]
  (d/dict*
    ::options-common
    ::options-foreign-key))


(defmethod opts :default
  [_]
  (prn _)
  ::options-common)


(s/def ::opt (s/multi-spec opts identity))


(comment
  (let [data {:null true
              ;:foreign-key :account/id}
              :on-delete FK-CASCADE}
        data2 {:null false
               :default [:now]}]
    (s/explain ::opts data)))
