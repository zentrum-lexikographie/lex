(ns zdl-lex-corpus.toc
  (:require [cheshire.core :as json]
            [zdl-lex-common.env :refer [env]]
            [clj-http.client :as http]))

(defn- corpus->map [{:keys [host port internal]}]
  "Extracts essential corpus parameters"
  {:host host
   :port (Integer/parseInt port)
   :internal (= "1" (str internal))})

(def ^:private corpora-req
  "HTTP request for corpus data"
  (merge
   {:method :get
    :url "https://kaskade.dwds.de/dstar/?f=json"}
   (let [user (env :corpora-user)
         password (env :corpora-password)]
     (if (and user password)
       ;; Use authenticated request for all corpora (including private ones)
       {:url "https://kaskade.dwds.de/dstar/intern.perl?f=json"
        :basic-auth [user password]}))))

(defn corpora []
  "Queries the set of available copora"
  (->>
   (-> (http/request corpora-req) :body (json/parse-string true))
   (group-by (comp keyword :corpus))
   (map (fn [[k v]] [k (-> v first corpus->map)]))
   (into (sorted-map))))

(comment
  (corpora))
