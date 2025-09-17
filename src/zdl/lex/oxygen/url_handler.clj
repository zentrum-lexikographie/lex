(ns zdl.lex.oxygen.url-handler
  (:gen-class
   :name de.zdl.oxygen.URLHandler
   :implements [ro.sync.exml.plugin.urlstreamhandler.URLStreamHandlerWithLockPluginExtension])
  (:require
   [taoensso.telemere :as tm]
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
            (tm/log! {:level :debug :id ::read :data id})
            (try
              (client/http-get-article id)
              (catch LockException e
                (tm/error! e)
                (throw (IOException. e)))))
          (getOutputStream
            []
            (tm/log! {:level :debug :id ::write :data id})
            (proxy [ByteArrayOutputStream] []
              (close []
                (try
                  (client/http-post-article id (.toByteArray this))
                  (catch LockException e
                    (tm/error! e)
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
          (tm/log! {:level :debug :id ::lock :data {:id      id
                                                    :timeout timeout}})
          (client/http-lock id timeout))
        (catch IOException e (tm/error! e) nil)))
    (unlock
      [url]
      (try
        (let [id (client/url->id url)]
          (tm/log! {:level :debug :id ::unlock :data id})
          (client/http-unlock id))
        (catch IOException e (tm/error! e) nil)))))

(defn -getURLStreamHandler
  [_ protocol]
  (when (= "lex" protocol) lexurl-handler))

(defn -isLockingSupported
  [_ protocol]
  (= "lex" protocol))

(defn -getLockHandler
  [_]
  lock-handler)
