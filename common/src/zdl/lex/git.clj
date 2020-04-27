(ns zdl.lex.git
  (:require [clojure.java.shell :as sh]
            [clojure.string :as str]
            [zdl.lex.sh :as zdl-sh]))

(defn sh!
  [dir & args]
  (->>
   (apply zdl-sh/sh! (concat ["git"] args))
   (sh/with-sh-dir dir)))

(defn refs
  [dir]
  (->> (sh! dir "for-each-ref" "--format" "%(refname:short)")
       :out str/split-lines
       (apply sorted-set)))

(defn head-ref
  [dir]
  (->> (sh! dir "symbolic-ref" "--short" "-q" "HEAD") :out str/trim))

(defn head-rev
  [dir]
  (->> (sh! dir "rev-parse" "HEAD") :out str/trim))

(def ^:private status->kw
  {\space :ok
   \A :added
   \C :copied
   \D :deleted
   \M :modified
   \R :renamed
   \? :untracked
   \! :ignored})

(defn status->paths
  [path]
  (->> (str/split path #"->")
       (map #(str/replace % #"\"" ""))
       (map not-empty) (remove nil?)))

(defn status
  [dir]
  (->> (sh! dir "status" "-s" "--porcelain")
       :out str/split-lines (map not-empty) (remove nil?)
       (map #(array-map :index (status->kw (nth % 0))
                        :dir (status->kw (nth % 1))
                        :paths (status->paths (subs % 3))))))

(defn dirty?
  [dir]
  (seq (status dir)))

(defn assert-clean
  [dir]
  (when (dirty? dir)
    (throw (IllegalStateException. "Git dir is dirty."))))


