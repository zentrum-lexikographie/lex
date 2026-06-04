(ns zdl.lex.corpora.korap
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.core.async :as a]
   [clojure.data.csv :as csv]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [com.potetm.fusebox.rate-limit :as rl :refer [with-rate-limit]]
   [com.potetm.fusebox.retry :as retry :refer [with-retry]]
   [hickory.core :as h]
   [jsonista.core :as json]
   [libpython-clj2.python :as py]
   [libpython-clj2.require :refer [require-python]]
   [org.httpkit.client :as hc]
   [taoensso.telemere :as tel]
   [zdl.lex.env :refer [getenv]]
   [zdl.lex.metrics :as metrics]
   [zdl.lex.util
    :refer
    [assoc*
     date->year
     format-date
     norm-str
     norm-str-coll
     norm-str-set
     parse-date
     parse-year
     pr-edn]])
  (:import
   (de.ids_mannheim.korap.tokenizer DerekoDfaTokenizer_de)
   (opennlp.tools.util Span)))

(def api-url
  {:dereko "https://korap.ids-mannheim.de/api/v1.0/search"
   :deliko "https://korap.dnb.de/api/v1.0/search"})

(def api-token
  {:dereko (getenv "DEREKO_ACCESS_TOKEN")
   :deliko (getenv "DELIKO_ACCESS_TOKEN")})

(def rate-limit
  {:dereko (rl/init {::rl/bucket-size     1
                     ::rl/period-ms       1000
                     ::rl/wait-timeout-ms 10000})
   :deliko (rl/init {::rl/bucket-size     1
                     ::rl/period-ms       2000
                     ::rl/wait-timeout-ms 20000})})

(def gateway-error-retry
  (retry/init
   {::retry/retry? (fn [n _ms ex]  (and  (< n 2)
                                         (some-> ex ex-data :status (= 502))))
    ::retry/delay  (fn [n _ms _ex] (* (inc n) 1000))}))

(def corpus-filter
  {:dereko "corpusSigle != /W[UDP]D.*/"})

(def nlp-batch-size
  (parse-long (getenv "NLP_BATCH_SIZE" "8")))

