(ns zdl.lex.article.token
  (:require [clojure.string :as str]))

(def abbreviation-whitelist
  #{"etw.", "jmd.", "jmds.", "jmdn.", "jmdm.", "bzw.", "usw.", "o.\u202fä.", "o.\u202fÄ.", "z.\u202fB."})

(defn tokenize
  [s]
  (str/split s #"\s+"))


(defn check-ends-with-punctuation
  [s]
  (when-not (re-seq #"(?:[….?!])|(?:[.?!]«)$" s)
    {:type ::final-punctuation :data [(subs s (max 0 (- (count s) 2)))]}))

(defn check-unknown-abbreviations
  [s]
  (when-let [data (some->>
                   (tokenize s)
                   (filter #(str/ends-with? % "."))
                   (remove abbreviation-whitelist)
                   (distinct) (seq) (vec))]
    {:type ::unknown-abbreviations :data data}))

(defn check-missing-whitespace
  [s]
  (when-let [data (some->>
                   (tokenize s)
                   (filter
                    (some-fn
                     ;; e.g. aB ,A )A -A
                     (partial re-seq #"[\p{Ll}\p{Pe}\p{Po}&&[^/\"']]\p{Lu}")
                     ;; e.g. a( A( .( -(
                     (partial re-seq #"[\p{Lu}\p{Ll}\p{Po}\p{Pd}]\p{Ps}")
                     (partial re-seq #"«[^\p{Pe}\p{Po}]")
                     (partial re-seq #"[^\p{Ps}]»")))
                   (distinct) (seq) (vec))]
    {:type ::missing-whitespace :data data}))

(defn check-redundant-whitespace
  [s]
  (when-let [data (some->>
                   (concat
                    (re-seq #"[»(/]\s" s)
                    (re-seq #"\s[\p{Po}&&[^%&*†/…\"']]" s))
                   (distinct) (seq) (vec))]
    {:type ::redundant-whitespace :data data}))
