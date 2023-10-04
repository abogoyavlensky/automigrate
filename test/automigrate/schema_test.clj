(ns automigrate.schema-test
  (:require [clojure.test :refer :all]
            [bond.james :as bond]
            [automigrate.migrations :as migrations]
            [automigrate.schema :as schema]
            [automigrate.util.file :as file-util]))


(deftest test-make-migration*-add-column-ok
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
          (#'migrations/make-migration* "" [])))))


(deftest test-make-migration*-add-column-decimal-ok
  ; read pre-existing migration for creating a model with just the id field
  (bond/with-stub [[schema/load-migrations-from-files (constantly
                                                        '(({:action :create-table,
                                                            :model-name :feed,
                                                            :fields {:id {:type :serial, :null false}}})))]
                   ; read the same model with new decimal fields with different params
                   [file-util/read-edn (constantly {:feed
                                                    {:fields [[:id :serial {:null false}]
                                                              [:amount [:decimal 10 2] {:null true}]
                                                              [:balance [:decimal 10] {:default 47.23}]
                                                              [:tx :decimal]]}})]]
    (is (= '({:action :add-column
              :field-name :tx
              :model-name :feed
              :options {:type :decimal}}
             {:action :add-column
              :field-name :amount
              :model-name :feed
              :options {:type [:decimal 10 2] :null true}}
             {:action :add-column
              :field-name :balance
              :model-name :feed
              :options {:type [:decimal 10] :default 47.23}})
          (#'migrations/make-migration* "" [])))))


(deftest test-make-migration*-add-column-restore-ok
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
    (is (not (seq (#'migrations/make-migration* "" []))))))


(deftest test-make-migration*-add-column-restore-decimal-ok
  (bond/with-stub [[schema/load-migrations-from-files
                    (constantly
                      '(({:action :create-table
                          :model-name :feed
                          :fields {:id {:type :serial :null false}
                                   :amount {:type [:decimal 10 2] :null false}}})))]

                   [file-util/read-edn
                    (constantly {:feed
                                 [[:id :serial {:null false}]
                                  [:amount [:decimal 10 2] {:null false}]]})]]
    (is (= [] (#'migrations/make-migration* "" [])))))


(deftest test-make-migration*-alter-column-decimal-ok
  (bond/with-stub [[schema/load-migrations-from-files
                    (constantly
                      '(({:action :create-table
                          :model-name :feed
                          :fields {:id {:type :serial :null false}
                                   :amount {:type [:decimal 10 2] :null false}}})))]

                   [file-util/read-edn
                    (constantly {:feed
                                 [[:id :serial {:null false}]
                                  [:amount [:decimal 10] {:null false}]]})]]
    (is (= '({:action :alter-column
              :changes {:type {:from [:decimal 10 2]
                               :to [:decimal 10]}}
              :field-name :amount
              :model-name :feed
              :options {:null false
                        :type [:decimal 10]}})
          (#'migrations/make-migration* "" [])))))


(deftest test-make-migration*-alter-column-restore-ok
  (bond/with-stub [[schema/load-migrations-from-files (constantly
                                                        '(({:action :create-table
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
                                                            :options {:type :date}
                                                            :changes {:type {:to :date :from :timestamp}
                                                                      :default {:to :EMPTY :from [:now]}}}
                                                           {:action :alter-column,
                                                            :field-name :name,
                                                            :model-name :feed,
                                                            :options {:type :text}
                                                            :changes {:type {:to :text :from [:varchar 50]}
                                                                      :null {:to :EMPTY :from true}}})))]
                   [file-util/read-edn (constantly {:feed
                                                    {:fields [[:id :serial {:null false}]
                                                              [:name :text]
                                                              [:created_at :date]]}})]]
    (is (not (seq (#'migrations/make-migration* "" []))))))


(deftest test-make-migration*-drop-column-restore-ok
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
    (is (not (seq (#'migrations/make-migration* "" []))))))


(deftest test-make-migration*-drop-table-restore-ok
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
    (is (not (seq (#'migrations/make-migration* "" []))))))


(deftest test-make-migration*-foreign-key-restore-ok
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
    (is (not (seq (#'migrations/make-migration* "" []))))))


(deftest test-make-migration*-create-index-restore-ok
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
    (is (not (seq (#'migrations/make-migration* "" []))))))


(deftest test-make-migration*-drop-index-restore-ok
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
    (is (not (seq (#'migrations/make-migration* "" []))))))


(deftest test-make-migration*-alter-index-restore-ok
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
    (is (not (seq (#'migrations/make-migration* "" []))))))


(deftest test-make-migration*-create-table-with-fk-on-delete-restore-ok
  (let [existing-actions '({:action :create-table
                            :model-name :account
                            :fields {:id {:type :serial
                                          :unique true
                                          :primary-key true}}}
                           {:action :create-table
                            :model-name :feed
                            :fields {:id {:type :serial}
                                     :name {:type :text}
                                     :account {:type :integer
                                               :foreign-key :account/id
                                               :on-delete :cascade}}})
        existing-models {:feed
                         {:fields [[:id :serial]
                                   [:name :text]
                                   [:account :integer {:foreign-key :account/id
                                                       :on-delete :cascade}]]}
                         :account [[:id :serial {:unique true
                                                 :primary-key true}]]}]
    (bond/with-stub [[schema/load-migrations-from-files
                      (constantly existing-actions)]
                     [file-util/read-edn (constantly existing-models)]]
      (is (not (seq (#'migrations/make-migration* "" [])))))))


(deftest test-make-migration*-alter-column-with-fk-on-delete-restore-ok
  (let [existing-actions '({:action :create-table
                            :model-name :account
                            :fields {:id {:type :serial
                                          :unique true
                                          :primary-key true}}}
                           {:action :create-table
                            :model-name :feed
                            :fields {:id {:type :serial}
                                     :name {:type :text}
                                     :account {:type :integer
                                               :foreign-key :account/id
                                               :on-delete :cascade}}}
                           {:action :alter-column
                            :field-name :account
                            :model-name :feed
                            :options {:type :integer
                                      :foreign-key :account/id
                                      :on-delete :set-null}
                            :changes {:on-delete {:from :cascade :to :set-null}}})
        existing-models {:feed
                         {:fields [[:id :serial]
                                   [:name :text]
                                   [:account :integer {:foreign-key :account/id
                                                       :on-delete :set-null}]]}
                         :account [[:id :serial {:unique true
                                                 :primary-key true}]]}]
    (bond/with-stub [[schema/load-migrations-from-files
                      (constantly existing-actions)]
                     [file-util/read-edn (constantly existing-models)]]
      (is (not (seq (#'migrations/make-migration* "" [])))))))
