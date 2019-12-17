(ns zdl-lex-wikimedia.dump
  "Download Wikimedia dumps and parse XML-encoded page revisions."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log])
  (:import [java.io File InputStream]
           [javax.xml.stream XMLEventReader XMLInputFactory]
           [javax.xml.stream.events Attribute Characters EndElement StartElement XMLEvent]
           javax.xml.transform.stream.StreamSource
           org.apache.commons.compress.archivers.sevenz.SevenZFile
           org.apache.commons.compress.compressors.CompressorStreamFactory))

;; ## Dump Download

(def ^:private dump-dir
  "Where downloads are stored: `$WIKIMEDIA_DUMP_DIR` (default:
  `wikimedia-dumps/`)"
  (-> "WIKIMEDIA_DUMP_DIR" System/getenv not-empty (or "wikimedia-dumps")))

(defn download-dump
  "Download non-existing dump files to the local store."
  [{:keys [url ^File dump-file] :as coords}]
  (when-not (.. dump-file (exists))
    (.. dump-file (getParentFile) (mkdirs))
    (log/tracef "Download Wikimedia dump %s (-> %s)" url (str dump-file))
    (with-open [dump-stream (io/input-stream url)]
      (io/copy dump-stream dump-file)))
  coords)

(defn ^InputStream extract-dump
  "Streaming extraction of a local dump-file."
  [{:keys [^File dump-file ^String compression]}]
  (condp = compression
    "7z"
    (let [dump-file (SevenZFile. dump-file)]
      (.. dump-file (getNextEntry))
      (proxy [InputStream] []
        (read
          ([] (.read dump-file))
          ([^bytes b off len] (.read dump-file b (int off) (int len))))
        (close [] (.close dump-file))))
    "bz2"
    (let [dump-stream (io/input-stream dump-file)
          compressor-stream (.. (CompressorStreamFactory.)
                                (createCompressorInputStream
                                 CompressorStreamFactory/BZIP2
                                 dump-stream))]
      compressor-stream)
    (throw (IllegalArgumentException. compression))))

(defn read-dump
  "Construct URL from dump coordinates, download dump if required and extract it."
  ([wiki dump-type]
   (read-dump wiki dump-type "latest"))
  ([wiki dump-type timestamp]
   (read-dump wiki dump-type timestamp "xml" "7z"))
  ([wiki dump-type timestamp fmt compression]
   (let [filename (format "%s-%s-%s.%s.%s" wiki timestamp dump-type fmt compression)
         url (format "https://dumps.wikimedia.org/%s/%s/%s" wiki timestamp filename)]
     (->
      {:wiki wiki
       :type dump-type
       :timestamp timestamp
       :fmt fmt
       :compression compression
       :filename filename
       :url url
       :dump-file (io/file dump-dir filename)}
      (download-dump)
      (extract-dump)))))

;; ## XML parsing

(def xml-size-limit-sys-props
  "System properties related to XML parsing."
  ["entityExpansionLimit" "totalEntitySizeLimit" "jdk.xml.totalEntitySizeLimit"])

(defn config-xml-parser-for-large-dump!
  "Maximize limits for parsing huge XML documents."
  []
  (log/tracef (str "Setting system properties to max values for "
                   "parsing huge XML documents (%s)") xml-size-limit-sys-props)
  (doseq [sys-prop xml-size-limit-sys-props]
    (System/setProperty sys-prop (str (Integer/MAX_VALUE)))))

;; Configure XML parser for huge XML documents via setting `$WIKIMEDIA_DUMP_HUGE`.

(when (System/getenv "WIKIMEDIA_DUMP_HUGE")
  (config-xml-parser-for-large-dump!))

;; Convert XML stream processing events to Clojure data structures (maps, strings).

(defn- xml-start-name
  [^StartElement event]
  (.. event getName getLocalPart))

(defn- xml-end-name
  [^EndElement event]
  (.. event getName getLocalPart))

(defn- xml-attrs
  [^StartElement event]
  (->>
   (for [^Attribute attr (iterator-seq (.getAttributes event))]
     [(keyword (.. attr getName getLocalPart)) (.getValue attr)])
   (into (hash-map))))

(defn- xml-text
  [^Characters event]
  (.getData event))

(defn- xml-text-join
  "Joins adjacent XML character events (strings)."
  [evts]
  (mapcat
   (fn [p] (if (-> p first string?) [(apply str p)] p))
   (partition-by string? evts)))

(defn- xml-event
  [^XMLEvent event]
  (cond
    (.isStartElement event) (merge {:< (xml-start-name event)} (xml-attrs event))
    (.isEndElement event) {:> (xml-end-name event)}
    (.isCharacters event) (xml-text event)
    :else nil))

(defn ^XMLEventReader xml-event-reader
  "Read XML stream events from a given input stream."
  [^InputStream is]
  (.. (XMLInputFactory/newInstance) (createXMLEventReader (StreamSource. is))))

(defn events->seq
  "Convert XML stream events to Clojure data."
  [^XMLEventReader events]
  (->> (iterator-seq events) (map xml-event) (keep identity) (xml-text-join)))

;; ## Revision extraction

(def ^:private property-element?
  "Elements in Wikimedia dumps for which we collect string contents."
  #{"title" "timestamp" "text" "username" "comment"})

(defn parse-revisions
  "Parse XML stream events from a dump into a sequence of revisions."
  ([events]
   (parse-revisions events (transient {})))
  ([events ctx]
   (if-let [evt (first events)]
     (if (map? evt)
       (cond
         (property-element? (evt :<))
         (lazy-seq (parse-revisions (rest events) (assoc! ctx :sb (StringBuilder.))))

         (property-element? (evt :>))
         (let [text (-> (.toString ^StringBuilder (ctx :sb)) str/trim not-empty)
               ctx (-> ctx (assoc! (-> evt :> keyword) text) (dissoc! :sb))]
           (lazy-seq (parse-revisions (rest events) ctx)))

         (= "revision" (evt :>))
         (cons (select-keys ctx [:title :timestamp :text :username :comment])
               (lazy-seq
                (parse-revisions
                 (rest events)
                 (dissoc! ctx :timestamp :text :username :comment))))

         (= "page" (evt :>))
         (lazy-seq (parse-revisions (rest events) (dissoc! ctx :title)))

         :else
         (lazy-seq (parse-revisions (rest events) ctx)))

       (do
         (when-let [^StringBuilder sb (ctx :sb)] (.append sb ^String evt))
         (lazy-seq (parse-revisions (rest events) ctx)))))))

(defmacro with-revisions
  "Download, extract and read a given Wikimedia dump, then parse it into a
  sequence of `revisions`."
  [coords & body]
  `(with-open [is# (apply read-dump ~coords)
               events# (xml-event-reader is#)]
     (let [~'revisions (-> events# events->seq parse-revisions)]
       ~@body)))
