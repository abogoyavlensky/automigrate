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
            [tuna.testing-util :as test-util]
            [tuna.testing-config :as config])
  (:import [java.io FileNotFoundException]
           [clojure.lang ExceptionInfo]))


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
  (#'migrations/make-migrations {:models-file (str config/MODELS-DIR "feed_basic.edn")
                                 :migrations-dir config/MIGRATIONS-DIR})
  (is (= '({:model-name :feed
            :fields {:id {:type :serial :null false}}
            :action :create-table})
        (-> (str config/MIGRATIONS-DIR "/0001_auto_create_table_feed.edn")
          (file-util/read-edn)))))


(deftest test-migrate-single-migrations-for-basic-model-ok
  (core/run {:cmd :make-migrations
             :models-file (str config/MODELS-DIR "feed_basic.edn")
             :migrations-dir config/MIGRATIONS-DIR})
  (core/run {:cmd :migrate
             :migrations-dir config/MIGRATIONS-DIR
             :jdbc-url config/DATABASE-URL})
  (is (= '({:id 1
            :name "0001_auto_create_table_feed"})
        (->> {:select [:*]
              :from [db-util/MIGRATIONS-TABLE]}
          (db-util/exec! config/DATABASE-CONN)
          (map #(dissoc % :created_at))))))


(deftest test-migrate-migrations-with-adding-columns-ok
  (core/run {:cmd :make-migrations
             :models-file (str config/MODELS-DIR "feed_basic.edn")
             :migrations-dir config/MIGRATIONS-DIR})
  (core/run {:cmd :make-migrations
             :models-file (str config/MODELS-DIR "feed_add_column.edn")
             :migrations-dir config/MIGRATIONS-DIR})
  (is (= '({:action :add-column
            :field-name :created-at
            :model-name :feed
            :options {:type :timestamp, :default [:now]}}
           {:action :add-column
            :field-name :name
            :model-name :feed
            :options {:type [:varchar 100] :null true}})
        (-> (str config/MIGRATIONS-DIR "/0002_auto_add_column_created_at.edn")
          (file-util/read-edn))))
  (core/run {:cmd :migrate
             :migrations-dir config/MIGRATIONS-DIR
             :jdbc-url config/DATABASE-URL})
  (is (= '({:id 1
            :name "0001_auto_create_table_feed"}
           {:id 2
            :name "0002_auto_add_column_created_at"})
        (->> {:select [:*]
              :from [db-util/MIGRATIONS-TABLE]}
          (db-util/exec! config/DATABASE-CONN)
          (map #(dissoc % :created_at))))))


(deftest test-migrate-forward-to-number-ok
  (core/run {:cmd :make-migrations
             :models-file (str config/MODELS-DIR "feed_basic.edn")
             :migrations-dir config/MIGRATIONS-DIR})
  (core/run {:cmd :make-migrations
             :models-file (str config/MODELS-DIR "feed_add_column.edn")
             :migrations-dir config/MIGRATIONS-DIR})
  (is (= (str "Created migration: test/tuna/migrations/0003_auto_alter_column_id.edn\n"
           "Actions:\n"
           "  - alter column id in table feed\n"
           "  - alter column name in table feed\n")
        (with-out-str
          (core/run {:cmd :make-migrations
                     :models-file (str config/MODELS-DIR "feed_alter_column.edn")
                     :migrations-dir config/MIGRATIONS-DIR}))))
  (testing "test migrate forward to specific number"
    (core/run {:cmd :migrate
               :migrations-dir config/MIGRATIONS-DIR
               :jdbc-url config/DATABASE-URL
               :number 2
               :migrations-table "custom-migrations_table"})
    (is (= #{"0001_auto_create_table_feed"
             "0002_auto_add_column_created_at"}
          (->> {:select [:*]
                :from [:custom-migrations-table]}
            (db-util/exec! config/DATABASE-CONN)
            (map :name)
            (set)))))
  (testing "test nothing to migrate forward with current number"
    (is (= "Nothing to migrate.\n"
          (with-out-str
            (core/run {:cmd :migrate
                       :migrations-dir config/MIGRATIONS-DIR
                       :jdbc-url config/DATABASE-URL
                       :number 2
                       :migrations-table "custom-migrations-table"}))))
    (is (= #{"0001_auto_create_table_feed"
             "0002_auto_add_column_created_at"}
          (->> {:select [:*]
                :from [:custom-migrations-table]}
            (db-util/exec! config/DATABASE-CONN)
            (map :name)
            (set)))))
  (testing "test migrate forward all"
    (core/run {:cmd :migrate
               :migrations-dir config/MIGRATIONS-DIR
               :jdbc-url config/DATABASE-URL
               :migrations-table "custom-migrations-table"})
    (is (= #{"0001_auto_create_table_feed"
             "0002_auto_add_column_created_at"
             "0003_auto_alter_column_id"}
          (->> {:select [:*]
                :from [:custom-migrations-table]}
            (db-util/exec! config/DATABASE-CONN)
            (map :name)
            (set))))))


(deftest test-migrate-backward-to-number-ok
  (core/run {:cmd :make-migrations
             :models-file (str config/MODELS-DIR "feed_basic.edn")
             :migrations-dir config/MIGRATIONS-DIR})
  (core/run {:cmd :make-migrations
             :models-file (str config/MODELS-DIR "feed_add_column.edn")
             :migrations-dir config/MIGRATIONS-DIR})
  (core/run {:cmd :make-migrations
             :models-file (str config/MODELS-DIR "feed_alter_column.edn")
             :migrations-dir config/MIGRATIONS-DIR})
  (core/run {:cmd :migrate
             :migrations-dir config/MIGRATIONS-DIR
             :jdbc-url config/DATABASE-URL})
  (is (= #{"0001_auto_create_table_feed"
           "0002_auto_add_column_created_at"
           "0003_auto_alter_column_id"}
        (->> {:select [:*]
              :from [db-util/MIGRATIONS-TABLE]}
          (db-util/exec! config/DATABASE-CONN)
          (map :name)
          (set))))
  (testing "test migrate backward to specific number"
    (core/run {:cmd :migrate
               :migrations-dir config/MIGRATIONS-DIR
               :jdbc-url config/DATABASE-URL
               :number 2})
    (is (= #{"0001_auto_create_table_feed"
             "0002_auto_add_column_created_at"}
          (->> {:select [:*]
                :from [db-util/MIGRATIONS-TABLE]}
            (db-util/exec! config/DATABASE-CONN)
            (map :name)
            (set)))))
  (testing "test unapply all migrations "
    (core/run {:cmd :migrate
               :migrations-dir config/MIGRATIONS-DIR
               :jdbc-url config/DATABASE-URL
               :number 0})
    (is (= #{}
          (->> {:select [:*]
                :from [db-util/MIGRATIONS-TABLE]}
            (db-util/exec! config/DATABASE-CONN)
            (map :name)
            (set))))))


