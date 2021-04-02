(ns tuna.util.file
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))


(defn list-files
  [migrations-dir]
  (->> (file-seq (io/file migrations-dir))
       (filter #(.isFile %))))


(defn read-file-obj
  [file-obj]
  (with-open [reader (io/reader file-obj)]
    (-> (slurp reader)
        (edn/read-string))))
