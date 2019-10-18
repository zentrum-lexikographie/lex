(ns zdl-lex-server.csv
  (:refer-clojure :exclude [format])
  (:require [clojure.data.csv :as csv]
            [muuntaja.format.core :as m-format]
            [clojure.java.io :as io]
            [taoensso.timbre :as timbre]))

(defn csv->stream [data charset stream]
  (let [w (io/writer stream :encoding charset)]
    (csv/write-csv w data)
    (.. w (flush))))

(defn encoder [_]
  (reify
    m-format/EncodeToBytes
    (encode-to-bytes [_ data charset]
      (let [buf (java.io.ByteArrayOutputStream.)]
        (csv->stream data charset buf)
        (.. buf (toByteArray))))
    m-format/EncodeToOutputStream
    (encode-to-output-stream [_ data charset]
      (partial csv->stream data charset))))

(def format
  (m-format/map->Format
   {:name "text/csv"
    :encoder [encoder]}))
