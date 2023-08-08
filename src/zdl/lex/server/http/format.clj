(ns zdl.lex.server.http.format
  (:require [clojure.data.csv :as csv]
            [gremid.data.xml :as dx]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [muuntaja.core :as m]
            [muuntaja.format.core :as m-format]))

(defn create-encoder
  [data->stream]
  (fn [_]
    (reify
      m-format/EncodeToBytes
      (encode-to-bytes [_ data charset]
        (let [buf (java.io.ByteArrayOutputStream.)]
          (data->stream data charset buf)
          (.. buf (toByteArray))))
      m-format/EncodeToOutputStream
      (encode-to-output-stream [_ data charset]
        (partial data->stream data charset)))))

(defn csv->stream [data charset stream]
  (let [w (io/writer stream :encoding charset)]
    (csv/write-csv w data)
    (.. w (flush))))


(def csv-format
  (m-format/map->Format
   {:name    "text/csv"
    :encoder [(create-encoder csv->stream)]}))

(defn xml->stream
  [{:keys [tag] :as doc} charset os]
  (when-not (= :-document tag)
    (throw (ex-info "No XML document response" {})))
  (with-open [w (io/writer os :encoding charset)]
    (dx/emit (assoc-in doc [:attrs :encoding] (str/upper-case charset)) w)))

(def xml-format
  (m-format/map->Format
   {:name    "application/xml"
    :encoder [(fn [_]
                (reify
                  m-format/EncodeToBytes
                  (encode-to-bytes [_ data charset]
                    (let [buf (java.io.ByteArrayOutputStream.)]
                      (xml->stream data charset buf)
                      (.. buf (toByteArray))))
                  m-format/EncodeToOutputStream
                  (encode-to-output-stream [_ data charset]
                    (partial xml->stream data charset))))]
    :decoder [(fn [_]
                (reify
                  m-format/Decode
                  (decode [_ data charset]
                    (with-open [r (io/reader data :encoding charset)]
                      (dx/parse r)))))]}))

(def customized-muuntaja
  (m/create
   (-> m/default-options
       (assoc-in [:http :encode-response-body?]
                 (fn [_ {:keys [body]}]
                   (or (coll? body) #_(string? body))))
       (assoc-in [:formats "text/csv"] csv-format)
       (assoc-in [:formats "application/xml"] xml-format))))
