(ns tuna.util.file
  "Utils for working with file system."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))


(def DEFAULT-ZERO-COUNT 4)


(defn list-files
  [migrations-dir]
  (->> (file-seq (io/file migrations-dir))
    (filter #(.isFile %))
    (sort)))


(defn read-edn
  "Return edn data from file.
  f - could be file path or reader."
  [f]
  (-> (slurp f)
    (edn/read-string)))


(defn read-file-obj
  [file-obj]
  (with-open [reader (io/reader file-obj)]
    (read-edn reader)))


(defn zfill
  "Convert number to string and fill with zero form left."
  ([number]
   (zfill number DEFAULT-ZERO-COUNT))
  ([number zero-count]
   (format (str "%0" zero-count "d") number)))


(defn safe-println
  [more]
  (.write *out*
    (str (str/join ";\n" more) "\n")))


(defn prn-err
  [e]
  (print (str (:message e) "\n")))
