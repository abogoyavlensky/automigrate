(ns automigrate.help-test
  (:require [automigrate.help :as help]
            [clojure.test :refer :all]))


(deftest test-help-output-for-general-help
  (is (= (str "Database schema auto-migration tool for Clojure.\n\n"
           "Available commands:\n"
           "  make - Create a new migration based on changes to the models.\n"
           "  migrate - Run existing migrations and change the database schema.\n"
           "  list - Show the list of existing migrations with status.\n"
           "  explain - Show raw SQL or human-readable description for a migration by number.\n"
           "  help - Help information for all commands of automigrate tool.\n\n"
           "Run 'help :cmd COMMAND' for more information on a command.\n\n"
           "To get more info, check out automigrate documentation at https://github.com/abogoyavlensky/automigrate#documentation\n")
        (with-out-str
          (help/show-help! {})))))


(deftest test-help-output-for-make-command-help
  (is (= (str "Create a new migration based on changes to the models.\n\n"
           "Available options:\n"
           "  :models-file - Path to the file with model definitions. (required)\n"
           "  :migrations-dir - Path to directory containing migration files. (required)\n"
           "  :name - Custom name for a migration. (optional)\n"
           "  :type - Type of a new migration, empty by default for auto-migration.\n"
           "          Also available `:empty-sql` - for creating an empty raw SQL migration. (optional)\n\n")
        (with-out-str
          (help/show-help! {:cmd 'make})))))
