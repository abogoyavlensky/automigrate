(ns automigrate.core-errors-test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [bond.james :as bond]
            [automigrate.core :as core]
            [automigrate.migrations :as migrations]
            [automigrate.testing-config :as config]
            [automigrate.util.file :as file-util]
            [automigrate.testing-util :as test-util]
            [automigrate.errors :as errors]))


(use-fixtures :each
  (test-util/with-drop-tables config/DATABASE-CONN)
  (test-util/with-delete-dir config/MIGRATIONS-DIR))


(deftest test-run-make-migration-args-error
  (testing "check missing model file path"
    (bond/with-spy [migrations/make-next-migration]
      (core/make {:migrations-dir config/MIGRATIONS-DIR})
      (is (= "resources/db/models.edn"
            (-> (bond/calls #'migrations/make-next-migration) first :args first :models-file)))))

  (testing "check missing migrations dir path"
    (bond/with-spy [migrations/make-next-migration]
      (core/make {:models-file (str config/MODELS-DIR "feed_basic.edn")})
      (is (= "resources/db/migrations"
            (-> (bond/calls #'migrations/make-next-migration) first :args first :migrations-dir)))))

  (testing "check wrong type of migration"
    (bond/with-stub! [[file-util/prn-err (constantly nil)]]
      (core/make {:models-file (str config/MODELS-DIR "feed_basic.edn")
                  :migrations-dir config/MIGRATIONS-DIR
                  :type "txt"})
      (let [error (-> (bond/calls file-util/prn-err) first :args first)]
        (is (= [{:message "Invalid migration type.\n\n  :txt"
                 :title "COMMAND ERROR"}]
              (test-util/get-spec-error-data (constantly error)))))))

  (testing "check missing migration name"
    (bond/with-stub! [[errors/custom-error->error-report (constantly nil)]]
      (core/make {:models-file (str config/MODELS-DIR "feed_basic.edn")
                  :migrations-dir config/MIGRATIONS-DIR
                  :type :empty-sql})
      (let [error (-> (bond/calls errors/custom-error->error-report) first :args first)]
        (is (= {:message "Missing migration name."}
              (dissoc (test-util/thrown-with-slingshot-data? [:type ::s/invalid] error) :type)))))))


(deftest test-run-migrate-args-error
  (testing "check missing db connection"
    (is (= (str "-- COMMAND ERROR -------------------------------------\n\n"
             "Missing database connection URL.\n\n"
             "  nil\n\n")
          (with-out-str
            (core/migrate {:migrations-dir config/MIGRATIONS-DIR})))))

  (testing "check invalid target migration number"
    (core/make {:models-file (str config/MODELS-DIR "feed_basic.edn")
                :migrations-dir config/MIGRATIONS-DIR})
    (is (= "-- ERROR -------------------------------------\n\nInvalid target migration number.\n\n"
          (with-out-str
            (core/migrate {:jdbc-url config/DATABASE-URL
                           :migrations-dir config/MIGRATIONS-DIR
                           :number 4}))))))


(deftest test-run-explain-args-error
  (testing "check missing db connection"
    (is (= (str "-- COMMAND ERROR -------------------------------------\n\n"
             "Invalid direction of migration.\n\n  :wrong\n\n")
          (with-out-str
            (core/explain {:migrations-dir config/MIGRATIONS-DIR
                           :number 1
                           :direction :wrong})))))

  (testing "check missing migration by number"
    (is (= (str "-- ERROR -------------------------------------\n\n"
             "Missing migration by number 10\n\n")
          (with-out-str
            (core/explain {:migrations-dir config/MIGRATIONS-DIR
                           :number 10}))))))


(deftest test-run-unexpected-error
  (testing "check fiction unexpected error"
    #_{:clj-kondo/ignore [:private-call]}
    (bond/with-stub! [[migrations/get-detailed-migrations-to-migrate
                       (fn [& _] (throw (Exception. "Testing error message.")))]]
      (is (= (str "-- UNEXPECTED ERROR -------------------------------------\n\n"
               "Testing error message.\n\n")
            (with-out-str
              (core/migrate {:migrations-dir config/MIGRATIONS-DIR
                             :jdbc-url config/DATABASE-URL})))))))
