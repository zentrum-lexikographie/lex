(ns zdl.lex.server.article.editor.util
  (:require [zdl.lex.article :as article]))

(defn red-1?
  [{:keys [status]}]
  (= "Red-1" status))

(defn edit-xml!
  [article f xml-edit-fn]
  (when (:xml article)
    (let [xml  (article/read-xml f)
          xml' (xml-edit-fn xml)]
      (when-not (= xml xml')
        (article/write-xml xml' f)))))
