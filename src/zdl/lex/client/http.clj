(ns zdl.lex.client.http
  (:require [clj-http.client :as http]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [lambdaisland.uri :as uri]
            [zdl.lex.env :as env]
            [zdl.lex.url :as lexurl]
            [integrant.core :as ig])
  (:import java.io.PushbackReader))

(def login-url
  (uri/join lexurl/server-base "status"))

(def ^:dynamic *auth*
  (atom (let [{:keys [user password]} (get env/config ::server)]
          (when (and user password) [user password]))))

(defn authenticate
  []
  (or
   @*auth*
   (let [status-con (.. (java.net.URL. (str login-url)) (openConnection))]
     (.. status-con (setRequestProperty "Accept" "application/edn"))
     (with-open [status-stream (.. status-con (getInputStream))
                 status-reader (io/reader status-stream :encoding "UTF-8")
                 status-reader (PushbackReader. status-reader)]
       (let [{:keys [user password]} (edn/read status-reader)]
         (when (and user password)
           (reset! *auth* [user password])))))))

(def ^:dynamic *insecure?*
  (get-in env/config [::server :insecure?]))

(defn request
  [{::keys [authenticated?] :as req :or {authenticated? true}}]
  (let [auth (and authenticated? (authenticate))]
    (http/request
     (->
      req
      (update :method #(or % :get))
      (update :url #(str (uri/join lexurl/server-base %)))
      (update :as #(or % :clojure))
      (update-in [:headers "Accept"] #(or % "application/edn"))
      (cond->
          auth        (assoc :basic-auth auth)
          *insecure?* (assoc :insecure? true))))))

(defmethod ig/init-key ::server
  [_ config]
  config)
