(ns zdl.lex.server.gen
  (:require  [clojure.test :as t]
             [zdl.lex.article.fs :as afs]
             [clojure.spec.gen.alpha :as gen]))


(comment
  (gen/generate
   (gen/set
    (gen/elements (afs/files "/home/gregor/repositories/zdl-wb"))
    {:min-elements 10 :max-elements 1000}))
  (time (count (afs/files "/home/gregor/repositories/zdl-wb"))))
