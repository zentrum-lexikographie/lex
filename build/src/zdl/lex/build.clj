(ns zdl.lex.build
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.deps.alpha.util.dir :as deps-dir]
            [uberdeps.api :as uberdeps]
            [zdl.lex.fs :refer :all]
            [zdl.lex.sh :as sh]
            [zdl.lex.version :as version]
            [zdl-xml.validate :as xv]
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
    (sh/run! "clojure" "-A:dev:prod"
             (path scripts-dir "compile_client.clj") :dir client-dir)
    (uberjar! client-dir (path client-jar) {:aliases #{:prod}})
    (clear-dir! classes)))

(defn package-server!
  []
  (let [classes (file server-dir "classes")]
    (clear-dir! classes)
    (sh/run! "clojure" "-A:dev:prod"
             (path scripts-dir "compile_server.clj") :dir server-dir)
    (uberjar! server-dir (path server-jar) {:aliases #{:prod}})
    (clear-dir! classes)))

(defn client!
  []
  (version/write!)
  (compile-rnc!)
  (package-client!))

(defn server!
  []
  (package-server!))

(defn docker!
  []
  (let [tag (str/join \/ ["lex.dwds.de" "zdl-api" "gateway"])
        tag (str tag ":" (version/current))]
    (sh/run! "docker" "build" "--rm" "--force-rm" "-t" tag ".")))

(defn -main
  [& args]
  (try
    (client!)
    (server!)
    (finally (shutdown-agents))))
