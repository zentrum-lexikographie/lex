(ns zdl.lex.build
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [with-sh-dir]]
            [clojure.string :as str]
            [clojure.tools.deps.alpha.util.dir :as deps-dir]
            [uberdeps.api :as uberdeps]
            [zdl.lex.fs :refer [file path clear-dir!]]
            [zdl.lex.build.fs :refer :all]
            [zdl.lex.git :as git]
            [zdl.lex.sh :refer [sh!]]
            [zdl.lex.build.version :as version]
            [zdl.xml.validate :as xv]
            [clojure.tools.logging :as log])
  (:import java.io.File))

(defn compile-rnc!
  []
  (let [source (file oxygen-dir "framework" "rnc")
        dest (file oxygen-dir "framework" "rng")
        rnc (file source "DWDSWB.rnc")
        rng (file dest "DWDSWB.rng")
        sch (file dest "DWDSWB.sch.xsl")]
    (log/info [(path rnc) (path dest)])
    (clear-dir! dest)
    (xv/rnc->rng rnc rng)
    (xv/rng->sch-xslt rng sch)))

(defn uberjar!
  [base-dir & args]
  (log/info (concat [(path base-dir)] args))
  (let [deps (-> (file base-dir "deps.edn") slurp edn/read-string)]
    (binding [uberdeps/level :error]
      (deps-dir/with-dir base-dir
        (apply uberdeps/package (concat [deps] args))))))

(defn package-client!
  []
  (let [classes (file client-dir "classes")]
    (clear-dir! classes)
    (with-sh-dir client-dir
      (sh! "clojure" "-A:dev:prod" (path scripts-dir "compile_client.clj")))
    (uberjar! client-dir (path client-jar) {:aliases #{:prod}})
    (clear-dir! classes)))

(defn package-cli!
  []
  (let [classes (file cli-dir "classes")]
    (clear-dir! classes)
    (with-sh-dir cli-dir
      (sh! "clojure" "-A:dev:prod" (path scripts-dir "compile_cli.clj")))
    (uberjar! cli-dir (path cli-jar)
              {:aliases #{:prod} :main-class "zdl.lex.cli"})
    (clear-dir! classes)))

(defn package-server!
  []
  (let [classes (file server-dir "classes")]
    (clear-dir! classes)
    (with-sh-dir server-dir
      (sh! "clojure" "-A:dev:prod" (path scripts-dir "compile_server.clj")))
    (uberjar! server-dir (path server-jar)
              {:aliases #{:prod} :main-class "zdl.lex.server"})
    (clear-dir! classes)))

(defn client!
  []
  (version/write!)
  (compile-rnc!)
  (package-client!))

(defn cli!
  []
  (package-cli!))

(defn server!
  []
  (package-server!))

(defn docker!
  []
  (git/assert-clean project-dir)
  (let [version (version/current)]
    (doseq [module ["solr" "server"]]
      (let [tag (str/join \/ ["lex.dwds.de" "zdl-lex" module])
            tag (str tag ":" (version/current))]
        (with-sh-dir (file project-dir "docker" module)
          (sh! "docker" "build" "--rm" "--force-rm" "-t" tag "."))))))

(defn -main
  [& [mode]]
  (try
    (let [release? (= "release" mode)]
      (when release? (version/tag-next!))
      (client!)
      (cli!)
      (server!)
      (when release? (docker!)))
    (finally (shutdown-agents))))
