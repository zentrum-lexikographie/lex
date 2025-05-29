(ns zdl.lex.server.ddc
  (:require
   [clj-http.client :as http]
   [clojure.core.async :as a]
   [clojure.string :as str]
   [jsonista.core :as json]
   [taoensso.telemere :as t]
   [zdl.lex.env :as env]
   [zdl.lex.server.util
    :refer
    [assoc*
     date->year
     merge-chs
     norm-str
     norm-str-coll
     norm-str-set
     parse-date
     parse-timestamp
     parse-year
     timestamp->date]])
  (:import
   (java.io DataInputStream OutputStream)
   (java.net Socket)
   (java.nio ByteBuffer ByteOrder)
   (java.nio.charset Charset)))

(defonce endpoints
  (->>
   (-> (http/request (assoc env/ddc-dstar-request
                          :url "https://ddc.dwds.de/dstar/intern.perl"
                          :query-params {"f" "json"}))
       :body (json/read-value))
   (into
    (sorted-map)
    (map (fn [{host "host" port "port" corpus "corpus"}]
           [corpus [host (parse-long port)]])))
   (delay)))


(def ^Charset charset
  (Charset/forName "UTF-8"))

(defn write-str
  [^OutputStream out ^String s]
  (let [payload (. s (getBytes charset))
        len     (count payload)]
    (. out (write (.array
                   (.. (ByteBuffer/allocate 4)
                       (order ByteOrder/LITTLE_ENDIAN)
                       (putInt (unchecked-int len))))))
    (. out (write payload))
    (. out (flush))))

(defn read-str
  [^DataInputStream in]
  (let [len (byte-array 4)]
    (.readFully in len)
    (let [len (.. (ByteBuffer/wrap len)
                  (order ByteOrder/LITTLE_ENDIAN)
                  (getInt))
          s   (byte-array len)]
      (.readFully in s)
      (String. s charset))))

(defn request
  [corpus cmd]
  (assert (@endpoints corpus) corpus)
  (let [[host port :as endpoint] (@endpoints corpus)]
    (try
      (t/event! ::request {:level :trace
                           :host  host
                           :port  port
                           :cmd   cmd})
      (with-open [socket (Socket. ^String host (int port))
                  output (.getOutputStream socket)
                  input  (DataInputStream. (.getInputStream socket))]
        (write-str output cmd)
        (let [result (read-str input)]
          (t/event! ::response {:level :trace
                                :host  host
                                :port  port
                                :cmd   cmd
                                :bytes (count result)})
          (json/read-value result)))
      (catch Throwable t
        (throw (ex-info "DDC request error"
                        {:corpus corpus :endpoint endpoint :cmd cmd} t))))))

