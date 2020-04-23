(ns zdl.lex.corpus.ddc
  (:require [aleph.tcp :as tcp]
            [cheshire.core :as json]
            [clojure.core.async :as a]
            [clojure.string :as str]
            [gloss.core :as gloss]
            [gloss.io :as bio]
            [manifold.deferred :as d]
            [manifold.stream :as s]
            [throttler.core :refer [throttle-chan]]
            [clojure.tools.logging :as log]
            [zdl.lex.corpus.toc :refer [corpora]]))

;; support core.async channels as manifold streams
(require 'manifold.stream.async)

(def message-frame
  "Message frames are strings with LSB-encoded byte-length headers"
  (gloss/finite-frame :uint32-le (gloss/string :utf-8)))

(def client-protocol
  "We send string requests to DDC and receive JSON in return"
  (gloss/compile-frame message-frame str #(json/parse-string % true)))

(defn- wrap-duplex-stream
  "Wraps a duplex stream with encoder/decoder of a given protocol"
  [protocol s]
  (let [out (s/stream)]
    (s/connect (s/map #(bio/encode protocol %) out) s)
    (s/splice out (bio/decode-stream s protocol))))

(defn query->cmd
  "Encodes a DDC query command"
  ([q]
   (query->cmd q 0 10 5))
  ([q offset limit timeout]
   (->> (str/join " " [offset limit timeout])
        (vector "run_query Distributed" q "json")
        (str/join (char 1)))))

(defn send-async-req
  "Sends a command to a given corpus, putting the result onto the provided channel."
  [cmd corpus ch]
  (log/debug {:corpus corpus :cmd cmd})
  (->
   (d/chain
    (tcp/client (select-keys corpus [:host :port]))
    (partial wrap-duplex-stream client-protocol)
    (fn [con]
      (-> (d/chain
           (s/put! con cmd)
           (fn [cmd-sent]
             (if cmd-sent
               (s/take! con)
               (-> (ex-info "Error sending command" (assoc corpus :cmd cmd))
                   (d/error-deferred)))))
          (d/finally #(s/close! con))))
    (partial s/put! (s/->sink ch)))
   (d/catch
       (fn [e]
         (log/debug
          (ex-info "Error in DDC request" (assoc corpus :cmd cmd) e))
         (a/close! ch)))))

(let [corpus-req-request (a/chan (a/sliding-buffer 1024))
      corpus-req-permit (throttle-chan corpus-req-request 8 :second)]
  (defn cmd->corpus
  "Sends a command to a DDC node/corpus and receives a result.
   Requests are rate-limited globally."
    [cmd timeout-ch corpus]
    (a/go
      (a/>! corpus-req-request :token)
      (when (a/alt! corpus-req-permit :token timeout-ch nil)
        (let [result (a/chan)]
         (send-async-req cmd corpus result)
         (a/<! result))))))
