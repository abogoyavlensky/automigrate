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
    (is (= '({:action :add-column,
              :name :created_at,
              :table-name :feed,
              :options {:type :timestamp, :default [:now]}}
             {:action :add-column,
              :name :name,
              :table-name :feed,
              :options {:type [:varchar 100], :null true}})
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


(deftest test-make-migrations*-alter-column-restore-ok
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
                                                            :options {:type :timestamp, :default [:now]}})
                                                          ({:action :alter-column,
                                                            :name :created_at,
                                                            :table-name :feed,
                                                            :changes {:type :date}
                                                            :drop #{:default}}
                                                           {:action :alter-column,
                                                            :name :name,
                                                            :table-name :feed,
                                                            :changes {:type :text}
                                                            :drop #{:null}})))]
                   [file-util/read-edn (constantly {:feed
                                                    {:fields {:id {:type :serial
                                                                   :null false}
                                                              :name {:type :text}
                                                              :created_at {:type :date}}}})]]
    (is (not (seq (#'migrations/make-migrations* [] ""))))))


(deftest test-make-migrations*-drop-column-restore-ok
  #_{:clj-kondo/ignore [:private-call]}
  (bond/with-stub [[schema/load-migrations-from-files
                    (constantly
                      '(({:action :create-table
                          :name :feed
                          :fields {:id {:type :serial
                                        :null false}}})
                        ({:action :add-column
                          :name :name
                          :table-name :feed
                          :options {:type [:varchar 100]
                                    :null true}}
                         {:action :add-column
                          :name :created_at
                          :table-name :feed
                          :options {:type :timestamp
                                    :default [:now]}})
                        ({:action :drop-column
                          :name :created_at
                          :table-name :feed})))]
                   [file-util/read-edn (constantly {:feed
                                                    {:fields {:id {:type :serial
                                                                   :null false}
                                                              :name {:type [:varchar 100]
                                                                     :null true}}}})]]
    (is (not (seq (#'migrations/make-migrations* [] ""))))))


(deftest test-make-migrations*-drop-table-restore-ok
  #_{:clj-kondo/ignore [:private-call]}
  (bond/with-stub [[schema/load-migrations-from-files
                    (constantly
                      '(({:action :create-table
                          :name :feed
                          :fields {:id {:type :serial
                                        :null false}}})
                        ({:action :create-table
                          :name :account
                          :fields {:id {:type :serial
                                        :null false}
                                   :name {:type [:varchar 256]}}})
                        ({:action :drop-table
                          :name :feed})))]
                   [file-util/read-edn (constantly {:account
                                                    {:fields {:id {:type :serial
                                                                   :null false}
                                                              :name {:type [:varchar 256]}}}})]]
    (is (not (seq (#'migrations/make-migrations* [] ""))))))


(deftest test-make-migrations*-foreign-key-restore-ok
  #_{:clj-kondo/ignore [:private-call]}
  (bond/with-stub [[schema/load-migrations-from-files
                    (constantly
                      '(({:action :create-table
                          :name :feed
                          :fields {:id {:type :serial
                                        :null false}
                                   :account {:type :integer
                                             :foreign-key [:account :id]}}})
                        ({:action :create-table
                          :name :account
                          :fields {:id {:type :serial
                                        :unique true}
                                   :name {:type [:varchar 256]}}})))]
                   [file-util/read-edn (constantly {:feed
                                                    {:fields {:id {:type :serial
                                                                   :null false}
                                                              :account {:type :integer
                                                                        :foreign-key [:account :id]}}}
                                                    :account
                                                    {:fields {:id {:type :serial
                                                                   :unique true}
                                                              :name {:type [:varchar 256]}}}})]]
    (is (not (seq (#'migrations/make-migrations* [] ""))))))


(deftest test-make-migrations*-create-index-restore-ok
  #_{:clj-kondo/ignore [:private-call]}
  (bond/with-stub [[schema/load-migrations-from-files
                    (constantly
                      '(({:action :create-table
                          :name :feed
                          :fields {:id {:type :serial
                                        :null false}
                                   :name {:type :text}}}
                         {:action :create-index
                          :name :feed_name_id_unique_idx
                          :table-name :feed
                          :options {:type :btree
                                    :fields [:name :id]
                                    :unique true}})))]
                   [file-util/read-edn (constantly {:feed
                                                    {:fields {:id {:type :serial
                                                                   :null false}
                                                              :name {:type :text}}
                                                     :indexes {:feed_name_id_unique_idx {:type :btree
                                                                                         :fields [:name :id]
                                                                                         :unique true}}}})]]
    (is (not (seq (#'migrations/make-migrations* [] ""))))))


(deftest test-make-migrations*-drop-index-restore-ok
  #_{:clj-kondo/ignore [:private-call]}
  (bond/with-stub [[schema/load-migrations-from-files
                    (constantly
                      '(({:action :create-table
                          :name :feed
                          :fields {:id {:type :serial
                                        :null false}
                                   :name {:type :text}}}
                         {:action :create-index
                          :name :feed_name_id_unique_idx
                          :table-name :feed
                          :options {:type :btree
                                    :fields [:name :id]
                                    :unique true}}
                         {:action :drop-index
                          :name :feed_name_id_unique_idx
                          :table-name :feed})))]
                   [file-util/read-edn (constantly {:feed
                                                    {:fields {:id {:type :serial
                                                                   :null false}
                                                              :name {:type :text}}}})]]
    (is (not (seq (#'migrations/make-migrations* [] ""))))))


(deftest test-make-migrations*-alter-index-restore-ok
  #_{:clj-kondo/ignore [:private-call]}
  (bond/with-stub [[schema/load-migrations-from-files
                    (constantly
                      '(({:action :create-table
                          :name :feed
                          :fields {:id {:type :serial
                                        :null false}
                                   :name {:type :text}}}
                         {:action :create-index
                          :name :feed_name_id_idx
                          :table-name :feed
                          :options {:type :btree
                                    :fields [:name :id]
                                    :unique true}}
                         {:action :alter-index
                          :name :feed_name_id_idx
                          :table-name :feed
                          :options {:type :btree
                                    :fields [:name :id]}})))]
                   [file-util/read-edn (constantly {:feed
                                                    {:fields {:id {:type :serial
                                                                   :null false}
                                                              :name {:type :text}}
                                                     :indexes {:feed_name_id_idx {:type :btree
                                                                                  :fields [:name :id]}}}})]]
    (is (not (seq (#'migrations/make-migrations* [] ""))))))
