(ns zdl.lex.server.gen
  (:require [clojure.spec.gen.alpha :as gen]
            [clojure.test :refer :all]
            [zdl.lex.article.fs :as afs]
            [zdl.lex.data :as data]
            [zdl.lex.env :refer [getenv]]
            [zdl.lex.fs :refer :all]
            [zdl.lex.git :as git]
            [zdl.lex.server.git :as server-git]))

(def prod-origin
  (delay (getenv "ZDL_LEX_PROD_GIT_ORIGIN" "git@git.zdl.org:zdl/wb.git")))

(def prod-branch
  (delay (getenv "ZDL_LEX_PROD_GIT_BRANCH" "zdl-lex-server/production")))

(def prod-dir
  (delay (data/dir "prod")))

(defn prod-articles
  []
  (seq (sort (afs/files @prod-dir))))

(defn clone-prod!
  []
  (let [dir @prod-dir
        f (file dir)
        path (path dir)
        origin @prod-origin
        branch @prod-branch]
    (when-not (prod-articles)
      (git/sh! dir "clone" "--quiet" origin path))
    (git/assert-clean dir)
    (when-not (= branch (git/head-ref dir))
      (git/sh! dir "checkout" "--track" (str "origin/" branch)))))

(defn pull-prod!
  []
  (clone-prod!)
  (git/sh! @prod-dir "pull"))

(defn gen-prod-article-set
  [& args]
  (when-not (prod-articles) (clone-prod!))
  (apply gen/set (gen/elements (prod-articles)) args))

(defn gen-article-set
  [& args]
  (when (.isDirectory (file @server-git/dir ".git"))
    (throw (IllegalStateException. "Git data exists")))
  (gen/fmap
   (fn [articles]
     (let [git-dir @server-git/dir]
       (git/sh! "." "init" "--quiet" (path git-dir))
       (let [article->dest (comp
                            (partial resolve-path (path-obj git-dir))
                            (partial relativize (path-obj @prod-dir)))]
         (doseq [article articles] (copy article (article->dest article)))
         (git/sh! git-dir "add" ".")
         (git/sh! git-dir "commit" "-m" "Sets up test")
         (afs/files git-dir))))
   (apply gen-prod-article-set args)))

(def ^:dynamic *article-set-min-elements* 100)

(def ^:dynamic *article-set-max-elements* 200)

(defn article-set-fixture
  [f]
  (delete! @server-git/dir)
  (gen/generate
   (gen-article-set {:min-elements *article-set-min-elements*
                     :max-elements *article-set-max-elements*}))
  (f)
  (delete! @server-git/dir))

(use-fixtures :once article-set-fixture)

(deftest fixtures
  (is (seq (afs/files @server-git/dir))))
