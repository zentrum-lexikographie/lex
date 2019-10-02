(def version (slurp "../VERSION"))

(defproject zdl-lex-wikimedia version
  :description "Parses (German) Wiktionary dumps."
  :license {:name "LGPL-3.0"
            :url "https://www.gnu.org/licenses/lgpl-3.0.html"}

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/data.xml "0.2.0-alpha6"]
                 [org.clojure/data.zip "0.1.3"]
                 [org.clojure/data.csv "0.1.4"]
                 [com.h2database/h2 "1.4.199"]
                 [com.layerware/hugsql "0.4.9"]
                 [com.outpace/clj-excel "0.0.9"]
                 [org.sweble.wikitext/swc-parser-lazy "3.1.9"]
                 [org.apache.jena/jena-arq "3.12.0"]
                 [zdl-lex-common ~version]]
  :main ^:skip-aot zdl-lex-wikimedia.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}
  :plugins [[lein-environ "1.1.0"]])
