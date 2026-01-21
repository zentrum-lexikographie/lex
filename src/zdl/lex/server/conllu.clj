(ns zdl.lex.server.conllu
  "Parses and serializes annotated sentences in CoNLL-U format."
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.string :as str]
   [clojure.java.io :as io]))

(def ->kebab-case
  (memoize csk/->kebab-case-keyword))

(defn decode-feature-set
  [v]
  (when v (map #(str/split % #"=" 2) (str/split v #"\|"))))

(defn assoc-feature
  [m [k v]]
  (cond-> m v (assoc (->kebab-case k) v)))

(defn assoc-features
  [i {:keys [head morph deps misc] :as token}]
  (let [head     (or (some-> head parse-long) 0)
        features (concat (decode-feature-set morph)
                         (decode-feature-set deps)
                         (decode-feature-set misc))
        features (cond-> (reduce assoc-feature {:i i :n (inc i)} features)
                   (pos? head) (assoc :head (dec head)))]
    (assoc token :features features)))

(def fields
  "Field names and order for token records."
  [:n :form :lemma :upos :xpos :morph :head :deprel :deps :misc])

(defn escape-underscore
  [s]
  (str/replace s #"_" "__"))

(defn unescape-underscore
  "ConLL-U uses `_` as `nil`."
  [s]
  (str/replace s #"__" "_"))

(defn decode-field
  "Translates empty/`nil` values."
  [v]
  (when-not (= "_" v) (-> v unescape-underscore not-empty)))

(defn assoc-i
  [i token]
  (assoc token :i i))

(defn assoc-head-i
  [{:keys [head] :as token}]
  (let [head (some-> head parse-long)]
    (cond-> token head (assoc :head-i (when-not (zero? head) (dec head))))))

(defn decode-token
  "Tokens and their annotations are lines with field values separated by tabs or
  at least two consecutive spaces."
  [i s]
  (->> (str/split s #"\t| {2,}")
       (map decode-field)
       (zipmap fields)
       (assoc-features i)))

(defn comment-line?
  "Comment lines with sentence metadata start with a hash symbol."
  [s]
  (str/starts-with? s "#"))

(defn parse-metadata
  [s]
  (-> s
      (str/replace #"^#\s+" "")
      (str/split #"\s*=\s*" 2)))

(defn parse-sentence
  [s]
  (let [[metadata tokens] (split-with comment-line? s)]
    (cond-> {:tokens (into [] (map-indexed decode-token) tokens)}
      (seq metadata) (assoc :metadata (into [] (map parse-metadata) metadata)))))

(defn empty-line?
  [s]
  (= "" s))

(def lines->sentences-xf
  (comp
   (partition-by empty-line?)
   (remove (comp empty-line? first))))

(defn parse
  "Parses sentences read from a given reader and separated by empty lines."
  [lines]
  (sequence (comp lines->sentences-xf (map parse-sentence)) lines))

(defn parse-str
  [s]
  (with-open [r (io/reader (java.io.StringReader. s))]
    (parse (doall (line-seq r)))))

(defn serialize-metadata
  [[k v]]
  (str "# " k " = " v))

(defn serialize-token-field
  [v]
  (or (some-> v not-empty escape-underscore) "_"))

(defn serialize-token
  [token]
  (str/join \tab (map (comp serialize-token-field token) fields)))

(defn serialize
  [{:keys [metadata tokens] :as _sentence}]
  (str/join \newline (concat (map serialize-metadata metadata)
                             (map serialize-token tokens)
                             (list "" ""))))
