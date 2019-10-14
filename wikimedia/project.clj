(defproject org.zdl.lex/wikimedia :lein-v
  :plugins [[lein-modules "0.3.11"]]

  :dependencies [[org.clojure/data.xml "0.2.0-alpha6"]
                 [org.clojure/data.zip "0.1.3"]
                 [org.clojure/data.csv "0.1.4"]
                 [com.h2database/h2 "1.4.199"]
                 [com.layerware/hugsql "0.4.9"]
                 [com.outpace/clj-excel "0.0.9"]
                 [org.sweble.wikitext/swc-parser-lazy "3.1.9"]
                 [org.apache.jena/jena-arq "3.12.0"]
                 [org.zdl.lex/common :version]
                 [org.zdl.lex/corpus :version]]

  :profiles {:uberjar {:aot :all
                       :main zdl-lex-wikimedia.core}}
  :aliases {"build" ["compile"]})
