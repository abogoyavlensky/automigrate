(ns automigrate.actions-create-table
  (:require [clojure.test :refer :all]
            [automigrate.testing-util :as test-util]
            [automigrate.testing-config :as config]))


(use-fixtures :each
  (test-util/with-drop-tables config/DATABASE-CONN)
  (test-util/with-delete-dir config/MIGRATIONS-DIR))


(deftest ^:eftest/slow test-action-create-table-with-name-user-ok
  (testing "check generated actions, queries edn and sql from all actions"
    (is (= {:new-actions [{:action :create-table
                           :fields {:id {:type :serial}
                                    :name {:type :varchar}}
                           :model-name :user}]
            :q-edn [{:create-table ["user"]
                     :with-columns ['(:id :serial)
                                    '(:name :varchar)]}]
            :q-sql [["CREATE TABLE \"user\" (id SERIAL, name VARCHAR)"]]}
          (test-util/perform-make-and-migrate!
            {:jdbc-url config/DATABASE-CONN
             :existing-actions []
             :existing-models {:user
                               {:fields [[:id :serial]
                                         [:name :varchar]]}}})))

    (testing "check actual db changes"
      (testing "test actual db schema after applying the migration"
        (is (= [{:character_maximum_length nil
                 :column_default "nextval('user_id_seq'::regclass)"
                 :column_name "id"
                 :data_type "integer"
                 :udt_name "int4"
                 :is_nullable "NO"
                 :table_name "user"}
                {:character_maximum_length nil
                 :column_default nil
                 :column_name "name"
                 :data_type "character varying"
                 :udt_name "varchar"
                 :is_nullable "YES"
                 :table_name "user"}]
              (test-util/get-table-schema-from-db config/DATABASE-CONN "user")))))))
