(ns zdl-lex-server.git
  (:require [clojure.java.shell :as sh]
            [zdl-lex-server.store :as store]
            [clojure.string :as str]
            [me.raynes.fs :as fs]))

(defn git [& args]
  (locking store/git-dir
    (sh/with-sh-dir store/git-dir
      (let [result (apply sh/sh (concat ["git"] args))]
        (when-not (= (result :exit) 0)
          (throw (ex-info (str args) result)))
        result))))

(defn- status-text->vec [txt]
  (as-> txt $
    (str/trim $)
    (str/split $ #"\s+" 2)
    (vector (keyword (first $)) (second $))))

(defn status []
  (as-> (git "status" "--porcelain") $
    (:out $)
    (str/split $ #"[\n\r]+")
    (remove empty? $)
    (map status-text->vec $)
    (filter (comp #{:?? :M :D} first) $)))

(def add (partial git "add"))

(def rm (partial git "rm"))

(defn commit []
  (let [stat (status)]
    (when-not (empty? stat)
      (add ".")
      (git "commit" "-m" "" "--allow-empty-message")
      (git "fetch" "origin")
      (git "rebase" "--allow-empty-message" "-s"  "recursive" "-X" "theirs")
      (git "push" "origin"))
    stat))
