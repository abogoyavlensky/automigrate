(ns tuna.migrations-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [tuna.migrations :as migrations]
            [tuna.core :as core]
            [tuna.util.db :as util-db]
            [tuna.util.test :as util-test]
            [tuna.testing-config :as config])
  (:import [java.io FileNotFoundException]))


(use-fixtures :each
  (util-test/with-drop-tables config/DATABASE-CONN)
  (util-test/with-delete-dir config/MIGRATIONS-DIR))


(deftest test-reading-models-from-file-ok
  (let [path (str config/MODELS-DIR "feed_basic.edn")]
    (is (= {:feed
            {:fields {:id {:type :serial
                           :null false}}}}
          (#'migrations/models path)))))


(deftest test-reading-models-from-file-err
  (let [path (str config/MODELS-DIR "not_existing.edn")]
    (is (thrown? FileNotFoundException
          (#'migrations/models path)))))


(deftest test-create-migrations-dir-ok
  (is (false? (.isDirectory (io/file config/MIGRATIONS-DIR))))
  (#'migrations/create-migrations-dir config/MIGRATIONS-DIR)
  (is (true? (.isDirectory (io/file config/MIGRATIONS-DIR)))))


(deftest test-make-single-migrations-for-basic-model-ok
  (#'migrations/make-migrations {:model-file (str config/MODELS-DIR "feed_basic.edn")
                                 :migrations-dir config/MIGRATIONS-DIR})
  (is (= '({:name :feed,
            :model {:fields {:id {:type :serial, :null false}}},
            :action :create-table})
        (-> (str config/MIGRATIONS-DIR "0000_create_table_feed.edn")
          (slurp)
          (edn/read-string)))))


(deftest test-migrate-single-migrations-for-basic-model-ok
  (core/run {:action :make-migrations
             :model-file (str config/MODELS-DIR "feed_basic.edn")
             :migrations-dir config/MIGRATIONS-DIR})
  (core/run {:action :migrate
             :migrations-dir config/MIGRATIONS-DIR
             :db-uri config/DATABASE-URL})
  (is (= '({:id 1
            :name "0000_create_table_feed"})
        (->> {:select [:*]
              :from [migrations/MIGRATIONS-TABLE]}
          (util-db/query config/DATABASE-CONN)
          (map #(dissoc % :created_at))))))
