(ns zdl-lex-server.exist2git
  (:require [clojure.string :as str]
            [me.raynes.fs :as fs]
            [zdl-lex-server.env :refer [config]]
            [zdl-lex-server.git :as git]
            [zdl-lex-server.store :as store]))

(def exist-db-host "spock.dwds.de")
(def exist-export-file (fs/file store/data-dir "exist-db-export.zip"))
(def exist-dir (fs/file store/data-dir "exist-db"))

(defn- proc [& cmd]
  (when-not (= 0 (.. (ProcessBuilder. cmd) (inheritIO) (start) (waitFor)))
    (throw (ex-info (str cmd) {}))))

(defn- proc-out [& cmd]
  (let [p (.. (ProcessBuilder. cmd) (start))]
    (if (= 0 (.waitFor p))
      (with-open [out (.getInputStream p)] (slurp out :encoding "UTF-8"))
      (with-open [err (.getErrorStream p)]
        (throw (ex-info (slurp err :encoding "UTF-8") {}))))))

(defn- path [f] (.getAbsolutePath f))

(defn- query-last-backup []
  (->
   (proc-out "ssh" exist-db-host
             "ls" "/home/eXist-db-2.2/webapp/WEB-INF/data/export/full*.zip")
   (str/split #"\s+")
   (sort)
   (last)))

(defn copy-articles-to-git []
  (let [exist-data-dir (fs/file exist-dir "db" "dwdswb" "data")
        exist-data-path-length (-> exist-data-dir path count inc)]
    (doseq [exist-file (store/xml-files exist-data-dir)
            :let [rel-path (-> exist-file path (.substring exist-data-path-length))
                  article-file (fs/file store/articles-dir rel-path)]]
      (fs/copy+ exist-file article-file)))
  (proc "chmod" "-R" "g+w" (path store/git-dir)))

(defn -main [& args]
  (when-not (fs/directory? exist-dir)
    (when-not (fs/exists? exist-export-file)
      (when-let [last-backup (query-last-backup)]
        (proc "scp" (str exist-db-host ":" last-backup) (path exist-export-file))))
    (fs/mkdirs exist-dir)
    (proc "unzip" (path exist-export-file) "-d" (path exist-dir)))

  (when (fs/directory? exist-dir)
    (when-not (fs/directory? store/git-dir)
      (proc "git" "clone" (config :git-repo) (path store/git-dir)))
    (when (empty? (store/xml-files store/git-dir))
      (copy-articles-to-git)
      (git/commit))))

