(ns zdl-lex-server.git
  (:require [clojure.java.shell :as sh]
            [zdl-lex-server.store :as store]))

(defn git [& args]
  (locking store/git-dir
    (sh/with-sh-dir store/git-dir
      (let [result (apply sh/sh (concat ["git"] args))]
        (when-not (= (result :exit) 0)
          (throw (ex-info (str args) result)))
        result))))

(def status (partial git "status" "--porcelain"))

(defn commit []
  (let [stat (status)]
    (when-not (empty? (stat :out))
      (git "add" ".")
      (git "commit" "-m" "" "--allow-empty-message")
      (git "push" "origin"))
    stat))
