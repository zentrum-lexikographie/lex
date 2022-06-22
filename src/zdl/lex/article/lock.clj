(ns zdl.lex.article.lock
  (:require [zdl.lex.util :refer [uuid]]))

(def ^:dynamic *token*
  (uuid))

(defn create-lock
  ([owner resource ttl]
   (create-lock *token* owner resource ttl))
  ([token owner resource ttl]
   {:token    token
    :owner    owner
    :resource resource
    :expires  (+ (System/currentTimeMillis) (* 1000 ttl))}))

(defn ->str
  [{:keys [resource owner expires]}]
  (format
   "<%s>@[%s,%s]"
   resource
   owner
   (-> expires
       (java.time.Instant/ofEpochMilli)
       (java.time.OffsetDateTime/ofInstant (java.time.ZoneId/systemDefault))
       (str))))

