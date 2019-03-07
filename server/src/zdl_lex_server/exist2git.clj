(ns zdl-lex-server.exist2git
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [me.raynes.fs :as fs]
            [taoensso.timbre :as timbre]))

(def exist-db-host "spock.dwds.de")

(def base-dir (fs/parent fs/*cwd*))
(def data-dir (fs/file base-dir "data"))

(def exist-export-file (fs/file data-dir "exist-db-export.zip"))
(def exist-dir (fs/file data-dir "exist-db"))

(def git-repo-dir (fs/file data-dir "repo.git"))
(def git-checkout-dir (fs/file data-dir "git"))

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

(defn xml-files [dir]
  (->> (file-seq dir)
       (map #(.getAbsolutePath %))
       (filter #(.endsWith % ".xml"))
       (remove #(.endsWith % "__contents__.xml"))
       (remove #(.endsWith % "indexedvalues.xml"))
       (remove #(.contains % ".git"))
       (map fs/file)))

(defn copy-articles-to-git []
  (let [exist-data-dir (fs/file exist-dir "db" "dwdswb" "data")
        exist-data-path-length (-> exist-data-dir path count inc)]
    (doseq [article-file (xml-files exist-data-dir)
            :let [rel-path (-> article-file path (.substring exist-data-path-length))
                  git-file (fs/file git-checkout-dir "articles" rel-path)]]
      (fs/copy+ article-file git-file)))
  (proc "chmod" "-R" "g+w" (path git-checkout-dir)))

(defn -main [& args]
  (when-not (fs/directory? exist-dir)
    (when-not (fs/exists? exist-export-file)
      (when-let [last-backup (query-last-backup)]
        (proc "scp" (str exist-db-host ":" last-backup) (path exist-export-file))))
    (when (fs/exists? exist-export-file)
      (fs/mkdirs exist-dir)
      (proc "unzip" (path exist-export-file) "-d" (path exist-dir))))

  (when (fs/directory? exist-dir)
    (when-not (fs/directory? git-repo-dir)
      (proc "git" "init" "--bare" (path git-repo-dir)))
    (when-not (fs/directory? git-checkout-dir)
      (proc "git" "clone" "--depth=1"
            (str "file://" (path git-repo-dir)) (path git-checkout-dir)))
    (when (empty? (xml-files git-checkout-dir))
      (copy-articles-to-git))))

