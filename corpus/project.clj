(defproject zdl-lex-corpus :lein-v
  :description "Client library for DDC/TCP servers."
  :license {:name "LGPL-3.0"
            :url "https://www.gnu.org/licenses/lgpl-3.0.html"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [clj-http "3.10.0" :exclusions [riddley commons-logging]]
                 [aleph "0.4.6"]
                 [gloss "0.2.6"]
                 [cheshire "5.9.0"]
                 [throttler "1.0.0" :exclusions [org.clojure/core.async]]
                 [zdl-lex-common nil]]
  :repl-options {:init-ns zdl-lex-corpus.dev}
  :prep-tasks [["v" "cache" "src" "edn"] "javac" "compile"]
  :middleware [leiningen.v/version-from-scm
               leiningen.v/dependency-version-from-scm
               leiningen.v/add-workspace-data]
  :plugins [[lein-environ "1.1.0"]
            [com.roomkey/lein-v "7.1.0"]]
  :profiles {:dev [:project/dev :profiles/dev]})
