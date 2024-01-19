(ns automigrate.migrations-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [bond.james :as bond]
            [automigrate.migrations :as migrations]
            [automigrate.core :as core]
            [automigrate.schema :as schema]
            [automigrate.sql :as sql]
            [automigrate.util.db :as db-util]
            [automigrate.util.file :as file-util]
            [automigrate.util.spec :as spec-util]
            [automigrate.testing-util :as test-util]
            [automigrate.testing-config :as config])
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
  (#'migrations/make-migration {:models-file (str config/MODELS-DIR "feed_basic.edn")
                                :migrations-dir config/MIGRATIONS-DIR})
  (is (= '({:model-name :feed
            :fields {:id {:type :serial :null false}}
            :action :create-table})
        (-> (str config/MIGRATIONS-DIR "/0001_auto_create_table_feed.edn")
          (file-util/read-edn)))))


(deftest test-migrate-single-migrations-for-basic-model-ok
  (core/make {:models-file (str config/MODELS-DIR "feed_basic.edn")
              :migrations-dir config/MIGRATIONS-DIR})
  (core/migrate {:migrations-dir config/MIGRATIONS-DIR
                 :jdbc-url config/DATABASE-URL})
  (is (= '({:id 1
            :name "0001_auto_create_table_feed"})
        (->> {:select [:*]
              :from [db-util/MIGRATIONS-TABLE]}
          (db-util/exec! config/DATABASE-CONN)
          (map #(dissoc % :created_at))))))


(deftest test-migrate-migrations-with-adding-columns-ok
  (core/make {:models-file (str config/MODELS-DIR "feed_basic.edn")
              :migrations-dir config/MIGRATIONS-DIR})
  (core/make {:models-file (str config/MODELS-DIR "feed_add_column.edn")
              :migrations-dir config/MIGRATIONS-DIR})
  (is (= '({:action :add-column
            :field-name :created-at
            :model-name :feed
            :options {:type :timestamp, :default [:now]}}
           {:action :add-column
            :field-name :name
            :model-name :feed
            :options {:type [:varchar 100] :null true}})
        (-> (str config/MIGRATIONS-DIR "/0002_auto_add_column_created_at_to_feed_etc.edn")
          (file-util/read-edn))))
  (core/migrate {:migrations-dir config/MIGRATIONS-DIR
                 :jdbc-url config/DATABASE-URL})
  (is (= '({:id 1
            :name "0001_auto_create_table_feed"}
           {:id 2
            :name "0002_auto_add_column_created_at_to_feed_etc"})
        (->> {:select [:*]
              :from [db-util/MIGRATIONS-TABLE]}
          (db-util/exec! config/DATABASE-CONN)
          (map #(dissoc % :created_at))))))


(deftest test-migrate-forward-to-number-ok
  (core/make {:models-file (str config/MODELS-DIR "feed_basic.edn")
              :migrations-dir config/MIGRATIONS-DIR})
  (core/make {:models-file (str config/MODELS-DIR "feed_add_column.edn")
              :migrations-dir config/MIGRATIONS-DIR})
  (is (= (str "Created migration: test/automigrate/migrations/0003_auto_alter_column_id_in_feed_etc.edn\n"
           "Actions:\n"
           "  - alter column id in feed\n"
           "  - alter column name in feed\n")
        (with-out-str
          (core/make {:models-file (str config/MODELS-DIR "feed_alter_column.edn")
                      :migrations-dir config/MIGRATIONS-DIR}))))
  (testing "test migrate forward to specific number"
    (core/migrate {:migrations-dir config/MIGRATIONS-DIR
                   :jdbc-url config/DATABASE-URL
                   :number 2
                   :migrations-table "custom-migrations_table"})
    (is (= #{"0001_auto_create_table_feed"
             "0002_auto_add_column_created_at_to_feed_etc"}
          (->> {:select [:*]
                :from [:custom-migrations-table]}
            (db-util/exec! config/DATABASE-CONN)
            (map :name)
            (set)))))
  (testing "test nothing to migrate forward with current number"
    (is (= "Nothing to migrate.\n"
          (with-out-str
            (core/migrate {:migrations-dir config/MIGRATIONS-DIR
                           :jdbc-url config/DATABASE-URL
                           :number 2
                           :migrations-table "custom-migrations-table"}))))
    (is (= #{"0001_auto_create_table_feed"
             "0002_auto_add_column_created_at_to_feed_etc"}
          (->> {:select [:*]
                :from [:custom-migrations-table]}
            (db-util/exec! config/DATABASE-CONN)
            (map :name)
            (set)))))
  (testing "test migrate forward all"
    (core/migrate {:migrations-dir config/MIGRATIONS-DIR
                   :jdbc-url config/DATABASE-URL
                   :migrations-table "custom-migrations-table"})
    (is (= #{"0001_auto_create_table_feed"
             "0002_auto_add_column_created_at_to_feed_etc"
             "0003_auto_alter_column_id_in_feed_etc"}
          (->> {:select [:*]
                :from [:custom-migrations-table]}
            (db-util/exec! config/DATABASE-CONN)
            (map :name)
            (set))))))


(deftest test-migrate-backward-to-number-ok
  (core/make {:models-file (str config/MODELS-DIR "feed_basic.edn")
              :migrations-dir config/MIGRATIONS-DIR})
  (core/make {:models-file (str config/MODELS-DIR "feed_add_column.edn")
              :migrations-dir config/MIGRATIONS-DIR})
  (core/make {:models-file (str config/MODELS-DIR "feed_alter_column.edn")
              :migrations-dir config/MIGRATIONS-DIR})
  (core/migrate {:migrations-dir config/MIGRATIONS-DIR
                 :jdbc-url config/DATABASE-URL})
  (testing "test migrations have been applied"
    (is (= #{"0001_auto_create_table_feed"
             "0002_auto_add_column_created_at_to_feed_etc"
             "0003_auto_alter_column_id_in_feed_etc"}
          (->> {:select [:*]
                :from [db-util/MIGRATIONS-TABLE]}
            (db-util/exec! config/DATABASE-CONN)
            (map :name)
            (set))))
    ; check actual db changes
    (is (= [{:character_maximum_length nil
             :column_default "nextval('feed_id_seq'::regclass)"
             :column_name "id"
             :data_type "integer"
             :is_nullable "NO"
             :table_name "feed"
             :udt_name "int4"}
            {:character_maximum_length nil
             :column_default "now()"
             :column_name "created_at"
             :data_type "timestamp without time zone"
             :is_nullable "YES"
             :table_name "feed"
             :udt_name "timestamp"}
            {:character_maximum_length nil
             :column_default nil
             :column_name "name"
             :data_type "text"
             :is_nullable "YES"
             :table_name "feed"
             :udt_name "text"}]
          (test-util/get-table-schema-from-db
            config/DATABASE-CONN
            "feed"))))
  (testing "test migrate backward to specific number"
    (core/migrate {:migrations-dir config/MIGRATIONS-DIR
                   :jdbc-url config/DATABASE-URL
                   :number 2})
    (is (= #{"0001_auto_create_table_feed"
             "0002_auto_add_column_created_at_to_feed_etc"}
          (->> {:select [:*]
                :from [db-util/MIGRATIONS-TABLE]}
            (db-util/exec! config/DATABASE-CONN)
            (map :name)
            (set))))
    ; check actual db changes
    (is (= [{:character_maximum_length nil
             :column_default "nextval('feed_id_seq'::regclass)"
             :column_name "id"
             :data_type "integer"
             :is_nullable "NO"
             :table_name "feed"
             :udt_name "int4"}
            {:character_maximum_length nil
             :column_default "now()"
             :column_name "created_at"
             :data_type "timestamp without time zone"
             :is_nullable "YES"
             :table_name "feed"
             :udt_name "timestamp"}
            {:character_maximum_length 100
             :column_default nil
             :column_name "name"
             :data_type "character varying"
             :udt_name "varchar"
             :is_nullable "YES"
             :table_name "feed"}]
          (test-util/get-table-schema-from-db
            config/DATABASE-CONN
            "feed"))))

  (testing "test to revert all migrations"
    (core/migrate {:migrations-dir config/MIGRATIONS-DIR
                   :jdbc-url config/DATABASE-URL
                   :number 0})
    (is (= #{}
          (->> {:select [:*]
                :from [db-util/MIGRATIONS-TABLE]}
            (db-util/exec! config/DATABASE-CONN)
            (map :name)
            (set))))
    (is (= []
          (test-util/get-table-schema-from-db
            config/DATABASE-CONN
            "feed")))))


