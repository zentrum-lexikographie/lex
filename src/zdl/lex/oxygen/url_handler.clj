(ns zdl.lex.oxygen.url-handler
  (:gen-class
   :name de.zdl.oxygen.URLHandler
   :implements [ro.sync.exml.plugin.urlstreamhandler.URLStreamHandlerWithLockPluginExtension])
  (:require
   [taoensso.telemere :as tel]
   [zdl.lex.client :as client])
  (:import
   (java.io ByteArrayOutputStream IOException)
   (java.net URL URLConnection URLStreamHandler URLStreamHandlerFactory)
   (ro.sync.exml.plugin.lock LockException LockHandler)))

(def lexurl-handler
  (proxy [URLStreamHandler] []
    (openConnection [url]
      (let [id (client/url->id url)]
        (proxy [URLConnection] [url]
          (connect
            []
            (comment "No-Op"))
          (getInputStream
            []
            (tel/log! :debug (str "< " id))
            (try
              (client/http-get-article id)
              (catch LockException e
                (tel/error! ::lock e)
                (throw (IOException. e)))))
          (getOutputStream
            []
            (tel/log! :debug (str "> " id))
            (proxy [ByteArrayOutputStream] []
              (close []
                (try
                  (client/http-post-article id (.toByteArray this))
                  (catch LockException e
                    (tel/error! ::lock e)
                    (throw (IOException. e))))))))))))


(defn install-stream-handler!
  []
  (try
    (URL/setURLStreamHandlerFactory
     (proxy [URLStreamHandlerFactory] []
       (createURLStreamHandler [protocol]
         (when (= "lex" protocol) lexurl-handler))))
    (catch Throwable _)))

(comment
  (install-stream-handler!))


(def lock-handler
  (proxy [LockHandler] []
    (isLockEnabled
      []
      true)
    (updateLock
      [url timeout]
      (try
        (let [id (client/url->id url)]
          (tel/log! :debug (str "+ " id " <" timeout ">"))
          (client/http-lock id timeout))
        (catch IOException e (tel/error! e) nil)))
    (unlock
      [url]
      (try
        (let [id (client/url->id url)]
          (tel/log! :debug (str "- " id))
          (client/http-unlock id))
        (catch IOException e (tel/error! e) nil)))))

(defn -getURLStreamHandler
  [_ protocol]
  (when (= "lex" protocol) lexurl-handler))

(defn -isLockingSupported
  [_ protocol]
  (= "lex" protocol))

(defn -getLockHandler
  [_]
  lock-handler)
