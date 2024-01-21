(ns automigrate.constraints-test
  (:require [clojure.test :refer :all]
            [automigrate.testing-util :as test-util]
            [automigrate.testing-config :as config]))


(use-fixtures :each
  (test-util/with-drop-tables config/DATABASE-CONN)
  (test-util/with-delete-dir config/MIGRATIONS-DIR))


; PRIMARY KEY

(deftest test-create-table-with-primary-key-constraint-on-column
  (testing "check generated actions, queries edn and sql from all actions"
    (is (= {:new-actions (list {:action :create-table
                                :fields {:id {:type :serial
                                              :primary-key true}}
                                :model-name :users})
            :q-edn [{:create-table [:users]
                     :with-columns ['(:id :serial [:constraint :users-pkey] :primary-key)]}]
            :q-sql [["CREATE TABLE users (id SERIAL CONSTRAINT users_pkey PRIMARY KEY)"]]}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions []
             :existing-models {:users
                               {:fields [[:id :serial {:primary-key true}]]}}})))

    (testing "check actual db changes"
      (is (= [{:character_maximum_length nil
               :column_default "nextval('users_id_seq'::regclass)"
               :column_name "id"
               :data_type "integer"
               :udt_name "int4"
               :is_nullable "NO"
               :table_name "users"}]
            (test-util/get-table-schema-from-db config/DATABASE-CONN "users"))))

    (testing "test constraints in db"
      (is (= [{:colname "id"
               :constraint_name "users_pkey"
               :constraint_type "PRIMARY KEY"
               :table_name "users"}]
            (test-util/get-constraints "users"))))))


(deftest test-alter-primary-key-constraint-added-on-column
  (testing "check generated actions, queries edn and sql from all actions"
    (is (= {:new-actions (list {:action :alter-column
                                :options {:type :serial
                                          :primary-key true}
                                :changes {:primary-key {:from :EMPTY
                                                        :to true}}
                                :field-name :id
                                :model-name :users})
            :q-edn [{:create-table [:users]
                     :with-columns ['(:id :serial)]}
                    {:alter-table '(:users {:add-constraint [:users-pkey [:primary-key :id]]})}]
            :q-sql [["CREATE TABLE users (id SERIAL)"]
                    ["ALTER TABLE users ADD CONSTRAINT users_pkey PRIMARY KEY(id)"]]}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions [{:action :create-table
                                 :fields {:id {:type :serial}}
                                 :model-name :users}]
             :existing-models {:users
                               {:fields [[:id :serial {:primary-key true}]]}}})))

    (testing "check actual db changes"
      (is (= [{:character_maximum_length nil
               :column_default "nextval('users_id_seq'::regclass)"
               :column_name "id"
               :data_type "integer"
               :udt_name "int4"
               :is_nullable "NO"
               :table_name "users"}]
            (test-util/get-table-schema-from-db config/DATABASE-CONN "users"))))

    (testing "test constraints in db"
      (is (= [{:colname "id"
               :constraint_name "users_pkey"
               :constraint_type "PRIMARY KEY"
               :table_name "users"}]
            (test-util/get-constraints "users"))))))


(deftest test-drop-primary-key-constraint
  (testing "check generated actions, queries edn and sql from all actions"
    (is (= {:new-actions (list {:action :alter-column
                                :options {:type :serial}
                                :changes {:primary-key {:from true
                                                        :to :EMPTY}}
                                :field-name :id
                                :model-name :users})
            :q-edn [{:create-table [:users]
                     :with-columns ['(:id :serial [:constraint :users-pkey] :primary-key)]}
                    {:alter-table '(:users {:drop-constraint :users-pkey})}]
            :q-sql [["CREATE TABLE users (id SERIAL CONSTRAINT users_pkey PRIMARY KEY)"]
                    ["ALTER TABLE users DROP CONSTRAINT users_pkey"]]}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions [{:action :create-table
                                 :fields {:id {:type :serial
                                               :primary-key true}}
                                 :model-name :users}]
             :existing-models {:users
                               {:fields [[:id :serial]]}}})))

    (testing "check actual db changes"
      (is (= [{:character_maximum_length nil
               :column_default "nextval('users_id_seq'::regclass)"
               :column_name "id"
               :data_type "integer"
               :udt_name "int4"
               :is_nullable "NO"
               :table_name "users"}]
            (test-util/get-table-schema-from-db config/DATABASE-CONN "users"))))

    (testing "test constraints in db"
      (is (= []
            (test-util/get-constraints "users"))))))


; UNIQUE

(deftest test-create-table-with-unique-constraint
  (testing "check generated actions, queries edn and sql from all actions"
    (is (= {:new-actions (list {:action :create-table
                                :fields {:id {:type :serial
                                              :unique true}}
                                :model-name :users})
            :q-edn [{:create-table [:users]
                     :with-columns ['(:id :serial [:constraint :users-id-key] :unique)]}]
            :q-sql [["CREATE TABLE users (id SERIAL CONSTRAINT users_id_key UNIQUE)"]]}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions []
             :existing-models {:users
                               {:fields [[:id :serial {:unique true}]]}}})))

    (testing "check actual db changes"
      (is (= [{:character_maximum_length nil
               :column_default "nextval('users_id_seq'::regclass)"
               :column_name "id"
               :data_type "integer"
               :udt_name "int4"
               :is_nullable "NO"
               :table_name "users"}]
            (test-util/get-table-schema-from-db config/DATABASE-CONN "users"))))

    (testing "test constraints in db"
      (is (= [{:colname "id"
               :constraint_name "users_id_key"
               :constraint_type "UNIQUE"
               :table_name "users"}]
            (test-util/get-constraints "users"))))))


(deftest test-alter-unique-constraint
  (testing "check generated actions, queries edn and sql from all actions"
    (is (= {:new-actions (list {:action :alter-column
                                :options {:type :serial
                                          :unique true}
                                :changes {:unique {:from :EMPTY
                                                   :to true}}
                                :field-name :id
                                :model-name :users})
            :q-edn [{:create-table [:users]
                     :with-columns ['(:id :serial)]}
                    {:alter-table '(:users {:add-constraint
                                            [:users-id-key [:unique nil :id]]})}]
            :q-sql [["CREATE TABLE users (id SERIAL)"]
                    ["ALTER TABLE users ADD CONSTRAINT users_id_key UNIQUE(id)"]]}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions [{:action :create-table
                                 :fields {:id {:type :serial}}
                                 :model-name :users}]
             :existing-models {:users
                               {:fields [[:id :serial {:unique true}]]}}})))

    (testing "check actual db changes"
      (is (= [{:character_maximum_length nil
               :column_default "nextval('users_id_seq'::regclass)"
               :column_name "id"
               :data_type "integer"
               :udt_name "int4"
               :is_nullable "NO"
               :table_name "users"}]
            (test-util/get-table-schema-from-db config/DATABASE-CONN "users"))))

    (testing "test constraints in db"
      (is (= [{:colname "id"
               :constraint_name "users_id_key"
               :constraint_type "UNIQUE"
               :table_name "users"}]
            (test-util/get-constraints "users"))))))


(deftest test-drop-unique-constraint
  (testing "check generated actions, queries edn and sql from all actions"
    (is (= {:new-actions (list {:action :alter-column
                                :options {:type :serial}
                                :changes {:unique {:from true
                                                   :to :EMPTY}}
                                :field-name :id
                                :model-name :users})
            :q-edn [{:create-table [:users]
                     :with-columns ['(:id :serial [:constraint :users-id-key] :unique)]}
                    {:alter-table '(:users {:drop-constraint :users-id-key})}]
            :q-sql [["CREATE TABLE users (id SERIAL CONSTRAINT users_id_key UNIQUE)"]
                    ["ALTER TABLE users DROP CONSTRAINT users_id_key"]]}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions [{:action :create-table
                                 :fields {:id {:type :serial
                                               :unique true}}
                                 :model-name :users}]
             :existing-models {:users
                               {:fields [[:id :serial]]}}})))

    (testing "check actual db changes"
      (is (= [{:character_maximum_length nil
               :column_default "nextval('users_id_seq'::regclass)"
               :column_name "id"
               :data_type "integer"
               :udt_name "int4"
               :is_nullable "NO"
               :table_name "users"}]
            (test-util/get-table-schema-from-db config/DATABASE-CONN "users"))))

    (testing "test constraints in db"
      (is (= []
            (test-util/get-constraints "users"))))))


; FOREIGN KEY

(deftest test-create-table-with-foreign-key-constraint
  (testing "check generated actions, queries edn and sql from all actions"
    (is (= {:new-actions (list
                           {:action :create-table
                            :fields {:id {:type :serial
                                          :primary-key true}}
                            :model-name :users}
                           {:action :create-table
                            :fields {:id {:primary-key true
                                          :type :serial}
                                     :user-id {:foreign-key :users/id
                                               :on-delete :cascade
                                               :type :integer}}
                            :model-name :post})
            :q-edn [{:create-table [:users]
                     :with-columns ['(:id :serial [:constraint :users-pkey] :primary-key)]}
                    {:create-table [:post]
                     :with-columns ['(:id :serial [:constraint :post-pkey] :primary-key)
                                    '(:user-id :integer
                                       [:constraint :post-user-id-fkey]
                                       (:references :users :id)
                                       [:raw "on delete"]
                                       [:raw "cascade"])]}]
            :q-sql [["CREATE TABLE users (id SERIAL CONSTRAINT users_pkey PRIMARY KEY)"]
                    [(str "CREATE TABLE post (id SERIAL CONSTRAINT post_pkey PRIMARY KEY,"
                       " user_id INTEGER CONSTRAINT post_user_id_fkey REFERENCES users(id) on delete cascade)")]]}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions []
             :existing-models {:users
                               {:fields [[:id :serial {:primary-key true}]]}
                               :post
                               {:fields [[:id :serial {:primary-key true}]
                                         [:user-id :integer {:foreign-key :users/id
                                                             :on-delete :cascade}]]}}})))

    (testing "check actual db changes"
      (is (= [{:character_maximum_length nil
               :column_default "nextval('users_id_seq'::regclass)"
               :column_name "id"
               :data_type "integer"
               :udt_name "int4"
               :is_nullable "NO"
               :table_name "users"}]
            (test-util/get-table-schema-from-db config/DATABASE-CONN "users")))
      (is (= [{:character_maximum_length nil
               :column_default "nextval('post_id_seq'::regclass)"
               :column_name "id"
               :data_type "integer"
               :udt_name "int4"
               :is_nullable "NO"
               :table_name "post"}
              {:character_maximum_length nil
               :column_default nil
               :column_name "user_id"
               :data_type "integer"
               :udt_name "int4"
               :is_nullable "YES"
               :table_name "post"}]
            (test-util/get-table-schema-from-db config/DATABASE-CONN "post"))))

    (testing "test constraints in db"
      (is (= [{:conname "post_user_id_fkey"
               :contype "f"
               :pg_get_constraintdef
               "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE"
               :table_name "post"}]
            (test-util/get-constraints-simple config/DATABASE-CONN
              {:model-name-str "post"
               :contype test-util/CONTYPE-FK}))))))


(deftest test-drop-foreign-key-constraint
  (testing "check generated actions, queries edn and sql from all actions"
    (is (= {:new-actions (list
                           {:action :alter-column
                            :options {:type :integer}
                            :changes {:foreign-key {:from :users/id
                                                    :to :EMPTY}
                                      :on-delete {:from :cascade
                                                  :to :EMPTY}}
                            :field-name :user-id
                            :model-name :post})
            :q-edn [{:create-table [:users]
                     :with-columns ['(:id :serial [:constraint :users-pkey] :primary-key)]}
                    {:create-table [:post]
                     :with-columns ['(:id :serial [:constraint :post-pkey] :primary-key)
                                    '(:user-id :integer
                                       [:constraint :post-user-id-fkey]
                                       (:references :users :id)
                                       [:raw "on delete"]
                                       [:raw "cascade"])]}
                    {:alter-table '(:post {:drop-constraint :post-user-id-fkey})}]
            :q-sql [["CREATE TABLE users (id SERIAL CONSTRAINT users_pkey PRIMARY KEY)"]
                    [(str "CREATE TABLE post (id SERIAL CONSTRAINT post_pkey PRIMARY KEY,"
                       " user_id INTEGER CONSTRAINT post_user_id_fkey REFERENCES users(id) on delete cascade)")]
                    ["ALTER TABLE post DROP CONSTRAINT post_user_id_fkey"]]}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions [{:action :create-table
                                 :fields {:id {:type :serial
                                               :primary-key true}}
                                 :model-name :users}
                                {:action :create-table
                                 :fields {:id {:primary-key true
                                               :type :serial}
                                          :user-id {:foreign-key :users/id
                                                    :on-delete :cascade
                                                    :type :integer}}
                                 :model-name :post}]
             :existing-models {:users
                               {:fields [[:id :serial {:primary-key true}]]}
                               :post
                               {:fields [[:id :serial {:primary-key true}]
                                         [:user-id :integer]]}}})))

    (testing "check actual db changes"
      (is (= [{:character_maximum_length nil
               :column_default "nextval('users_id_seq'::regclass)"
               :column_name "id"
               :data_type "integer"
               :udt_name "int4"
               :is_nullable "NO"
               :table_name "users"}]
            (test-util/get-table-schema-from-db config/DATABASE-CONN "users")))
      (is (= [{:character_maximum_length nil
               :column_default "nextval('post_id_seq'::regclass)"
               :column_name "id"
               :data_type "integer"
               :udt_name "int4"
               :is_nullable "NO"
               :table_name "post"}
              {:character_maximum_length nil
               :column_default nil
               :column_name "user_id"
               :data_type "integer"
               :udt_name "int4"
               :is_nullable "YES"
               :table_name "post"}]
            (test-util/get-table-schema-from-db config/DATABASE-CONN "post"))))

    (testing "test constraints in db"
      (is (= []
            (test-util/get-constraints-simple config/DATABASE-CONN
              {:model-name-str "post"
               :contype test-util/CONTYPE-FK}))))))


; CHECK

(deftest test-create-table-with-check-constraint-on-column
  (testing "check generated actions, queries edn and sql from all actions"
    (is (= {:new-actions (list {:action :create-table
                                :fields {:month {:type :integer
                                                 :check [:and
                                                         [:> :month 0]
                                                         [:<= :month 12]]}}
                                :model-name :post})
            :q-edn [{:create-table [:post]
                     :with-columns ['(:month :integer [:constraint :post-month-check]
                                       [:check [:and [:> :month 0] [:<= :month 12]]])]}]
            :q-sql [["CREATE TABLE post (month INTEGER CONSTRAINT post_month_check CHECK((month > 0) AND (month <= 12)))"]]}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions []
             :existing-models {:post
                               {:fields [[:month :integer {:check [:and
                                                                   [:> :month 0]
                                                                   [:<= :month 12]]}]]}}})))

    (testing "check actual db changes"
      (is (= [{:character_maximum_length nil
               :column_default nil
               :column_name "month"
               :data_type "integer"
               :udt_name "int4"
               :is_nullable "YES"
               :table_name "post"}]
            (test-util/get-table-schema-from-db config/DATABASE-CONN "post"))))

    (testing "test constraints in db"
      (is (= [{:conname "post_month_check"
               :contype "c"
               :pg_get_constraintdef "CHECK (((month > 0) AND (month <= 12)))"
               :table_name "post"}]
            (test-util/get-constraints-simple config/DATABASE-CONN
              {:model-name-str "post"
               :contype test-util/CONTYPE-CHECK}))))))


(deftest test-add-column-with-check-constraint
  (testing "check generated actions, queries edn and sql from all actions"
    (is (= {:new-actions (list {:action :add-column
                                :options {:type :integer
                                          :check [:and
                                                  [:> :month 0]
                                                  [:<= :month 12]]}
                                :field-name :month
                                :model-name :post})
            :q-edn [{:create-table [:post]
                     :with-columns ['(:id :serial)]}
                    {:add-column '(:month :integer [:constraint :post-month-check]
                                    [:check [:and [:> :month 0] [:<= :month 12]]])
                     :alter-table :post}]
            :q-sql [["CREATE TABLE post (id SERIAL)"]
                    ["ALTER TABLE post ADD COLUMN month INTEGER CONSTRAINT post_month_check CHECK((month > 0) AND (month <= 12))"]]}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions [{:action :create-table
                                 :fields {:id {:type :serial}}
                                 :model-name :post}]
             :existing-models {:post
                               {:fields [[:id :serial]
                                         [:month :integer {:check [:and
                                                                   [:> :month 0]
                                                                   [:<= :month 12]]}]]}}})))

    (testing "check actual db changes"
      (is (= [{:character_maximum_length nil
               :column_default "nextval('post_id_seq'::regclass)"
               :column_name "id"
               :data_type "integer"
               :udt_name "int4"
               :is_nullable "NO"
               :table_name "post"}
              {:character_maximum_length nil
               :column_default nil
               :column_name "month"
               :data_type "integer"
               :udt_name "int4"
               :is_nullable "YES"
               :table_name "post"}]
            (test-util/get-table-schema-from-db config/DATABASE-CONN "post"))))

    (testing "test constraints in db"
      (is (= [{:conname "post_month_check"
               :contype "c"
               :pg_get_constraintdef "CHECK (((month > 0) AND (month <= 12)))"
               :table_name "post"}]
            (test-util/get-constraints-simple config/DATABASE-CONN
              {:model-name-str "post"
               :contype test-util/CONTYPE-CHECK}))))))


(deftest test-add-check-constraint-on-column
  (testing "check generated actions, queries edn and sql from all actions"
    (is (= {:new-actions (list {:action :alter-column
                                :options {:type :integer
                                          :check [:and
                                                  [:> :month 0]
                                                  [:<= :month 12]]}
                                :changes {:check {:from :EMPTY
                                                  :to [:and
                                                       [:> :month 0]
                                                       [:<= :month 12]]}}
                                :field-name :month
                                :model-name :post})
            :q-edn [{:create-table [:post]
                     :with-columns ['(:month :integer)]}
                    {:alter-table
                     '(:post {:add-constraint
                              [:post-month-check [:check [:and [:> :month 0] [:<= :month 12]]]]})}]
            :q-sql [["CREATE TABLE post (month INTEGER)"]
                    ["ALTER TABLE post ADD CONSTRAINT post_month_check CHECK((month > 0) AND (month <= 12))"]]}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions [{:action :create-table
                                 :fields {:month {:type :integer}}
                                 :model-name :post}]
             :existing-models {:post
                               {:fields [[:month :integer {:check [:and
                                                                   [:> :month 0]
                                                                   [:<= :month 12]]}]]}}})))

    (testing "check actual db changes"
      (is (= [{:character_maximum_length nil
               :column_default nil
               :column_name "month"
               :data_type "integer"
               :udt_name "int4"
               :is_nullable "YES"
               :table_name "post"}]
            (test-util/get-table-schema-from-db config/DATABASE-CONN "post"))))

    (testing "test constraints in db"
      (is (= [{:conname "post_month_check"
               :contype "c"
               :pg_get_constraintdef "CHECK (((month > 0) AND (month <= 12)))"
               :table_name "post"}]
            (test-util/get-constraints-simple config/DATABASE-CONN
              {:model-name-str "post"
               :contype test-util/CONTYPE-CHECK}))))))


