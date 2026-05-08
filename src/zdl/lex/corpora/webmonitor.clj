(ns zdl.lex.corpora.webmonitor
  (:require
   [clojure.core.async :as a]
   [clojure.string :as str]
   [com.potetm.fusebox.rate-limit :as rl]
   [gremid.xml :as gx]
   [taoensso.telemere :as tm]
   [tick.core :as t]
   [zdl.lex.corpora.http :as http]
   [zdl.lex.corpora.webmonitor.justext :as justext]
   [zdl.lex.util :refer [lines-resource]]
   [lambdaisland.uri :as uri])
  (:import
   (java.io InputStream IOException)
   (java.net UnknownHostException URI)
   (java.security.cert CertPathBuilderException)
   (java.time.format DateTimeFormatter DateTimeParseException)
   (java.util NoSuchElementException)
   (org.apache.http HttpException)))

(def top-level-domains
  (into #{}
        (map str/lower-case)
        (lines-resource "zdl/lex/corpora/webmonitor/tlds.txt")))

(def feeds
  (vec (sort (lines-resource "zdl/lex/corpora/webmonitor/feeds.txt"))))

(defn url->domain
  [s]
  (when-let [host (. (URI. s) (getHost))]
    (when-not (re-matches #"^[0-9\.]+$" host)
      (let [domains (str/split host #"\.")]
        (->>
         (concat
          (take-last 1 (take-while (complement top-level-domains) domains))
          (drop-while (complement top-level-domains) domains))
         (str/join \.) (list))))))

(defn parse-timestamp
  [s]
  (t/instant
   (try
     (t/parse-offset-date-time s DateTimeFormatter/RFC_1123_DATE_TIME)
     (catch DateTimeParseException _
       (try
         (t/parse-zoned-date-time s (t/formatter "EEE, dd MMM yyyy HH:mm:ss zzz"))
         (catch DateTimeParseException _
           (t/parse-offset-date-time s DateTimeFormatter/ISO_OFFSET_DATE_TIME)))))))

(def recency-threshold
  (t/<< (t/instant) (t/of-days 365)))

(defn extract-feed-item
  [node]
  (when-let [link (gx/element :link node)]
    (when-let [url (or (gx/attr :href link) (gx/text link))]
      (let [published (or (gx/element :pubDate node) (gx/element :updated node))
            published (some-> published (gx/text) (parse-timestamp))
            title     (some-> (gx/element :title node) (gx/text))]
        (when (and published (t/<= recency-threshold published))
          (list
           (cond-> {:url url :published published}
             title (assoc :title title))))))))

(def crawl-parallel
  (* 2 (. (Runtime/getRuntime) (availableProcessors))))

(def global-rate-limit
  (rl/init {::rl/bucket-size     8
            ::rl/period-ms       1000
            ::rl/wait-timeout-ms (* 25000 crawl-parallel 2)}))

(def host-rate-limit-params
  {::rl/bucket-size     1
   ::rl/period-ms       3000
   ::rl/wait-timeout-ms (* 30000 crawl-parallel 2)})

(def host-rate-limits
  (atom {}))

(defn reset-host-rate-limits!
  []
  (let [[rate-limits _] (reset-vals! host-rate-limits {})]
    (doseq [rate-limit (vals rate-limits)] (rl/shutdown rate-limit))))

(defn get-feed
  [url]
  (let [domain (first (url->domain url))
        result (cond-> {:feed url} domain (assoc :feed-domain domain))]
    (try
      (let [response (rl/with-rate-limit global-rate-limit
                       (http/request {:method :get :url url :as :stream}))
            redirect (last (response :trace-redirects))
            result   (cond-> result redirect (assoc :redirect redirect))
            xml      (with-open [^InputStream xml (get response :body)]
                       (-> xml gx/read-events gx/events->node))]
        (->> (concat (gx/elements :item xml) (gx/elements :entry xml))
             (into [] (comp (mapcat extract-feed-item)
                            (map #(merge result %))))))
      (catch UnknownHostException _
        (list (assoc result :error :dns)))
      (catch CertPathBuilderException _
        (list (assoc result :error :cert)))
      (catch HttpException _
        (list (assoc result :error :http)))
      (catch IOException _
        (list (assoc result :error :io)))
      (catch NoSuchElementException e
        (->>
         (if (str/includes? (.getMessage e) "ParseError") :xml e)
         (assoc result :error) (list)))
      (catch clojure.lang.ExceptionInfo ei
        (->>
         (or (some->> ei ex-data :status (str "http-") (keyword)) ei)
         (assoc result :error) (list)))
      (catch Throwable t
        (list (assoc result :error t))))))

(defn get-html
  [{:keys [url] :as item}]
  (try
    (let [host        (get (uri/uri url) :host)
          rate-limits (swap! host-rate-limits update host
                             #(or % (rl/init host-rate-limit-params)))
          html        (rl/with-rate-limit global-rate-limit
                        (rl/with-rate-limit (rate-limits host)
                          (http/get-request url)))]
      (assoc item :html html))
    (catch Throwable t
      (assoc item :error t))))

(defn extract-content
  [{{html :body} :html :as item}]
  (try
    (cond-> item
      html (assoc :content (justext/paragraphs (http/parse-html html))))
    (catch Throwable t
      (assoc item :error t))))

(defn crawl->ch
  ([ch]
   (crawl->ch feeds ch))
  ([feeds ch]
   (let [feeds-ch (doto (a/chan) (a/onto-chan! feeds))
         items-ch (a/chan 1 (remove (comp :error first)))
         html-ch  (a/chan)
         >feeds   (->>
                   (a/go-loop []
                     (when-let [feed-url (a/<! feeds-ch)]
                       (when (a/>! items-ch (get-feed feed-url))
                         (recur))))
                   (for [_ (range crawl-parallel)]) (a/merge) (a/into []))
         >html    (->>
                   (a/go-loop []
                     (when-let [items (a/<! items-ch)]
                       (when (loop [items items]
                               (if-let [item (first items)]
                                 (when (a/>! html-ch (get-html item))
                                   (recur (rest items)))
                                 true))
                         (recur))))
                   (for [_ (range crawl-parallel)]) (a/merge) (a/into []))
         >content (->>
                   (a/go-loop []
                     (when-let [item (a/<! html-ch)]
                       (when (a/>! ch (extract-content item))
                         (recur))))
                   (for [_ (range crawl-parallel)]) (a/merge) (a/into []))]
     (a/go (when (a/<! >content) (a/close! ch)))
     (a/go (when (a/<! >html) (a/close! html-ch)))
     (a/go (when (a/<! >feeds) (a/close! items-ch)))
     ch)))

(comment
  (keys @host-rate-limits)
  (reset-host-rate-limits!)

  (tm/with-min-level nil "zdl.lex.corpora.http" :debug
    (a/<!! (a/into [] (crawl->ch (take 20 (shuffle feeds))
                                 (a/chan 1 (take 5)))))))
