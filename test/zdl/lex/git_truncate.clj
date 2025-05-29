(ns zdl.lex.git-truncate
  (:require
   [babashka.fs :as fs]
   [clojure.java.process :as p]))

(defn git!
  [dir & args]
  (apply p/exec {:dir (fs/file dir) :out :inherit :err :inherit} "git" args))

(def orig-repo
  "git@git.zdl.org:zdl/wb.git")

(def orig-branch
  "zdl-lex-server/production")

(def truncated-repo
  "git@git.zdl.org:zdl/dict.git")

(def truncated-branch
  "main")

(defn -main
  [orig-dir work-dir]
  (if (fs/directory? orig-dir)
    (git! orig-dir "pull")
    (git! "." "clone" orig-repo orig-dir))
  (git! orig-dir "checkout" orig-branch)
  (when (fs/directory? work-dir) (fs/delete-tree work-dir))
  (fs/copy-tree orig-dir work-dir)
  (fs/delete-tree (fs/file work-dir ".git"))
  (git! work-dir "init" ".")
  (git! work-dir "checkout" "-b" truncated-branch)
  (git! work-dir "add" ".")
  (git! work-dir "commit" "-a" "-m" "Migrated repository")
  (git! work-dir "remote" "add" "origin" truncated-repo)
  (git! work-dir "push" "--force" "-u" "origin" truncated-branch))
