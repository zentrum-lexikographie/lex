(ns zdl.lex.fixture.git
  (:require
   [clojure.test.check.generators :as gen]
   [clojure.tools.logging :as log]
   [zdl.lex.fixture.system :as fixture.system]
   [zdl.lex.fs :as fs]
   [zdl.lex.git :as git]
   [zdl.lex.server.git :as server.git]
   [clojure.string :as str]))

(def prod-dir
  (fs/ensure-dirs fixture.system/test-data "prod"))

(def prod-origin
  "git@git.zdl.org:zdl/wb.git")

(def prod-branch
  "zdl-lex-server/production")

(defn prod-articles
  []
  (seq (filter (every-pred fs/file? server.git/article?) (file-seq prod-dir))))

(defn clone-prod!
  []
  (when-not (prod-articles)
    (git/sh! prod-dir "clone" "--quiet" prod-origin (fs/path prod-dir)))
  (git/assert-clean prod-dir)
  (when-not (= prod-branch (git/head-ref prod-dir))
    (git/sh! prod-dir "checkout" "--track" (str "origin/" prod-branch))))

(defn pull-prod!
  []
  (clone-prod!)
  (git/sh! prod-dir "pull"))

(defn gen-prod-articles
  ([]
   (gen-prod-articles
    (prod-articles)
    {:min-elements 1000 :max-elements 2000}))
  ([articles opts]
   (pull-prod!)
   (gen/set (gen/elements articles) opts)))

(defn gen-articles
  [prod-articles]
  (let [git-dir fixture.system/git-dir]
    (when (fs/directory? git-dir ".git")
      (throw (IllegalStateException. "Git data exists")))
    (gen/fmap
     (fn [articles]
       (git/sh! "." "init" "--quiet" git-dir)
       (log/infof "Copy fixture of %d articles to %s" (count articles) git-dir)
       (let [article->dest (comp
                            (partial fs/resolve-path (fs/path-obj git-dir))
                            (partial fs/relativize (fs/path-obj prod-dir)))]
         (doseq [article articles] (fs/copy article (article->dest article)))
         (git/sh! git-dir "add" ".")
         (git/sh! git-dir "commit" "-m" "Sets up test")
         (server.git/articles git-dir)))
     prod-articles)))

(defn articles
  [f]
  (fs/delete! (fs/file fixture.system/git-dir) true)
  (gen/generate (gen-articles (gen-prod-articles)))
  (f))

(defn newer-articles
  [f]
  (fs/delete! (fs/file fixture.system/git-dir) true)
  (gen/generate
   (gen-articles
    (gen-prod-articles
     (filter #(str/includes? (str %) "Neuartikel") (prod-articles))
     {:min-elements 1000 :max-elements 2000})))
  (f))
