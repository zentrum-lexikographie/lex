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
  (getenv "TEST_GIT_ORIGIN" "git@git.zdl.org:zdl/wb.git"))

(def prod-branch
  (getenv "TEST_GIT_BRANCH" "zdl-lex-server/production"))

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

(def ^:dynamic *min-articles*
  1000)

(def ^:dynamic *max-articles*
  2000)

(defn gen-prod-article-set
  []
  (pull-prod!)
  (gen/set
   (gen/elements (prod-articles))
   {:min-elements *min-articles* :max-elements *max-articles*}))

(defn gen-article-set
  []
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
       (afs/files server-git/dir)))
   (gen-prod-article-set)))

(comment
  (binding [*min-articles* 100000 *max-articles* 200000]
    (delete! server-git/dir true)
    (gen/generate (gen-article-set))))

(defn article-set-fixture
  [f]
  (delete! server-git/dir true)
  (gen/generate (gen-article-set))
  (f)
  (delete! server-git/dir))

(use-fixtures :once article-set-fixture)

(deftest fixtures
  (is (seq (afs/files server-git/dir))))
