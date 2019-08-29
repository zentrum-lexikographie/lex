(ns zdl-lex-server.http-client
  (:require [clj-http.client :as http]
            [taoensso.timbre :as timbre]))

(defn configure [auth-user auth-password]
  (comp #(timbre/spy :trace %)
        #(dissoc % :http-client)
        http/request
        (if (and auth-user auth-password)
          (partial merge {:basic-auth [auth-user auth-password]})
          identity)
        #(timbre/spy :trace %)))
