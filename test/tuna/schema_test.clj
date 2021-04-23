(ns tuna.schema-test
  (:require [clojure.test :refer :all]
            [bond.james :as bond]
            [tuna.migrations :as migrations]
            [tuna.schema :as schema]
            [tuna.util.file :as file-util]))


(deftest test-make-migrations*-add-column-ok
  #_{:clj-kondo/ignore [:private-call]}
  (bond/with-stub [[schema/load-migrations-from-files (constantly
                                                        '(({:action :create-table,
                                                            :name :feed,
                                                            :fields {:id {:type :serial, :null false}}})))]
                   [file-util/read-edn (constantly {:feed
                                                    {:fields {:id {:type :serial
                                                                   :null false}
                                                              :name {:type [:varchar 100]
                                                                     :null true}
                                                              :created_at {:type :timestamp
                                                                           :default [:now]}}}})]]
    (is (= '(({:action :add-column,
               :name :name,
               :table-name :feed,
               :options {:type [:varchar 100], :null true}}
              {:action :add-column,
               :name :created_at,
               :table-name :feed,
               :options {:type :timestamp, :default [:now]}}))
          (#'migrations/make-migrations* [] "")))))


(deftest test-make-migrations*-add-column-restore-ok
  #_{:clj-kondo/ignore [:private-call]}
  (bond/with-stub [[schema/load-migrations-from-files (constantly
                                                        '(({:action :create-table,
                                                            :name :feed,
                                                            :fields {:id {:type :serial, :null false}}})
                                                          ({:action :add-column,
                                                            :name :name,
                                                            :table-name :feed,
                                                            :options {:type [:varchar 100], :null true}}
                                                           {:action :add-column,
                                                            :name :created_at,
                                                            :table-name :feed,
                                                            :options {:type :timestamp, :default [:now]}})))]
                   [file-util/read-edn (constantly {:feed
                                                    {:fields {:id {:type :serial
                                                                   :null false}
                                                              :name {:type [:varchar 100]
                                                                     :null true}
                                                              :created_at {:type :timestamp
                                                                           :default [:now]}}}})]]
    (is (not (seq (#'migrations/make-migrations* [] ""))))))
