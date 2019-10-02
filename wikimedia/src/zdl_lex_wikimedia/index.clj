(ns zdl-lex-wikimedia.index
  (:require [clojure.java.jdbc :as jdbc]
            [hugsql.core :refer [def-db-fns]]
            [clojure.java.io :as io]
            [me.raynes.fs :as fs]
            [zdl-lex-wikimedia.article :as article]
            [zdl-lex-wikimedia.dump :as dump]))

(def-db-fns "zdl_lex_wikimedia/index.sql")

(defn db [name]
  (let [db {:dbtype "h2" :dbname name :user "sa" :password ""}]
    (jdbc/with-db-connection [c db]
      (create-excerpt-table c)
      (create-lemma-table c))
    db))

(defn wiktionary->db [dump-file db-file]
  (let [pages (dump/pages dump-file)
        db nil #_(db (str db-file))]
    (for [page (pmap article/parse pages)
          :let [{:keys [title entries]} page]
          entry entries
          :when (= "Deutsch" (entry :lang))
          :let [{:keys [types]} entry]
          type types
          :let [{:keys [pos-set]} type]
          pos (disj pos-set "Deutsch")]
      [title pos "de.wiktionary.org" (pr-str type)])))

(comment
  (take 10 (wiktionary->db (fs/file "data/dewiktionary.xml")
                           (fs/file "data/de.wiktionary.org-index"))))
