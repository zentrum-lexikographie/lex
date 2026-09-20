(ns zdl.lex.corpora.sources
  (:require
   [babashka.fs :as fs]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [next.jdbc :as jdbc]
   [taoensso.telemere :as tel]
   [zdl.lex.env :refer [getenv]])
  (:import
   (java.nio.file Files FileVisitOption)))

(def dir
  (io/file (getenv "CORPUS_SOURCE_DIR" "corpora")))

(def db
  {:jdbcUrl (str "jdbc:sqlite:" (getenv "CORPUS_SOURCE_DB" "corpora.db"))})

(defn list-subdirs
  [dir]
  (filter fs/directory? (fs/list-dir dir)))

(defn collection-dir?
  [dir]
  (#{"asv" "genios" "kern" "sz"} (fs/file-name dir)))

(defn corpus-dirs
  []
  (->> (list-subdirs dir)
       (mapcat #(if (collection-dir? %) (list-subdirs %) (list %)))))

(defn xml-source-dir
  [corpus-dir]
  (or (let [xml-source-dir (fs/path corpus-dir "xml")]
        (when (fs/directory? xml-source-dir) xml-source-dir))
      (->> (list-subdirs corpus-dir)
           (filter #(str/starts-with? "xml-" (fs/file-name %)))
           (sort-by fs/file-name)
           (last))
      (->> (list-subdirs corpus-dir)
           (filter #(re-seq #"[0-9]{4}-[0-9]{2}-[0-9]{2}" (fs/file-name %)))
           (sort-by fs/file-name)
           (last))))

(defn xml-files
  [dir]
  (let [visit-options (into-array FileVisitOption (list FileVisitOption/FOLLOW_LINKS))]
    (->> (Files/walk (fs/path dir) visit-options)
         (stream-seq!)
         (filter #(and (fs/regular-file? %) (str/ends-with? (fs/file-name %) ".xml"))))))

(defn dir->db
  []
  (jdbc/execute!
   db
   ["create table if not exists files (
       path text not null,
       modified integer not null,
       corpus text not null,
       size integer not null,
       primary key (path, modified)
     )"])
  (->>
   (for [corpus-dir (random-sample 0.1 (corpus-dirs))
         :let       [xml-source-dir (xml-source-dir corpus-dir)]
         :when      xml-source-dir
         :let       [corpus (fs/file-name corpus-dir)]
         xml-file   (take 100 (xml-files xml-source-dir))]
     (let [path     (str (fs/relativize dir xml-file))
           modified (fs/last-modified-time xml-file)
           size     (fs/size xml-file)]
       (tel/with-ctx+ {::path     path
                       ::modified (fs/file-time->instant modified)
                       ::corpus   corpus
                       ::size     size}
         (tel/event! ::xml-file :debug)
         [path (fs/file-time->millis modified) corpus size])))
   (partition-all 10240)
   (run! #(jdbc/execute-batch!
           db
           "insert into files (path, modified, corpus, size)
              values (?, ?, ?, ?)
              on conflict do nothing"
           % {}))))