(deftest test-migrate-migrations-with-alter-columns-ok
  (core/make {:models-file (str config/MODELS-DIR "feed_add_column.edn")
              :migrations-dir config/MIGRATIONS-DIR})
  (core/make {:models-file (str config/MODELS-DIR "feed_alter_column.edn")
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
        (-> (str config/MIGRATIONS-DIR "/0002_auto_alter_column_id_in_feed_etc.edn")
          (file-util/read-edn))))
  (core/migrate {:migrations-dir config/MIGRATIONS-DIR
                 :jdbc-url config/DATABASE-URL})
  (is (= '({:id 1
            :name "0001_auto_create_table_feed"}
           {:id 2
            :name "0002_auto_alter_column_id_in_feed_etc"})
        (->> {:select [:*]
              :from [db-util/MIGRATIONS-TABLE]}
          (db-util/exec! config/DATABASE-CONN)
          (map #(dissoc % :created_at))))))


(deftest test-migrate-migrations-with-drop-columns-ok
  (core/make {:models-file (str config/MODELS-DIR "feed_add_column.edn")
              :migrations-dir config/MIGRATIONS-DIR})
  (core/make {:models-file (str config/MODELS-DIR "feed_drop_column.edn")
              :migrations-dir config/MIGRATIONS-DIR})
  (is (= '({:action :drop-column
            :field-name :name
            :model-name :feed})
        (-> (str config/MIGRATIONS-DIR "/0002_auto_drop_column_name_from_feed.edn")
          (file-util/read-edn))))
  (core/migrate {:migrations-dir config/MIGRATIONS-DIR
                 :jdbc-url config/DATABASE-URL})
  (is (= '({:id 1
            :name "0001_auto_create_table_feed"}
           {:id 2
            :name "0002_auto_drop_column_name_from_feed"})
        (->> {:select [:*]
              :from [db-util/MIGRATIONS-TABLE]}
          (db-util/exec! config/DATABASE-CONN)
          (map #(dissoc % :created_at))))))


(deftest test-migrate-migrations-with-drop-table-ok
  (core/make {:models-file (str config/MODELS-DIR "feed_add_column.edn")
              :migrations-dir config/MIGRATIONS-DIR})
  (core/make {:models-file (str config/MODELS-DIR "feed_drop_table.edn")
              :migrations-dir config/MIGRATIONS-DIR})
  (is (= '({:action :drop-table
            :model-name :feed})
        (-> (str config/MIGRATIONS-DIR "/0002_auto_drop_table_feed.edn")
          (file-util/read-edn))))
  (core/migrate {:migrations-dir config/MIGRATIONS-DIR
                 :jdbc-url config/DATABASE-URL})
  (is (= '({:id 1
            :name "0001_auto_create_table_feed"}
           {:id 2
            :name "0002_auto_drop_table_feed"})
        (->> {:select [:*]
              :from [db-util/MIGRATIONS-TABLE]}
          (db-util/exec! config/DATABASE-CONN)
          (map #(dissoc % :created_at))))))


(deftest test-explain-basic-migration-sql-ok
  (bond/with-stub [[migrations/migrations-list (constantly ["0001_auto_create_table_feed_etc.edn"])]
                   [file-util/safe-println (constantly nil)]
                   [migrations/migration->actions (constantly
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
            "CREATE TABLE feed (id SERIAL CONSTRAINT feed_pkey PRIMARY KEY NOT NULL, number INTEGER DEFAULT 0, info TEXT)"
            "CREATE TABLE account (id SERIAL NULL UNIQUE, name VARCHAR(100) NULL, rate FLOAT)"
            "CREATE TABLE role (is_active BOOLEAN, created_at TIMESTAMP DEFAULT NOW())"
            "ALTER TABLE account ADD COLUMN day DATE"
            (str "ALTER TABLE account ALTER COLUMN number TYPE INTEGER USING number :: INTEGER, ADD UNIQUE(number), "
              "ALTER COLUMN number SET DEFAULT 0, ALTER COLUMN number DROP NOT NULL, "
              "DROP CONSTRAINT account_pkey")
            "ALTER TABLE feed DROP COLUMN url"
            "DROP TABLE IF EXISTS feed"
            "CREATE TABLE feed (account SERIAL REFERENCES account(id))"
            "ALTER TABLE feed DROP CONSTRAINT feed_account_fkey"
            (str "ALTER TABLE feed"
              " ADD CONSTRAINT feed_account_fkey FOREIGN KEY(account) REFERENCES account(id)")
            "CREATE INDEX feed_name_idx ON FEED USING BTREE(name)"
            "DROP INDEX feed_name_idx"
            "DROP INDEX feed_name_idx"
            "CREATE INDEX feed_name_idx ON FEED USING BTREE(name)"
            "COMMIT;"]
          (-> (bond/calls file-util/safe-println)
            (last)
            :args
            (first))))))


(deftest test-explain-basic-migration-human-ok
  (bond/with-stub [[migrations/migrations-list (constantly ["0001_auto_create_table_feed_etc.edn"])]
                   [file-util/safe-println (constantly nil)]
                   [migrations/migration->actions (constantly
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
                         :number 1
                         :format :human})
    (is (= ["  - create table feed"
            "  - create table account"
            "  - create table role"
            "  - add column day to account"
            "  - alter column number in account"
            "  - drop column url from feed"
            "  - drop table feed"
            "  - create table feed"
            "  - alter column account in feed"
            "  - alter column account in feed"
            "  - create index feed_name_idx on feed"
            "  - drop index feed_name_idx on feed"
            "  - alter index feed_name_idx on feed"]
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
                            :created_at {:type :timestamp}}})
        old-schema (#'schema/actions->internal-models actions)]
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
          (#'migrations/sort-actions old-schema actions)))))


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
                   :options {:type :text}})
        old-schema (#'schema/actions->internal-models actions)]

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
          (#'migrations/sort-actions old-schema actions)))))


(deftest test-sort-actions-with-alter-index-ok
  (let [actions '({:action :alter-index
                   :index-name :feed-name-id-idx
                   :model-name :feed
                   :options {:type :btree
                             :fields [:name :id]}}
                  {:action :add-column
                   :field-name :name
                   :model-name :feed
                   :options {:type :text}})
        old-schema (#'schema/actions->internal-models actions)]

    (is (= '({:action :add-column
              :field-name :name
              :model-name :feed
              :options {:type :text}}
             {:action :alter-index
              :index-name :feed-name-id-idx
              :model-name :feed
              :options {:type :btree
                        :fields [:name :id]}})
          (#'migrations/sort-actions old-schema actions)))))


(deftest test-make-and-migrate-create-index-on-new-model-ok
  (let [existing-actions '()
        existing-models {:feed
                         {:fields [[:id :serial {:null false}]
                                   [:name :text]]
                          :indexes [[:feed-name-id-unique-idx :btree {:fields [:name]
                                                                      :unique true}]]}}
        db config/DATABASE-CONN
        actions (test-util/make-migration-spy! {:existing-actions existing-actions
                                                :existing-models existing-models})
        queries (map #(spec-util/conform ::sql/->sql %) actions)]
    (testing "test make-migration for model changes"
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
               ["CREATE UNIQUE INDEX feed_name_id_unique_idx ON FEED USING BTREE(name)"])
            (map #(sql/->sql %) actions))))
    (testing "test running migrations on db"
      (is (every?
            #(= [#:next.jdbc{:update-count 0}] %)
            (#'migrations/exec-actions!
             {:db db
              :actions (concat existing-actions actions)
              :direction :forward
              :migration-type :edn}))))))


(deftest test-make-and-migrate-create-index-on-existing-model-ok
  (let [existing-actions '({:action :create-table
                            :model-name :feed
                            :fields {:id {:type :serial
                                          :null false}
                                     :name {:type :text}}})
        existing-models {:feed
                         {:fields [[:id :serial {:null false}]
                                   [:name :text]]
                          :indexes [[:feed-name-id-unique-idx :btree {:fields [:name]
                                                                      :unique true}]]}}
        db config/DATABASE-CONN
        actions (test-util/make-migration-spy! {:existing-actions existing-actions
                                                :existing-models existing-models})
        queries (map #(spec-util/conform ::sql/->sql %) actions)]
    (testing "test make-migration for model changes"
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
      (is (= '(["CREATE UNIQUE INDEX feed_name_id_unique_idx ON FEED USING BTREE(name)"])
            (map #(sql/->sql %) actions))))
    (testing "test running migrations on db"
      (is (every?
            #(= [#:next.jdbc{:update-count 0}] %)
            (#'migrations/exec-actions!
             {:db db
              :actions (concat existing-actions actions)
              :direction :forward
              :migration-type :edn}))))))


(deftest test-make-and-migrate-drop-index-ok
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
                                      :unique true}})
        existing-models {:feed
                         [[:id :serial {:null false}]
                          [:name :text]]}
        db config/DATABASE-CONN
        actions (test-util/make-migration-spy! {:existing-actions existing-actions
                                                :existing-models existing-models})
        queries (map #(spec-util/conform ::sql/->sql %) actions)]
    (testing "test make-migration for model changes"
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
              :migration-type :edn}))))))


(deftest test-make-and-migrate-alter-index-ok
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
                                      :unique true}})
        existing-models {:feed
                         {:fields [[:id :serial {:null false}]
                                   [:name :text]]
                          :indexes [[:feed_name_id_idx :btree {:fields [:name]}]]}}
        db config/DATABASE-CONN
        actions (test-util/make-migration-spy! {:existing-actions existing-actions
                                                :existing-models existing-models})
        queries (map #(spec-util/conform ::sql/->sql %) actions)]
    (testing "test make-migration for model changes"
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
                ["CREATE INDEX feed_name_id_idx ON FEED USING BTREE(name)"]))
            (map #(sql/->sql %) actions))))
    (testing "test running migrations on db"
      (is (every?
            #(= [#:next.jdbc{:update-count 0}] %)
            (#'migrations/exec-actions!
             {:db db
              :actions (concat existing-actions actions)
              :direction :forward
              :migration-type :edn}))))))


(deftest test-make-and-migrate-add-fk-field-on-delete-ok
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
        expected-q-edn '({:create-table [:account]
                          :with-columns [(:id :serial [:constraint :account-pkey] :primary-key :unique)]}
                         {:create-table [:feed]
                          :with-columns [(:id :serial)
                                         (:name :text)]}
                         {:add-column (:account
                                        :integer
                                        (:references :account :id)
                                        [:raw "on delete"]
                                        [:raw "cascade"]),
                          :alter-table :feed})
        expected-q-sql '(["CREATE TABLE account (id SERIAL CONSTRAINT account_pkey PRIMARY KEY UNIQUE)"]
                         ["CREATE TABLE feed (id SERIAL, name TEXT)"]
                         ["ALTER TABLE feed ADD COLUMN account INTEGER REFERENCES account(id) on delete cascade"])]

    (is (= {:new-actions expected-actions
            :q-edn expected-q-edn
            :q-sql expected-q-sql}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions existing-actions
             :existing-models changed-models})))))


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
        expected-q-edn '({:create-table [:account]
                          :with-columns [(:id :serial
                                           [:constraint :account-pkey]
                                           :primary-key
                                           :unique)]}
                         {:create-table [:feed]
                          :with-columns [(:id :serial)
                                         (:name :text)
                                         (:account :integer
                                           (:references :account :id)
                                           [:raw "on delete"]
                                           [:raw "cascade"])]}
                         {:alter-table (:feed
                                         {:drop-constraint [[:raw "IF EXISTS"]
                                                            :feed-account-fkey]}
                                         {:add-constraint (:feed-account-fkey
                                                            [:foreign-key :account]
                                                            (:references :account :id)
                                                            [:raw "on delete"]
                                                            [:raw "set null"])})})
        expected-q-sql (list
                         ["CREATE TABLE account (id SERIAL CONSTRAINT account_pkey PRIMARY KEY UNIQUE)"]
                         ["CREATE TABLE feed (id SERIAL, name TEXT, account INTEGER REFERENCES account(id) on delete cascade)"]
                         [(str "ALTER TABLE feed DROP CONSTRAINT IF EXISTS feed_account_fkey, "
                            "ADD CONSTRAINT feed_account_fkey FOREIGN KEY(account) "
                            "REFERENCES account(id) on delete set null")])]

    (is (= {:new-actions expected-actions
            :q-edn expected-q-edn
            :q-sql expected-q-sql}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions existing-actions
             :existing-models changed-models})))

    (testing "test constraints in db"
      (is (= [{:colname "id"
               :constraint_name "account_pkey"
               :constraint_type "PRIMARY KEY"
               :table_name "account"}
              {:colname "id"
               :constraint_name "feed_account_fkey"
               :constraint_type "FOREIGN KEY"
               :table_name "feed"}]
            (db-util/exec!
              config/DATABASE-CONN
              {:select [:tc.constraint_name
                        :tc.constraint_type
                        :tc.table_name
                        [:ccu.column_name :colname]]
               :from [[:information_schema.table_constraints :tc]]
               :join [[:information_schema.key_column_usage :kcu]
                      [:= :tc.constraint_name :kcu.constraint_name]

                      [:information_schema.constraint_column_usage :ccu]
                      [:= :ccu.constraint_name :tc.constraint_name]]
               :where [:in :ccu.table_name ["feed" "account"]]
               :order-by [:tc.constraint_name]}))))))


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
        expected-q-edn '({:create-table [:account]
                          :with-columns [(:id :serial [:constraint :account-pkey] :primary-key :unique)]}
                         {:create-table [:feed]
                          :with-columns [(:id :serial)
                                         (:name :text)
                                         (:account :integer
                                           (:references :account :id)
                                           [:raw "on delete"]
                                           [:raw "cascade"])]}
                         {:alter-table (:feed
                                         {:drop-constraint :feed-account-fkey})})
        expected-q-sql (list
                         ["CREATE TABLE account (id SERIAL CONSTRAINT account_pkey PRIMARY KEY UNIQUE)"]
                         ["CREATE TABLE feed (id SERIAL, name TEXT, account INTEGER REFERENCES account(id) on delete cascade)"]
                         [(str "ALTER TABLE feed DROP CONSTRAINT feed_account_fkey")])]

    (is (= {:new-actions expected-actions
            :q-edn expected-q-edn
            :q-sql expected-q-sql}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions existing-actions
             :existing-models changed-models})))))


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
  (core/make {:models-file (str config/MODELS-DIR "feed_basic.edn")
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
        expected-q-edn '({:create-table [:feed]
                          :with-columns [(:name
                                           [:varchar
                                            100])]}
                         {:alter-table
                          (:feed {:alter-column
                                  [:name :type [:varchar 200]
                                   :using [:raw "name"] [:raw "::"] [:varchar 200]]})})
        expected-q-sql (list
                         ["CREATE TABLE feed (name VARCHAR(100))"]
                         [(str "ALTER TABLE feed ALTER COLUMN name TYPE VARCHAR(200)"
                            " USING name :: VARCHAR(200)")])]

    (is (= {:new-actions expected-actions
            :q-edn expected-q-edn
            :q-sql expected-q-sql}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions existing-actions
             :existing-models changed-models})))))


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
        expected-q-edn '({:create-table [:feed]
                          :with-columns [(:name
                                           [:varchar
                                            100])]}
                         {:alter-table
                          (:feed {:alter-column
                                  [:name :type [:char 100]
                                   :using [:raw "name"] [:raw "::"] [:char 100]]})})
        expected-q-sql (list
                         ["CREATE TABLE feed (name VARCHAR(100))"]
                         [(str "ALTER TABLE feed ALTER COLUMN name TYPE CHAR(100)"
                            " USING name :: CHAR(100)")])]

    (is (= {:new-actions expected-actions
            :q-edn expected-q-edn
            :q-sql expected-q-sql}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions existing-actions
             :existing-models changed-models})))))


