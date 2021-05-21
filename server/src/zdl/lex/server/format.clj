(ns zdl.lex.server.format
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [doctype]]
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

(defn html->stream
  [doc charset os]
  (with-open [w (io/writer os :encoding charset)]
    (.write w ^String (html {:mode :html} (doctype :html5) doc))))

(def html-format
  (m-format/map->Format
   {:name    "text/html"
    :encoder [(create-encoder html->stream)]}))

(def customized-muuntaja
  (m/create
   (-> m/default-options
       (assoc-in [:http :encode-response-body?]
                 (fn [_ {:keys [body] :as response}]
                   (or (coll? body) (string? body))))
       (assoc-in [:formats "text/csv"] csv-format)
       (assoc-in [:formats "text/html"] html-format))))
