(ns zdl.lex.build
  (:require [clojure.edn :as edn]
            [clojure.java.shell :refer [with-sh-env]]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [uberdeps.api :as uberdeps]
            [zdl.lex.build.fs :refer [oxygen-dir scripts-dir cli-jar client-jar server-jar]]
            [zdl.lex.env :refer [getenv]]
            [zdl.lex.fs :refer [clear-dir! file path]]
            [zdl.lex.sh :refer [sh!]]
            [zdl.lex.util :refer [exec!]]
            [zdl.xml.validate :as xv]))

(def deps
  (-> (file "deps.edn") slurp edn/read-string))

(defn compile-rnc!
  []
  (let [source (file oxygen-dir "framework" "rnc")
        dest (file oxygen-dir "framework" "rng")
        rnc (file source "DWDSWB.rnc")
        rng (file dest "DWDSWB.rng")
        sch (file dest "DWDSWB.sch.xsl")]
    (log/info "Transpiling Artikel-XML schema (RNC -> RNG)")
    (clear-dir! dest)
    (xv/rnc->rng rnc rng)
    (xv/rng->sch-xslt rng sch)))

(defn uberjar!
  [& args]
  (log/debug args)
  (let [deps (-> (file "deps.edn") slurp edn/read-string)]
    (binding [uberdeps/level :error
              uberdeps/exclusions (conj uberdeps/exclusions #"^\.env$")]
      (apply uberdeps/package (concat [deps] args)))))

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

(def client-jar-exclusions
  (conj uberdeps/exclusions
        #"^plugin/.*"
        #"^plugin\.dtd"
        #"^test-project\.xpr"
        #"^updateSite\.xml"))

(defn package-client!
  []
  (let [classes (file "classes" "client")]
    (clear-dir! classes)
    (compile-rnc!)
    (log/info "Compiling Oxygen XML Editor plugin (client)")
    (with-sh-env (java-8-env)
      (when-not (java-8?)
        (throw (ex-info "Java v8 required for building client" {})))
      (sh! "clojure" "-M:dev:client:prod-client"
           (path scripts-dir "compile_client.clj")))
    (log/info "Packaging Oxygen XML Editor plugin (client)")
    (binding [uberdeps/level      :error
              uberdeps/exclusions client-jar-exclusions]
      (uberdeps/package deps (path client-jar)
                        {:aliases #{:client :prod-client}}))
    (clear-dir! classes)))

(defn package-cli!
  []
  (let [classes (file "classes" "cli")]
    (log/info "Compiling CLI")
    (clear-dir! classes)
    (sh! "clojure" "-M:prod-cli" (path scripts-dir "compile_cli.clj"))
    (log/info "Packaging CLI")
    (binding [uberdeps/level :error]
      (uberdeps/package deps (path cli-jar)
                        {:aliases    #{:prod-cli}
                         :main-class "zdl.lex.cli"}))
    (clear-dir! classes)))

(defn package-server!
  []
  (let [classes (file "classes" "server")]
    (log/info "Compiling server")
    (clear-dir! classes)
    (sh! "clojure" "-M:server:prod-server" (path scripts-dir "compile_server.clj"))
    (log/info "Packaging server")
    (binding [uberdeps/level :error]
      (uberdeps/package deps (path server-jar)
                        {:aliases    #{:server :prod-server}
                         :main-class "zdl.lex.server"}))
    (clear-dir! classes)))

(defn build
  [_]
  (package-client!)
  (package-cli!))

(def build!
  (partial exec! build))