(defn clear-tags
  [s]
  (str/replace s #"</?[^>]+>" ""))

(defn html->text
  [node]
  (if (string? node)
    node
    (let [{:keys [tag content]} node
          content               (str/join (map html->text content))]
      (if (= :mark tag) (str "<t>" (clear-tags content) "</t>") content))))

(defn norm-title
  [s]
  (some-> (norm-str s) (str/replace #"(\p{Punct})\." "$1")))

(defn parse-bibl
  [title {corpus "corpusTitle" author "author" place "pubPlace" date "pubDate"}]
  (let [title  (some->> (norm-str-coll [author title]) (str/join ": "))
        corpus (norm-str corpus)
        place  (norm-str place)
        date   (or (format-date (parse-date date)) (str (parse-year date)))]
    (some->> (norm-str-coll [title corpus place date]) (str/join ". ")
             (norm-title))))

(defn str->vals
  [s]
  (some-> (norm-str s) (str/split #"[:\s]")))

(defn parse-match
  [corpus {title "title" subtitle "subTitle" snippet "snippet" :as match}]
  (let [title (some->> (norm-str-coll [title subtitle]) (str/join ". ")
                       (norm-title))
        date  (parse-date (match "pubDate"))]
    (-> {:collection corpus
         :text       (-> snippet h/parse h/as-hickory html->text str/trim)}
        (assoc* :title title)
        (assoc* :bibl (parse-bibl title match))
        (assoc* :author (norm-str (match "author")))
        (assoc* :file (norm-str (match "textSigle")))
        (assoc* :date date)
        (assoc* :year (or (date->year date) (parse-year (match "pubDate"))))
        (assoc* :country (norm-str (match "pubPlaceKey")))
        (assoc* :text-classes (norm-str-set (str->vals (match "textType"))))
        (assoc* :topics (norm-str-set (str->vals (match "textClass"))))
        (assoc* :availability (norm-str (match "availability"))))))

(def timer
  (metrics/timer "korap"))

(def corpus-timer
  {:dereko (metrics/timer "korap.dereko")
   :deliko (metrics/timer "korap.deliko")})

(def user-agent
  "zdl-lex/1.0 (https://www.dwds.de/)")

(defn request
  ([corpus q]
   (request corpus q 0))
  ([corpus q offset]
   (let [cq  (corpus-filter corpus)
         at  (api-token corpus)
         req {:method       :get
              :url          (api-url corpus)
              :headers      {"User-Agent" user-agent}
              :query-params {"ql"           "cosmas2"
                             "q"            q
                             "context"      "sentence"
                             "offset"       (str offset)
                             "count"        "50"
                             "fields"       "@all"
                             "show-snippet" "true"}}
         req (cond-> req
               cq (assoc-in [:query-params "cq"] cq)
               at (assoc :oauth-token at))]
     (tel/with-ctx+ {::request req}
       (with-retry gateway-error-retry
         (with-rate-limit (rate-limit corpus)
           (with-open [_ (metrics/timed! timer)
                       _ (metrics/timed! (corpus-timer corpus))]
             (tel/event! ::request :debug)
             (let [response @(hc/request req)]
               (tel/with-ctx+ {::response response}
                 (tel/event! ::response :trace))
               (let [error  (response :error)
                     status (response :status)]
                 (when error
                   (tel/error! ::error error)
                   (throw error))
                 (when (not= 200 status)
                   (let [error (ex-info (format "HTTP/%d" status) response)]
                     (tel/error! ::error error)
                     (throw error)))
                 (let [result  (json/read-value (response :body))
                       total   (get-in result ["meta" "totalResults"] 0)
                       matches (into
                                []
                                (map (partial parse-match (name corpus)))
                                (get result "matches"))
                       n       (count matches)]
                   (tel/with-ctx+ {::offset offset
                                   ::n      n
                                   ::total  total}
                     (tel/event! ::results :debug)
                     (when (seq matches)
                       (lazy-cat
                        matches
                        (let [offset (+ offset n)]
                          (when (< offset total)
                            (request corpus q offset))))))))))))))))

(def ^DerekoDfaTokenizer_de tokenizer
  (DerekoDfaTokenizer_de.))

(defn token->map
  [s space-after? [token next-token]]
  (let [start        (.getStart token)
        end          (.getEnd token)
        text         (subs s start end)
        space-after? (if next-token (< end (.getStart next-token)) space-after?)]
    {:form text :space-after? space-after?}))

(defn tokenize
  [s hit? [^Span sentence ^Span next-sentence]]
  (let [start        (.getStart sentence)
        end          (.getEnd sentence)
        text         (subs s start end)
        space-after? (and next-sentence (< end (.getStart next-sentence)))
        tokens       (.tokenizePos tokenizer text)
        hit?         (into (sorted-set)
                           (comp (map-indexed
                                  (fn [i ^Span token]
                                    (when (some hit?
                                                (range (+ start (.getStart token))
                                                       (+ start (.getEnd token))))
                                      (list i))))
                                 (mapcat identity))
                           tokens)
        token->map   (partial token->map (subs s start) space-after?)]
    (cond-> {:tokens (into [] (map token->map) (partition-all 2 1 tokens))}
      (seq hit?) (assoc :hit? hit?))))

(defn segment
  [{:keys [text] :as match}]
  (let [segments  (map-indexed vector (str/split text #"</?t>"))
        [s hit?]  (reduce
                  (fn [[s hit?] [n segment]]
                    [(str s segment)
                     (cond-> hit?
                       (odd? n) (into (range (count s)
                                             (+ (count s) (count segment)))))])
                  ["" #{}] segments)
        sentences (locking tokenizer
                    (->> (.sentPosDetect tokenizer s)
                         (partition-all 2 1)
                         (into [] (comp (map (partial tokenize s hit?))
                                        (filter :hit?)))))]
    (when (seq sentences)
      (list (-> (dissoc match :text) (merge (first sentences)))))))

(defn excessive-text?
  [{:keys [tokens]}]
  (< 100 (count tokens)))

(defn fingerprint-token?
  [{:keys [upos xpos]}]
  (and (nil? (#{:adp :cconj :det :pron :punct} upos))
       (nil? (#{:ptka :tkzu} xpos))))

(defn token->lemma
  [{:keys [compound-verb lemma form]}]
  (or compound-verb lemma form ""))

(defn deduplicate-xf
  [rf]
  (let [seen (volatile! #{})]
    (fn
      ([] (rf))
      ([result] (rf result))
      ([result match]
       (let [fingerprint (into (sorted-set)
                               (comp (filter fingerprint-token?)
                                     (map token->lemma))
                               (match :tokens))]
         (if-not (@seen fingerprint)
           (do (vswap! seen conj fingerprint)
               (rf result match))
           result))))))

(def ->kebab-case-keyword
  (memoize csk/->kebab-case-keyword))

(defn merge-token-annotations
  [token {features                                          "feats"
          {dwdsmor? "DWDSmor" compound-verb "CompoundVerb"} "misc"
          :as                                               annotations}]
  (reduce-kv
   (fn [m k v]
     (let [k (->kebab-case-keyword k)]
       (assoc m
              (->kebab-case-keyword k)
              (cond-> v (not (#{:head :id :form :lemma} k)) (->kebab-case-keyword)))))
   (cond-> (assoc token :dwdsmor? (not= dwdsmor? "No"))
     compound-verb (assoc :compound-verb compound-verb))
   (merge (dissoc annotations "feats" "misc") features)))

(defn parse-span
  [[tag & tokens]]
  [(->kebab-case-keyword tag) (into (sorted-set) (map dec) tokens)])

(defn parse-spans
  [s]
  (some->> s (json/read-value) (into [] (map parse-span))))

(defn merge-sentence-annotations
  [{:keys [tokens] :as sentence} annotations]
  (-> sentence
      (assoc* :gdex         (some-> (annotations "gdex") (parse-double)))
      (assoc* :lang         (some-> (annotations "lang") (keyword)))
      (assoc* :collocations (some-> (annotations "collocations") (parse-spans)))
      (assoc* :entities     (some-> (annotations "entities") (parse-spans)))
      (assoc* :tokens       (some->> (annotations "tokens")
                                     (map merge-token-annotations tokens)
                                     (vec)))))

(require-python 'zdl_lex.nlp)

(def annotate-xf
  (comp
   (mapcat segment)
   (remove excessive-text?)
   (partition-all nlp-batch-size)
   (mapcat (fn [matches]
             (py/with-gil-stack-rc-context
               (->>
                (for [m matches] (for [t (m :tokens)] [(t :form) (t :space-after?)]))
                (zdl_lex.nlp/annotate)
                (py/->jvm)
                (map merge-sentence-annotations matches)
                (vec)))))))

(defn sentence->text
  [{:keys [tokens hit?]}]
  (->> tokens
       (map-indexed
        (fn [n {:keys [form space-after?]}]
          (->
           (cond->> form (hit? n) (format "<t>%s</t>"))
           (str (when space-after? " ")))))
       (str/join)))

(def embed-xf
  (comp
   (partition-all nlp-batch-size)
   (mapcat (fn [matches]
             (py/with-gil-stack-rc-context
               (->>
                (map sentence->text matches)
                (zdl_lex.nlp/embed)
                (py/->jvm)
                (map #(assoc %1 :embedding %2) matches)
                (vec)))))))

(defn request->ch
  [corpus q]
  (let [ch (a/chan nlp-batch-size)]
    (a/thread
      (tel/with-ctx+ {::corpus corpus ::query q}
        (try
          (let [matches (request corpus q)]
            (loop [vs (seq matches)]
              (when (and vs (a/>!! ch (first vs)))
                (recur (next vs)))))
          (catch Throwable t
            (tel/error! ::request t))
          (finally
            (a/close! ch)))))
    ch))

(defn query->ch
  [q]
  (let [deliko  (request->ch :deliko q)
        dereko  (request->ch :dereko q)
        matches (a/merge (list deliko dereko))
        results (a/chan nlp-batch-size (comp annotate-xf deduplicate-xf))]
    (a/pipe matches results)
    results))

(defn clear-term
  [s]
  (str/replace s #"[ ()#,\"]" ""))

(defn terms->query
  [ss]
  (str/join " /s0 " (map (comp #(format "(&%s or %s)" % %) clear-term) ss)))

(defn fix-multi-term-hits
  [terms {:keys [tokens hit?] :as sentence}]
  (let [fh    (first hit?)
        lh    (last hit?)
        hit?? #(or (= fh %) (= lh %)
                   (terms (get-in tokens [% :lemma]))
                   (terms (get-in tokens [% :form])))]
    (assoc sentence :hit? (into (sorted-set) (filter hit??) hit?))))

(defn multi-term-query
  [terms]
  (let [clauses (map (comp #(format "(&%s or %s)" % %) clear-term) terms)
        q       (str/join " /s0 " clauses)
        terms   (into #{} terms)
        results (a/chan nlp-batch-size
                        (comp (map #(fix-multi-term-hits terms %)) embed-xf))]
    (a/pipe (query->ch q) results)
    results))

(def lemma-terms
  (with-open [is (io/input-stream (io/resource "zdl/lex/dev/lemma-terms.csv.gz"))
              is (java.util.zip.GZIPInputStream. is)
              r  (io/reader is)]
    (vec (csv/read-csv r))))

(defn sample
  [& _]
  (tel/with-min-level :warn
    (let [single-terms (into [] (remove second) lemma-terms)
          single-terms (random-sample (/ 100 (count single-terms)) single-terms)
          multi-terms  (into [] (filter second) lemma-terms)
          multi-terms  (random-sample (/ 100 (count multi-terms)) multi-terms)
          queries      (shuffle (concat single-terms multi-terms))]
      (doseq [q queries]
        (let [examples (multi-term-query q)]
          (try
            (loop [n 0]
              (when (< n 500)
                (when-let [example (a/<!! examples)]
                  (pr-edn [q example])
                  (println)
                  (recur (inc n)))))
            (catch Throwable t
              (tel/error! :error t))
            (finally
              (a/close! examples))))))))

(comment
  (count (filter second lemma-terms))
  (tel/with-min-level :debug
    (let [terms   (rand-nth (filter second lemma-terms))
          results (a/chan 1 (take 100))]
      (a/pipe (multi-term-query terms) results)
      (->>
       (a/<!! (a/into [] results))
       (map (juxt :author :title :bibl (comp str/trim sentence->text)))
       (vec)))))
