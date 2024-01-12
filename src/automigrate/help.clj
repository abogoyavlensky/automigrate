(ns automigrate.help
  (:require [clojure.string :as str]))


(def ^:private DOC-LINK
  "https://github.com/abogoyavlensky/automigrate#documentation")


(def HELP-CMDS-ORDER
  ['make 'migrate 'list 'explain 'help])


(defn- fn-docstring
  [public-methods fn-sym]
  (-> public-methods (get fn-sym) (meta) :doc (str "\n")))


(defn- general-help
  [public-methods]
  (let [all-cmd-descs (reduce
                        (fn [acc cmd]
                          (let [cmd-desc (->> (fn-docstring public-methods cmd)
                                           (str/split-lines)
                                           (first))]
                            (conj acc (str "  " cmd " - " cmd-desc))))
                        []
                        HELP-CMDS-ORDER)]
    (str/join
      "\n"
      (concat
        ["Auto-generated database migrations for Clojure.\n"
         "Available commands:"]
        all-cmd-descs
        ["\nRun 'help :cmd COMMAND' for more information on a command.\n"
         (str "To get more info, check out automigrate documentation at " DOC-LINK)]))))


(defn- print-out-str
  [doc-str]
  (.write *out*
    (str doc-str "\n")))


(defn show-help!
  [{:keys [cmd]}]
  (let [public-methods (ns-publics 'automigrate.core)]
    (if (some? cmd)
      (-> public-methods (fn-docstring cmd) (print-out-str))
      (-> public-methods (general-help) (print-out-str)))))

