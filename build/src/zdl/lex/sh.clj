(ns zdl.lex.sh
  (:refer-clojure :exclude [run!])
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.tools.deps.alpha.util.dir :as deps-dir]
            [uberdeps.api :as uberdeps]
            [zdl.lex.fs :refer [path]])
  (:import java.io.File))

(defn run!
  [& args]
  (log/info (map #(if (instance? File %) (path %) (str %)) args))
  (let [{:keys [exit out err] :as result} (apply sh args)
        successful? (= 0 exit)]
    (when-not successful?
      (if-let [output (->> (map not-empty [out err])
                           (remove nil?)
                           (str/join \newline)
                           (not-empty))]
        (log/errorf "%s\n%s" args output))
      (throw (ex-info (str args) result)))
    result))
