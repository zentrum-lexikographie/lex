(ns zdl-lex-server.exist2git
  (:require [clojure.string :as str]
            [me.raynes.fs :as fs]
            [zdl-lex-server.git :as git]
            [zdl-lex-server.store :as store]))

(def exist-db-host "spock.dwds.de")

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
  (let [exist-data-dir (fs/file store/exist-dir "db" "dwdswb" "data")
        exist-data-path-length (-> exist-data-dir path count inc)]
    (doseq [exist-file (store/xml-files exist-data-dir)
            :let [rel-path (-> exist-file path (.substring exist-data-path-length))
                  article-file (fs/file store/articles-dir rel-path)]]
      (fs/copy+ exist-file article-file)))
  (proc "chmod" "-R" "g+w" (path store/git-checkout-dir)))

(defn -main [& args]
  (when-not (fs/directory? store/exist-dir)
    (when-not (fs/exists? store/exist-export-file)
      (when-let [last-backup (query-last-backup)]
        (proc "scp" (str exist-db-host ":" last-backup)
              (path store/exist-export-file))))
    (fs/mkdirs store/exist-dir)
    (proc "unzip" (path store/exist-export-file) "-d" (path store/exist-dir)))

  (when (fs/directory? store/exist-dir)
    (when-not (fs/directory? store/git-repo-dir)
      (proc "git" "init" "--bare" (path store/git-repo-dir)))
    (when-not (fs/directory? store/git-checkout-dir)
      (proc "git" "clone" "--depth=1"
            (str "file://" (path store/git-repo-dir))
            (path store/git-checkout-dir)))
    (when (empty? (store/xml-files store/git-checkout-dir))
      (copy-articles-to-git)
      (git/commit))))

