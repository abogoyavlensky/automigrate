(ns tuna.migrations-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [tuna.migrations :as migrations])
  (:import [java.io FileNotFoundException]))


(deftest test-reading-models-from-file-ok
  (let [path "test/tuna/models/feed_basic.edn"]
    (is (= {:feed
            {:fields {:id {:type :serial
                           :null false}}}}
          (#'migrations/models path)))))


(deftest test-reading-models-from-file-err
  (let [path "test/tuna/models/not_existing.edn"]
    (is (thrown? FileNotFoundException
          (#'migrations/models path)))))


(deftest test-create-migrations-dir-ok
  (let [path "test/tuna/migrations"]
    (is (false? (.isDirectory (io/file path))))
    (#'migrations/create-migrations-dir path)
    (is (true? (.isDirectory (io/file path))))
    (io/delete-file path)))
