(ns tuna.util.file
  "Utils for working with file system."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))


(def DEFAULT-ZERO-COUNT 4)


(defn list-files
  [migrations-dir]
  (->> (file-seq (io/file migrations-dir))
    (filter #(.isFile %))))


(defn read-file-obj
  [file-obj]
  (with-open [reader (io/reader file-obj)]
    (-> (slurp reader)
      (edn/read-string))))


(defn zfill
  "Convert number to string and fill with zero form left."
  ([number]
   (zfill number DEFAULT-ZERO-COUNT))
  ([number zero-count]
   (format (str "%0" zero-count "d") number)))