(deftest test-make-and-migrate-alter-varchar-type-to-integer-field-ok
  (let [existing-actions '({:action :create-table
                            :model-name :feed
                            :fields {:name {:type [:varchar 100]}}})
        changed-models {:feed
                        {:fields [[:name :integer]]}}
        expected-actions '({:action :alter-column
                            :field-name :name
                            :model-name :feed
                            :options {:type :integer}
                            :changes {:type {:from [:varchar 100]
                                             :to :integer}}})
        expected-q-edn '({:create-table [:feed]
                          :with-columns [(:name [:varchar 100])]}
                         {:alter-table
                          (:feed {:alter-column
                                  [:name :type :integer
                                   :using [:raw "name"] [:raw "::"] :integer]})})
        expected-q-sql (list
                         ["CREATE TABLE feed (name VARCHAR(100))"]
                         [(str "ALTER TABLE feed ALTER COLUMN name TYPE INTEGER"
                            " USING name :: INTEGER")])]

    (is (= {:new-actions expected-actions
            :q-edn expected-q-edn
            :q-sql expected-q-sql}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions existing-actions
             :existing-models changed-models})))))


(deftest test-make-and-migrate-add-decimal-field-ok
  (let [existing-actions '({:action :create-table
                            :model-name :feed
                            :fields {:name {:type [:varchar 100]}}})
        changed-models {:feed
                        {:fields [[:name [:varchar 100]]
                                  [:amount [:decimal 10 2]]]}}
        expected-actions '({:action :add-column
                            :field-name :amount
                            :model-name :feed
                            :options {:type [:decimal 10 2]}})
        expected-q-edn '({:create-table [:feed]
                          :with-columns [(:name [:varchar 100])]}
                         {:alter-table :feed
                          :add-column (:amount [:decimal 10 2])})
        expected-q-sql (list
                         ["CREATE TABLE feed (name VARCHAR(100))"]
                         ["ALTER TABLE feed ADD COLUMN amount DECIMAL(10, 2)"])]

    (is (= {:new-actions expected-actions
            :q-edn expected-q-edn
            :q-sql expected-q-sql}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions existing-actions
             :existing-models changed-models})))))


