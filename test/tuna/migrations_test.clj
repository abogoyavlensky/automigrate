(ns tuna.migrations-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [bond.james :as bond]
            [tuna.migrations :as migrations]
            [tuna.core :as core]
            [tuna.util.db :as db-util]
            [tuna.util.file :as file-util]
            [tuna.util.test :as test-util]
            [tuna.testing-config :as config])
  (:import [java.io FileNotFoundException]))


(use-fixtures :each
  (test-util/with-drop-tables config/DATABASE-CONN)
  (test-util/with-delete-dir config/MIGRATIONS-DIR))


(deftest test-reading-models-from-file-ok
  (let [path (str config/MODELS-DIR "feed_basic.edn")]
    (is (= {:feed
            {:fields {:id {:type :serial
                           :null false}}}}
          (file-util/read-edn path)))))


(deftest test-reading-models-from-file-err
  (let [path (str config/MODELS-DIR "not_existing.edn")]
    (is (thrown? FileNotFoundException
          (file-util/read-edn path)))))


(deftest test-create-migrations-dir-ok
  (testing "test creating dir"
    (is (false? (.isDirectory (io/file config/MIGRATIONS-DIR))))
    (#'migrations/create-migrations-dir config/MIGRATIONS-DIR)
    (is (true? (.isDirectory (io/file config/MIGRATIONS-DIR)))))
  (testing "test when dir already exists"
    (#'migrations/create-migrations-dir config/MIGRATIONS-DIR)
    (is (true? (.isDirectory (io/file config/MIGRATIONS-DIR))))))


(deftest test-make-single-migrations-for-basic-model-ok
  (#'migrations/make-migrations {:model-file (str config/MODELS-DIR "feed_basic.edn")
                                 :migrations-dir config/MIGRATIONS-DIR})
  (is (= '({:name :feed,
            :model {:fields {:id {:type :serial, :null false}}},
            :action :create-table})
        (-> (str config/MIGRATIONS-DIR "0000_create_table_feed.edn")
          (file-util/read-edn)))))


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
              :from [db-util/MIGRATIONS-TABLE]}
          (db-util/query config/DATABASE-CONN)
          (map #(dissoc % :created_at))))))


(deftest test-explain-basic-migration-ok
  #_{:clj-kondo/ignore [:private-call]}
  (bond/with-stub [[migrations/migrations-list (constantly ["0000_create_table_feed"])]
                   [file-util/safe-println (constantly nil)]
                   [migrations/read-migration (constantly
                                                '({:name :feed
                                                   :model {:fields {:id {:type :serial
                                                                         :null false
                                                                         :primary-key true}}}
                                                   :action :create-table}
                                                  {:name :account
                                                   :model {:fields {:id {:null true
                                                                         :unique true
                                                                         :type :serial}}}
                                                   :action :create-table}))]]
    (migrations/explain {:migrations-dir config/MIGRATIONS-DIR
                         :number 0})
    (is (= ["CREATE TABLE feed (id SERIAL NOT NULL PRIMARY KEY)"
            "CREATE TABLE account (id SERIAL NULL UNIQUE)"]
          (-> (bond/calls file-util/safe-println)
            (last)
            :args
            (first))))))
