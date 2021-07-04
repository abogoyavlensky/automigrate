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
                                                            :model-name :feed,
                                                            :fields {:id {:type :serial, :null false}}})))]
                   [file-util/read-edn (constantly {:feed
                                                    {:fields [[:id :serial {:null false}]
                                                              [:name [:varchar 100] {:null true}]
                                                              [:created_at :timestamp {:default [:now]}]]}})]]
    (is (= '({:action :add-column,
              :field-name :created-at,
              :model-name :feed,
              :options {:type :timestamp, :default [:now]}}
             {:action :add-column,
              :field-name :name,
              :model-name :feed,
              :options {:type [:varchar 100], :null true}})
          (#'migrations/make-migrations* [] "")))))


(deftest test-make-migrations*-add-column-restore-ok
  #_{:clj-kondo/ignore [:private-call]}
  (bond/with-stub [[schema/load-migrations-from-files (constantly
                                                        '(({:action :create-table,
                                                            :model-name :feed,
                                                            :fields {:id {:type :serial, :null false}}})
                                                          ({:action :add-column,
                                                            :field-name :name,
                                                            :model-name :feed,
                                                            :options {:type [:varchar 100], :null true}}
                                                           {:action :add-column,
                                                            :field-name :created-at,
                                                            :model-name :feed,
                                                            :options {:type :timestamp, :default [:now]}})))]
                   [file-util/read-edn (constantly {:feed
                                                    [[:id :serial {:null false}]
                                                     [:name [:varchar 100] {:null true}]
                                                     [:created_at :timestamp {:default [:now]}]]})]]
    (is (not (seq (#'migrations/make-migrations* [] ""))))))


(deftest test-make-migrations*-alter-column-restore-ok
  #_{:clj-kondo/ignore [:private-call]}
  (bond/with-stub [[schema/load-migrations-from-files (constantly
                                                        '(({:action :create-table,
                                                            :model-name :feed,
                                                            :fields {:id {:type :serial, :null false}}})
                                                          ({:action :add-column,
                                                            :field-name :name,
                                                            :model-name :feed,
                                                            :options {:type [:varchar 100], :null true}}
                                                           {:action :add-column,
                                                            :field-name :created-at,
                                                            :model-name :feed,
                                                            :options {:type :timestamp, :default [:now]}})
                                                          ({:action :alter-column,
                                                            :field-name :created-at,
                                                            :model-name :feed,
                                                            :changes {:type {:to :date :from :timestamp}
                                                                      :default {:to :EMPTY :from [:now]}}}
                                                           {:action :alter-column,
                                                            :field-name :name,
                                                            :model-name :feed,
                                                            :changes {:type {:to :text :from [:varchar 50]}
                                                                      :null {:to :EMPTY :from true}}})))]
                   [file-util/read-edn (constantly {:feed
                                                    {:fields [[:id :serial {:null false}]
                                                              [:name :text]
                                                              [:created_at :date]]}})]]
    (is (not (seq (#'migrations/make-migrations* [] ""))))))


(deftest test-make-migrations*-drop-column-restore-ok
  #_{:clj-kondo/ignore [:private-call]}
  (bond/with-stub [[schema/load-migrations-from-files
                    (constantly
                      '(({:action :create-table
                          :model-name :feed
                          :fields {:id {:type :serial
                                        :null false}}})
                        ({:action :add-column
                          :field-name :name
                          :model-name :feed
                          :options {:type [:varchar 100]
                                    :null true}}
                         {:action :add-column
                          :field-name :created_at
                          :model-name :feed
                          :options {:type :timestamp
                                    :default [:now]}})
                        ({:action :drop-column
                          :field-name :created_at
                          :model-name :feed})))]
                   [file-util/read-edn (constantly {:feed
                                                    {:fields [[:id :serial {:null false}]
                                                              [:name [:varchar 100] {:null true}]]}})]]
    (is (not (seq (#'migrations/make-migrations* [] ""))))))


(deftest test-make-migrations*-drop-table-restore-ok
  #_{:clj-kondo/ignore [:private-call]}
  (bond/with-stub [[schema/load-migrations-from-files
                    (constantly
                      '(({:action :create-table
                          :model-name :feed
                          :fields {:id {:type :serial
                                        :null false}}})
                        ({:action :create-table
                          :model-name :account
                          :fields {:id {:type :serial
                                        :null false}
                                   :name {:type [:varchar 256]}}})
                        ({:action :drop-table
                          :model-name :feed})))]
                   [file-util/read-edn (constantly {:account
                                                    [[:id :serial {:null false}]
                                                     [:name [:varchar 256]]]})]]
    (is (not (seq (#'migrations/make-migrations* [] ""))))))


(deftest test-make-migrations*-foreign-key-restore-ok
  #_{:clj-kondo/ignore [:private-call]}
  (bond/with-stub [[schema/load-migrations-from-files
                    (constantly
                      '(({:action :create-table
                          :model-name :feed
                          :fields {:id {:type :serial
                                        :null false}
                                   :account {:type :integer
                                             :foreign-key :account/id}}})
                        ({:action :create-table
                          :model-name :account
                          :fields {:id {:type :serial
                                        :unique true}
                                   :name {:type [:varchar 256]}}})))]
                   [file-util/read-edn (constantly {:feed
                                                    {:fields [[:id :serial {:null false}]
                                                              [:account :integer {:foreign-key :account/id}]]}
                                                    :account
                                                    {:fields [[:id :serial {:unique true}]
                                                              [:name [:varchar 256]]]}})]]
    (is (not (seq (#'migrations/make-migrations* [] ""))))))


(deftest test-make-migrations*-create-index-restore-ok
  #_{:clj-kondo/ignore [:private-call]}
  (bond/with-stub [[schema/load-migrations-from-files
                    (constantly
                      '(({:action :create-table
                          :model-name :feed
                          :fields {:id {:type :serial
                                        :null false}
                                   :name {:type :text}}}
                         {:action :create-index
                          :index-name :feed-name-id-unique-idx
                          :model-name :feed
                          :options {:type :btree
                                    :fields [:name :id]
                                    :unique true}})))]
                   [file-util/read-edn (constantly {:feed
                                                    {:fields [[:id :serial {:null false}]
                                                              [:name :text]]
                                                     :indexes [[:feed_name_id_unique_idx :btree {:fields [:name :id]
                                                                                                 :unique true}]]}})]]
    (is (not (seq (#'migrations/make-migrations* [] ""))))))


(deftest test-make-migrations*-drop-index-restore-ok
  #_{:clj-kondo/ignore [:private-call]}
  (bond/with-stub [[schema/load-migrations-from-files
                    (constantly
                      '(({:action :create-table
                          :model-name :feed
                          :fields {:id {:type :serial
                                        :null false}
                                   :name {:type :text}}}
                         {:action :create-index
                          :index-name :feed_name_id_unique_idx
                          :model-name :feed
                          :options {:type :btree
                                    :fields [:name :id]
                                    :unique true}}
                         {:action :drop-index
                          :index-name :feed_name_id_unique_idx
                          :model-name :feed})))]
                   [file-util/read-edn (constantly {:feed
                                                    {:fields [[:id :serial {:null false}]
                                                              [:name :text]]}})]]
    (is (not (seq (#'migrations/make-migrations* [] ""))))))


(deftest test-make-migrations*-alter-index-restore-ok
  #_{:clj-kondo/ignore [:private-call]}
  (bond/with-stub [[schema/load-migrations-from-files
                    (constantly
                      '(({:action :create-table
                          :model-name :feed
                          :fields {:id {:type :serial
                                        :null false}
                                   :name {:type :text}}}
                         {:action :create-index
                          :index-name :feed-name-id-idx
                          :model-name :feed
                          :options {:type :btree
                                    :fields [:name :id]
                                    :unique true}}
                         {:action :alter-index
                          :index-name :feed-name-id-idx
                          :model-name :feed
                          :options {:type :btree
                                    :fields [:name :id]}})))]
                   [file-util/read-edn (constantly {:feed
                                                    {:fields [[:id :serial {:null false}]
                                                              [:name :text]]
                                                     :indexes [[:feed_name_id_idx :btree {:fields [:name :id]}]]}})]]
    (is (not (seq (#'migrations/make-migrations* [] ""))))))