(deftest test-make-and-migrate-alter-numeric-field-ok
  (let [existing-actions '({:action :create-table
                            :model-name :feed
                            :fields {:name {:type [:varchar 100]}
                                     :amount {:type [:numeric 10 2]}}})
        changed-models {:feed
                        {:fields [[:name [:varchar 100]]
                                  [:amount [:numeric 10]]]}}
        expected-actions '({:action :alter-column
                            :field-name :amount
                            :model-name :feed
                            :options {:type [:numeric 10]}
                            :changes {:type {:from [:numeric 10 2]
                                             :to [:numeric 10]}}})
        expected-q-edn '({:create-table [:feed]
                          :with-columns [(:name [:varchar 100])
                                         (:amount [:numeric 10 2])]}
                         {:alter-table
                          (:feed {:alter-column
                                  [:amount :type [:numeric 10]
                                   :using [:raw "amount"] [:raw "::"] [:numeric 10]]})})
        expected-q-sql (list
                         ["CREATE TABLE feed (name VARCHAR(100), amount NUMERIC(10, 2))"]
                         [(str "ALTER TABLE feed ALTER COLUMN amount TYPE NUMERIC(10)"
                            " USING amount :: NUMERIC(10)")])]

    (is (= {:new-actions expected-actions
            :q-edn expected-q-edn
            :q-sql expected-q-sql}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions existing-actions
             :existing-models changed-models})))))


