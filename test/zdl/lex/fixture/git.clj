(ns zdl.lex.fixture.git
  (:require [clojure.test.check.generators :as gen]
            [clojure.test :refer [use-fixtures deftest is]]
            [clojure.tools.logging :as log]
            [zdl.lex.article :as article]
            [zdl.lex.data :as data]
            [zdl.lex.env :refer [getenv]]
            [zdl.lex.fs :refer [file path path-obj delete! resolve-path relativize copy]]
            [zdl.lex.git :as git]
            [zdl.lex.server.git :as server.git]))

(def prod-origin
  (getenv "TEST_GIT_ORIGIN" "git@git.zdl.org:zdl/wb.git"))

(def prod-branch
  (getenv "TEST_GIT_BRANCH" "zdl-lex-server/production"))

(def prod-dir
  (data/dir "prod"))

(defn prod-articles
  []
  (seq (sort (article/files prod-dir))))

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

(defn gen-prod-articles
  ([]
   (gen-prod-articles {:min-elements 1000 :max-elements 2000}))
  ([opts]
   (pull-prod!)
   (gen/set (gen/elements (prod-articles)) opts)))

(defn gen-articles
  []
  (when (.isDirectory (file server.git/dir ".git"))
    (throw (IllegalStateException. "Git data exists")))
  (gen/fmap
   (fn [articles]
     (git/sh! "." "init" "--quiet" (path server.git/dir))
     (log/debugf "Copy %d articles to %s"
                 (count articles)
                 (path server.git/dir))
     (let [article->dest (comp
                          (partial resolve-path (path-obj server.git/dir))
                          (partial relativize (path-obj prod-dir)))]
       (doseq [article articles] (copy article (article->dest article)))
       (git/sh! server.git/dir "add" ".")
       (git/sh! server.git/dir "commit" "-m" "Sets up test")
       (article/files server.git/dir)))
   (gen-prod-articles)))

(defn articles
  [f]
  (delete! server.git/dir true)
  (gen/generate (gen-articles))
  (f))

(use-fixtures :once articles)

(deftest articles-generated
  (is (seq (article/files server.git/dir))))
