(ns zdl-lex-build.version
  (:require [clj-jgit.internal :as jgit-int]
            [clj-jgit.porcelain :as jgit]
            [clj-jgit.querying :as jgit-query]
            [me.raynes.fs :as fs]
            [clojure.string :as str]
            [zdl-lex-common.log :as log]
            [zdl-lex-common.util :refer [file]]))

(defn last-version [repo-path]
  (jgit/with-repo repo-path
    (let [branch (jgit/git-branch-current repo)
          branch (str "refs/heads/" branch)]
      (some->>
       (for [[tag ref] (jgit-int/get-refs repo "refs/tags/")]
         (assoc (->> (.getObjectId ref)
                     (jgit-query/find-rev-commit repo rev-walk)
                     (jgit-query/commit-info repo))
                :tag tag))
       (filter (comp (partial some #{branch}) :branches))
       (filter (comp #(str/starts-with? % "v") :tag))
       (last)))))

(defn -main [& args]
  (log/configure)
  (let [version (some-> (last-version "..")
                        (:tag) (str/replace #"^v" "")
                        (or "000000.00.00"))
        client-version-file (file "../client/src/version.edn")
        server-version-file (file "../server/src/version.edn")]
    (doseq [f [client-version-file server-version-file]]
      (->> {:version version} pr-str (spit f)))))

(comment (-main))