(deftest test-make-and-migrate-add-decimal-field-with-default-value-ok
  (let [db config/DATABASE-CONN
        existing-actions '({:action :create-table
                            :model-name :feed
                            :fields {:id {:type :serial
                                          :primary-key true}
                                     :name {:type [:varchar 100]}}})
        changed-models {:feed
                        {:fields [[:id :serial {:primary-key true}]
                                  [:name [:varchar 100]]
                                  [:amount [:decimal 10 2] {:default "9.99"}]
                                  [:balance :decimal {:default 7.77M}]
                                  [:tx [:decimal 6] {:default 6.4}]]}}
        expected-actions '({:action :add-column
                            :field-name :tx
                            :model-name :feed
                            :options {:type [:decimal 6]
                                      :default 6.4}}
                           {:action :add-column
                            :field-name :amount
                            :model-name :feed
                            :options {:type [:decimal 10 2]
                                      :default "9.99"}}
                           {:action :add-column
                            :field-name :balance
                            :model-name :feed
                            :options {:type :decimal
                                      :default 7.77M}})
        expected-q-edn '({:create-table [:feed]
                          :with-columns [(:id :serial [:constraint :feed-pkey] :primary-key)
                                         (:name [:varchar 100])]}
                         {:alter-table :feed
                          :add-column (:tx [:decimal 6] [:default 6.4])}
                         {:alter-table :feed
                          :add-column (:amount [:decimal 10 2] [:default "9.99"])}
                         {:alter-table :feed
                          :add-column (:balance :decimal [:default 7.77M])})
        expected-q-sql (list
                         ["CREATE TABLE feed (id SERIAL CONSTRAINT feed_pkey PRIMARY KEY, name VARCHAR(100))"]
                         ["ALTER TABLE feed ADD COLUMN tx DECIMAL(6) DEFAULT 6.4"]
                         ["ALTER TABLE feed ADD COLUMN amount DECIMAL(10, 2) DEFAULT '9.99'"]
                         ["ALTER TABLE feed ADD COLUMN balance DECIMAL DEFAULT 7.77"])]

    (is (= {:new-actions expected-actions
            :q-edn expected-q-edn
            :q-sql expected-q-sql}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions existing-actions
             :existing-models changed-models})))

    (testing "test actual db schema after applying the migration"
      (is (= [{:character_maximum_length nil
               :column_default "nextval('feed_id_seq'::regclass)"
               :column_name "id"
               :data_type "integer"
               :is_identity "NO"
               :is_nullable "NO"
               :numeric_precision 32
               :numeric_scale 0
               :table_name "feed"}
              {:character_maximum_length 100
               :column_default nil
               :column_name "name"
               :data_type "character varying"
               :is_identity "NO"
               :is_nullable "YES"
               :numeric_precision nil
               :numeric_scale nil
               :table_name "feed"}
              {:character_maximum_length nil
               :column_default "6.4"
               :column_name "tx"
               :data_type "numeric"
               :is_identity "NO"
               :is_nullable "YES"
               :numeric_precision 6
               :numeric_scale 0
               :table_name "feed"}
              {:character_maximum_length nil
               :column_default "9.99"
               :column_name "amount"
               :data_type "numeric"
               :is_identity "NO"
               :is_nullable "YES"
               :numeric_precision 10
               :numeric_scale 2
               :table_name "feed"}
              {:character_maximum_length nil
               :column_default "7.77"
               :column_name "balance"
               :data_type "numeric"
               :is_identity "NO"
               :is_nullable "YES"
               :numeric_precision nil
               :numeric_scale nil
               :table_name "feed"}]
            (db-util/exec!
              db
              {:select [:table-name :data-type :column-name :column-default
                        :is-nullable :is-identity :numeric-precision
                        :numeric-scale :character-maximum-length]
               :from [:information-schema.columns]
               :where [:= :table-name "feed"]
               :order-by [:ordinal-position]}))))

    (testing "test indexes"
      (is (= [{:indexdef "CREATE UNIQUE INDEX feed_pkey ON public.feed USING btree (id)"
               :indexname "feed_pkey"
               :schemaname "public"
               :tablename "feed"
               :tablespace nil}]
            (db-util/exec!
              db
              {:select [:*]
               :from [:pg-indexes]
               :where [:= :tablename "feed"]
               :order-by [:indexname]}))))

    (testing "test constraints"
      (is (= [{:conname "feed_pkey"
               :contype "p"}]
            (db-util/exec!
              db
              {:select [:c.conname :c.contype]
               :from [[:pg-constraint :c]]
               :join [[:pg-class :t] [:= :t.oid :c.conrelid]]
               :where [:= :t.relname "feed"]
               :order-by [:c.oid]}))))))


