(ns zdl.lex.server.article.editor
  (:require
   [clojure.tools.logging :as log]
   [slingshot.slingshot :refer [try+]]
   [zdl.lex.article.lock :as article.lock]
   [zdl.lex.server.article.editor.gloss]
   [zdl.lex.server.article.editor.status]
   [zdl.lex.server.article.editor.typography]
   [zdl.lex.server.article.lock :as server.article.lock]
   [zdl.lex.server.git :as server.git]))

(def editors
  [zdl.lex.server.article.editor.typography/edit!
   zdl.lex.server.article.editor.gloss/edit!
   zdl.lex.server.article.editor.status/edit!])

(defn edit'
  [article f]
  (doseq [editor editors]
    (editor article f)))

(defn create-edit-lock
  [resource]
  (article.lock/create-lock "zdl-lex-server" resource 60))

(defn edit-resource!
  [git-dir lock-db edit-fn resource lock]
  (log/debugf "! %s" resource)
  (let [edit-fn' (server.article.lock/locking-edit-fn lock-db lock edit-fn)]
    (server.git/edit! git-dir resource edit-fn')))

(defn edit!
  [git-dir lock-db]
  (doseq [article (server.git/articles git-dir)]
    (let [{:keys [id]} article
          lock         (create-edit-lock id)]
      (try+
       (edit-resource! git-dir lock-db (partial edit' article) id lock)
       (catch [:type ::server.article.lock/locked] {:keys [lock]}
         (log/warnf "Cannot edit locked article %s: %s" id lock))))))
