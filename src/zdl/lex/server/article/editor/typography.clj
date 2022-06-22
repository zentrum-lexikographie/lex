(ns zdl.lex.server.article.editor.typography
  (:require
   [clojure.string :as str]
   [gremid.data.xml :as dx]
   [zdl.lex.server.article.editor.util :refer [edit-xml! red-1?]]
   [clojure.tools.logging :as log]
   [zdl.lex.article :as article]))

(dx/alias-uri :dwds "http://www.dwds.de/ns/1.0")

(defn gloss?
  [node]
  (= ::dwds/Belegtext (:tag node)))

(defn fix-typography'
  [s]
  (-> s
      (str/replace "..." "…")
      (str/replace ". . ." "…")
      (str/replace "—", "–") ; EM DASH → EN DASH
      (str/replace " -- ", " – ")
      (str/replace #"\s([\.,;])\s" "$1 ")))

(defn fix-typography
  ([node]
   (fix-typography (gloss? node) node))
  ([in-gloss? node]
   (if (string? node)
     (cond-> node in-gloss? (fix-typography'))
     (update node :content
             (partial map (partial fix-typography (or in-gloss? (gloss? node))))))))

(defn edit!
  [article f]
  (when (red-1? article)
    (log/infof "! %s" (article/desc article))
    (edit-xml! article f fix-typography)))