(deftest test-migrate-migrations-with-alter-columns-ok
  (core/run {:cmd :make-migrations
             :models-file (str config/MODELS-DIR "feed_add_column.edn")
             :migrations-dir config/MIGRATIONS-DIR})
  (core/run {:cmd :make-migrations
             :models-file (str config/MODELS-DIR "feed_alter_column.edn")
             :migrations-dir config/MIGRATIONS-DIR})
  (is (= '({:action :alter-column
            :changes {:primary-key {:from :EMPTY
                                    :to true}}
            :options {:null false
                      :primary-key true
                      :type :serial}
            :field-name :id
            :model-name :feed}
           {:action :alter-column
            :changes {:type {:from [:varchar 100] :to :text}
                      :null {:from true :to :EMPTY}}
            :options {:type :text}
            :field-name :name
            :model-name :feed})
        (-> (str config/MIGRATIONS-DIR "/0002_auto_alter_column_id.edn")
          (file-util/read-edn))))
  (core/run {:cmd :migrate
             :migrations-dir config/MIGRATIONS-DIR
             :jdbc-url config/DATABASE-URL})
  (is (= '({:id 1
            :name "0001_auto_create_table_feed"}
           {:id 2
            :name "0002_auto_alter_column_id"})
        (->> {:select [:*]
              :from [db-util/MIGRATIONS-TABLE]}
          (db-util/exec! config/DATABASE-CONN)
          (map #(dissoc % :created_at))))))


(deftest test-migrate-migrations-with-drop-columns-ok
  (core/run {:cmd :make-migrations
             :models-file (str config/MODELS-DIR "feed_add_column.edn")
             :migrations-dir config/MIGRATIONS-DIR})
  (core/run {:cmd :make-migrations
             :models-file (str config/MODELS-DIR "feed_drop_column.edn")
             :migrations-dir config/MIGRATIONS-DIR})
  (is (= '({:action :drop-column
            :field-name :name
            :model-name :feed})
        (-> (str config/MIGRATIONS-DIR "/0002_auto_drop_column_name.edn")
          (file-util/read-edn))))
  (core/run {:cmd :migrate
             :migrations-dir config/MIGRATIONS-DIR
             :jdbc-url config/DATABASE-URL})
  (is (= '({:id 1
            :name "0001_auto_create_table_feed"}
           {:id 2
            :name "0002_auto_drop_column_name"})
        (->> {:select [:*]
              :from [db-util/MIGRATIONS-TABLE]}
          (db-util/exec! config/DATABASE-CONN)
          (map #(dissoc % :created_at))))))


(deftest test-migrate-migrations-with-drop-table-ok
  (core/run {:cmd :make-migrations
             :models-file (str config/MODELS-DIR "feed_add_column.edn")
             :migrations-dir config/MIGRATIONS-DIR})
  (core/run {:cmd :make-migrations
             :models-file (str config/MODELS-DIR "feed_drop_table.edn")
             :migrations-dir config/MIGRATIONS-DIR})
  (is (= '({:action :drop-table
            :model-name :feed})
        (-> (str config/MIGRATIONS-DIR "/0002_auto_drop_table_feed.edn")
          (file-util/read-edn))))
  (core/run {:cmd :migrate
             :migrations-dir config/MIGRATIONS-DIR
             :jdbc-url config/DATABASE-URL})
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
  (bond/with-stub [[migrations/migrations-list (constantly ["0001_auto_create_table_feed.edn"])]
                   [file-util/safe-println (constantly nil)]
                   [migrations/read-migration (constantly
                                                '({:model-name :feed
                                                   :fields {:id {:type :serial
                                                                 :null false
                                                                 :primary-key true}
                                                            :number {:type :integer
                                                                     :default 0}
                                                            :info {:type :text}}
                                                   :action :create-table}
                                                  {:model-name :account
                                                   :fields {:id {:null true
                                                                 :unique true
                                                                 :type :serial}
                                                            :name {:null true
                                                                   :type [:varchar 100]}
                                                            :rate {:type :float}}
                                                   :action :create-table}
                                                  {:model-name :role
                                                   :fields {:is-active {:type :boolean}
                                                            :created-at {:type :timestamp
                                                                         :default [:now]}}
                                                   :action :create-table}
                                                  {:field-name :day
                                                   :model-name :account
                                                   :options {:type :date}
                                                   :action :add-column}
                                                  {:field-name :number
                                                   :model-name :account
                                                   :changes {:type {:to :integer :from :text}
                                                             :unique {:to true :from :EMPTY}
                                                             :default {:to 0 :from :EMPTY}
                                                             :primary-key {:to :EMPTY :from true}
                                                             :null {:to :EMPTY :from true}}
                                                   :options {:type :integer
                                                             :unique true
                                                             :default 0}
                                                   :action :alter-column}
                                                  {:field-name :url
                                                   :model-name :feed
                                                   :action :drop-column}
                                                  {:model-name :feed
                                                   :action :drop-table}
                                                  {:model-name :feed
                                                   :fields {:account {:type :serial
                                                                      :foreign-key :account/id}}
                                                   :action :create-table}
                                                  {:field-name :account
                                                   :model-name :feed
                                                   :changes {:foreign-key {:to :EMPTY :from :account/id}}
                                                   :options {:type :serial
                                                             :foreign-key :account/id}
                                                   :action :alter-column}
                                                  {:field-name :account
                                                   :model-name :feed
                                                   :changes {:foreign-key {:to :account/id :from :EMPTY}}
                                                   :options {:type :integer
                                                             :foreign-key :account/id}
                                                   :action :alter-column}
                                                  {:index-name :feed_name_idx
                                                   :model-name :feed
                                                   :options {:type :btree
                                                             :fields [:name]}
                                                   :action :create-index}
                                                  {:index-name :feed_name_idx
                                                   :model-name :feed
                                                   :action :drop-index}
                                                  {:index-name :feed_name_idx
                                                   :model-name :feed
                                                   :options {:type :btree
                                                             :fields [:name]}
                                                   :action :alter-index}))]]
    (migrations/explain {:migrations-dir config/MIGRATIONS-DIR
                         :number 1})
    (is (= ["BEGIN"
            "CREATE TABLE feed (id SERIAL NOT NULL PRIMARY KEY, number INTEGER DEFAULT 0, info TEXT)"
            "CREATE TABLE account (id SERIAL NULL UNIQUE, name VARCHAR(100) NULL, rate FLOAT)"
            "CREATE TABLE role (is_active BOOLEAN, created_at TIMESTAMP DEFAULT NOW())"
            "ALTER TABLE account ADD COLUMN day DATE"
            (str "ALTER TABLE account ALTER COLUMN number TYPE INTEGER, ADD UNIQUE(number), "
              "ALTER COLUMN number SET DEFAULT 0, ALTER COLUMN number DROP NOT NULL, "
              "DROP CONSTRAINT account_pkey")
            "ALTER TABLE feed DROP COLUMN url"
            "DROP TABLE IF EXISTS feed"
            "CREATE TABLE feed (account SERIAL REFERENCES ACCOUNT(ID))"
            "ALTER TABLE feed DROP CONSTRAINT feed_account_fkey"
            (str "ALTER TABLE feed DROP CONSTRAINT IF EXISTS feed_account_fkey,"
              " ADD CONSTRAINT feed_account_fkey FOREIGN KEY(ACCOUNT) REFERENCES ACCOUNT(ID)")
            "CREATE INDEX feed_name_idx ON FEED USING BTREE(NAME)"
            "DROP INDEX feed_name_idx"
            "DROP INDEX feed_name_idx"
            "CREATE INDEX feed_name_idx ON FEED USING BTREE(NAME)"
            "COMMIT;"]
          (-> (bond/calls file-util/safe-println)
            (last)
            :args
            (first))))))


