(ns zdl-lex-server.consistency
  (:require [clojure.string :as str]
            [zdl-lex-common.article :as article]
            [zdl-lex-common.xml :as xml]
            [zdl-lex-server.store :as store]))

(comment
  (for [article (->> (store/article-files) (take 10000))
        ref (->> article xml/->xdm article/references)
        :let [{:keys [sense lemma]} ref]
        :when (and sense (str/includes? sense "α"))]
    [article ref]))

