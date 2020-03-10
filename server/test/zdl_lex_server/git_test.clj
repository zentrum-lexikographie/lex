(ns zdl-lex-server.git-test
  (:require [clj-jgit.porcelain :as jgit]
            [clj-jgit.querying :as jgit-query]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [faker.lorem :as lorem]
            [me.raynes.fs :as fs])
  (:import org.eclipse.jgit.transport.RefSpec))

(def test-dir (fs/file "target" "test-data" "git-test"))

(def repo (fs/file test-dir "repo"))

(def master (fs/file test-dir "master"))

(def qa (fs/file test-dir "qa"))

(defn git-env [f]
  (let [repo-uri (.. ^java.io.File repo (toURI) (toString))]
    (fs/mkdirs test-dir)
    (jgit/git-init :dir repo :bare? true)
    (jgit/git-clone repo-uri :dir master)
    (jgit/with-repo master
      (jgit/git-commit repo "Init repo" :allow-empty? true))
    (jgit/git-clone repo-uri :dir qa)
    (jgit/with-repo qa
      (jgit/git-commit repo "Init repo" :allow-empty? true)
      (jgit/git-checkout repo :name "qa"
                         :upstream-mode :track
                         :create-branch? true))
    (f)
    (doseq [dir [repo master qa]]
      (fs/delete-dir dir))))

(defn write-rand-txt-file [f]
  (fs/mkdirs (fs/parent f))
  (spit f (str/join "\n\n" (take 3 (lorem/paragraphs))) :encoding "UTF-8")
  f)

(defn write-git-file [git-dir & path]
  (let [f (apply fs/file (cons git-dir path))]
    (write-rand-txt-file f)
    (jgit/with-repo git-dir
      (jgit/git-add repo ".")
      (jgit/git-commit repo (str "Added " (str/join "/" path))))))

(defn remove-git-file [git-dir & path]
  (let [f (apply fs/file (cons git-dir path))]
    (jgit/with-repo git-dir
      (jgit/git-rm repo (str/join java.io.File/separator path))
      (jgit/git-commit repo (str "Removed " (str/join "/" path))))))

(defn ff-git [git-dir refs]
  (->> 
   (jgit/with-repo git-dir
     (jgit/git-fetch repo :ref-specs ["refs/tags/*:refs/tags/*"
                                      "refs/heads/*:refs/remotes/origin/*"])
     (let [head-before (jgit-query/find-rev-commit repo rev-walk "HEAD")
           merge (jgit/git-merge repo refs)]
       (if (.. merge (getMergeStatus) (isSuccessful))
         (->> (jgit/git-log repo :since head-before)
              (map :id)
              (reverse)
              (mapcat (partial jgit-query/changed-files repo)))
         (throw (ex-info (str merge) {:git-dir git-dir :merge merge})))))
   (vec)
   (log/spy :info)))

(defn push-git [git-dir]
  (jgit/with-repo git-dir
    (jgit/git-push repo)))

(deftest git-workflow
  (write-git-file master "added" "test1.txt")
  (write-git-file master "added" "test2.txt")
  (push-git master)
  (ff-git qa ["origin/master"])
  (write-git-file qa "added" "test1.txt")
  (remove-git-file qa "added" "test2.txt")
  (write-git-file qa "added" "test3.txt")
  (push-git qa)
  (ff-git master ["origin/qa"])
  (write-git-file master "added" "test4.txt")
  (push-git master)
  (ff-git qa ["origin/master"]))

(use-fixtures :each git-env)
