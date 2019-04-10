(ns zdl-lex-client.article-delete
  (:import [ro.sync.ecss.extensions.api ArgumentDescriptor])
  (:gen-class
   :name de.zdl.oxygen.ArticleDeleteOperation
   :implements [ro.sync.ecss.extensions.api.AuthorOperation]))

(defn -getDescription [this] "Deletes a lexicon article")

(defn -getArguments [this] (make-array ArgumentDescriptor 0))

(defn -doOperation [this author-access args])

