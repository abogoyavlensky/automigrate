(ns automigrate.util.model
  (:require [clojure.spec.alpha :as s]
            [spec-dict :as d]
            [automigrate.util.spec :as spec-util]
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
  (reduce-kv
    (fn [m k v]
      (assoc m (kw->kebab-case k) v))
    {}
    map-kw))


(defn- remove-empty-option
  "Remove option key if value by `option-key` direction is empty."
  [option-key m k v]
  (if (not= EMPTY-OPTION (get v option-key))
    (assoc m k (get v option-key))
    m))


(defn changes-to-add
  ([changes]
   (changes-to-add changes OPTION-KEY-FORWARD))
  ([changes option-key]
   {:pre [(s/assert ::option-key option-key)]}
   (reduce-kv (partial remove-empty-option option-key) {} changes)))


(defn changes-to-drop
  ([changes]
   (changes-to-drop changes OPTION-KEY-FORWARD))
  ([changes option-key]
   {:pre [(s/assert ::option-key option-key)]}
   (->> changes
     (filter #(= EMPTY-OPTION (get (val %) option-key)))
     (map key)
     (set))))


(defn check-option-state
  [value]
  (not= (get value OPTION-KEY-BACKWARD) (get value OPTION-KEY-FORWARD)))


(defn option-states
  [field-spec]
  (s/and
    (d/dict*
      ^:opt {OPTION-KEY-BACKWARD (s/and (s/or :empty #{EMPTY-OPTION}
                                          :value field-spec)
                                   (s/conformer spec-util/tagged->value))
             OPTION-KEY-FORWARD (s/and (s/or :empty #{EMPTY-OPTION}
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
             ^:opt {OPTION-KEY-BACKWARD type-spec
                    OPTION-KEY-FORWARD type-spec})
           check-option-state)})


(defn has-duplicates?
  "Check duplicated items in collection."
  [items]
  (->> items
    (frequencies)
    (vals)
    (every? #(= 1 %))))