(deftest test-make-and-migrate-add-char-field-with-default-value-ok
  (let [db config/DATABASE-CONN
        existing-actions '({:action :create-table
                            :model-name :feed
                            :fields {:id {:type :serial
                                          :primary-key true}}})
        changed-models {:feed
                        {:fields [[:id :serial {:primary-key true}]
                                  [:name [:varchar 10] {:default "test"}]]}}
        expected-actions '({:action :add-column
                            :field-name :name
                            :model-name :feed
                            :options {:type [:varchar 10]
                                      :default "test"}})
        expected-q-edn '({:create-table [:feed]
                          :with-columns [(:id :serial [:constraint :feed-pkey] :primary-key)]}
                         {:alter-table :feed
                          :add-column (:name [:varchar 10] [:default "test"])})
        expected-q-sql (list
                         ["CREATE TABLE feed (id SERIAL CONSTRAINT feed_pkey PRIMARY KEY)"]
                         ["ALTER TABLE feed ADD COLUMN name VARCHAR(10) DEFAULT 'test'"])]

    (is (= {:new-actions expected-actions
            :q-edn expected-q-edn
            :q-sql expected-q-sql}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions existing-actions
             :existing-models changed-models})))

    (testing "test actual db schema after applying the migration"
      (is (= [{:character_maximum_length nil
               :column_default "nextval('feed_id_seq'::regclass)"
               :column_name "id"
               :data_type "integer"
               :is_nullable "NO"
               :table_name "feed"}
              {:character_maximum_length 10
               :column_default "'test'::character varying"
               :column_name "name"
               :data_type "character varying"
               :is_nullable "YES"
               :table_name "feed"}]
            (db-util/exec!
              db
              {:select [:table-name :data-type :column-name :column-default
                        :is-nullable :character-maximum-length]
               :from [:information-schema.columns]
               :where [:= :table-name "feed"]
               :order-by [:ordinal-position]}))))))


