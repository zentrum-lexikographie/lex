(ns zdl.lex.server.solr.suggest
  (:require [clojure.core.async :as a]
            [lambdaisland.uri :as uri :refer [uri]]
            [metrics.timers :as timers]
            [zdl.lex.http :as http]
            [zdl.lex.server.solr.client :as solr.client]))

(def forms-suggestions-build-timer
  (timers/timer ["solr" "client" "forms-suggestions-build-timer"]))

(defn build-forms-suggestions
  []
  (a/go
    (let [request {:url          (uri "suggest")
                   :query-params {"suggest.dictionary" "forms"
                                  "suggest.buildAll"   "true"}}]
      (when-let [response (a/<! (solr.client/request request))]
        (http/update-timer! forms-suggestions-build-timer response)
        (http/read-json response)))))
  

(def suggest-timer
  (timers/timer ["solr" "client" "suggest-timer"]))

(defn suggest-forms
  [{{{:keys [q]} :query} :parameters}]
  (a/go
    (let [request {:url          (uri "suggest")
                   :query-params {"suggest.dictionary" "forms"
                                  "suggest.q"          q}}]
      (if-let [response (a/<! (solr.client/request request))]
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

