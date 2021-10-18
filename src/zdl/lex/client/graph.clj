(ns zdl.lex.client.graph
  (:require [clojure.core.cache.wrapped :as cache]
            [manifold.deferred :as d]
            [mount.core :refer [defstate]]
            [zdl.lex.client :as client]
            [zdl.lex.client.auth :as auth]
            [zdl.lex.client.bus :as bus]
            [zdl.lex.url :as lexurl]))

(def cache
  (cache/ttl-cache-factory {} :ttl (* 15 60 1000)))

(defn update-graph!
  [_ url]
  (let [id (lexurl/url->id url)]
    (d/chain
     (or
      (some-> (cache/lookup cache id) (d/success-deferred))
      (d/chain (auth/with-authentication (client/get-graph id))
               :body #(cache/lookup-or-miss cache id (constantly %))))
      #(bus/publish! :graph [id %]))))

(defstate graph-update
  :start (bus/listen #{:editor-activated :editor-saved} update-graph!)
  :stop (graph-update))

(comment
  @cache
  @(bus/publish! :editor-activated (lexurl/id->url "WDG/ro/roh-E_r_5749.xml")))
