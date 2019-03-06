(ns zdl-lex-server.webdav
  (:require [clojure.java.io :as io])
  (:import io.milton.ent.config.HttpManagerBuilderEnt
           io.milton.http.fs.SimpleLockManager
           [io.milton.servlet MiltonServlet ServletRequest ServletResponse]
           java.util.HashMap
           org.eclipse.jetty.server.handler.AbstractHandler))

(defn handler [base-dir users-passwords]
  (let [fs-home-dir (-> base-dir io/file (.getAbsolutePath))
        user-password-map (HashMap. users-passwords)
        http-manager (-> (doto (HttpManagerBuilderEnt.)
                           (.setCaldavEnabled false)
                           (.setCarddavEnabled false)
                           (.setEnableWellKnown false)
                           (.setAclEnabled false)
                           (.setEnabledJson false)
                           (.setFsHomeDir fs-home-dir)
                           (.setFsRealm "WebDAV")
                           (.setMapOfNameAndPasswords user-password-map))
                         (.buildHttpManager))]
    (proxy [AbstractHandler] []
      (handle [target baseRequest request response]
        (try
          (MiltonServlet/setThreadlocals request response)
          (.process http-manager
                    (ServletRequest. request nil)
                    (ServletResponse. response))
          (finally
            (.setHandled baseRequest true)
            (MiltonServlet/clearThreadlocals)
            (.. response (getOutputStream) (flush))
            (.flushBuffer response)))))))