(defn num-tokens
  [corpus]
  (->> (request corpus "info")
       (tree-seq #(get % "corpora") #(get % "corpora"))
       (map #(get % "ntokens" 0))
       (reduce +)))

(def flagship-corpora
  #{"ebookxl" "dibilit" "dtaxl" "kernbasis" "webxl" "wikipedia" "zeitungenxl"})

(defonce flagship-corpus-sizes
  (delay (into {} (pmap (juxt identity num-tokens) flagship-corpora))))

(defn lemma->sql
  [lemma]
  (str "'" (str/replace lemma #"'" "") "'"))

(defn ppm
  [lemmata corpus]
  (let [lexdb-url (str "https://ddc.dwds.de/dstar/" corpus "/lexdb/export.perl")
        query     {"select"  "l, sum(f)"
                   "from"    "lex"
                   "where"   (str "l in ("
                                (str/join "," (map lemma->sql lemmata))
                                ")")
                   "groupby" "l"
                   "limit"   (count lemmata)
                   "fmt"     "json"}
        freqs     (into {} (-> (http/request (assoc env/ddc-dstar-request
                                                    :request-method :post
                                                    :url lexdb-url
                                                    :form-params query))
                               :body json/read-value (get "rows")))
        total     (@flagship-corpus-sizes corpus)]
    (into {}
          (map (juxt identity #(/ (* (parse-long (get freqs % "0")) 1000000) total)))
          lemmata)))

(defn ppms
  [lemmata]
  (let [ppms (pmap (partial ppm lemmata) flagship-corpora)]
    (into {}
          (map (juxt identity (fn [lemma] (reduce max (map #(get % lemma) ppms)))))
          lemmata)))

(defn parse-bibl
  [page s]
  (some-> s (cond-> page (str/replace #"#page#" page))))

(defn split-vals
  [s]
  (some-> s (str/split #"\s*:\s*")))

(defn parse-first-date
  [coll]
  (some->> coll norm-str-coll (map parse-date) (remove nil?) (first)))

(defn parse-first-year
  [coll]
  (some->> coll norm-str-coll (map parse-year) (remove nil?) (first)))

(defn parse-metadata
  [query-metadata m]
  (let [collection (norm-str (get m "collection"))
        page       (or (norm-str (get m "page_")) (norm-str (get m "pageRange")))
        dates      (split-vals (get m "date_"))
        date       (parse-first-date dates)
        ts         (parse-timestamp (get m "timestamp"))]
    (-> query-metadata
        ;; base
        (assoc* :collection collection)
        (assoc* :url (norm-str (get m "url")))
        (assoc* :file (norm-str (get m "basename")))
        (assoc* :bibl (parse-bibl page (norm-str (get m "bibl"))))
        (assoc* :page page)
        ;; bibl
        (assoc* :author (or (norm-str (get m "author"))
                            (norm-str (get m "sentPers"))))
        (assoc* :editor (or (norm-str (get m "editor"))
                            (condp = collection
                              "wikipedia"  "Wikipedia"
                              "wikivoyage" "Wikivoyage"
                              nil)))
        (assoc* :title  (norm-str (get m "title")))
        (assoc* :short-title (when (= "gesetze" collection)
                               (norm-str (get m "biblSig"))))
        ;; classification
        (assoc* :text-classes (norm-str-set (split-vals (get m "textClass"))))
        (assoc* :flags (norm-str-set (split-vals (get m "flags"))))
        ;; rights
        (assoc* :access (norm-str (get m "access")))
        (assoc* :availability (norm-str (get m "avail")))
        ;; time
        (assoc* :date date)
        (assoc* :year (or (date->year date) (parse-first-year dates)))
        (assoc* :timestamp ts)
        (assoc* :access-date (or (parse-first-date (get m "urlDate"))
                                 (parse-first-date (get m "dump"))
                                 (timestamp->date ts)))
        ;; location
        (assoc* :country (norm-str (get m "country")))
        (assoc* :region (norm-str (get m "region")))
        (assoc* :subregion (norm-str (get m "subregion"))))))

(defn token->text
  [n {:keys [hit?] form "w" space-before "ws"}]
  (cond->> (if (pos? hit?) (str "<t>" form "</t>") form)
    (and (pos? n) (= "1" space-before)) (str " ")))

(defn parse-query-result
  [metadata* offset n {[_ tokens _]                 "ctx_"
                       {ks "indices_" :as metadata} "meta_"}]
  (let [tokens (map (partial zipmap (cons :hit? ks)) tokens)]
    (-> (assoc metadata* ::offset (+ offset n))
        (parse-metadata metadata)
        (assoc :text (str/join (map-indexed token->text tokens))))))

(def max-page-size
  1000)

(def ^:dynamic *query-timeout*
  30)

(def ddc-timer
  (env/timer "ddc"))

(defn corpus-query
  ([corpus q]
   (corpus-query corpus q 10 0))
  ([corpus q page-size offset]
   (try
     (let [cmd      (->> (str/join " " [offset page-size *query-timeout*])
                         (vector "run_query Distributed" q "json")
                         (str/join \))
           response (with-open [_ (env/timed! ddc-timer)
                                _ (env/timed! (env/timer (str "ddc-" corpus)))]
                      (request corpus cmd))
           total    (get response "nhits_" 0)
           metadata {::corpus corpus ::total total}
           parse    (partial parse-query-result metadata offset)
           matches  (into [] (map-indexed parse) (get response "hits_"))
           n        (count matches)]
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
              (corpus-query corpus q page-size offset))))))
     (catch Throwable t
       (t/error! {:id ::query :corpus corpus :query q :offset offset} t)
       nil))))

(defn result-stream
  ([q]
   (result-stream q flagship-corpora))
  ([q corpora]
   (result-stream q corpora max-page-size))
  ([q corpora page-size]
   (let [corpus-query #(doto (a/chan)
                         (a/onto-chan!! (corpus-query % q page-size 0)))]
     (merge-chs (into [] (map corpus-query) corpora)))))

(defn lexeme-stream
  [lexeme & args]
  (apply result-stream (str "'" (str/replace lexeme #"'" "\\'") "'") args))
