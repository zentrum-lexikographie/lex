(ns zdl.lex.http
  (:require [clj-http.client :as http]
            [clojure.core.async :as a]
            [jsonista.core :as json])
  (:import java.util.concurrent.Future))

(defn async-request
  ([req]
   (async-request req (a/chan 1) (a/chan 1)))
  ([req result-ch error-ch]
   (async-request req result-ch error-ch nil))
  ([req result-ch error-ch cancel-ch]
   (let [req        (assoc req :async? true)
         complete!  (partial a/>!! result-ch)
         error!     (partial a/>!! error-ch)]
     (try
       (let [^Future f (http/request req complete! error!)]
         (when cancel-ch
           (a/go (when (a/<! cancel-ch) (. f (cancel true))))))
       (catch Throwable t (error! t)))
     [result-ch error-ch cancel-ch])))

(defn read-json
  [v]
  (json/read-value v json/keyword-keys-object-mapper))

(defn read-edn
  [v]
  (read-string (String. v "UTF-8")))

(defn sync-response
  [[result-ch error-ch]]
  (let [[resp-type response] (a/alt!! result-ch ([v] [:success v])
                                      error-ch ([v] [:error v]))]
    (condp = resp-type
      :success response
      :error   (throw response))))

(comment
  (sync-response
    (async-request {:url    "http://localhost:8983/solr/articles/query"
                    :method :get})))
