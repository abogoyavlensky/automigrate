(ns tuna.migrations-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [bond.james :as bond]
            [tuna.migrations :as migrations]
            [tuna.core :as core]
            [tuna.schema :as schema]
            [tuna.sql :as sql]
            [tuna.util.db :as db-util]
            [tuna.util.file :as file-util]
            [tuna.util.spec :as spec-util]
            [tuna.util.test :as test-util]
            [tuna.testing-config :as config])
  (:import [java.io FileNotFoundException]))


(use-fixtures :each
  (test-util/with-drop-tables config/DATABASE-CONN)
  (test-util/with-delete-dir config/MIGRATIONS-DIR))


(deftest test-reading-models-from-file-ok
  (let [path (str config/MODELS-DIR "feed_basic.edn")]
    (is (= {:feed
            {:fields [[:id :serial {:null false}]]}}
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
  (is (= '({:name :feed
            :fields {:id {:type :serial :null false}}
            :action :create-table})
        (-> (str config/MIGRATIONS-DIR "0001_auto_create_table_feed.edn")
          (file-util/read-edn)))))


(deftest test-migrate-single-migrations-for-basic-model-ok
  (core/run {:action :make-migrations
             :model-file (str config/MODELS-DIR "feed_basic.edn")
             :migrations-dir config/MIGRATIONS-DIR})
  (core/run {:action :migrate
             :migrations-dir config/MIGRATIONS-DIR
             :db-uri config/DATABASE-URL})
  (is (= '({:id 1
            :name "0001_auto_create_table_feed"})
        (->> {:select [:*]
              :from [db-util/MIGRATIONS-TABLE]}
          (db-util/exec! config/DATABASE-CONN)
          (map #(dissoc % :created_at))))))


(deftest test-migrate-migrations-with-adding-columns-ok
  (core/run {:action :make-migrations
             :model-file (str config/MODELS-DIR "feed_basic.edn")
             :migrations-dir config/MIGRATIONS-DIR})
  (core/run {:action :make-migrations
             :model-file (str config/MODELS-DIR "feed_add_column.edn")
             :migrations-dir config/MIGRATIONS-DIR})
  (is (= '({:action :add-column
            :name :created_at
            :model-name :feed
            :options {:type :timestamp, :default [:now]}}
           {:action :add-column
            :name :name
            :model-name :feed
            :options {:type [:varchar 100] :null true}})
        (-> (str config/MIGRATIONS-DIR "0002_auto_add_column_created_at.edn")
          (file-util/read-edn))))
  (core/run {:action :migrate
             :migrations-dir config/MIGRATIONS-DIR
             :db-uri config/DATABASE-URL})
  (is (= '({:id 1
            :name "0001_auto_create_table_feed"}
           {:id 2
            :name "0002_auto_add_column_created_at"})
        (->> {:select [:*]
              :from [db-util/MIGRATIONS-TABLE]}
          (db-util/exec! config/DATABASE-CONN)
          (map #(dissoc % :created_at))))))


(deftest test-migrate-migrations-with-alter-columns-ok
  (core/run {:action :make-migrations
             :model-file (str config/MODELS-DIR "feed_add_column.edn")
             :migrations-dir config/MIGRATIONS-DIR})
  (core/run {:action :make-migrations
             :model-file (str config/MODELS-DIR "feed_alter_column.edn")
             :migrations-dir config/MIGRATIONS-DIR})
  (is (= '({:action :alter-column
            :changes {:type :text}
            :drop #{:null}
            :name :name
            :model-name :feed}
           {:action :alter-column
            :changes {:primary-key true}
            :drop #{}
            :name :id
            :model-name :feed})
        (-> (str config/MIGRATIONS-DIR "0002_auto_alter_column_name.edn")
          (file-util/read-edn))))
  (core/run {:action :migrate
             :migrations-dir config/MIGRATIONS-DIR
             :db-uri config/DATABASE-URL})
  (is (= '({:id 1
            :name "0001_auto_create_table_feed"}
           {:id 2
            :name "0002_auto_alter_column_name"})
        (->> {:select [:*]
              :from [db-util/MIGRATIONS-TABLE]}
          (db-util/exec! config/DATABASE-CONN)
          (map #(dissoc % :created_at))))))


(deftest test-migrate-migrations-with-drop-columns-ok
  (core/run {:action :make-migrations
             :model-file (str config/MODELS-DIR "feed_add_column.edn")
             :migrations-dir config/MIGRATIONS-DIR})
  (core/run {:action :make-migrations
             :model-file (str config/MODELS-DIR "feed_drop_column.edn")
             :migrations-dir config/MIGRATIONS-DIR})
  (is (= '({:action :drop-column
            :name :name
            :model-name :feed})
        (-> (str config/MIGRATIONS-DIR "0002_auto_drop_column_name.edn")
          (file-util/read-edn))))
  (core/run {:action :migrate
             :migrations-dir config/MIGRATIONS-DIR
             :db-uri config/DATABASE-URL})
  (is (= '({:id 1
            :name "0001_auto_create_table_feed"}
           {:id 2
            :name "0002_auto_drop_column_name"})
        (->> {:select [:*]
              :from [db-util/MIGRATIONS-TABLE]}
          (db-util/exec! config/DATABASE-CONN)
          (map #(dissoc % :created_at))))))


(deftest test-migrate-migrations-with-drop-table-ok
  (core/run {:action :make-migrations
             :model-file (str config/MODELS-DIR "feed_add_column.edn")
             :migrations-dir config/MIGRATIONS-DIR})
  (core/run {:action :make-migrations
             :model-file (str config/MODELS-DIR "feed_drop_table.edn")
             :migrations-dir config/MIGRATIONS-DIR})
  (is (= '({:action :drop-table
            :name :feed})
        (-> (str config/MIGRATIONS-DIR "0002_auto_drop_table_feed.edn")
          (file-util/read-edn))))
  (core/run {:action :migrate
             :migrations-dir config/MIGRATIONS-DIR
             :db-uri config/DATABASE-URL})
  (is (= '({:id 1
            :name "0001_auto_create_table_feed"}
           {:id 2
            :name "0002_auto_drop_table_feed"})
        (->> {:select [:*]
              :from [db-util/MIGRATIONS-TABLE]}
          (db-util/exec! config/DATABASE-CONN)
          (map #(dissoc % :created_at))))))


(deftest test-explain-basic-migration-ok
  #_{:clj-kondo/ignore [:private-call]}
  (bond/with-stub [[migrations/migrations-list (constantly ["0001_auto_create_table_feed"])]
                   [file-util/safe-println (constantly nil)]
                   [migrations/read-migration (constantly
                                                '({:name :feed
                                                   :fields {:id {:type :serial
                                                                 :null false
                                                                 :primary-key true}
                                                            :number {:type :integer
                                                                     :default 0}
                                                            :info {:type :text}}
                                                   :action :create-table}
                                                  {:name :account
                                                   :fields {:id {:null true
                                                                 :unique true
                                                                 :type :serial}
                                                            :name {:null true
                                                                   :type [:varchar 100]}
                                                            :rate {:type :float}}
                                                   :action :create-table}
                                                  {:name :role
                                                   :fields {:is-active {:type :boolean}
                                                            :created-at {:type :timestamp
                                                                         :default [:now]}}
                                                   :action :create-table}
                                                  {:name :day
                                                   :model-name :account
                                                   :options {:type :date}
                                                   :action :add-column}
                                                  {:name :number
                                                   :model-name :account
                                                   :changes {:type :integer
                                                             :unique true
                                                             :default 0}
                                                   :drop #{:primary-key :null}
                                                   :action :alter-column}
                                                  {:name :url
                                                   :model-name :feed
                                                   :action :drop-column}
                                                  {:name :feed
                                                   :action :drop-table}
                                                  {:name :feed
                                                   :fields {:account {:type :serial
                                                                      :foreign-key [:account :id]}}
                                                   :action :create-table}
                                                  {:name :account
                                                   :model-name :feed
                                                   :changes nil
                                                   :drop #{:foreign-key}
                                                   :action :alter-column}
                                                  {:name :account
                                                   :model-name :feed
                                                   :changes {:foreign-key [:account :id]}
                                                   :drop #{}
                                                   :action :alter-column}
                                                  {:name :feed_name_idx
                                                   :model-name :feed
                                                   :options {:type :btree
                                                             :fields [:name]}
                                                   :action :create-index}
                                                  {:name :feed_name_idx
                                                   :model-name :feed
                                                   :action :drop-index}
                                                  {:name :feed_name_idx
                                                   :model-name :feed
                                                   :options {:type :btree
                                                             :fields [:name]}
                                                   :action :alter-index}))]]
    (migrations/explain {:migrations-dir config/MIGRATIONS-DIR
                         :number 1})
    (is (= ["CREATE TABLE feed (id SERIAL NOT NULL PRIMARY KEY, number INTEGER DEFAULT 0, info TEXT)"
            "CREATE TABLE account (id SERIAL NULL UNIQUE, name VARCHAR(100) NULL, rate FLOAT)"
            "CREATE TABLE role (is_active BOOLEAN, created_at TIMESTAMP DEFAULT NOW())"
            "ALTER TABLE account ADD COLUMN day DATE"
            (str "ALTER TABLE account ALTER COLUMN number TYPE INTEGER, ADD UNIQUE(number), "
              "ALTER COLUMN number SET DEFAULT 0, ALTER COLUMN number SET NOT NULL, "
              "DROP CONSTRAINT account_pkey")
            "ALTER TABLE feed DROP COLUMN url"
            "DROP TABLE IF EXISTS feed"
            "CREATE TABLE feed (account SERIAL REFERENCES ACCOUNT(ID))"
            "ALTER TABLE feed DROP CONSTRAINT feed_account_fkey"
            "ALTER TABLE feed ADD CONSTRAINT feed_account_fkey FOREIGN KEY(ACCOUNT) REFERENCES ACCOUNT(ID)"
            "CREATE INDEX feed_name_idx ON FEED USING BTREE(NAME)"
            "DROP INDEX feed_name_idx"
            "DROP INDEX feed_name_idx"
            "CREATE INDEX feed_name_idx ON FEED USING BTREE(NAME)"]
          (-> (bond/calls file-util/safe-println)
            (last)
            :args
            (first))))))


(deftest test-sort-actions-ok
  (let [actions '({:action :create-table,
                   :name :foo1,
                   :fields {:id {:type :serial, :unique true}, :account {:type :integer, :foreign-key [:account :id]}}}
                  {:action :create-table,
                   :name :bar1,
                   :fields {:id {:type :serial, :unique true},
                            :foo1 {:type :integer, :foreign-key [:foo1 :id]},
                            :account {:type :integer, :foreign-key [:account :id]}}}
                  {:action :drop-column, :name :created_at, :model-name :feed}
                  {:action :add-column, :name :foo1, :model-name :account, :options {:type :integer, :foreign-key [:foo1 :id]}}
                  {:action :add-column, :name :slug, :model-name :account, :options {:type :text, :null false, :unique true}}
                  {:action :add-column, :name :bar1, :model-name :feed, :options {:type :integer, :foreign-key [:account :id]}}
                  {:action :create-table,
                   :name :articles,
                   :fields {:id {:type :serial, :unique true}, :bar1 {:type :integer, :foreign-key [:bar1 :id]}}})]
    (is (= '({:action :create-table,
              :name :foo1,
              :fields {:id {:type :serial, :unique true}, :account {:type :integer, :foreign-key [:account :id]}}}
             {:action :create-table,
              :name :bar1,
              :fields {:id {:type :serial, :unique true},
                       :foo1 {:type :integer, :foreign-key [:foo1 :id]},
                       :account {:type :integer, :foreign-key [:account :id]}}}
             {:action :add-column, :name :slug, :model-name :account, :options {:type :text, :null false, :unique true}}
             {:action :drop-column, :name :created_at, :model-name :feed}
             {:action :add-column, :name :bar1, :model-name :feed, :options {:type :integer, :foreign-key [:account :id]}}
             {:action :add-column, :name :foo1, :model-name :account, :options {:type :integer, :foreign-key [:foo1 :id]}}
             {:action :create-table,
              :name :articles,
              :fields {:id {:type :serial, :unique true}, :bar1 {:type :integer, :foreign-key [:bar1 :id]}}})
          (#'migrations/sort-actions actions)))))


(deftest test-sort-actions-with-create-index-ok
  (let [actions '({:action :create-index
                   :name :feed-name-id-idx
                   :model-name :feed
                   :options {:type :btree
                             :fields [:name :id]}}
                  {:action :create-table
                   :name :feed
                   :fields {:id {:type :serial
                                 :unique true}}}
                  {:action :add-column
                   :name :name
                   :model-name :feed
                   :options {:type :text}})]

    (is (= '({:action :create-table
              :name :feed
              :fields {:id {:type :serial
                            :unique true}}}
             {:action :add-column
              :name :name
              :model-name :feed
              :options {:type :text}}
             {:action :create-index
              :name :feed-name-id-idx
              :model-name :feed
              :options {:type :btree
                        :fields [:name :id]}})
          (#'migrations/sort-actions actions)))))


(deftest test-sort-actions-with-alter-index-ok
  (let [actions '({:action :alter-index
                   :name :feed-name-id-idx
                   :model-name :feed
                   :options {:type :btree
                             :fields [:name :id]}}
                  {:action :add-column
                   :name :name
                   :model-name :feed
                   :options {:type :text}})]

    (is (= '({:action :add-column
              :name :name
              :model-name :feed
              :options {:type :text}}
             {:action :alter-index
              :name :feed-name-id-idx
              :model-name :feed
              :options {:type :btree
                        :fields [:name :id]}})
          (#'migrations/sort-actions actions)))))


(deftest test-make-and-migrate-create-index-on-new-model-ok
  (let [existing-actions '()]
    #_{:clj-kondo/ignore [:private-call]}
    (bond/with-stub [[schema/load-migrations-from-files
                      (constantly existing-actions)]
                     [file-util/read-edn (constantly {:feed
                                                      {:fields [[:id :serial {:null false}]
                                                                [:name :text]]
                                                       :indexes [[:feed-name-id-unique-idx :btree {:fields [:name]
                                                                                                   :unique true}]]}})]]
      (let [db config/DATABASE-CONN
            actions (#'migrations/make-migrations* [] "")
            queries (map #(spec-util/conform ::sql/->sql %) actions)]
        (testing "test make-migrations for model changes"
          (is (= '({:action :create-table
                    :name :feed
                    :fields {:id {:type :serial
                                  :null false}
                             :name {:type :text}}}
                   {:action :create-index
                    :name :feed-name-id-unique-idx
                    :model-name :feed
                    :options {:type :btree
                              :fields [:name]
                              :unique true}})
                actions)))
        (testing "test converting migration actions to sql queries formatted as edn"
          (is (= '({:create-table [:feed]
                    :with-columns [(:id :serial [:not nil])
                                   (:name :text)]}
                   {:create-unique-index [:feed-name-id-unique-idx :on :feed :using (:btree :name)]})
                queries)))
        (testing "test converting actions to sql"
          (is (= '(["CREATE TABLE feed (id SERIAL NOT NULL, name TEXT)"]
                   ["CREATE UNIQUE INDEX feed_name_id_unique_idx ON FEED USING BTREE(NAME)"])
                (map #(sql/->sql %) actions))))
        (testing "test running migrations on db"
          (is (every?
                #(= [#:next.jdbc{:update-count 0}] %)
                (#'migrations/exec-actions! db (concat existing-actions actions)))))))))


(deftest test-make-and-migrate-create-index-on-existing-model-ok
  (let [existing-actions '({:action :create-table
                            :name :feed
                            :fields {:id {:type :serial
                                          :null false}
                                     :name {:type :text}}})]
    #_{:clj-kondo/ignore [:private-call]}
    (bond/with-stub [[schema/load-migrations-from-files
                      (constantly existing-actions)]
                     [file-util/read-edn (constantly {:feed
                                                      {:fields [[:id :serial {:null false}]
                                                                [:name :text]]
                                                       :indexes [[:feed-name-id-unique-idx :btree {:fields [:name]
                                                                                                   :unique true}]]}})]]
      (let [db config/DATABASE-CONN
            actions (#'migrations/make-migrations* [] "")
            queries (map #(spec-util/conform ::sql/->sql %) actions)]
        (testing "test make-migrations for model changes"
          (is (= '({:action :create-index
                    :name :feed-name-id-unique-idx
                    :model-name :feed
                    :options {:type :btree
                              :fields [:name]
                              :unique true}})
                actions)))
        (testing "test converting migration actions to sql queries formatted as edn"
          (is (= '({:create-unique-index
                    [:feed-name-id-unique-idx :on :feed :using (:btree :name)]})
                queries)))
        (testing "test converting actions to sql"
          (is (= '(["CREATE UNIQUE INDEX feed_name_id_unique_idx ON FEED USING BTREE(NAME)"])
                (map #(sql/->sql %) actions))))
        (testing "test running migrations on db"
          (is (every?
                #(= [#:next.jdbc{:update-count 0}] %)
                (#'migrations/exec-actions! db (concat existing-actions actions)))))))))


(deftest test-make-and-migrate-drop-index-ok
  #_{:clj-kondo/ignore [:private-call]}
  (let [existing-actions '({:action :create-table
                            :name :feed
                            :fields {:id {:type :serial
                                          :null false}
                                     :name {:type :text}}}
                           {:action :create-index
                            :name :feed-name-id-idx
                            :model-name :feed
                            :options {:type :btree
                                      :fields [:name :id]
                                      :unique true}})]
    (bond/with-stub [[schema/load-migrations-from-files
                      (constantly existing-actions)]
                     [file-util/read-edn (constantly {:feed
                                                      [[:id :serial {:null false}]
                                                       [:name :text]]})]]
      (let [db config/DATABASE-CONN
            actions (#'migrations/make-migrations* [] "")
            queries (map #(spec-util/conform ::sql/->sql %) actions)]
        (testing "test make-migrations for model changes"
          (is (= '({:action :drop-index
                    :name :feed-name-id-idx
                    :model-name :feed})
                actions)))
        (testing "test converting migration actions to sql queries formatted as edn"
          (is (= '({:drop-index :feed-name-id-idx})
                queries)))
        (testing "test converting actions to sql"
          (is (= '(["DROP INDEX feed_name_id_idx"])
                (map #(sql/->sql %) actions))))
        (testing "test running migrations on db"
          (is (every?
                #(= [#:next.jdbc{:update-count 0}] %)
                (#'migrations/exec-actions! db (concat existing-actions actions)))))))))


(deftest test-make-and-migrate-alter-index-ok
  #_{:clj-kondo/ignore [:private-call]}
  (let [existing-actions '({:action :create-table
                            :name :feed
                            :fields {:id {:type :serial
                                          :null false}
                                     :name {:type :text}}}
                           {:action :create-index
                            :name :feed_name_id_idx
                            :model-name :feed
                            :options {:type :btree
                                      :fields [:name :id]
                                      :unique true}})]
    (bond/with-stub [[schema/load-migrations-from-files
                      (constantly existing-actions)]
                     [file-util/read-edn (constantly {:feed
                                                      {:fields [[:id :serial {:null false}]
                                                                [:name :text]]
                                                       :indexes [[:feed_name_id_idx :btree {:fields [:name]}]]}})]]
      (let [db config/DATABASE-CONN
            actions (#'migrations/make-migrations* [] "")
            queries (map #(spec-util/conform ::sql/->sql %) actions)]
        (testing "test make-migrations for model changes"
          (is (= '({:action :alter-index
                    :name :feed_name_id_idx
                    :options {:fields [:name]
                              :type :btree}
                    :model-name :feed})
                actions)))
        (testing "test converting migration actions to sql queries formatted as edn"
          (is (= '([{:drop-index :feed_name_id_idx}
                    {:create-index
                     [:feed_name_id_idx :on :feed :using (:btree :name)]}])
                queries)))
        (testing "test converting actions to sql"
          (is (= '((["DROP INDEX feed_name_id_idx"]
                    ["CREATE INDEX feed_name_id_idx ON FEED USING BTREE(NAME)"]))
                (map #(sql/->sql %) actions))))
        (testing "test running migrations on db"
          (is (every?
                #(= [#:next.jdbc{:update-count 0}] %)
                (#'migrations/exec-actions! db (concat existing-actions actions)))))))))
