(ns zdl-lex-client.article-create
  (:import [ro.sync.ecss.extensions.api ArgumentDescriptor])
  (:gen-class
   :name de.zdl.oxygen.ArticleCreateOperation
   :implements [ro.sync.ecss.extensions.api.AuthorOperation]))

(defn -getDescription [this] "Creates a new lexicon article")

(defn -getArguments [this] (make-array ArgumentDescriptor 0))

(defn -doOperation [this author-access args])


