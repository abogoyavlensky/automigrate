(ns tuna.util.model
  (:require [clojure.spec.alpha :as s]
            [spec-dict :as d]
            [medley.core :as medley]
            [tuna.util.spec :as spec-util]
            [clojure.string :as str]))


(def EMPTY-OPTION :EMPTY)

(def OPTION-KEY-FORWARD :to)
(def OPTION-KEY-BACKWARD :from)


(s/def ::option-key
  #{OPTION-KEY-FORWARD OPTION-KEY-BACKWARD})


(defn kw->vec
  [kw]
  (when (qualified-keyword? kw)
    (mapv keyword
      ((juxt namespace name) kw))))


(defn kw->name
  "Convert full qualified keyword to keywordized name."
  [kw]
  (-> kw name keyword))


(defn kw->kebab-case
  [kw]
  (-> kw
    (name)
    (str/replace #"_" "-")
    (keyword)))


(defn map-kw-keys->kebab-case
  [map-kw]
  (medley/map-keys kw->kebab-case map-kw))


(defn changes-to-add
  ([changes]
   (changes-to-add changes OPTION-KEY-FORWARD))
  ([changes option-key]
   {:pre [(s/assert ::option-key option-key)]}
   (->> changes
     (medley/remove-kv #(= EMPTY-OPTION (-> %2 (get option-key))))
     (reduce-kv #(assoc %1 %2 (get %3 option-key)) {}))))


(defn changes-to-drop
  ([changes]
   (changes-to-drop changes OPTION-KEY-FORWARD))
  ([changes option-key]
   {:pre [(s/assert ::option-key option-key)]}
   (->> changes
        ; TODO: use var instead of :EMPTY val!
     (filter #(= EMPTY-OPTION (get (val %) option-key)))
     (map key)
     (set))))


(defn check-option-state
  [value]
  (not= (:from value) (:to value)))


(defn option-states
  [field-spec]
  (s/and
    (d/dict*
      ^:opt {:from (s/and (s/or :empty #{EMPTY-OPTION}
                            :value field-spec)
                     (s/conformer spec-util/tagged->value))
             :to (s/and (s/or :empty #{EMPTY-OPTION}
                          :value field-spec)
                   (s/conformer spec-util/tagged->value))})
    check-option-state))


(defn generate-changes
  [option-specs]
  {:pre [(s/assert (s/coll-of qualified-keyword?) option-specs)]}
  (reduce #(assoc %1
             (kw->name %2)
             (option-states %2))
    {}
    option-specs))


(defn generate-type-option
  [type-spec]
  {:type (s/and
           (d/dict*
             ^:opt {:from type-spec
                    :to type-spec})
           check-option-state)})


(defn has-duplicates?
  "Check duplicated items in collection."
  [items]
  (->> items
    (frequencies)
    (vals)
    (every? #(= 1 %))))
