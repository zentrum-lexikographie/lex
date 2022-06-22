(ns zdl.lex.server.article.editor.status
  (:require
   [clojure.tools.logging :as log]
   [gremid.data.xml :as dx]
   [zdl.lex.article :as article]
   [zdl.lex.server.article.editor.util :refer [edit-xml! red-1?]]))

(dx/alias-uri :dwds "http://www.dwds.de/ns/1.0")

(defn red-1->red-2
  [node]
  (if (= ::dwds/Artikel (:tag node))
    (assoc-in node [:attrs :Status] "Red-2")
    (if (string? node)
      node
      (update node :content (partial map red-1->red-2)))))

(defn edit!
  [article f]
  (when (red-1? article)
    (log/infof "! %s" (article/desc article))
    (edit-xml! article f red-1->red-2)))
