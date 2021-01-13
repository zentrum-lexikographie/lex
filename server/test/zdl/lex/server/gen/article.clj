(ns zdl.lex.server.gen.article
  (:require [clojure.spec.gen.alpha :as gen]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [zdl.lex.article.fs :as afs]
            [zdl.lex.data :as data]
            [zdl.lex.env :refer [getenv]]
            [zdl.lex.fs :refer :all]
            [zdl.lex.git :as git]
            [zdl.lex.server.git :as server-git]))

(def prod-origin
  (getenv "PROD_GIT_ORIGIN" "git@git.zdl.org:zdl/wb.git"))

(def prod-branch
  (getenv "PROD_GIT_BRANCH" "zdl-lex-server/production"))

(def prod-dir
  (data/dir "prod"))

(defn prod-articles
  []
  (seq (sort (afs/files prod-dir))))

(defn clone-prod!
  []
  (when-not (prod-articles)
    (git/sh! prod-dir "clone" "--quiet" prod-origin (path prod-dir)))
  (git/assert-clean prod-dir)
  (when-not (= prod-branch (git/head-ref prod-dir))
    (git/sh! prod-dir "checkout" "--track" (str "origin/" prod-branch))))

(defn pull-prod!
  []
  (clone-prod!)
  (git/sh! prod-dir "pull"))

(defn gen-prod-article-set
  [& args]
  (when-not (prod-articles) (clone-prod!))
  (apply gen/set (gen/elements (prod-articles)) args))

(defn gen-article-set
  [& args]
  (when (.isDirectory (file server-git/dir ".git"))
    (throw (IllegalStateException. "Git data exists")))
  (gen/fmap
   (fn [articles]
     (git/sh! "." "init" "--quiet" (path server-git/dir))
     (log/debugf "Copy %d articles to %s"
                 (count articles)
                 (path server-git/dir))
     (let [article->dest (comp
                          (partial resolve-path (path-obj server-git/dir))
                          (partial relativize (path-obj prod-dir)))]
       (doseq [article articles] (copy article (article->dest article)))
       (git/sh! server-git/dir "add" ".")
       (git/sh! server-git/dir "commit" "-m" "Sets up test")
       (afs/files server-git/dir))))
   (apply gen-prod-article-set args))

(defn generate-article-set
  [[min-articles max-articles]]
  (gen/generate
   (gen-article-set {:min-elements min-articles :max-elements max-articles})))

(defn create-article-set-fixture
  ([]
   (create-article-set-fixture [100 200]))
  ([set-size-range]
   (fn [f]
     (delete! server-git/dir true)
     (generate-article-set set-size-range)
     (f)
     (delete! server-git/dir))))

(use-fixtures :once (create-article-set-fixture [1000 2000]))

(deftest fixtures
  (is (seq (afs/files server-git/dir))))
