(ns zdl.lex.git
  (:require [clojure.string :as str]
            [zdl.lex.fs :refer [project-dir]]
            [zdl.lex.sh :as sh]))

(defn sh!
  [& args]
  (apply sh/run! (concat ["git"] args [:dir project-dir])))

(defn dirty?
  []
  (-> (sh! "status" "--porcelain") :out str/trim not-empty some?))

(defn assert-clean
  []
  (when (dirty?) (throw (IllegalStateException. "Git dir is dirty."))))
