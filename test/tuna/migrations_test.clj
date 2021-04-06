(ns tuna.migrations-test
  (:require [clojure.test :refer :all]
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