(deftest test-make-and-migrate-alter-fk-if-fk-was-empty-ok
  (let [existing-actions '({:action :create-table
                            :model-name :feed
                            :fields {:id {:type :serial}
                                     :account {:type :integer}}}
                           {:action :create-table
                            :model-name :customer
                            :fields {:id {:type :serial
                                          :unique true}}})

        changed-models {:customer
                        {:fields [[:id :serial {:unique true}]]}

                        :feed
                        {:fields [[:id :serial]
                                  [:account :integer {:foreign-key :customer/id}]]}}
        expected-actions '({:action :alter-column
                            :field-name :account
                            :model-name :feed
                            :options {:type :integer
                                      :foreign-key :customer/id}
                            :changes {:foreign-key {:from :EMPTY
                                                    :to :customer/id}}})
        expected-q-edn '({:create-table [:feed]
                          :with-columns [(:id :serial)
                                         (:account :integer)]}
                         {:create-table [:customer]
                          :with-columns [(:id :serial :unique)]}
                         {:alter-table
                          (:feed
                            {:add-constraint (:feed-account-fkey
                                               [:foreign-key :account]
                                               (:references :customer :id))})})
        expected-q-sql (list
                         ["CREATE TABLE feed (id SERIAL, account INTEGER)"]
                         ["CREATE TABLE customer (id SERIAL UNIQUE)"]
                         [(str "ALTER TABLE feed"
                            " ADD CONSTRAINT feed_account_fkey FOREIGN KEY(account) REFERENCES customer(id)")])]

    (is (= {:new-actions expected-actions
            :q-edn expected-q-edn
            :q-sql expected-q-sql}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions existing-actions
             :existing-models changed-models})))

    (testing "test constraints"
      (is (= [{:colname "id"
               :constraint_name "customer_id_key"
               :constraint_type "UNIQUE"
               :table_name "customer"}
              {:colname "id"
               :constraint_name "feed_account_fkey"
               :constraint_type "FOREIGN KEY"
               :table_name "feed"}]
            (db-util/exec!
              config/DATABASE-CONN
              {:select [:tc.constraint_name
                        :tc.constraint_type
                        :tc.table_name
                        [:ccu.column_name :colname]]
               :from [[:information_schema.table_constraints :tc]]
               :join [[:information_schema.key_column_usage :kcu]
                      [:= :tc.constraint_name :kcu.constraint_name]

                      [:information_schema.constraint_column_usage :ccu]
                      [:= :ccu.constraint_name :tc.constraint_name]]
               :where [:in :ccu.table_name ["feed" "customer"]]
               :order-by [:tc.constraint_name]}))))))


