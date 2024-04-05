(ns automigrate.util.file
  "Utils for working with file system."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [resauce.core :as resauce])
  (:import [java.nio.file Paths]
           [com.github.vertical_blank.sqlformatter SqlFormatter]
           [com.github.vertical_blank.sqlformatter.languages Dialect]))


(def DEFAULT-ZERO-COUNT 4)


(defn list-files
  [migrations-dir]
  (resauce/resource-dir migrations-dir))


(defn read-edn
  "Return edn data from file.
  f - could be file path, file object or reader."
  [f]
  (edn/read-string (slurp f)))


(defn zfill
  "Convert number to string and fill with zero form left."
  ([number]
   (zfill number DEFAULT-ZERO-COUNT))
  ([number zero-count]
   (format (str "%0" zero-count "d") number)))


(defn fmt-sql
  [sql-str]
  ; TODO: switch to Dialect/StandardSql for other databases by default!
  (.format (SqlFormatter/of Dialect/PostgreSql) sql-str))


(defn safe-println
  ([more]
   (safe-println more ";"))
  ([more delimiter]
   (.write *out*
     (str (str/join (str delimiter "\n") more) "\n"))))


; TODO: reduce duplication with safe-println!
(defn safe-println-sql
  ([more]
   (safe-println-sql more ";"))
  ([more delimiter]
   (.write *out*
     (str (str/join (str delimiter "\n\n") more) "\n"))))


(defn prn-err
  [e]
  (print (str (:message e) "\n")))


(defn join-path
  "Join multiple pieces into single file path.
  Origin implementation: https://clojureverse.org/t/how-to-join-file-paths/814"
  [p & ps]
  (str (.normalize (Paths/get p (into-array String ps)))))
