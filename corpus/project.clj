(def version (slurp "../VERSION"))

(defproject zdl-lex-corpus version
  :description "Client library for DDC/TCP servers."
  :license {:name "LGPL-3.0"
            :url "https://www.gnu.org/licenses/lgpl-3.0.html"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [clj-http "3.10.0" :exclusions [riddley commons-logging]]
                 [aleph "0.4.6"]
                 [gloss "0.2.6"]
                 [cheshire "5.9.0"]
                 [throttler "1.0.0" :exclusions [org.clojure/core.async]]
                 [zdl-lex-common ~version]]
  :repl-options {:init-ns zdl-lex-corpus.dev}
  :plugins [[lein-environ "1.1.0"]]
  :profiles {:dev [:project/dev :profiles/dev]})
