;; # Trend Word Detection for DWDS
;;
;; _Alexander Geyken, Luise Köhler, Gregor Middell (Berlin-Brandenburg
;; Academy of Sciences and Humanities)_
;;
;; This notebook assembles experiments in detecting word usage trends
;; and their relevance for the lexicographic workflow of
;; the [DWDS](https://www.dwds.de/).

(ns trend-word-detection
  {:nextjournal.clerk/toc true}
  (:require
   [clojure.core.async :as a]
   [clojure.data.csv :as csv]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [excel-clj.core :as excel]
   [excel-clj.cell :as cell]
   [jsonista.core :as json]
   [libpython-clj2.python :as py]
   [libpython-clj2.require :refer [require-python]]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as jdbc.sql]
   [nextjournal.clerk :as clerk]
   [org.httpkit.client :as hc]
   [taoensso.telemere :as tm]
   [tick.core :as t]
   [zdl.lex.article :as article]
   [zdl.lex.corpora.webmonitor :as webmonitor]
   [zdl.lex.env :refer [getenv]]))

{:nextjournal.clerk/visibility {:result :hide}}

(require-python 'zdl_lex.trends)

;; ## Frequency Histograms and Linear Regression
;;
;; Adopting [Lexical Computing's approach](https://www.sketchengine.eu/guide/trends/),
;; we compare histograms of word occurrence frequencies from **3 datasources**
;; and apply a [linear regression model](https://nlp.fi.muni.cz/raslan/2013/paper11.pdf)
;; in order to estimate word usage trends.
;;
;; 1. The [DWDS Newspaper
;;    Corpus](https://www.dwds.de/d/korpora/zeitungenxl) is queried
;;    for word frequency data in **monthly** intervals over the last 4
;;    years.
;; 1. The [DWDS Web Monitor Corpus](https://www.dwds.de/d/korpora/webmonitor)
;;    is queried for the same but more recent type of data in **weekly**
;;    intervals over the last year.
;; 1. [Google Trends](https://trends.google.de/trends/) is queried for
;;    search query frequency of a given word over the last year in
;;    **weekly** intervals as well.
;;
;; ### Querying DDC/D* and Google Trends

(defn parse-ts
  "Parses timestamps of date-, month- and year-granularity."
  [s]
  (condp = (count s)
    10 (t/date s)
    7  (t/year-month s)
    4  (t/year s)))

(defn parse-freq
  "Parses string representations of numeric frequency values. Google
  Trends encodes scaled frequency values between 0 and 1 as `<0`
  which this fn maps to zero; so it does for parsing errors."
  [s]
  (try
    (Long/parseLong (get {"<1" "0"} s s))
    (catch NumberFormatException _
      (tm/log! {:id ::parse-freq :level :warn :data {:s s}})
      0)))

(defn http-error?
  "Predicate for failed httpkit requests."
  [{:keys [error status] :as _resp}]
  (or error (not= 200 status)))

(let [auth [(getenv "DDC_DSTAR_USER")  (getenv "DDC_DSTAR_PASSWORD")]]
  (defn dstar-histogram
    "Retrieve frequency histograms from DDC/D* corpora. Frequencies are
     returned for the last 50 epochs (days, weeks, or months) and get
     scaled linearily into the range `[0,100]`."
    [corpus epoch term]
    (let [term  (cond->> term (re-find #"\s" term) (format "\"%s\""))
          slice (get {:daily "1d" :weekly "7d" :monthly "1m"} epoch)
          dl    (get {:daily 7 :weekly 1 :monthly 0} epoch)
          url   (str "https://ddc.dwds.de/dstar/" corpus "/dhist-plot.perl")
          resp  @(hc/request {:method       :get
                              :basic-auth   auth
                              :url          url
                              :query-params {"norm"    "abs"
                                             "query"   term
                                             "slice"   slice
                                             "single"  "1"
                                             "pformat" "text"}})
          _     (when (http-error? resp) (throw (ex-info "HTTP error" resp)))
          freqs (reduce
                 (fn [m [f ts _]] (update m ts (fnil + 0) (parse-freq f)))
                 (sorted-map)
                 (csv/read-csv (resp :body) :separator \tab))
          freqs (take-last 50 (drop-last dl freqs))
          fmax  (reduce max 1 (vals freqs))]
      (reduce
       (fn [m [ts f]]
         (assoc m ts (int (Math/round (double (* 100 (/ f fmax)))))))
       (sorted-map) freqs))))

(def webmonitor-histogram
  "Retrieve weekly frequency data from DDC/D*'s Web Monitor Corpus."
  (partial dstar-histogram "webmonitor" :weekly))

(def zeitungenxl-histogram
  "Retrieve monthly frequency data from DDC/D*'s Newspaper Corpus."
  (partial dstar-histogram "zeitungenxl" :monthly))

(let [api-key (getenv "SERP_API_KEY")]
  (defn google-trends-histogram
    "Retrieve frequency histograms from Google Trends via SerpApi.
     Frequencies are returned for the last 50 epochs and are already
     scaled to the same domain range as the DDC/D* frequencies ([0,100])."
    [epoch term]
    (let [parse-ts (cond->> parse-ts ; Google Trends starts weeks on Sunday
                     (= epoch :weekly) (comp #(t/>> % (t/of-days 1))))
          slice    (get {:weekly "today 5-y" :monthly "all"} epoch)
          dl       (get {:weekly 1 :monthly 1} epoch)
          req      {:method       :get
                    :url          "https://serpapi.com/search"
                    :query-params {"api_key" api-key
                                   "engine"  "google_trends"
                                   "hl"      "de"
                                   "geo"     "DE"
                                   "q"       term
                                   "date"    slice
                                   "tz"      "0"
                                   "csv"     "true"}}
          resp     (deref (hc/request req))
          _        (when (http-error? resp) (throw (ex-info "HTTP error" resp)))
          freqs    (transduce
                    (comp (mapcat csv/read-csv) (drop 2))
                    (completing
                     (fn [m [ts & [f]]]
                       (update m (parse-ts ts) (fnil + 0) (parse-freq f))))
                    (sorted-map)
                    (-> resp :body json/read-value (get "csv")))]
      (into (sorted-map) (take-last 50 (drop-last dl freqs))))))


;; ### Collecting Histograms and Estimating Trends
;;
;; Histograms are queried in parallel from all 3 datasources; for each
;; time series of frequency data a line is fitted via a [Theil-Sen
;; estimator](https://en.wikipedia.org/wiki/Theil%E2%80%93Sen_estimator):
;;
;; > In non-parametric statistics, the Theil–Sen estimator is a method
;; > for robustly fitting a line to sample points in the plane (a form
;; > of simple linear regression) by choosing the median of the slopes
;; > of all lines through pairs of points.
;;
;; To measure how well the line fits the time series, the [Kendall
;; rank correlation
;; coefficient](https://en.wikipedia.org/wiki/Kendall_rank_correlation_coefficient)
;; is computed:
;;
;; > Kendall’s tau is a measure of the correspondence between two
;; > rankings. Values close to 1 indicate strong agreement, and values
;; > close to -1 indicate strong disagreement.
;;
;; – Documentation of [`scipy.stats.kendalltau`](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.kendalltau.html)
;;
;; Correspondingly, we assume the threshold for a (possitive or negative) trend as
;;

^{::clerk/visibility {:code :hide :result :show}}
(clerk/tex "\\left|\\Tau\\right| \\geq 0.2")


(defn histograms
  "Assemble histograms from datasources for a given word, then
   annotate the data with a linear regression model and a correlation
   coefficient."
  [word]
  (let [[g w z] (map
                 #(vec (vals (deref %)))
                 (list (future (google-trends-histogram :weekly word))
                       (future (webmonitor-histogram word))
                       (future (zeitungenxl-histogram word))))]
    (zipmap [:google :webmonitor :zeitungenxl]
            (map #(py/with-gil-stack-rc-context
                    (let [metrics (zdl_lex.trends/metrics %)
                          tau     (py/->jvm (py/py. metrics get "tau"))]
                      {:freqs       %
                       :slope       (py/->jvm (py/py. metrics get "slope"))
                       :intercept   (py/->jvm (py/py. metrics get "intercept"))
                       :p-value     (py/->jvm (py/py. metrics get "p-value"))
                       :tau         tau
                       :correlated? (<= 0.2 (abs tau))}))
                 [g w z]))))

;; ### Plotting Sample Histograms
;;
;; For some sample words that are potential trends words in April
;; 2026, we plot histograms and linear trend estimates.

(def sample-histograms
  (into {} (map (juxt identity histograms))
        ["Kerosin" "Heizungsgesetz" "Mikrobiom" "Spargel" "Sonnencreme"]))

;; 1. Per histogram, 50 epochs are plotted, going back in time from
;;    the end of April in either weekly or monthly steps. The
;;    Newspaper Corpus thus provides a long-term perspective of about
;;    4 years, whereas the Webmonitor Corpus and Google Trends deliver
;;    a 1-year history.
;; 1. The estimated linear trend is overlayed on each frequency
;;    histogram, with a correlation coefficient above the assumed
;;    threshold coloring the trend in green.

(def source-titles
  {:google      "Google Trends (weekly)"
   :webmonitor  "Web Monitor Corpus (weekly)"
   :zeitungenxl "Newspapers (monthly)"})

(defn plot-histograms
  [[lemma histograms]]
  (apply
   clerk/row
   {::clerk/width :full}
   (for [source [:zeitungenxl :webmonitor :google]]
     (let [histogram  (histograms source)
           x-encoding {:field "i"
                       :type  "nominal"
                       :title (condp = source :zeitungenxl "month" "week")
                       :axis  {:labelAngle -90}}
           trend-mark (merge
                       {:type "line" :clip true}
                         (if (histogram :correlated?)
                           {:color "forestgreen" :strokeWidth 3}
                           {:color "salmon" :strokeWidth 3 :strokeDash [8 8]}))]
       (clerk/caption
        (format "%s | %s | Δ %.2f | τ: %.4f"
                lemma (source-titles source) (histogram :slope) (histogram :tau))
        (clerk/vl
         {:data  {:values (let [{:keys [slope intercept freqs]} histogram]
                            (into []
                                  (comp
                                   (map-indexed
                                    (fn [i f]
                                      {:i (+ (- (count freqs)) i)
                                       :f f
                                       :t (+ intercept (* slope i))}))
                                   (filter #(zero? (mod (% :i) 2))))
                                  freqs))}
          :layer [{:mark     {:type "bar" :color "lightsteelblue"}
                   :encoding {:x x-encoding
                              :y {:field "f"
                                  :type  "quantitative"
                                  :title "frequency"
                                  :scale {:domain [0 100]}}}}
                  {:mark     trend-mark
                   :encoding {:x x-encoding
                              :y {:field "t"
                                  :type  "quantitative"
                                  :scale {:domain [0 100]}}}}]
          :width 400}))))))

^{::clerk/visibility {:result :show}}
(apply clerk/col {::clerk/width :full} (map plot-histograms sample-histograms))


;; ## Focused Web Crawling and Dispersion Metrics


;; ### RSS Sources

^{::clerk/visibility {:code :show :result :show}}
(count webmonitor/feeds)

^{::clerk/visibility {:code :show :result :show}}
(into [] (random-sample 0.05) webmonitor/feeds)

;; ### Crawling

{::clerk/visibility {:code :show :result :hide}}

(def data-dir
  (doto (io/file (System/getenv "HOME") "data" "zdl" "lex") (.mkdirs)))

(def webmonitor-frequencies-file
  (io/file data-dir "webmonitor-freqs.csv"))

(defn ch->seq
  [ch]
  (when-let [v (a/<!! ch)] (lazy-seq (cons v (ch->seq ch)))))

(defn webmonitor-doc->token-frequencies
  [{:keys [feed url published content] :as _doc}]
  (py/with-gil-stack-rc-context
    (let [timestamp   (some-> published (t/date) (str))
          token-stats (py/->jvm (zdl_lex.trends/extract_tokens (map second content))) 
          token-total (token-stats "tokens")]
      (for [[t f] (sort-by (comp - second) (token-stats "freqs"))]
        [t f token-total timestamp feed url]))))

(when-not (.exists webmonitor-frequencies-file)
  (tm/with-min-level nil "zdl.lex.corpora.http" :debug
    (with-open [w (io/writer webmonitor-frequencies-file)]
      (let [ch (webmonitor/crawl->ch (shuffle webmonitor/feeds) (a/chan 32))]
        (try
          (csv/write-csv w (mapcat webmonitor-doc->token-frequencies (ch->seq ch)))
          (finally
            (a/close! ch)))))))

;; ### NLP

^{::clerk/visibility {:code :hide :result :show}}
(clerk/md (str "```python\n" (slurp (io/file "zdl_lex/trends.py")) "\n```"))

;; ### Token Frequencies / Document / Feed

^{::clerk/visibility {:code :show :result :show}}
(with-open [r (io/reader webmonitor-frequencies-file)]
  (->>
   (csv/read-csv r)
   (random-sample 0.0001)
   (pmap (fn [[t tf _tt published feed url]]
           [t (parse-long tf) (t/date published) feed url]))
   (into [] (take 100))
   (cons ["Token" "f" "Published" "Feed" "Document URL"])
   (clerk/use-headers) (clerk/table)))

;; ### Frequency Database

(def webmonitor-frequencies-db-file
  (io/file data-dir "webmonitor-freqs.db"))

(def webmonitor-frequencies-db
  {:jdbcUrl (str "jdbc:sqlite:" webmonitor-frequencies-db-file)})

(when-not (.exists webmonitor-frequencies-db-file)
  (jdbc/execute!
   webmonitor-frequencies-db
   ["create table if not exists freqs (
       feed text,
       url text,
       published text,
       lemma text,
       f integer
     )"])
  (with-open [r (io/reader webmonitor-frequencies-file)]
    (->>
     (csv/read-csv r)
     (pmap (fn [[lemma f _fs published feed url]]
             [feed url published lemma (parse-long f)]))
     (partition-all 1024)
     (run! #(jdbc.sql/insert-multi!
             webmonitor-frequencies-db
             :freqs [:feed :url :published :lemma :f] %)))))


^{::clerk/visibility {:code :show :result :show}}
(jdbc/execute! webmonitor-frequencies-db ["select count(*) as fn from freqs"])

;; ### Trend Candidates
;;
;; We **score** lemmata by their dispersion over the crawled feeds while
;; discounting for high document and token frequency:

^{::clerk/visibility {:code :hide :result :show}}
(clerk/tex "score = \\cfrac{f_{feeds}}{f_{docs} + \\sqrt{f_{tokens}}}")

;; We **filter** lemmata based on the following criteria:
;;
;; 1. The lemma is present in at least 3 feeds/sources.
;; 1. The document frequency is (strictly) greater than the feed frequency.
;; 1. The token/term frequency is at least 3 times greater than the document frequency.

(def trend-candidates
  (->> ["select
         lemma as l,
         count(distinct feed) as ff,
         count(distinct url) as df,
         sum(f) as tf,
         count(distinct feed) / (sqrt(sum(f)) + count(distinct url)) as score
       from freqs
       where published like ?
       group by lemma
       having 3 < ff and ff < df and (? * df) < tf
       order by score desc, lemma" "2026-04-%" 3]
       (jdbc/execute! webmonitor-frequencies-db)))

^{::clerk/visibility {:code :show :result :show}}
(->> (map (juxt :freqs/l :ff :df :tf :score) trend-candidates)
     (cons ["Lemma" "# Sources" "# Docs" "# Tokens" "Score"])
     (into [] (take 100))
     (clerk/use-headers) (clerk/table))

;; ## Prioritizing Lexicographic Work

;; ### DWDSwb Article Metadata

(def dwdswb-sources
  (io/file (System/getenv "HOME") "data" "zdl" "wb"))

(def dwdswb
  (->> (file-seq dwdswb-sources)
       (filter #(.. ^java.io.File % (getName) (endsWith ".xml")))
       (pmap (comp article/metadata article/read-xml))
       (remove #(or (= "Mehrwortausdruck" (% :pos))
                    (= "wird_gestrichen" (% :status))))
       (reduce
        (fn [m {:keys [forms] :as article}]
          (reduce
           (fn [m form]
             (let [article* (get m form {})
                   attr-set #(conj (or (article* %) (sorted-set))
                                   (article %))]
               (assoc m form
                      {:type      (attr-set :type)
                       :status    (attr-set :status)
                       :source    (attr-set :source)
                       :timestamp (attr-set :last-modified)})))
           m forms))
        {})))

;; ### Trending DWDSwb Articles

^{::clerk/visibility {:code :show :result :show}}
(def trend-articles
  (->>
   trend-candidates
   (map
    (fn [{:freqs/keys [l] :as candidate}]
      (let [articles (dwdswb l)] (cond-> candidate articles (merge articles)))))
   (into [])))

(defn xls-cell
  ([v highlight?]
   (xls-cell v highlight? :yellow))
  ([v highlight? color]
   (cond-> v
     highlight? (cell/style {:fill-pattern          :solid-foreground
                             :fill-foreground-color color}))))

(def recency-threshold
  (t/<< (t/date) (t/of-years 5)))

(def trend-word-xlsx-file
  (io/file data-dir "webmonitor-trends.xlsx"))

(when-not (.exists trend-word-xlsx-file)
  (excel/write!
   {"Trend Words"
    (excel/table-grid
     ["Lemma"
      "# Sources"
      "# Documents"
      "# Tokens"
      "Score"
      "DWDSwb Types"
      "DWDSwb Status"
      "DWDSwb Source"
      "DWDSwb Timestamp"]
     (for [{:freqs/keys [l] :keys [ff tf df score type source status timestamp]}
           trend-articles]
       (let [timestamp  (first timestamp)
             wb?        (or (empty? source) (some? source))
             online?    (or (empty? status) (= ["Red-f"] (vec status)))
             minimal?   (some #{"Minimalartikel"} type)
             recent?    (or (nil? timestamp) (t/< recency-threshold (t/date timestamp)))
             missing?   (nil? timestamp)
             attention? (or (not wb?) (not online?) minimal? (not recent?))]
         {"Lemma"            (-> l (xls-cell attention?) (xls-cell missing? :orange))
          "Score"            score
          "# Sources"        ff
          "# Documents"      df
          "# Tokens"         tf
          "DWDSwb Types"     (xls-cell (str/join ", " type) minimal?)
          "DWDSwb Status"    (xls-cell (str/join ", " status) (not online?))
          "DWDSwb Source"    (str/join ", " source)
          "DWDSwb Timestamp" (xls-cell timestamp (not recent?))})))}
   trend-word-xlsx-file))
