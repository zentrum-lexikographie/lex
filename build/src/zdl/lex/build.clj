(ns zdl.lex.build
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [with-sh-dir with-sh-env]]
            [clojure.string :as str]
            [clojure.stacktrace :refer [print-stack-trace]]
            [clojure.tools.deps.alpha.util.dir :as deps-dir]
            [uberdeps.api :as uberdeps]
            [zdl.lex.env :refer [getenv]]
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

(defn java-8-env
  []
  (let [env (into {} (System/getenv))
        env-path (get env "PATH")]
    (->>
     (when-let [java-8-home (getenv "JAVA8_HOME")]
       {"PATH" (str (path java-8-home "bin") ":" env-path)})
     (merge env))))

(defn java-8?
  []
  (->>
   (sh! "clojure" "-M"
        "-e" "(.startsWith (System/getProperty \"java.version\") \"1.8\")")
   :out str/trim (= "true")))

(defn package-client!
  []
  (let [classes (file client-dir "classes")]
    (clear-dir! classes)
    (version/write!)
    (compile-rnc!)
    (with-sh-env (java-8-env)
      (when-not (java-8?)
        (throw (ex-info "Java v8 required for building client" {})))
      (with-sh-dir client-dir
        (sh! "clojure" "-M:dev:prod" (path scripts-dir "compile_client.clj"))))
    (uberjar! client-dir (path client-jar) {:aliases #{:prod}})
    (clear-dir! classes)))

(defn package-cli!
  []
  (let [classes (file cli-dir "classes")]
    (clear-dir! classes)
    (with-sh-dir cli-dir
      (sh! "clojure" "-M:dev:prod" (path scripts-dir "compile_cli.clj")))
    (uberjar! cli-dir (path cli-jar)
              {:aliases #{:prod} :main-class "zdl.lex.cli"})
    (clear-dir! classes)))

(defn package-server!
  []
  (let [classes (file server-dir "classes")]
    (clear-dir! classes)
    (with-sh-dir server-dir
      (sh! "clojure" "-M:dev:prod" (path scripts-dir "compile_server.clj")))
    (uberjar! server-dir (path server-jar)
              {:aliases #{:prod} :main-class "zdl.lex.server"})
    (clear-dir! classes)))

(defn build!
  [_]
  (try
    (package-client!)
    (package-cli!)
    (package-server!)
    (System/exit 0)
    (catch Throwable t
      (print-stack-trace t)
      (System/exit 1))))
