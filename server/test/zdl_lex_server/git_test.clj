(ns zdl-lex-server.git-test
  (:require [clj-jgit.porcelain :as jgit]
            [clj-jgit.querying :as jgit-query]
            [clojure.test :refer :all]
            [faker.lorem :as lorem]
            [me.raynes.fs :as fs]
            [midje.sweet :as midje]
            [clojure.string :as str]))

(def test-dir (fs/file "target" "test-data" "git-test"))

(def repo (fs/file test-dir "repo"))

(def clones (map #(fs/file test-dir (str "clone-" %)) (range 2)))

(defn git-env [f]
  (let [repo-uri (.. ^java.io.File repo (toURI) (toString))]
    (fs/mkdirs test-dir)
    (jgit/git-init :dir repo :bare? true)
    (doseq [clone clones] (jgit/git-clone repo-uri :dir clone))
    (f)
    (doseq [dir (cons repo clones)]
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

(defn pull-git [git-dir & args]
  (jgit/with-repo git-dir
    (let [get-rev #(first (jgit-query/rev-list repo rev-walk))
          rev-before (get-rev)
          _ (apply jgit/git-pull (cons repo args))
          rev-after (get-rev)]
      (if rev-before
        (jgit-query/changed-files-between-commits repo rev-before rev-after)
        (jgit-query/changed-files repo rev-after)))))

(defn push-git [git-dir]
  (jgit/with-repo git-dir
    (jgit/git-push repo)))

(deftest git-merge
  (let [clone-1 (first clones)
        clone-2 (second clones)]
    (write-git-file clone-1 "added" "test1.txt")
    (push-git clone-1)
    (println (pull-git clone-2))
    (write-git-file clone-2 "added" "test1.txt")
    (write-git-file clone-2 "added" "test2.txt")
    (write-git-file clone-2 "added" "test1.txt")
    (remove-git-file clone-2 "added" "test1.txt")
    (push-git clone-2)
    (println (pull-git clone-1))
    (write-git-file clone-2 "added" "test1.txt")
    (push-git clone-2)
    (println (pull-git clone-1))))

(use-fixtures :each git-env)