(deftest test-alter-check-constraint-on-column
  (testing "check generated actions, queries edn and sql from all actions"
    (is (= {:new-actions (list {:action :alter-column
                                :options {:type :integer
                                          :check [:> :month 0]}
                                :changes {:check {:from [:and
                                                         [:> :month 0]
                                                         [:<= :month 12]]
                                                  :to [:> :month 0]}}
                                :field-name :month
                                :model-name :post})
            :q-edn [{:create-table [:post]
                     :with-columns ['(:month :integer [:constraint :post-month-check]
                                       [:check [:and [:> :month 0] [:<= :month 12]]])]}
                    {:alter-table
                     '(:post
                        {:drop-constraint [[:raw "IF EXISTS"] :post-month-check]}
                        {:add-constraint [:post-month-check [:check [:> :month 0]]]})}]
            :q-sql [["CREATE TABLE post (month INTEGER CONSTRAINT post_month_check CHECK((month > 0) AND (month <= 12)))"]
                    [(str "ALTER TABLE post DROP CONSTRAINT IF EXISTS post_month_check,"
                       " ADD CONSTRAINT post_month_check CHECK(month > 0)")]]}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions [{:action :create-table
                                 :fields {:month {:type :integer
                                                  :check [:and
                                                          [:> :month 0]
                                                          [:<= :month 12]]}}
                                 :model-name :post}]
             :existing-models {:post
                               {:fields [[:month :integer {:check [:> :month 0]}]]}}})))

    (testing "check actual db changes"
      (is (= [{:character_maximum_length nil
               :column_default nil
               :column_name "month"
               :data_type "integer"
               :udt_name "int4"
               :is_nullable "YES"
               :table_name "post"}]
            (test-util/get-table-schema-from-db config/DATABASE-CONN "post"))))

    (testing "test constraints in db"
      (is (= [{:conname "post_month_check"
               :contype "c"
               :pg_get_constraintdef "CHECK ((month > 0))"
               :table_name "post"}]
            (test-util/get-constraints-simple config/DATABASE-CONN
              {:model-name-str "post"
               :contype test-util/CONTYPE-CHECK}))))))


