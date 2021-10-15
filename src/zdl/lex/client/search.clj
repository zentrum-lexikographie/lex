(ns zdl.lex.client.search
  (:require [manifold.deferred :as d]
            [zdl.lex.client :as client]
            [zdl.lex.client.auth :as auth]
            [zdl.lex.client.bus :as bus]
            [zdl.lex.util :refer [uuid]]
            [clojure.tools.logging :as log]))

(defn request
  [q]
  (let [id (uuid)]
    (bus/publish! :search-request {:query q :id id})
    (d/chain
     (auth/with-authentication (client/search-articles q :limit 1000))
     (fn [{result :body}]
       (bus/publish! :search-response (assoc result :query q :id id))))))
