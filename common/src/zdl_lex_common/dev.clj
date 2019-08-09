(ns zdl-lex-common.dev
  (:require [zdl-lex-common.article :as article]
            [zdl-lex-common.util :as util]
            [zdl-lex-common.xml :as xml]
            [clojure.java.io :as io]))

(comment
  (->> (io/file "../data/git/articles/Neuartikel/Abgastechnik-E_6900838.xml")
      (xml/parse)
      (article/doc->articles)
      (map article/excerpt))
  (util/->clean-map {:a nil})
  (-> "<root/>" xml/parse xml/serialize))

