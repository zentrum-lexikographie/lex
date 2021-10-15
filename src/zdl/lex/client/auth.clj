(ns zdl.lex.client.auth
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [lambdaisland.uri :as uri]
            [zdl.lex.client :as client]
            [zdl.lex.url :refer [server-base]])
  (:import java.io.PushbackReader))

(def ^:private auth
  (atom nil))

(defn authenticate
  []
  (or
   @auth
   (let [status-con (.. (java.net.URL. (str (uri/join server-base "status")))
                        (openConnection))]
     (.. status-con (setRequestProperty "Accept" "application/edn"))
     (with-open [status-stream (.. status-con (getInputStream))
                 status-reader (io/reader status-stream :encoding "UTF-8")
                 status-reader (PushbackReader. status-reader)]
       (let [{:keys [user password]} (edn/read status-reader)]
         (if (and user password) (reset! auth [user password])))))))

(defmacro with-authentication
  [& body]
  `(binding [client/*auth* (or client/*auth* (authenticate))]
     ~@body))
