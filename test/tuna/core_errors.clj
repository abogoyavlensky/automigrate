(ns tuna.core-errors
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [bond.james :as bond]
            [tuna.core :as core]
            [tuna.testing-config :as config]
            [tuna.util.file :as file-util]
            [tuna.util.test :as test-util]
            [tuna.errors :as errors]))


(deftest test-run-make-migrations-args-error
  (testing "check invalid command"
    (bond/with-stub! [[file-util/prn-err (constantly nil)]]
      (core/run {:cmd :wrong-cmd
                 :model-file (str config/MODELS-DIR "feed_basic.edn")
                 :migrations-dir config/MIGRATIONS-DIR})
      (let [error (-> (bond/calls file-util/prn-err) first :args first)]
        (is (= [{:message (str "Invalid command name.\n\n  {:cmd :wrong-cmd, "
                            ":model-file \"test/tuna/models/feed_basic.edn\", "
                            ":migrations-dir \"test/tuna/migrations\"}")
                 :title "COMMAND ERROR"}]
              (test-util/get-spec-error-data (constantly error)))))))

  (testing "check missing command"
    (bond/with-stub! [[file-util/prn-err (constantly nil)]]
      (core/run {:model-file (str config/MODELS-DIR "feed_basic.edn")
                 :migrations-dir config/MIGRATIONS-DIR})
      (let [error (-> (bond/calls file-util/prn-err) first :args first)]
        (is (= [{:message (str "Invalid command name.\n\n  {"
                            ":model-file \"test/tuna/models/feed_basic.edn\", "
                            ":migrations-dir \"test/tuna/migrations\"}")
                 :title "COMMAND ERROR"}]
              (test-util/get-spec-error-data (constantly error)))))))

  (testing "check missing model file path"
    (bond/with-stub! [[file-util/prn-err (constantly nil)]]
      (core/run {:cmd :make-migrations
                 :migrations-dir config/MIGRATIONS-DIR})
      (let [error (-> (bond/calls file-util/prn-err) first :args first)]
        (is (= [{:message (str "Missing model file path.\n\n  {:cmd :make-migrations, "
                            ":migrations-dir \"test/tuna/migrations\"}")
                 :title "COMMAND ERROR"}]
              (test-util/get-spec-error-data (constantly error)))))))

  (testing "check missing migrations dir path"
    (bond/with-stub! [[file-util/prn-err (constantly nil)]]
      (core/run {:cmd :make-migrations
                 :model-file (str config/MODELS-DIR "feed_basic.edn")})
      (let [error (-> (bond/calls file-util/prn-err) first :args first)]
        (is (= [{:message (str "Missing migrations dir path.\n\n  {:cmd :make-migrations, "
                            ":model-file \"test/tuna/models/feed_basic.edn\"}")
                 :title "COMMAND ERROR"}]
              (test-util/get-spec-error-data (constantly error)))))))

  (testing "check wrong type of migration"
    (bond/with-stub! [[file-util/prn-err (constantly nil)]]
      (core/run {:cmd :make-migrations
                 :model-file (str config/MODELS-DIR "feed_basic.edn")
                 :migrations-dir config/MIGRATIONS-DIR
                 :type "txt"})
      (let [error (-> (bond/calls file-util/prn-err) first :args first)]
        (is (= [{:message "Invalid migration type.\n\n  :txt"
                 :title "COMMAND ERROR"}]
              (test-util/get-spec-error-data (constantly error)))))))

  (testing "check missing migration name"
    (bond/with-stub! [[errors/custom-error->error-report (constantly nil)]]
      (core/run {:cmd :make-migrations
                 :model-file (str config/MODELS-DIR "feed_basic.edn")
                 :migrations-dir config/MIGRATIONS-DIR
                 :type :sql})
      (let [error (-> (bond/calls errors/custom-error->error-report) first :args first)]
        (is (= {:message "Missing migration name."}
              (dissoc (test-util/thrown-with-slingshot-data? [:type ::s/invalid] error) :type)))))))


(deftest test-run-migrate-args-error
  (testing "check missing db connection"
    (bond/with-stub! [[file-util/prn-err (constantly nil)]]
      (core/run {:cmd :migrate
                 :migrations-dir config/MIGRATIONS-DIR})
      (let [error (-> (bond/calls file-util/prn-err) first :args first)]
        (is (= [{:message (str "Missing db connection config.\n\n  {:cmd :migrate, "
                            ":migrations-dir \"test/tuna/migrations\"}")
                 :title "COMMAND ERROR"}]
              (test-util/get-spec-error-data (constantly error))))))))


(deftest test-run-explain-args-error
  (testing "check missing db connection"
    (bond/with-stub! [[file-util/prn-err (constantly nil)]]
      (core/run {:cmd :explain
                 :migrations-dir config/MIGRATIONS-DIR
                 :number 1
                 :direction :wrong})
      (let [error (-> (bond/calls file-util/prn-err) first :args first)]
        (is (= [{:message "Invalid direction of migration.\n\n  :wrong"
                 :title "COMMAND ERROR"}]
              (test-util/get-spec-error-data (constantly error))))))))