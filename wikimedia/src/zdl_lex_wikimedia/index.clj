(ns zdl-lex-wikimedia.index
  (:require [clojure.java.jdbc :as jdbc]
            [hugsql.core :refer [def-db-fns]]
            [clojure.java.io :as io]
            [me.raynes.fs :as fs]
            [zdl-lex-common.article :as zdl-article]
            [zdl-lex-common.env :refer [env]]
            [zdl-lex-wikimedia.article :as wkt-article]
            [zdl-lex-wikimedia.dump :as dump]))

(def-db-fns "zdl_lex_wikimedia/index.sql")

(defn db []
  (let [dbname (str (fs/file (env :data-dir) "dictionary-index"))
        db {:dbtype "h2" :dbname dbname :user "sa" :password ""}]
    (jdbc/with-db-connection [c db]
      (doto c
        (set-collation)
        (create-excerpt-table)
        (create-excerpt-collection-index)
        (create-lemma-table)))
    db))

(defn wiktionary->db [pages db]
  (let [collection "de.wiktionary.org"]
    (jdbc/with-db-transaction [c db]
      (delete-collection c {:collection collection})
      (doseq [page (pmap wkt-article/parse pages)
              :let [{:keys [title entries]} page]]
        (doseq [entry entries :when (= "Deutsch" (entry :lang))
                :let [{:keys [types]} entry]]
          (doseq [type types
                  :let [excerpt-params {:collection collection
                                        :contents (pr-str type)}
                        {excerpt :id} (insert-excerpt c excerpt-params)
                        {:keys [pos-set]} type]]
            (doseq [pos (disj pos-set "Deutsch")]
              (merge-lemma c {:surface_form title
                              :part_of_speech pos
                              :excerpt excerpt}))))))))

(defn zdl->db [excerpts db]
  (let [collection "zdl.org"]
    (jdbc/with-db-transaction [c db]
      (delete-collection c {:collection collection})
      (doseq [excerpt excerpts
              :let [{:keys [forms pos type source status]} excerpt
                    contents (-> excerpt (dissoc :file) (pr-str))
                    excerpt-params {:collection collection :contents contents}
                    {excerpt :id} (insert-excerpt c excerpt-params)]]
        (doseq [form forms]
          (doseq [pos (or pos ["-"])]
            (merge-lemma c {:surface_form form
                            :part_of_speech pos
                            :excerpt excerpt})))))))


(def dump-file (fs/file "data" "dewiktionary.xml"))

(comment
  (jdbc/with-db-transaction [c (db)]
    (take 5 (select-entries c {} {})))

  (jdbc/with-db-transaction [c (db)]
    (select-entries
     c {} {}
     {:row-fn #(select-keys % [:surface_form :part_of_speech :collection])
      :result-set-fn (comp
                     vec
                     (partial take 10)
                     (partial drop 10)
                     (partial partition-by
                              #(select-keys % [:surface_form :part_of_speech])))}))

  (jdbc/with-db-transaction [c (db)]
    (select-entries
     c {} {}
     {:row-fn #(select-keys % [:collection :part_of_speech])
      :result-set-fn (comp
                      vec
                      (partial map second)
                      (partial group-by :collection)
                      (partial apply hash-set))}))

  (->> (zdl-article/excerpts "../../lex-data/articles")
       (remove (partial :pos))
       (count))

  (time
   (wiktionary->db
    (->> (fs/file "data" "dewiktionary.xml")
         (dump/pages))
    (db)))

  (time
   (zdl->db
    (zdl-article/excerpts "../../lex-data/articles")
    (db))))