(deftest test-sort-actions-ok
  (let [actions '({:action :drop-column, :field-name :created_at, :model-name :account}
                  {:action :create-table,
                   :model-name :foo,
                   :fields {:id {:type :serial, :unique true},
                            :account {:type :integer, :foreign-key :account/id}}}
                  {:action :create-table,
                   :model-name :bar,
                   :fields {:id {:type :serial, :unique true},
                            :foo1 {:type :integer, :foreign-key :foo/id},
                            :account {:type :integer, :foreign-key :account/id}}}
                  {:action :add-column,
                   :field-name :account,
                   :model-name :foo,
                   :options {:type :integer,
                             :foreign-key :account/id}}
                  {:action :create-table,
                   :model-name :account,
                   :fields {:id {:type :serial, :unique true, :primary-key true}
                            :created_at {:type :timestamp}}})]
    (is (= '({:action :create-table,
              :model-name :account,
              :fields {:id {:type :serial, :unique true, :primary-key true}
                       :created_at {:type :timestamp}}}
             {:action :create-table,
              :model-name :foo,
              :fields {:id {:type :serial, :unique true}, :account {:type :integer, :foreign-key :account/id}}}
             {:action :create-table,
              :model-name :bar,
              :fields {:id {:type :serial, :unique true},
                       :foo1 {:type :integer, :foreign-key :foo/id},
                       :account {:type :integer, :foreign-key :account/id}}}
             {:action :add-column,
              :field-name :account,
              :model-name :foo,
              :options {:type :integer,
                        :foreign-key :account/id}}
             {:action :drop-column, :field-name :created_at, :model-name :account})
          (#'migrations/sort-actions actions)))))


(deftest test-sort-actions-with-create-index-ok
  (let [actions '({:action :create-index
                   :index-name :feed-name-id-idx
                   :model-name :feed
                   :options {:type :btree
                             :fields [:name :id]}}
                  {:action :create-table
                   :model-name :feed
                   :fields {:id {:type :serial
                                 :unique true}}}
                  {:action :add-column
                   :field-name :name
                   :model-name :feed
                   :options {:type :text}})]

    (is (= '({:action :create-table
              :model-name :feed
              :fields {:id {:type :serial
                            :unique true}}}
             {:action :add-column
              :field-name :name
              :model-name :feed
              :options {:type :text}}
             {:action :create-index
              :index-name :feed-name-id-idx
              :model-name :feed
              :options {:type :btree
                        :fields [:name :id]}})
          (#'migrations/sort-actions actions)))))


(deftest test-sort-actions-with-alter-index-ok
  (let [actions '({:action :alter-index
                   :index-name :feed-name-id-idx
                   :model-name :feed
                   :options {:type :btree
                             :fields [:name :id]}}
                  {:action :add-column
                   :field-name :name
                   :model-name :feed
                   :options {:type :text}})]

    (is (= '({:action :add-column
              :field-name :name
              :model-name :feed
              :options {:type :text}}
             {:action :alter-index
              :index-name :feed-name-id-idx
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
            actions (#'migrations/make-migrations* "" [])
            queries (map #(spec-util/conform ::sql/->sql %) actions)]
        (testing "test make-migrations for model changes"
          (is (= '({:action :create-table
                    :model-name :feed
                    :fields {:id {:type :serial
                                  :null false}
                             :name {:type :text}}}
                   {:action :create-index
                    :index-name :feed-name-id-unique-idx
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
                (#'migrations/exec-actions!
                 {:db db
                  :actions (concat existing-actions actions)
                  :direction :forward
                  :migration-type :edn}))))))))


(deftest test-make-and-migrate-create-index-on-existing-model-ok
  (let [existing-actions '({:action :create-table
                            :model-name :feed
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
            actions (#'migrations/make-migrations* "" [])
            queries (map #(spec-util/conform ::sql/->sql %) actions)]
        (testing "test make-migrations for model changes"
          (is (= '({:action :create-index
                    :index-name :feed-name-id-unique-idx
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
                (#'migrations/exec-actions!
                 {:db db
                  :actions (concat existing-actions actions)
                  :direction :forward
                  :migration-type :edn}))))))))


(deftest test-make-and-migrate-drop-index-ok
  #_{:clj-kondo/ignore [:private-call]}
  (let [existing-actions '({:action :create-table
                            :model-name :feed
                            :fields {:id {:type :serial
                                          :null false}
                                     :name {:type :text}}}
                           {:action :create-index
                            :index-name :feed-name-id-idx
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
            actions (#'migrations/make-migrations* "" [])
            queries (map #(spec-util/conform ::sql/->sql %) actions)]
        (testing "test make-migrations for model changes"
          (is (= '({:action :drop-index
                    :index-name :feed-name-id-idx
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
                (#'migrations/exec-actions!
                 {:db db
                  :actions (concat existing-actions actions)
                  :direction :forward
                  :migration-type :edn}))))))))


(deftest test-make-and-migrate-alter-index-ok
  #_{:clj-kondo/ignore [:private-call]}
  (let [existing-actions '({:action :create-table
                            :model-name :feed
                            :fields {:id {:type :serial
                                          :null false}
                                     :name {:type :text}}}
                           {:action :create-index
                            :index-name :feed-name-id-idx
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
            actions (#'migrations/make-migrations* "" [])
            queries (map #(spec-util/conform ::sql/->sql %) actions)]
        (testing "test make-migrations for model changes"
          (is (= '({:action :alter-index
                    :index-name :feed-name-id-idx
                    :options {:fields [:name]
                              :type :btree}
                    :model-name :feed})
                actions)))
        (testing "test converting migration actions to sql queries formatted as edn"
          (is (= '([{:drop-index :feed-name-id-idx}
                    {:create-index
                     [:feed-name-id-idx :on :feed :using (:btree :name)]}])
                queries)))
        (testing "test converting actions to sql"
          (is (= '((["DROP INDEX feed_name_id_idx"]
                    ["CREATE INDEX feed_name_id_idx ON FEED USING BTREE(NAME)"]))
                (map #(sql/->sql %) actions))))
        (testing "test running migrations on db"
          (is (every?
                #(= [#:next.jdbc{:update-count 0}] %)
                (#'migrations/exec-actions!
                 {:db db
                  :actions (concat existing-actions actions)
                  :direction :forward
                  :migration-type :edn}))))))))


(defn- test-make-and-migrate-ok!
  [existing-actions changed-models expected-actions expected-q-edn expected-q-sql]
  #_{:clj-kondo/ignore [:private-call]}
  (bond/with-stub [[schema/load-migrations-from-files
                    (constantly existing-actions)]
                   [file-util/read-edn (constantly changed-models)]]
    (let [db config/DATABASE-CONN
          actions (#'migrations/make-migrations* "" [])
          queries (map #(spec-util/conform ::sql/->sql %) actions)]
      (testing "test make-migrations for model changes"
        (is (= expected-actions actions)))
      (testing "test converting migration actions to sql queries formatted as edn"
        (is (= expected-q-edn queries)))
      (testing "test converting actions to sql"
        (is (= expected-q-sql (map #(sql/->sql %) actions))))
      (testing "test running migrations on db"
        (is (every?
              #(= [#:next.jdbc{:update-count 0}] %)
              (#'migrations/exec-actions!
               {:db db
                :actions (concat existing-actions actions)
                :direction :forward
                :migration-type :edn})))))))


(deftest test-make-and-migrate-add-fk-field-on-delete-ok
  #_{:clj-kondo/ignore [:private-call]}
  (let [existing-actions '({:action :create-table
                            :model-name :account
                            :fields {:id {:type :serial
                                          :unique true
                                          :primary-key true}}}
                           {:action :create-table
                            :model-name :feed
                            :fields {:id {:type :serial}
                                     :name {:type :text}}})
        changed-models {:feed
                        {:fields [[:id :serial]
                                  [:name :text]
                                  [:account :integer {:foreign-key :account/id
                                                      :on-delete :cascade}]]}
                        :account [[:id :serial {:unique true
                                                :primary-key true}]]}
        expected-actions '({:action :add-column
                            :field-name :account
                            :model-name :feed
                            :options {:type :integer
                                      :foreign-key :account/id
                                      :on-delete :cascade}})
        expected-q-edn '({:add-column (:account
                                        :integer
                                        (:references :account :id)
                                        [:raw "on delete"]
                                        [:raw "cascade"]),
                          :alter-table :feed})
        expected-q-sql '(["ALTER TABLE feed ADD COLUMN account INTEGER REFERENCES ACCOUNT(ID) ON DELETE CASCADE"])]
    (test-make-and-migrate-ok! existing-actions changed-models expected-actions expected-q-edn expected-q-sql)))


(deftest test-make-and-migrate-alter-fk-field-on-delete-ok
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
        changed-models {:feed
                        {:fields [[:id :serial]
                                  [:name :text]
                                  [:account :integer {:foreign-key :account/id
                                                      :on-delete :set-null}]]}
                        :account [[:id :serial {:unique true
                                                :primary-key true}]]}
        expected-actions '({:action :alter-column
                            :field-name :account
                            :model-name :feed
                            :options {:type :integer
                                      :foreign-key :account/id
                                      :on-delete :set-null}
                            :changes {:on-delete {:from :cascade :to :set-null}}})
        expected-q-edn '({:alter-table (:feed
                                         {:drop-constraint [[:raw "IF EXISTS"]
                                                            :feed-account-fkey]}
                                         {:add-constraint (:feed-account-fkey
                                                            [:foreign-key :account]
                                                            (:references :account :id)
                                                            [:raw "on delete"]
                                                            [:raw "set null"])})})
        expected-q-sql (list [(str "ALTER TABLE feed DROP CONSTRAINT IF EXISTS feed_account_fkey, "
                                "ADD CONSTRAINT feed_account_fkey FOREIGN KEY(ACCOUNT) "
                                "REFERENCES ACCOUNT(ID) ON DELETE SET NULL")])]
    (test-make-and-migrate-ok! existing-actions changed-models expected-actions expected-q-edn expected-q-sql)))


