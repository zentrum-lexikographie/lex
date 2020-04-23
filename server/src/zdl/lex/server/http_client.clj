(ns zdl.lex.server.http-client
  (:require [clj-http.client :as http]
            [clojure.tools.logging :as log]))

(defn configure [auth-user auth-password]
  (comp #(log/spy :trace %)
        #(dissoc % :http-client)
        http/request
        (if (and auth-user auth-password)
          (partial merge {:basic-auth [auth-user auth-password]})
          identity)
        #(log/spy :trace %)))