(deftest test-make-and-migrate-alter-fk-drop-constraint-on-key-change-ok
  (let [existing-actions '({:action :create-table
                            :model-name :account
                            :fields {:id {:type :serial
                                          :unique true}}}
                           {:action :create-table
                            :model-name :feed
                            :fields {:id {:type :serial}
                                     :account {:type :integer
                                               :foreign-key :account/id}}}
                           {:action :create-table
                            :model-name :customer
                            :fields {:id {:type :serial
                                          :unique true}}})

        changed-models {:account
                        {:fields [[:id :serial {:unique true}]]}

                        :customer
                        {:fields [[:id :serial {:unique true}]]}

                        :feed
                        {:fields [[:id :serial]
                                  [:account :integer {:foreign-key :customer/id}]]}}
        expected-actions '({:action :alter-column
                            :field-name :account
                            :model-name :feed
                            :options {:type :integer
                                      :foreign-key :customer/id}
                            :changes {:foreign-key {:from :account/id
                                                    :to :customer/id}}})
        expected-q-edn '({:create-table [:account]
                          :with-columns [(:id :serial :unique)]}
                         {:create-table [:feed]
                          :with-columns [(:id :serial)
                                         (:account :integer (:references :account :id))]}
                         {:create-table [:customer]
                          :with-columns [(:id :serial :unique)]}
                         {:alter-table
                          (:feed
                            {:drop-constraint [[:raw "IF EXISTS"] :feed-account-fkey]}
                            {:add-constraint (:feed-account-fkey
                                               [:foreign-key :account]
                                               (:references :customer :id))})})
        expected-q-sql (list
                         ["CREATE TABLE account (id SERIAL UNIQUE)"]
                         ["CREATE TABLE feed (id SERIAL, account INTEGER REFERENCES account(id))"]
                         ["CREATE TABLE customer (id SERIAL UNIQUE)"]
                         [(str "ALTER TABLE feed DROP CONSTRAINT IF EXISTS feed_account_fkey"
                            ", ADD CONSTRAINT feed_account_fkey FOREIGN KEY(account) REFERENCES customer(id)")])]

    (is (= {:new-actions expected-actions
            :q-edn expected-q-edn
            :q-sql expected-q-sql}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions existing-actions
             :existing-models changed-models})))))
