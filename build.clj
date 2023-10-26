(ns build
  "Tools for building and deploying lib artefacts to Clojars."
  (:require [clojure.tools.build.api :as tools-build]
            [org.corfield.build :as build-clj]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]))


; Enable asserts for spec
(s/check-asserts true)


(def ^:private lib 'net.clojars.abogoyavlensky/automigrate)

(def ^:private SNAPSHOT-SUFFIX "-SNAPSHOT")


(defn- latest-git-tag-name
  "Return latest git tag name as a string."
  []
  (tools-build/git-process {:git-args ["describe" "--tags" "--abbrev=0"]}))


(defn- latest-git-tag-name-across-all-branches
  "Return latest git tag name as a string across all branches."
  []
  (let [latest-tag-rev (tools-build/git-process
                         {:git-args ["rev-list" "--tags" "--max-count=1"]})]
    (tools-build/git-process {:git-args ["describe" "--tags" latest-tag-rev]})))


(s/def ::version
  (s/coll-of integer? :min-count 3 :max-count 3 :kind vector?))


(s/def ::snapshot? boolean?)
(s/def ::release? boolean?)
(s/def ::bump #{:major :minor :patch})


(s/def ::version-args
  (s/keys
    :opt-un [::snapshot?
             ::release?
             ::bump]))


(defn- split-git-tag
  [git-tag]
  (mapv #(Integer. %) (str/split git-tag #"\.")))


(defn- join-git-tag
  [git-tag-splitted]
  (str/join "." (mapv str git-tag-splitted)))


(defn- bump-version
  "Bump anyu part of semver version string."
  [git-tag-name bump]
  (let [splitted-tag (split-git-tag git-tag-name)
        next-git-tag (condp = bump
                       :patch (update splitted-tag 2 inc)
                       :minor (-> splitted-tag
                                (update 1 inc)
                                (assoc 2 0))
                       :major (-> splitted-tag
                                (update 0 inc)
                                (assoc 1 0)
                                (assoc 2 0)))]
    (join-git-tag next-git-tag)))


(defn- add-snapshot
  "Add SNAPSHOT suffix to the string version."
  [git-tag-name]
  (str git-tag-name SNAPSHOT-SUFFIX))


(defn- version
  "Return version by latest tag.

  Optionally you could bump any part of version or add snapshot suffix."
  [{:keys [snapshot? bump release?] :as version-args}]
  {:pre [(s/valid? ::version-args version-args)]}
  (let [latest-version (if (true? release?)
                         (latest-git-tag-name)
                         (latest-git-tag-name-across-all-branches))
        _ (s/assert ::version (split-git-tag latest-version))
        _ (println (format "Latest version: %s" latest-version))
        new-version (cond-> latest-version
                      (some? bump) (bump-version bump)
                      (true? snapshot?) (add-snapshot))]
    _ (println (format "New version: %s" new-version))
    new-version))


(defn- create-git-tag
  [{version-name :version
    :as opts}]
  (tools-build/git-process
    {:git-args ["tag" "-a" version-name "-m" (format "'Release version %s'" version-name)]})
  opts)


(defn- push-git-tag
  [{version-name :version
    :as opts}]
  (tools-build/git-process {:git-args ["push" "origin" version-name]})
  opts)


; Public

(defn build
  "Build a jar-file for the lib."
  [opts]
  (-> opts
    (assoc
      :lib lib
      :version (version (select-keys opts [:snapshot? :release? :bump]))
      :src-pom "templates/pom.xml")
    (build-clj/clean)
    (build-clj/jar)))


(defn install
  "Build and install jar-file to the local repo."
  [opts]
  (-> opts
    (build)
    (build-clj/install)))


(defn deploy
  "Build and deploy the jar-file to Clojars."
  [opts]
  (-> opts
    (build)
    (build-clj/deploy)))


(defn release
  "Bump the latest git tag version, create and push new git tag with next version."
  [opts]
  (-> opts
    (assoc
      :lib lib
      :version (version (select-keys opts [:bump])))
    (create-git-tag)
    (push-git-tag)))
