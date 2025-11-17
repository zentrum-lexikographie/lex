(ns zdl.lex.server.queue
  (:require
   [langohr.core :as rmq]
   [langohr.channel :as lch]
   [langohr.exchange :as le]
   [langohr.queue :as lq]
   [langohr.consumers :as lc]
   [langohr.basic :as lb]
   [zdl.lex.env :as env]
   [clojure.string :as str]
   [taoensso.telemere :as tm]
   [zdl.lex.server.conllu :as conllu]))


(def ^:dynamic connection
  nil)

(def ^:dynamic channel
  nil)

(defn request-examples
  [lemmata]
  (when channel
    (lb/publish channel "" "example_queries"
                (. (str/join \newline lemmata) (getBytes "UTF-8"))
                {:content-type "text/plain"})))

(defn handle-examples
  [_ch _metadata ^bytes payload]
  (tm/event! ::examples
             {:level :info
              :data  {:n (count (conllu/parse-str (String. payload "UTF-8")))}}))

(defn disconnect
  []
  (when channel (rmq/close channel))
  (when connection (rmq/close connection))
  (alter-var-root #'channel (constantly nil))
  (alter-var-root #'connection (constantly nil)))

(defn connect
  []
  (let [c  (rmq/connect env/queue)
        ch (lch/open c)]
    (lq/declare ch "example_queries" {:durable true :auto-delete false})
    (let [examples (lq/declare ch "" {:exclusive true :auto-delete true})
          examples (. examples (getQueue))]
      (le/declare ch "examples" "fanout")
      (lq/bind ch examples "examples")
      (lc/subscribe ch examples handle-examples {:auto-ack true}))
    (alter-var-root #'connection (constantly c))
    (alter-var-root #'channel (constantly ch))))

(comment
  (connect)
  (request-examples #{"Lektion" "erteilen"})
  (disconnect))