(deftest test-drop-check-constraint-on-column
  (testing "check generated actions, queries edn and sql from all actions"
    (is (= {:new-actions (list {:action :alter-column
                                :options {:type :integer}
                                :changes {:check {:from [:and
                                                         [:> :month 0]
                                                         [:<= :month 12]]
                                                  :to :EMPTY}}
                                :field-name :month
                                :model-name :post})
            :q-edn [{:create-table [:post]
                     :with-columns ['(:month :integer [:constraint :post-month-check]
                                       [:check [:and [:> :month 0] [:<= :month 12]]])]}
                    {:alter-table
                     '(:post {:drop-constraint :post-month-check})}]
            :q-sql [["CREATE TABLE post (month INTEGER CONSTRAINT post_month_check CHECK((month > 0) AND (month <= 12)))"]
                    ["ALTER TABLE post DROP CONSTRAINT post_month_check"]]}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions [{:action :create-table
                                 :fields {:month {:type :integer
                                                  :check [:and
                                                          [:> :month 0]
                                                          [:<= :month 12]]}}
                                 :model-name :post}]
             :existing-models {:post
                               {:fields [[:month :integer]]}}})))

    (testing "check actual db changes"
      (is (= [{:character_maximum_length nil
               :column_default nil
               :column_name "month"
               :data_type "integer"
               :udt_name "int4"
               :is_nullable "YES"
               :table_name "post"}]
            (test-util/get-table-schema-from-db config/DATABASE-CONN "post"))))

    (testing "test constraints in db"
      (is (= []
            (test-util/get-constraints-simple config/DATABASE-CONN
              {:model-name-str "post"
               :contype test-util/CONTYPE-CHECK}))))))


(deftest test-error-check-constraint-on-column
  (testing "check can't be nil"
    (let [params {:existing-models
                  {:account
                   {:fields [[:thing :integer {:check nil}]]}}}]
      (is (= (str "-- MODEL ERROR -------------------------------------\n\n"
               "Option :check of field :account/thing should be a not empty vector.\n\n"
               "  {:check nil}\n\n")
            (with-out-str
              (test-util/make-migration! params))))))

  (testing "check can't be string"
    (let [params {:existing-models
                  {:account
                   {:fields [[:thing :integer {:check "WRONG"}]]}}}]
      (is (= (str "-- MODEL ERROR -------------------------------------\n\n"
               "Option :check of field :account/thing should be a not empty vector.\n\n"
               "  {:check \"WRONG\"}\n\n")
            (with-out-str
              (test-util/make-migration! params))))))

  (testing "check can't be an empty vector"
    (let [params {:existing-models
                  {:account
                   {:fields [[:thing :integer {:check []}]]}}}]
      (is (= (str "-- MODEL ERROR -------------------------------------\n\n"
               "Option :check of field :account/thing should be a not empty vector.\n\n"
               "  {:check []}\n\n")
            (with-out-str
              (test-util/make-migration! params)))))))
