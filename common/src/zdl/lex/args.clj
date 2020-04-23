(ns zdl.lex.args
  (:require [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]))

(defn usage [options-summary]
  (->> ["Usage: program-name ..."
        ""
        "Options:"
        options-summary]
       (str/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn parse
  "Validate command line arguments. Either return a map indicating the program
  should exit (with a error message, and optional ok status), or a map
  indicating the action the program should take and the options provided."
  ([cli-options args]
   (parse cli-options (constantly true) args))
  ([cli-options valid? args]
   (parse cli-options valid? usage args))
  ([cli-options valid? usage args]
   (let [{:keys [options arguments errors summary] :as args}
         (parse-opts args cli-options)]
     (cond
       (:help options) ; help => exit OK with usage summary
       (exit 0 (usage summary))
       errors ; errors => exit with description of errors
       (exit 1 (error-msg errors))
       ;; custom validation on arguments
       (valid? arguments options)
       args
       :else ; failed custom validation => exit with usage summary
       (exit 2 (usage summary))))))
