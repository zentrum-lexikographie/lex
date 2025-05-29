(ns zdl.lex.server.conllu
  "Parses and serializes annotated sentences in CoNLL-U format."
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.string :as str]
   [jsonista.core :as json])
  (:import
   (de.ids_mannheim.korap.tokenizer DerekoDfaTokenizer_de)
   (opennlp.tools.util Span)))

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

(def ^DerekoDfaTokenizer_de tokenizer
  (DerekoDfaTokenizer_de.))

(defn token->conllu
  [s space-after? i [token next-token]]
  (let [n            (inc i)
        start        (.getStart token)
        end          (.getEnd token)
        text         (subs s start end)
        space-after? (if next-token (< end (.getStart next-token)) space-after?)]
    (cond-> {:n (str n) :form text :features {:n n :i i}}
      (not space-after?) (-> (assoc :misc "SpaceAfter=No")
                             (assoc-in [:features :space-after] "No")))))

(defn tokenize
  [s hit? [^Span sentence ^Span next-sentence]]
  (let [start         (.getStart sentence)
        end           (.getEnd sentence)
        text          (subs s start end)
        space-after?  (and next-sentence (< end (.getStart next-sentence)))
        tokens        (.tokenizePos tokenizer text)
        hits          (into []
                            (comp (map-indexed
                                   (fn [i ^Span token]
                                     (when (some hit?
                                                 (range (+ start (.getStart token))
                                                        (+ start (.getEnd token))))
                                       (list (inc i)))))
                                  (mapcat identity))
                            tokens)
        token->conllu (partial token->conllu (subs s start) space-after?)]
    {:tokens   (into [] (map-indexed token->conllu) (partition-all 2 1 tokens))
     :metadata (cond-> []
                 (seq hits) (conj ["hits" (json/write-value-as-string hits)]))}))

(defn segment
  [s]
  (let [segments (map-indexed vector (str/split s #"</?t>"))
        [s hit?] (reduce
                  (fn [[s hit?] [n segment]]
                    [(str s segment)
                     (cond-> hit?
                       (odd? n) (into (range (count s)
                                             (+ (count s) (count segment)))))])
                  ["" #{}] segments)]
    (locking tokenizer
      (->> (.sentPosDetect tokenizer s)
           (partition-all 2 1)
           (into [] (map (partial tokenize s hit?)))))))
