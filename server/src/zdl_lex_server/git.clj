(ns zdl-lex-server.git
  (:require [clojure.core.async :as async]
            [clojure.java.shell :as sh]
            [clojure.string :as str]
            [mount.core :refer [defstate]]
            [taoensso.timbre :as timbre]
            [zdl-lex-server.bus :as bus]
            [zdl-lex-server.store :as store]))

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
    (vector (-> $ first keyword {:?? :added :M :modified :D :deleted})
            (second $))))

(defn status []
  (as-> (git "status" "--porcelain") $
    (:out $)
    (str/split $ #"[\n\r]+")
    (remove empty? $)
    (map status-text->vec $)
    (filter first $)
    (vec $)))

(def add (partial git "add"))

(def rm (partial git "rm"))

(defn commit []
  (let [stat (status)]
    (when-not (empty? stat)
      (add ".")
      (git "commit" "-m" "" "--allow-empty-message")
      (git "fetch" "origin")
      (git "rebase" "--allow-empty-message" "-s" "recursive" "-X" "theirs")
      (git "push" "origin")
      stat)))

(defstate changes
  :start (let [stop-ch (async/chan)]
           (async/go-loop []
             (when (async/alt! (async/timeout 10000) :tick stop-ch nil)
               (some->> (commit) (async/>! bus/git-changes))
               (recur)))
           stop-ch)
  :stop (async/close! changes))
