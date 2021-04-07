(ns tuna.migrations-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [tuna.migrations :as migrations]
            [tuna.util.file :as util-file]
            [tuna.testing-config :as config])
  (:import [java.io FileNotFoundException]))


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
  (is (true? (.isDirectory (io/file config/MIGRATIONS-DIR))))
  (io/delete-file config/MIGRATIONS-DIR))


(deftest test-make-single-migrations-for-basic-model-ok
  (is (false? (.isDirectory (io/file config/MIGRATIONS-DIR))))
  (#'migrations/make-migrations {:model-file (str config/MODELS-DIR "feed_basic.edn")
                                 :migrations-dir config/MIGRATIONS-DIR})
  (is (= '({:name :feed,
            :model {:fields {:id {:type :serial, :null false}}},
            :action :create-table})
        (-> (str config/MIGRATIONS-DIR "0000_create_table_feed.edn")
          (slurp)
          (edn/read-string))))
  (util-file/delete-recursively config/MIGRATIONS-DIR))


; TODO: remove!
;(prn (:db-url (config)))
;(let [db-uri (:db-uri (config))]
;  (->> {:select [:*]
;        :from [:pg_catalog.pg_tables]}
;    (hsql/format)
;    (jdbc/query {:connection-uri db-uri})
;    (prn))))
