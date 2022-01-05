(ns build
  "Tools for building and deploying lib artefacts to Clojars."
  (:require [org.corfield.build :as build-clj]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]))


; Enable asserts for spec in function's pre and post conditions
(s/check-asserts true)


(def ^:private lib 'com.github.abogoyavlensky/automigrate)

(def ^:private SNAPSHOT-SUFFIX "-SNAPSHOT")


(defn- latest-git-tag-name
  "Return latest git tag name as a string."
  []
  ; TODO: uncomment!
  ;(tools-build/git-process {:git-args ["describe" "--tags" "--abbrev=0"]}))
  "0.1.0")


(s/def ::str-int
  #(integer? (Integer. %)))


(s/def ::version
  (s/coll-of ::str-int :min-count 3 :max-count 3 :kind vector?))


(defn- version
  "Return version by latest tag with snapshot suffix optionally."
  [{snapshot? :snapshot}]
  (let [latest-version (latest-git-tag-name)]
    (s/assert ::version (str/split latest-version #"\."))
    (if (true? snapshot?)
      (str latest-version SNAPSHOT-SUFFIX)
      latest-version)))


(defn- assoc-version
  [opts]
  (assoc opts
    :lib lib
    :version (version opts)))

; Public

(defn build
  "Build a jar file for the lib."
  [opts]
  (-> opts
    (assoc-version)
    (build-clj/clean)
    (build-clj/jar)))


(defn install
  "Build a jar file for the lib and install it to the local repo."
  [opts]
  (-> opts
    (assoc-version)
    (build-clj/install)))
