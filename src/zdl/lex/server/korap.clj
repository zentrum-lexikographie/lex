(ns zdl.lex.server.korap
  (:require
   [clj-http.client :as http]
   [clojure.core.async :as a]
   [clojure.string :as str]
   [com.potetm.fusebox.rate-limit :as rl :refer [with-rate-limit]]
   [hickory.core :as h]
   [jsonista.core :as json]
   [taoensso.telemere :as t]
   [zdl.lex.env :as env]
   [zdl.lex.server.util
    :refer
    [assoc*
     date->year
     format-date
     merge-chs
     norm-str
     norm-str-coll
     norm-str-set
     parse-date
     parse-year]]))

(defn html->text
  [node]
  (if (string? node)
    node
    (let [{:keys [tag content]} node
          content               (str/join (map html->text content))]
      (if (= :mark tag) (str "<t>" content "</t>") content))))

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

(defn split-vals
  [s]
  (some-> (norm-str s) (str/split #"[:\s]")))

(defn parse-match
  [{title "title" subtitle "subTitle" snippet "snippet" :as match}]
  (let [title (some->> (norm-str-coll [title subtitle]) (str/join ". ")
                       (norm-title))
        date  (parse-date (match "pubDate"))]
    (-> {:text (-> snippet h/parse h/as-hickory html->text str/trim)}
        (assoc* :title title)
        (assoc* :bibl (parse-bibl title match))
        (assoc* :author (norm-str (match "author")))
        (assoc* :file (norm-str (match "textSigle")))
        (assoc* :date date)
        (assoc* :year (or (date->year date) (parse-date (match "pubDate"))))
        (assoc* :country (norm-str (match "pubPlaceKey")))
        (assoc* :text-classes (norm-str-set (split-vals (match "textType"))))
        (assoc* :topics (norm-str-set (split-vals (match "textClass"))))
        (assoc* :availability (norm-str (match "availability"))))))

(def korap-timer
  (env/timer "korap"))

(defn corpus-query
  ([corpus base-request rate-limit q]
   (corpus-query corpus base-request rate-limit q 0))
  ([corpus base-request rate-limit q offset]
   (try
     (let [params     {"ql"           "poliqarp"
                       "q"            q
                       "cq"           "corpusSigle != /W[UDP]D.*/"
                       "context"      "sentence"
                       "offset"       (str offset)
                       "count"        "100"
                       "fields"       "@all"
                       "show-snippet" "true"}
           request    (assoc base-request :query-params params)
           response   (with-open [_ (env/timed! korap-timer)
                                  _ (env/timed! (env/timer (str "korap-" corpus)))]
                        (with-rate-limit rate-limit
                          (-> (http/request request) :body json/read-value)))
           total      (get-in response ["meta" "totalResults"])
           assoc-meta #(assoc %2
                              :collection corpus
                              ::corpus corpus
                              ::offset (+ offset %1)
                              ::total total)
           matches    (into [] (comp (map parse-match)
                                     (map-indexed assoc-meta))
                            (get response "matches"))
           n          (count matches)]
       (t/event! ::result {:level   :debug
                           :msg     (format "[%12s][%,8d + %,5d|%,8d] %s"
                                        corpus offset n total q)
                           :corpus  corpus
                           :query   q
                           :matches matches
                           :total   total
                           :offset  offset
                           :n       n})
       (when (seq matches)
         (lazy-cat
          matches
          (let [offset (+ offset n)]
            (when (< offset total)
              (corpus-query corpus base-request rate-limit q offset))))))
     (catch Throwable t
       (t/error! {:id ::query :corpus corpus :query q} t)
       nil))))

(def dereko-rate-limit
  (rl/init {::rl/bucket-size     1
            ::rl/period-ms       1000
            ::rl/wait-timeout-ms 10000}))

(def dereko-query
  (partial corpus-query "dereko" env/korap-dereko-request dereko-rate-limit))

(def dnb-rate-limit
  (rl/init {::rl/bucket-size     1
            ::rl/period-ms       2000
            ::rl/wait-timeout-ms 20000}))

(def dnb-query
  (partial corpus-query "dnb" env/korap-dnb-request dnb-rate-limit))

(defn result-stream
  [q]
  (merge-chs [(doto (a/chan) (a/onto-chan!! (dereko-query q)))
              (doto (a/chan) (a/onto-chan!! (dnb-query q)))]))

(defn lexeme-stream
  [lexeme]
  (result-stream (format "[base=\"%s\"]" (str/replace lexeme #"\"" "\\\""))))