(deftest test-make-and-migrate-remove-fk-option-on-field-ok
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
        changed-models {:feed
                        {:fields [[:id :serial]
                                  [:name :text]
                                  [:account :integer]]}
                        :account [[:id :serial {:unique true
                                                :primary-key true}]]}
        expected-actions '({:action :alter-column
                            :field-name :account
                            :model-name :feed
                            :options {:type :integer}
                            :changes {:foreign-key {:from :account/id :to :EMPTY}
                                      :on-delete {:from :cascade :to :EMPTY}}})
        expected-q-edn '({:alter-table (:feed
                                         {:drop-constraint :feed-account-fkey})})
        expected-q-sql (list [(str "ALTER TABLE feed DROP CONSTRAINT feed_account_fkey")])]
    (test-make-and-migrate-ok! existing-actions changed-models expected-actions expected-q-edn expected-q-sql)))


(deftest test-validate-migration-numbers
  (testing "check there are duplicates of migration numbers err"
    (let [names ["0001_test"
                 "0002_foo"
                 "0002_bar"
                 "0003_bar"
                 "0003_bar"
                 "0003_bar"]]
      (is (thrown? ExceptionInfo
            (#'migrations/validate-migration-numbers names)))))
  (testing "check there are no duplicates of migration numbers ok"
    (let [names ["0001_test"
                 "0002_foo"
                 "0003_bar"]]
      (is (= names
            (#'migrations/validate-migration-numbers names))))))


(deftest test-custom-migration-name-ok
  (core/run {:cmd :make-migrations
             :models-file (str config/MODELS-DIR "feed_basic.edn")
             :migrations-dir config/MIGRATIONS-DIR
             :name "some-custom-migration-name"})
  (is (= '({:model-name :feed
            :fields {:id {:type :serial :null false}}
            :action :create-table})
        (file-util/read-edn
          (str config/MIGRATIONS-DIR "/0001_some_custom_migration_name.edn")))))


(deftest test-make-and-migrate-alter-varchar-value-field-ok
  (let [existing-actions '({:action :create-table
                            :model-name :feed
                            :fields {:name {:type [:varchar 100]}}})
        changed-models {:feed
                        {:fields [[:name [:varchar 200]]]}}
        expected-actions '({:action :alter-column
                            :field-name :name
                            :model-name :feed
                            :options {:type [:varchar 200]}
                            :changes {:type {:from [:varchar 100]
                                             :to [:varchar 200]}}})
        expected-q-edn '({:alter-table (:feed {:alter-column [:name :type [:varchar 200]]})})
        expected-q-sql (list ["ALTER TABLE feed ALTER COLUMN name TYPE VARCHAR(200)"])]
    (test-make-and-migrate-ok!
      existing-actions
      changed-models
      expected-actions
      expected-q-edn
      expected-q-sql)))


(deftest test-make-and-migrate-alter-varchar-type-field-ok
  (let [existing-actions '({:action :create-table
                            :model-name :feed
                            :fields {:name {:type [:varchar 100]}}})
        changed-models {:feed
                        {:fields [[:name [:char 100]]]}}
        expected-actions '({:action :alter-column
                            :field-name :name
                            :model-name :feed
                            :options {:type [:char 100]}
                            :changes {:type {:from [:varchar 100]
                                             :to [:char 100]}}})
        expected-q-edn '({:alter-table (:feed {:alter-column [:name :type [:char 100]]})})
        expected-q-sql (list ["ALTER TABLE feed ALTER COLUMN name TYPE CHAR(100)"])]
    (test-make-and-migrate-ok!
      existing-actions
      changed-models
      expected-actions
      expected-q-edn
      expected-q-sql)))
