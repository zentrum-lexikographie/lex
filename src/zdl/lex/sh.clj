(ns zdl.lex.sh
  (:require [clojure.java.shell :refer [sh]]
            [clojure.string :as str]
            [clojure.tools.logging :as log]))

(defn sh!
  [& args]
  (log/debug args)
  (let [{:keys [exit out err] :as result} (apply sh args)
        successful? (= 0 exit)]
    (when-not successful?
      (when-let [output (->> (map not-empty [out err])
                           (remove nil?)
                           (str/join \newline)
                           (not-empty))]
        (log/errorf "%s\n%s" args output))
      (throw (ex-info (str args) result)))
    result))
