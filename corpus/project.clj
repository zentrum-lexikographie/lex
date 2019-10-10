(defproject org.zdl.lex/corpus :lein-v
  :plugins [[lein-modules "0.3.11"]]

  :dependencies [[org.zdl.lex/common :version]
                 [clj-http "3.10.0" :exclusions [riddley commons-logging]]
                 [aleph "0.4.6"]
                 [gloss "0.2.6"]
                 [cheshire "5.9.0"]
                 [throttler "1.0.0" :exclusions [org.clojure/core.async]]]
  :repl-options {:init-ns zdl-lex-corpus.dev})
