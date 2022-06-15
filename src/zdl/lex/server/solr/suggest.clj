(ns zdl.lex.server.solr.suggest
  (:require [clojure.core.async :as a]
            [lambdaisland.uri :as uri :refer [uri]]
            [metrics.timers :as timers]
            [zdl.lex.http :as http]
            [zdl.lex.server.solr.client :as solr.client]))

(def suggest-timer
  (timers/timer ["solr" "client" "suggest-timer"]))

(defn suggest-forms
  [{{{:keys [q]} :query} :parameters}]
  (a/go
    (let [request (solr.client/->request
                   {:url          (uri "suggest")
                    :query-params {"suggest.dictionary" "forms"
                                   "suggest.cfq"        "article"
                                   "suggest.q"          q}})]
      (if-let [response (a/<! (solr.client/async-request request))]
        (let [response    (http/update-timer! suggest-timer response)
              response    (http/read-json response)
              forms       (get-in response [:body :suggest :forms])
              q           (keyword q)
              total       (get-in forms [q :numFound] 0)
              suggestions (get-in forms [q :suggestions] [])
              result      (for [{:keys [term payload]} suggestions]
                            (assoc (read-string payload) :suggestion term))]
          {:status 200
           :body   {:total  total
                    :result result}})
        {:status 500}))))

(def forms-suggestions-build-timer
  (timers/timer ["solr" "client" "forms-suggestions-build-timer"]))

(defn build-forms-suggestions!
  []
  (->>
   {:url          (uri "suggest")
    :query-params {"suggest.dictionary" "forms"
                   "suggest.buildAll"   "true"}}
   (solr.client/->request)
   (http/request)
   (http/update-timer! forms-suggestions-build-timer)
   (http/read-json)))
